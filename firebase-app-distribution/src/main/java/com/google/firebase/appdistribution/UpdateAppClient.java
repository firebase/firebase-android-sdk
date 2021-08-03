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

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.core.os.HandlerCompat;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;

/** Client class for updateApp functionality in {@link FirebaseAppDistribution}. */
public class UpdateAppClient {

  private int UPDATE_INTERVAL_MS = 250;
  private static final String TAG = "FADUpdateAppClient";
  private static final String REQUEST_METHOD = "GET";
  private TaskCompletionSource<UpdateState> updateAppTaskCompletionSource = null;
  private CancellationTokenSource updateAppCancellationSource;
  private TaskCompletionSource<File> downloadTaskCompletionSource;
  private CancellationTokenSource downloadCancellationTokenSource;
  private Executor downloadExecutor;
  private Handler downloadHandler;
  private TaskCompletionSource<Void> installTaskCompletionSource;
  private CancellationTokenSource installCancellationTokenSource;
  UpdateTaskImpl updateTask;
  private FirebaseApp firebaseApp;

  public UpdateAppClient(@NonNull FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    this.downloadExecutor = Executors.newSingleThreadExecutor();
    this.downloadHandler = HandlerCompat.createAsync(Looper.getMainLooper());
  }

  @NonNull
  public UpdateTask getUpdateTask(
      @NonNull AppDistributionReleaseInternal latestRelease, @NonNull Activity currentActivity)
      throws FirebaseAppDistributionException {

    if (this.updateTask != null && !updateTask.isComplete()) {
      return this.updateTask;
    }

    updateAppCancellationSource = new CancellationTokenSource();
    updateAppTaskCompletionSource =
        new TaskCompletionSource<>(updateAppCancellationSource.getToken());
    this.updateTask = new UpdateTaskImpl(updateAppTaskCompletionSource.getTask());

    if (latestRelease.getBinaryType() == BinaryType.AAB) {
      redirectToPlayForAabUpdate(latestRelease.getDownloadUrl(), currentActivity);
    } else {
      updateApk(latestRelease.getDownloadUrl(), currentActivity);
    }

    return this.updateTask;
  }

  private void redirectToPlayForAabUpdate(String downloadUrl, Activity currentActivity)
      throws FirebaseAppDistributionException {
    if (downloadUrl == null) {
      throw new FirebaseAppDistributionException(
          "Download URL not found.", FirebaseAppDistributionException.Status.NETWORK_FAILURE);
    }
    Intent updateIntent = new Intent(Intent.ACTION_VIEW);
    Uri uri = Uri.parse(downloadUrl);
    updateIntent.setData(uri);
    updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(updateIntent);
    UpdateState updateState =
        UpdateState.builder()
            .setApkBytesDownloaded(-1)
            .setApkTotalBytesToDownload(-1)
            .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
            .build();
    updateAppTaskCompletionSource.setResult(updateState);
    this.updateTask.updateProgress(updateState);
  }

  public void updateApk(String downloadUrl, Activity currentActivity) {
    downloadApk(downloadUrl)
        .addOnSuccessListener(
            file -> {
              install(file.getPath(), currentActivity)
                  .addOnSuccessListener(downloadExecutor,
                      Void -> {
                        UpdateState updateState =
                            UpdateState.builder()
                                .setApkTotalBytesToDownload(file.length())
                                .setApkBytesDownloaded(file.length())
                                .setUpdateStatus(UpdateStatus.INSTALLED)
                                .build();
                        updateTask.updateProgress(updateState);
                        updateAppTaskCompletionSource.setResult(updateState);
                      })
                  .addOnFailureListener(
                      e ->
                          setUpdateAppErrorWithDefault(e,
                              new FirebaseAppDistributionException(
                                  Constants.ErrorMessages.NETWORK_ERROR,
                                  FirebaseAppDistributionException.Status.INSTALLATION_FAILURE)));
            })
        .addOnFailureListener(
            e ->
                setUpdateAppErrorWithDefault(
                    e,
                    new FirebaseAppDistributionException(
                        Constants.ErrorMessages.NETWORK_ERROR,
                        FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE)));
  }

  public Task<File> downloadApk(String downloadUrl) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      downloadCancellationTokenSource.cancel();
    }

    downloadCancellationTokenSource = new CancellationTokenSource();
    downloadTaskCompletionSource =
        new TaskCompletionSource<>(downloadCancellationTokenSource.getToken());

    makeApkDownloadRequest(downloadUrl);
    return downloadTaskCompletionSource.getTask();
  }

  public void makeApkDownloadRequest(String downloadUrl) {
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
              updateProgressOnMainThread(responseLength, 0, UpdateStatus.PENDING);
              String fileName = getApplicationName() + ".apk";
              downloadToDisk(connection.getInputStream(), responseLength, fileName);
            }
          } catch (IOException | FirebaseAppDistributionException e) {
            setDownloadTaskCompletionErrorWithDefault(e,
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
          }
        });
  }

  private void downloadToDisk(InputStream input, long totalSize, String fileName) {

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
          updateProgressOnMainThread(totalSize, downloadedSize, UpdateStatus.DOWNLOADING);
        }
      }
      // completion
      updateProgressOnMainThread(totalSize, downloadedSize, UpdateStatus.DOWNLOADED);

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

  private void setDownloadTaskCompletionErrorWithDefault(Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setDownloadTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setDownloadTaskCompletionError(defaultFirebaseException);
    }
  }

  public Task<Void> install(String path, Activity currentActivity) {
    Intent intent = new Intent(currentActivity, InstallActivity.class);
    intent.putExtra("INSTALL_PATH", path);
    this.installCancellationTokenSource = new CancellationTokenSource();
    this.installTaskCompletionSource =
        new TaskCompletionSource<>(installCancellationTokenSource.getToken());
    InstallActivity.registerOnCompletionListener(this.installTaskCompletionSource);
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    currentActivity.startActivity(intent);
    return installTaskCompletionSource.getTask();
  }

  private void setUpdateAppTaskCompletionError(FirebaseAppDistributionException e) {
    if (updateAppTaskCompletionSource != null
        && !updateAppTaskCompletionSource.getTask().isComplete()) {
      updateAppTaskCompletionSource.setException(e);
    }
  }

  private void setUpdateAppErrorWithDefault(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setUpdateAppTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setUpdateAppTaskCompletionError(defaultFirebaseException);
    }
  }

  private void updateProgressOnMainThread(
      long totalBytes, long downloadedBytes, UpdateStatus status) {
    downloadHandler.post(
        () -> {
          updateTask.updateProgress(
              UpdateState.builder()
                  .setApkTotalBytesToDownload(totalBytes)
                  .setApkBytesDownloaded(downloadedBytes)
                  .setUpdateStatus(status)
                  .build());
        });
  }
}
