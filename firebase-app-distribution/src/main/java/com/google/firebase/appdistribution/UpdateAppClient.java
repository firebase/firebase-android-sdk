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
import android.content.pm.ApplicationInfo;
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
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/** Client class for updateApp functionality in {@link FirebaseAppDistribution}. */
public class UpdateAppClient {

  private int UPDATE_INTERVAL_MS = 250;
  private TaskCompletionSource<UpdateState> updateAppTaskCompletionSource = null;
  private CancellationTokenSource updateAppCancellationSource;
  private TaskCompletionSource<File> downloadTaskCompletionSource;
  private CancellationTokenSource downloadCancellationTokenSource;
  private Executor downloadExecutor;
  private Handler downloadHandler;
  private TaskCompletionSource<Integer> installTaskCompletionSource;
  private CancellationTokenSource installCancellationTokenSource;
  private OkHttpClient httpClient;
  UpdateTaskImpl updateTask;
  private FirebaseApp firebaseApp;

  public UpdateAppClient(@NonNull FirebaseApp firebaseApp) {
    this.firebaseApp = firebaseApp;
    this.httpClient = new OkHttpClient();
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
    getDownloadTask(downloadUrl)
        .addOnSuccessListener(
            file -> {
              Log.v("updateApk", "success");
              install(file.getPath(), currentActivity)
                  .addOnSuccessListener(
                      integer -> {
                        // will change with api update
                        updateAppTaskCompletionSource.setResult(null);
                      })
                  .addOnFailureListener(
                      e ->
                          setUpdateAppTaskCompletionError(
                              new FirebaseAppDistributionException(
                                  Constants.ErrorMessages.NETWORK_ERROR,
                                  FirebaseAppDistributionException.Status.INSTALLATION_FAILURE)));
            })
        .addOnFailureListener(e -> Log.v("updateApk", "failure"));
  }

  public Task<File> getDownloadTask(String downloadUrl) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      downloadCancellationTokenSource.cancel();
    }

    downloadCancellationTokenSource = new CancellationTokenSource();
    downloadTaskCompletionSource =
        new TaskCompletionSource<>(downloadCancellationTokenSource.getToken());

    makeDownloadRequestApk(downloadUrl);
    return downloadTaskCompletionSource.getTask();
  }

  public void makeDownloadRequestApk(String downloadUrl) {
    downloadExecutor.execute(
        () -> {
          try {
            Request request = new Request.Builder().url(new URL(downloadUrl)).build();
            Response response = httpClient.newCall(request).execute();
            if (!response.isSuccessful()) {
              setDownloadTaskCompletionError(
                  new FirebaseAppDistributionException(
                      Constants.ErrorMessages.NETWORK_ERROR,
                      FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
            } else if (response.body() != null) {
              ResponseBody responseBody = response.body();
              long responseLength = responseBody.contentLength();
              String fileName = getApplicationName() + ".apk";
              ;
              downloadToDisk(responseBody.byteStream(), responseLength, fileName);
            }
          } catch (IOException e) {
            setDownloadTaskCompletionError(
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

        //update progress logic for onProgressListener
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastMsUpdated > UPDATE_INTERVAL_MS) {
          lastMsUpdated = currentTimeMs;
          updateProgressOnMainThread(totalSize, downloadedSize, UpdateStatus.DOWNLOADING);
        }
      }

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
    Context context = firebaseApp.getApplicationContext();
    ApplicationInfo applicationInfo = context.getApplicationInfo();
    int stringId = applicationInfo.labelRes;
    return stringId == 0
        ? applicationInfo.nonLocalizedLabel.toString()
        : context.getString(stringId);
  }

  private void setDownloadTaskCompletionError(FirebaseAppDistributionException e) {
    if (downloadTaskCompletionSource != null
        && !downloadTaskCompletionSource.getTask().isComplete()) {
      downloadTaskCompletionSource.setException(e);
    }
  }

  public Task<Integer> install(String path, Activity currentActivity) {
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

  private void updateProgressOnMainThread(long total, long downloaded, UpdateStatus status) {
    downloadHandler.post(
        new Runnable() {
          @Override
          public void run() {
            Log.v("left", String.valueOf(total-downloaded));
            Log.v("downloaded", String.valueOf(downloaded));
            updateTask.updateProgress(
                UpdateState.builder()
                    .setApkTotalBytesToDownload(total - downloaded)
                    .setApkBytesDownloaded(downloaded)
                    .setUpdateStatus(status)
                    .build());
          }
        });
  }
}
