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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
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

  private final int UPDATE_INTERVAL_MS = 250;
  private static final String TAG = "FADUpdateAppClient";
  private static final String REQUEST_METHOD = "GET";
  private TaskCompletionSource<File> downloadTaskCompletionSource;
  private CancellationTokenSource downloadCancellationTokenSource;
  private final Executor downloadExecutor;
  private TaskCompletionSource<Void> installTaskCompletionSource;
  private final FirebaseApp firebaseApp;

  public UpdateApkClient(@NonNull FirebaseApp firebaseApp) {
    this.downloadExecutor = Executors.newSingleThreadExecutor();
    this.firebaseApp = firebaseApp;
  }

  public void updateApk(
      @NonNull UpdateTaskImpl updateTask,
      @NonNull String downloadUrl,
      @NonNull Activity currentActivity) {

    downloadApk(downloadUrl, updateTask)
        .addOnSuccessListener(
            downloadExecutor,
            file ->
                install(file.getPath(), currentActivity)
                    .addOnSuccessListener(
                        unused -> {
                          postUpdateProgress(
                              updateTask, file.length(), file.length(), UpdateStatus.DOWNLOADED);
                          updateTask.setResult();
                        })
                    .addOnFailureListener(
                        e ->
                            setTaskCompletionErrorWithDefault(
                                updateTask,
                                e,
                                new FirebaseAppDistributionException(
                                    Constants.ErrorMessages.NETWORK_ERROR,
                                    FirebaseAppDistributionException.Status.INSTALLATION_FAILURE))))
        .addOnFailureListener(
            downloadExecutor,
            e ->
                setTaskCompletionErrorWithDefault(
                    updateTask,
                    e,
                    new FirebaseAppDistributionException(
                        Constants.ErrorMessages.NETWORK_ERROR,
                        FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE)));
  }

  @VisibleForTesting
  @NonNull
  Task<File> downloadApk(@NonNull String downloadUrl, UpdateTaskImpl updateTask) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      downloadCancellationTokenSource.cancel();
    }

    downloadCancellationTokenSource = new CancellationTokenSource();
    downloadTaskCompletionSource =
        new TaskCompletionSource<>(downloadCancellationTokenSource.getToken());

    makeApkDownloadRequest(downloadUrl, updateTask);
    return downloadTaskCompletionSource.getTask();
  }

  private void makeApkDownloadRequest(@NonNull String downloadUrl, UpdateTaskImpl updateTask) {
    downloadExecutor.execute(
        () -> {
          try {
            HttpsURLConnection connection = openHttpsUrlConnection(downloadUrl);
            connection.setRequestMethod(REQUEST_METHOD);
            if (connection.getInputStream() == null) {
              setDownloadTaskCompletionError(
                  new FirebaseAppDistributionException(
                      Constants.ErrorMessages.NETWORK_ERROR,
                      FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
            } else {
              long responseLength = connection.getContentLength();
              postUpdateProgress(updateTask, responseLength, 0, UpdateStatus.PENDING);
              String fileName = getApplicationName() + ".apk";
              downloadToDisk(connection.getInputStream(), responseLength, fileName, updateTask);
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
      InputStream input, long totalSize, String fileName, UpdateTaskImpl updateTask) {

    File apkFile = getApkFileForApp(fileName);
    apkFile.delete();
    try (BufferedOutputStream outputStream =
        new BufferedOutputStream(
            firebaseApp.getApplicationContext().openFileOutput(fileName, Context.MODE_PRIVATE))) {

      byte[] data = new byte[8 * 1024];
      int readSize = input.read(data);
      long downloadedSize = 0;
      long lastMsUpdated = 0;

      while (readSize != -1) {
        outputStream.write(data, 0, readSize);
        downloadedSize += readSize;
        readSize = input.read(data);

        // update progress logic for onProgressListener
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastMsUpdated > UPDATE_INTERVAL_MS) {
          lastMsUpdated = currentTimeMs;
          postUpdateProgress(updateTask, totalSize, downloadedSize, UpdateStatus.DOWNLOADING);
        }
      }
      // completion
      postUpdateProgress(updateTask, totalSize, downloadedSize, UpdateStatus.DOWNLOADED);

    } catch (IOException e) {
      setDownloadTaskCompletionError(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NETWORK_ERROR,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }

    try {
      // check that file is actual JAR
      new JarFile(apkFile);
    } catch (Exception e) {
      setDownloadTaskCompletionError(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.NETWORK_ERROR,
              FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
    }

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
      Log.e(TAG, "Unable to retrieve App name");
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

  private Task<Void> install(String path, Activity currentActivity) {
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    CancellationTokenSource installCancellationTokenSource = new CancellationTokenSource();
    this.installTaskCompletionSource =
        new TaskCompletionSource<>(installCancellationTokenSource.getToken());
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(intent);
    return installTaskCompletionSource.getTask();
  }

  void setInstallationResult(int resultCode) {
    if (resultCode == Activity.RESULT_OK) {
      installTaskCompletionSource.setResult(null);
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

  private void setTaskCompletionError(
      UpdateTaskImpl updateTask, FirebaseAppDistributionException e) {
    if (updateTask != null && !updateTask.isComplete()) {
      updateTask.setException(e);
    }
  }

  private void setTaskCompletionErrorWithDefault(
      UpdateTaskImpl updateTask,
      Exception e,
      FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setTaskCompletionError(updateTask, (FirebaseAppDistributionException) e);
    } else {
      setTaskCompletionError(updateTask, defaultFirebaseException);
    }
  }

  private void postUpdateProgress(
      UpdateTaskImpl updateTask, long totalBytes, long downloadedBytes, UpdateStatus status) {
    updateTask.updateProgress(
        UpdateProgress.builder()
            .setApkFileTotalBytes(totalBytes)
            .setApkBytesDownloaded(downloadedBytes)
            .setUpdateStatus(status)
            .build());
  }
}
