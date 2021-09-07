// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution;

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;
import static com.google.firebase.appdistribution.internal.ReleaseIdentificationUtils.calculateApkInternalCodeHash;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;
import javax.net.ssl.HttpsURLConnection;

/** Client class that handles updateApp functionality for APKs in {@link UpdateAppClient}. */
class UpdateApkClient {
  private static final int UPDATE_INTERVAL_MS = 250;
  private static final String TAG = "UpdateApkClient:";
  private static final String REQUEST_METHOD = "GET";
  private final FirebaseAppDistributionNotificationsManager appDistributionNotificationsManager;

  private TaskCompletionSource<File> downloadTaskCompletionSource;
  private final Executor downloadExecutor;
  private TaskCompletionSource<Void> installTaskCompletionSource;
  private final FirebaseApp firebaseApp;

  @GuardedBy("updateTaskLock")
  private UpdateTaskImpl cachedUpdateTask;

  private ReleaseIdentifierStorage releaseIdentifierStorage;

  @GuardedBy("activityLock")
  private Activity currentActivity;

  private final Object activityLock = new Object();
  private final Object updateTaskLock = new Object();

  public UpdateApkClient(@NonNull FirebaseApp firebaseApp) {
    this.downloadExecutor = Executors.newSingleThreadExecutor();
    this.firebaseApp = firebaseApp;
    this.releaseIdentifierStorage =
        new ReleaseIdentifierStorage(firebaseApp.getApplicationContext());
    this.appDistributionNotificationsManager =
        new FirebaseAppDistributionNotificationsManager(firebaseApp);
  }

  public synchronized UpdateTaskImpl updateApk(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showDownloadNotificationManager) {
    synchronized (updateTaskLock) {
      if (cachedUpdateTask != null && !cachedUpdateTask.isComplete()) {
        return cachedUpdateTask;
      }

      cachedUpdateTask = new UpdateTaskImpl();
    }

    downloadApk(newRelease, showDownloadNotificationManager)
        .addOnSuccessListener(
            downloadExecutor,
            file ->
                install(file.getPath())
                    .addOnFailureListener(
                        e -> {
                          LogWrapper.getInstance().e(TAG + "Newest release failed to install.", e);
                          postInstallationFailure(
                              e, file.length(), showDownloadNotificationManager);
                          setTaskCompletionErrorWithDefault(
                              e,
                              new FirebaseAppDistributionException(
                                  Constants.ErrorMessages.NETWORK_ERROR,
                                  FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
                        }))
        .addOnFailureListener(
            downloadExecutor,
            e -> {
              LogWrapper.getInstance().e(TAG + "Newest release failed to download.", e);
              setTaskCompletionErrorWithDefault(
                  e,
                  new FirebaseAppDistributionException(
                      Constants.ErrorMessages.NETWORK_ERROR,
                      FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
            });

    synchronized (updateTaskLock) {
      return cachedUpdateTask;
    }
  }

  @VisibleForTesting
  @NonNull
  Task<File> downloadApk(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showDownloadNotificationManager) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      return downloadTaskCompletionSource.getTask();
    }

    downloadTaskCompletionSource = new TaskCompletionSource<>();

    makeApkDownloadRequest(newRelease, showDownloadNotificationManager);
    return downloadTaskCompletionSource.getTask();
  }

  private void makeApkDownloadRequest(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showDownloadNotificationManager) {
    downloadExecutor.execute(
        () -> {
          try {
            HttpsURLConnection connection = openHttpsUrlConnection(newRelease.getDownloadUrl());
            connection.setRequestMethod(REQUEST_METHOD);
            if (connection.getInputStream() == null) {
              setDownloadTaskCompletionError(
                  new FirebaseAppDistributionException(
                      Constants.ErrorMessages.NETWORK_ERROR,
                      FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
            } else {
              long responseLength = connection.getContentLength();
              postUpdateProgress(
                  responseLength, 0, UpdateStatus.PENDING, showDownloadNotificationManager);
              String fileName = getApplicationName() + ".apk";
              LogWrapper.getInstance().v(TAG + "Attempting to download to disk");

              downloadToDisk(
                  connection.getInputStream(),
                  responseLength,
                  fileName,
                  newRelease,
                  showDownloadNotificationManager);
            }
          } catch (IOException | FirebaseAppDistributionException e) {
            setDownloadTaskCompletionErrorWithDefault(
                e,
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
          }
        });
  }

  private void downloadToDisk(
      InputStream input,
      long totalSize,
      String fileName,
      AppDistributionReleaseInternal newRelease,
      boolean showDownloadNotificationManager) {

    File apkFile = getApkFileForApp(fileName);
    apkFile.delete();
    long bytesDownloaded = 0;
    try (BufferedOutputStream outputStream =
        new BufferedOutputStream(
            firebaseApp.getApplicationContext().openFileOutput(fileName, Context.MODE_PRIVATE))) {

      byte[] data = new byte[8 * 1024];
      int readSize = input.read(data);
      long lastMsUpdated = 0;

      while (readSize != -1) {
        outputStream.write(data, 0, readSize);
        bytesDownloaded += readSize;
        readSize = input.read(data);

        // update progress logic for onProgressListener
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastMsUpdated > UPDATE_INTERVAL_MS) {
          lastMsUpdated = currentTimeMs;
          postUpdateProgress(
              totalSize,
              bytesDownloaded,
              UpdateStatus.DOWNLOADING,
              showDownloadNotificationManager);
        }
      }

    } catch (IOException e) {
      postUpdateProgress(
          totalSize,
          bytesDownloaded,
          UpdateStatus.DOWNLOAD_FAILED,
          showDownloadNotificationManager);
      setDownloadTaskCompletionError(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NETWORK_ERROR,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }

    try {
      // check that file is actual JAR
      new JarFile(apkFile).close();

    } catch (Exception e) {
      postUpdateProgress(
          totalSize,
          bytesDownloaded,
          UpdateStatus.DOWNLOAD_FAILED,
          showDownloadNotificationManager);
      setDownloadTaskCompletionError(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NETWORK_ERROR,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }

    File downloadedFile = new File(firebaseApp.getApplicationContext().getFilesDir(), fileName);

    String internalCodeHash = calculateApkInternalCodeHash(downloadedFile);

    if (internalCodeHash != null) {
      releaseIdentifierStorage.setCodeHashMap(internalCodeHash, newRelease);
    }

    // completion
    postUpdateProgress(
        totalSize, totalSize, UpdateStatus.DOWNLOADED, showDownloadNotificationManager);

    downloadTaskCompletionSource.setResult(apkFile);
  }

  private File getApkFileForApp(String fileName) {
    return new File(firebaseApp.getApplicationContext().getFilesDir(), fileName);
  }

  private String getApplicationName() {
    try {
      Context context = firebaseApp.getApplicationContext();
      return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
    } catch (Exception e) {
      LogWrapper.getInstance().v(TAG + "Unable to retrieve app name");
      return "";
    }
  }

  HttpsURLConnection openHttpsUrlConnection(String downloadUrl)
      throws FirebaseAppDistributionException {
    HttpsURLConnection httpsURLConnection;

    try {
      URL url = new URL(downloadUrl);
      httpsURLConnection = (HttpsURLConnection) url.openConnection();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR, NETWORK_FAILURE, e);
    }
    return httpsURLConnection;
  }

  private void setDownloadTaskCompletionError(FirebaseAppDistributionException e) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      LogWrapper.getInstance().e(TAG + "Download failed to complete ", e);
      downloadTaskCompletionSource.setException(e);
    }
  }

  private void setDownloadTaskCompletionErrorWithDefault(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setDownloadTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setDownloadTaskCompletionError(defaultFirebaseException);
    }
  }

  private Task<Void> install(String path) {
    Activity currentActivity = getCurrentActivity();
    if (currentActivity == null) {
      return Tasks.forException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.APP_BACKGROUNDED,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    CancellationTokenSource installCancellationTokenSource = new CancellationTokenSource();
    this.installTaskCompletionSource =
        new TaskCompletionSource<>(installCancellationTokenSource.getToken());
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(intent);
    LogWrapper.getInstance().v(TAG + "Prompting user with install activity ");
    return installTaskCompletionSource.getTask();
  }

  void setInstallationResult(int resultCode) {
    if (resultCode == Activity.RESULT_OK) {
      installTaskCompletionSource.setResult(null);

      synchronized (updateTaskLock) {
        cachedUpdateTask.setResult();
      }
    } else if (resultCode == Activity.RESULT_CANCELED) {
      installTaskCompletionSource.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.UPDATE_CANCELED,
              FirebaseAppDistributionException.Status.INSTALLATION_CANCELED));
    } else {
      installTaskCompletionSource.setException(
          new FirebaseAppDistributionException(
              "Installation failed with result code: " + resultCode,
              FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
    }
  }

  private void setTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateTaskLock) {
      if (cachedUpdateTask != null && !cachedUpdateTask.isComplete()) {
        cachedUpdateTask.setException(e);
      }
    }
  }

  private void setTaskCompletionErrorWithDefault(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setTaskCompletionError(defaultFirebaseException);
    }
  }

  @VisibleForTesting
  void postUpdateProgress(
      long totalBytes,
      long downloadedBytes,
      UpdateStatus status,
      boolean showDownloadNotificationManager) {
    synchronized (updateTaskLock) {
      cachedUpdateTask.updateProgress(
          UpdateProgress.builder()
              .setApkFileTotalBytes(totalBytes)
              .setApkBytesDownloaded(downloadedBytes)
              .setUpdateStatus(status)
              .build());
    }
    if (showDownloadNotificationManager) {
      appDistributionNotificationsManager.updateNotification(totalBytes, downloadedBytes, status);
    }
  }

  private void postInstallationFailure(
      Exception e, long fileLength, boolean showDownloadNotificationManager) {
    if (e instanceof FirebaseAppDistributionException
        && ((FirebaseAppDistributionException) e).getErrorCode() == Status.INSTALLATION_CANCELED) {
      postUpdateProgress(
          fileLength, fileLength, UpdateStatus.INSTALL_CANCELED, showDownloadNotificationManager);
    } else {
      postUpdateProgress(
          fileLength, fileLength, UpdateStatus.INSTALL_FAILED, showDownloadNotificationManager);
    }
  }

  @Nullable
  Activity getCurrentActivity() {
    synchronized (activityLock) {
      return this.currentActivity;
    }
  }

  void setCurrentActivity(@Nullable Activity activity) {
    synchronized (activityLock) {
      this.currentActivity = activity;
    }
  }
}
