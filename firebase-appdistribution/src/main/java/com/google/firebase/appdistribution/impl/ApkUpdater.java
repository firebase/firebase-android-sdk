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

package com.google.firebase.appdistribution.impl;

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;

import android.content.Context;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.UpdateStatus;
import com.google.firebase.appdistribution.UpdateTask;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.Executor;
import java.util.jar.JarFile;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.HttpsURLConnection;

/** Class that handles updateApp functionality for APKs in {@link FirebaseAppDistribution}. */
@Singleton // Only one update should happen at a time, even across FirebaseAppDistribution instances
class ApkUpdater {
  private static final String TAG = "ApkUpdater";
  private static final int UPDATE_INTERVAL_MS = 250;
  private static final String REQUEST_METHOD_GET = "GET";
  private static final String DEFAULT_APK_FILE_NAME = "downloaded_release.apk";

  private final @Blocking Executor blockingExecutor;
  private final @Lightweight Executor lightweightExecutor;
  private final Context context;
  private final ApkInstaller apkInstaller;
  private final FirebaseAppDistributionNotificationsManager appDistributionNotificationsManager;
  private final HttpsUrlConnectionFactory httpsUrlConnectionFactory;
  private final FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier;
  private UpdateTaskCache cachedUpdateTask;

  @Inject
  ApkUpdater(
      @NonNull Context context,
      @NonNull ApkInstaller apkInstaller,
      @NonNull FirebaseAppDistributionNotificationsManager appDistributionNotificationsManager,
      @NonNull HttpsUrlConnectionFactory httpsUrlConnectionFactory,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifeCycleNotifier,
      @NonNull @Blocking Executor blockingExecutor,
      @NonNull @Lightweight Executor lightweightExecutor) {
    this.blockingExecutor = blockingExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.context = context;
    this.apkInstaller = apkInstaller;
    this.appDistributionNotificationsManager = appDistributionNotificationsManager;
    this.httpsUrlConnectionFactory = httpsUrlConnectionFactory;
    this.lifeCycleNotifier = lifeCycleNotifier;
    this.cachedUpdateTask = new UpdateTaskCache(lightweightExecutor);
  }

  UpdateTask updateApk(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showNotification) {
    return cachedUpdateTask.getOrCreateUpdateTask(
        () -> {
          downloadApk(newRelease, showNotification)
              .addOnSuccessListener(lightweightExecutor, file -> installApk(file, showNotification))
              .addOnFailureListener(
                  lightweightExecutor,
                  e ->
                      setUpdateTaskCompletionErrorWithDefault(
                          e, "Failed to download APK", Status.DOWNLOAD_FAILURE));
          return new UpdateTaskImpl();
        });
  }

  private void installApk(File file, boolean showDownloadNotificationManager) {
    lifeCycleNotifier
        .applyToForegroundActivityTask(
            activity -> apkInstaller.installApk(file.getPath(), activity))
        .addOnSuccessListener(lightweightExecutor, unused -> cachedUpdateTask.setResult())
        .addOnFailureListener(
            blockingExecutor, // Getting the file length performs disk IO
            e -> {
              long fileLength = file.length();
              postUpdateProgress(
                  fileLength,
                  fileLength,
                  UpdateStatus.INSTALL_FAILED,
                  showDownloadNotificationManager,
                  R.string.install_failed);
              setUpdateTaskCompletionErrorWithDefault(
                  e, ErrorMessages.APK_INSTALLATION_FAILED, Status.INSTALLATION_FAILURE);
            });
  }

  @VisibleForTesting
  @NonNull
  Task<File> downloadApk(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showNotification) {
    return TaskUtils.runAsyncInTask(
        blockingExecutor, () -> makeApkDownloadRequest(newRelease, showNotification));
  }

  private File makeApkDownloadRequest(
      @NonNull AppDistributionReleaseInternal newRelease, boolean showNotification)
      throws FirebaseAppDistributionException {
    String downloadUrl = newRelease.getDownloadUrl();
    HttpsURLConnection connection;
    int responseCode;
    try {
      connection = httpsUrlConnectionFactory.openConnection(downloadUrl);
      connection.setRequestMethod(REQUEST_METHOD_GET);
      responseCode = connection.getResponseCode();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          "Failed to open connection to: " + downloadUrl, NETWORK_FAILURE, e);
    }

    if (!isResponseSuccess(responseCode)) {
      throw new FirebaseAppDistributionException(
          "Failed to download APK. Response code: " + responseCode, DOWNLOAD_FAILURE);
    }

    long responseLength = connection.getContentLength();
    postUpdateProgress(
        responseLength, 0, UpdateStatus.PENDING, showNotification, R.string.downloading_app_update);
    String fileName = getApkFileName();
    LogWrapper.v(TAG, "Attempting to download APK to disk");

    long bytesDownloaded = downloadToDisk(connection, responseLength, fileName, showNotification);

    File apkFile = context.getFileStreamPath(fileName);
    validateJarFile(apkFile, responseLength, showNotification, bytesDownloaded);

    postUpdateProgress(
        responseLength,
        bytesDownloaded,
        UpdateStatus.DOWNLOADED,
        showNotification,
        R.string.download_completed);
    return apkFile;
  }

  private static boolean isResponseSuccess(int responseCode) {
    return responseCode >= 200 && responseCode < 300;
  }

  private long downloadToDisk(
      HttpsURLConnection connection, long totalSize, String fileName, boolean showNotification)
      throws FirebaseAppDistributionException {
    context.deleteFile(fileName);
    int fileCreationMode =
        VERSION.SDK_INT >= VERSION_CODES.N ? Context.MODE_PRIVATE : Context.MODE_WORLD_READABLE;
    long bytesDownloaded = 0;
    try (BufferedOutputStream outputStream =
            new BufferedOutputStream(context.openFileOutput(fileName, fileCreationMode));
        InputStream inputStream = connection.getInputStream()) {
      byte[] data = new byte[8 * 1024];
      int readSize = inputStream.read(data);
      long lastMsUpdated = 0;

      while (readSize != -1) {
        outputStream.write(data, 0, readSize);
        bytesDownloaded += readSize;
        readSize = inputStream.read(data);

        // update progress logic for onProgressListener
        long currentTimeMs = System.currentTimeMillis();
        if (currentTimeMs - lastMsUpdated > UPDATE_INTERVAL_MS) {
          lastMsUpdated = currentTimeMs;
          postUpdateProgress(
              totalSize,
              bytesDownloaded,
              UpdateStatus.DOWNLOADING,
              showNotification,
              R.string.downloading_app_update);
        }
      }
    } catch (IOException e) {
      postUpdateProgress(
          totalSize,
          bytesDownloaded,
          UpdateStatus.DOWNLOAD_FAILED,
          showNotification,
          R.string.download_failed);
      throw new FirebaseAppDistributionException("Failed to download APK", DOWNLOAD_FAILURE, e);
    }
    return bytesDownloaded;
  }

  @VisibleForTesting
  void validateJarFile(File apkFile, long totalSize, boolean showNotification, long bytesDownloaded)
      throws FirebaseAppDistributionException {
    try {
      new JarFile(apkFile).close();
    } catch (IOException e) {
      postUpdateProgress(
          totalSize,
          bytesDownloaded,
          UpdateStatus.DOWNLOAD_FAILED,
          showNotification,
          R.string.download_failed);
      throw new FirebaseAppDistributionException(
          "Downloaded APK was not a valid JAR file", DOWNLOAD_FAILURE, e);
    }
  }

  private String getApkFileName() {
    try {
      String applicationName =
          context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
      return applicationName + ".apk";
    } catch (Exception e) {
      LogWrapper.w(
          TAG,
          "Unable to retrieve app name. Using generic file name for APK: " + DEFAULT_APK_FILE_NAME);
      return DEFAULT_APK_FILE_NAME;
    }
  }

  private void setUpdateTaskCompletionErrorWithDefault(Exception e, String message, Status status) {
    if (e instanceof FirebaseAppDistributionException) {
      cachedUpdateTask.setException((FirebaseAppDistributionException) e);
    } else {
      cachedUpdateTask.setException(new FirebaseAppDistributionException(message, status, e));
    }
  }

  private void postUpdateProgress(
      long totalBytes,
      long downloadedBytes,
      UpdateStatus status,
      boolean showNotification,
      int stringResourceId) {
    cachedUpdateTask.setProgress(
        UpdateProgressImpl.builder()
            .setApkFileTotalBytes(totalBytes)
            .setApkBytesDownloaded(downloadedBytes)
            .setUpdateStatus(status)
            .build());
    if (showNotification) {
      appDistributionNotificationsManager.showAppUpdateNotification(
          totalBytes, downloadedBytes, stringResourceId);
    }
  }
}
