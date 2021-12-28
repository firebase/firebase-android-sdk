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

package com.google.firebase.app.distribution;

import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskException;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskResult;

import android.content.Context;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.internal.LogWrapper;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.jar.JarFile;
import javax.net.ssl.HttpsURLConnection;

/** Class that handles updateApp functionality for APKs in {@link FirebaseAppDistribution}. */
class ApkUpdater {
  private static final int UPDATE_INTERVAL_MS = 250;
  private static final String TAG = "UpdateApkClient:";
  private static final String REQUEST_METHOD = "GET";

  private TaskCompletionSource<File> downloadTaskCompletionSource;
  private final Executor taskExecutor; // Executor to run task listeners on a background thread
  private final FirebaseApp firebaseApp;
  private final ApkInstaller apkInstaller;
  private final FirebaseAppDistributionNotificationsManager appDistributionNotificationsManager;

  @GuardedBy("updateTaskLock")
  private UpdateTaskImpl cachedUpdateTask;

  private final Object updateTaskLock = new Object();

  public ApkUpdater(@NonNull FirebaseApp firebaseApp, @NonNull ApkInstaller apkInstaller) {
    this(
        Executors.newSingleThreadExecutor(),
        firebaseApp,
        apkInstaller,
        new FirebaseAppDistributionNotificationsManager(firebaseApp.getApplicationContext()));
  }

  @VisibleForTesting
  public ApkUpdater(
      @NonNull Executor taskExecutor,
      @NonNull FirebaseApp firebaseApp,
      @NonNull ApkInstaller apkInstaller,
      @NonNull FirebaseAppDistributionNotificationsManager appDistributionNotificationsManager) {
    this.appDistributionNotificationsManager = appDistributionNotificationsManager;
    this.taskExecutor = taskExecutor;
    this.firebaseApp = firebaseApp;
    this.apkInstaller = apkInstaller;
  }

  UpdateTaskImpl updateApk(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showNotification) {
    synchronized (updateTaskLock) {
      if (cachedUpdateTask != null && !cachedUpdateTask.isComplete()) {
        return cachedUpdateTask;
      }

      cachedUpdateTask = new UpdateTaskImpl();
    }

    downloadApk(newRelease, showNotification)
        // Using onSuccess task to ensure that all install errors get cascaded to the Failure
        // listener down below
        .addOnSuccessListener(
            taskExecutor,
            file ->
                apkInstaller
                    .installApk(file.getPath())
                    .addOnFailureListener(
                        taskExecutor,
                        e -> {
                          LogWrapper.getInstance().e(TAG + "Newest release failed to install.", e);
                          postUpdateProgress(
                              file.length(),
                              file.length(),
                              UpdateStatus.INSTALL_FAILED,
                              showNotification);
                          setUpdateTaskCompletionErrorWithDefault(
                              e,
                              new FirebaseAppDistributionException(
                                  Constants.ErrorMessages.APK_INSTALLATION_FAILED,
                                  FirebaseAppDistributionException.Status.INSTALLATION_FAILURE));
                        })
                    .addOnSuccessListener(
                        taskExecutor,
                        unused -> {
                          synchronized (updateTaskLock) {
                            safeSetTaskResult(cachedUpdateTask);
                          }
                        }))
        .addOnFailureListener(
            taskExecutor,
            e -> {
              LogWrapper.getInstance()
                  .e(TAG + "Download or Installation failure for newest release.", e);
              setUpdateTaskCompletionErrorWithDefault(
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
      @NonNull AppDistributionReleaseInternal newRelease, boolean showNotification) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      return downloadTaskCompletionSource.getTask();
    }

    downloadTaskCompletionSource = new TaskCompletionSource<>();

    makeApkDownloadRequest(newRelease, showNotification);
    return downloadTaskCompletionSource.getTask();
  }

  private void makeApkDownloadRequest(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showNotification) {
    taskExecutor.execute(
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
              postUpdateProgress(responseLength, 0, UpdateStatus.PENDING, showNotification);
              String fileName = getApplicationName() + ".apk";
              LogWrapper.getInstance().v(TAG + "Attempting to download to disk");

              downloadToDisk(
                  connection.getInputStream(), responseLength, fileName, showNotification);
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
      InputStream input, long totalSize, String fileName, boolean showNotification) {

    File apkFile = getApkFileForApp(fileName);
    apkFile.delete();
    long bytesDownloaded = 0;
    try (BufferedOutputStream outputStream =
        new BufferedOutputStream(
            firebaseApp
                .getApplicationContext()
                .openFileOutput(
                    fileName,
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N
                        ? Context.MODE_PRIVATE
                        : Context.MODE_WORLD_READABLE))) {

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
              totalSize, bytesDownloaded, UpdateStatus.DOWNLOADING, showNotification);
        }
      }

    } catch (IOException e) {
      postUpdateProgress(
          totalSize, bytesDownloaded, UpdateStatus.DOWNLOAD_FAILED, showNotification);
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
          totalSize, bytesDownloaded, UpdateStatus.DOWNLOAD_FAILED, showNotification);
      setDownloadTaskCompletionError(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NETWORK_ERROR,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }

    // completion
    postUpdateProgress(totalSize, totalSize, UpdateStatus.DOWNLOADED, showNotification);

    safeSetTaskResult(downloadTaskCompletionSource, apkFile);
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
    LogWrapper.getInstance().e(TAG + "Download failed to complete ", e);
    safeSetTaskException(downloadTaskCompletionSource, e);
  }

  private void setDownloadTaskCompletionErrorWithDefault(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setDownloadTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setDownloadTaskCompletionError(defaultFirebaseException);
    }
  }

  private void setUpdateTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateTaskLock) {
      safeSetTaskException(cachedUpdateTask, e);
    }
  }

  private void setUpdateTaskCompletionErrorWithDefault(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setUpdateTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setUpdateTaskCompletionError(defaultFirebaseException);
    }
  }

  @VisibleForTesting
  void postUpdateProgress(
      long totalBytes, long downloadedBytes, UpdateStatus status, boolean showNotification) {
    synchronized (updateTaskLock) {
      cachedUpdateTask.updateProgress(
          UpdateProgress.builder()
              .setApkFileTotalBytes(totalBytes)
              .setApkBytesDownloaded(downloadedBytes)
              .setUpdateStatus(status)
              .build());
    }
    if (showNotification) {
      appDistributionNotificationsManager.updateNotification(totalBytes, downloadedBytes, status);
    }
  }
}
