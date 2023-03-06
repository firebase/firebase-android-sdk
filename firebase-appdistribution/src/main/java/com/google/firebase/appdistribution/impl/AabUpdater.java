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
import static com.google.firebase.appdistribution.impl.TaskUtils.runAsyncInTask;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.UpdateStatus;
import com.google.firebase.appdistribution.UpdateTask;
import java.io.IOException;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.net.ssl.HttpsURLConnection;

/** Class that handles updateApp functionality for AABs in {@link FirebaseAppDistribution}. */
@Singleton // Only one update should happen at a time, even across FirebaseAppDistribution instances
class AabUpdater {
  private static final String TAG = "AabUpdater";

  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final HttpsUrlConnectionFactory httpsUrlConnectionFactory;
  @Blocking private final Executor blockingExecutor;
  @Lightweight private final Executor lightweightExecutor;
  private final UpdateTaskCache updateTaskCache;

  private AppDistributionReleaseInternal aabReleaseInProgress;
  private boolean hasBeenSentToPlayForCurrentTask = false;

  @Inject
  AabUpdater(
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      @NonNull HttpsUrlConnectionFactory httpsUrlConnectionFactory,
      @NonNull @Blocking Executor blockingExecutor,
      @NonNull @Lightweight Executor lightweightExecutor) {
    this.lifecycleNotifier = lifecycleNotifier;
    this.httpsUrlConnectionFactory = httpsUrlConnectionFactory;
    lifecycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
    this.blockingExecutor = blockingExecutor;
    this.lightweightExecutor = lightweightExecutor;
    this.updateTaskCache = new UpdateTaskCache(lightweightExecutor);
  }

  @VisibleForTesting
  void onActivityStarted(Activity activity) {
    if (activity instanceof SignInResultActivity || activity instanceof InstallActivity) {
      return;
    }
    // If app resumes and update is in progress, assume that installation didn't happen and fail the
    // task
    if (hasBeenSentToPlayForCurrentTask) {
      updateTaskCache
          .setException(
              new FirebaseAppDistributionException(
                  ErrorMessages.UPDATE_CANCELED,
                  FirebaseAppDistributionException.Status.INSTALLATION_CANCELED,
                  ReleaseUtils.convertToAppDistributionRelease(aabReleaseInProgress)))
          .addOnSuccessListener(
              lightweightExecutor,
              unused -> LogWrapper.e(TAG, "App Resumed without update completing."));
    }
  }

  UpdateTask updateAab(@NonNull AppDistributionReleaseInternal newRelease) {
    return updateTaskCache.getOrCreateUpdateTask(
        () -> {
          UpdateTaskImpl cachedUpdateTask = new UpdateTaskImpl();
          aabReleaseInProgress = newRelease;
          hasBeenSentToPlayForCurrentTask = false;

          // On a background thread, fetch the redirect URL and open it in the Play app
          runAsyncInTask(
                  blockingExecutor, () -> fetchDownloadRedirectUrl(newRelease.getDownloadUrl()))
              .onSuccessTask(
                  lightweightExecutor,
                  redirectUrl ->
                      lifecycleNotifier.consumeForegroundActivity(
                          activity -> openRedirectUrlInPlay(redirectUrl, activity)))
              .addOnFailureListener(
                  lightweightExecutor,
                  e -> cachedUpdateTask.setException(FirebaseAppDistributionExceptions.wrap(e)));

          return cachedUpdateTask;
        });
  }

  private String fetchDownloadRedirectUrl(String downloadUrl)
      throws FirebaseAppDistributionException {
    HttpsURLConnection connection = null;
    int responseCode;
    String redirectUrl;

    try {
      connection = httpsUrlConnectionFactory.openConnection(downloadUrl);
      connection.setInstanceFollowRedirects(false);
      responseCode = connection.getResponseCode();
      redirectUrl = connection.getHeaderField("Location");
      // Prevents a {@link LeakedClosableViolation} in strict mode even when the connection is
      // disconnected
      connection.getInputStream().close();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          "Failed to open connection to: " + downloadUrl, NETWORK_FAILURE, e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }

    if (!isRedirectResponse(responseCode)) {
      throw new FirebaseAppDistributionException(
          "Expected redirect response code, but got: " + responseCode, DOWNLOAD_FAILURE);
    }

    if (redirectUrl == null) {
      throw new FirebaseAppDistributionException(
          "No Location header found in response from: " + downloadUrl, DOWNLOAD_FAILURE);
    } else if (redirectUrl.isEmpty()) {
      throw new FirebaseAppDistributionException(
          "Empty Location header found in response from: " + downloadUrl, DOWNLOAD_FAILURE);
    }

    return redirectUrl;
  }

  private static boolean isRedirectResponse(int responseCode) {
    return responseCode >= 300 && responseCode < 400;
  }

  private void openRedirectUrlInPlay(String redirectUrl, Activity hostActivity) {
    Intent updateIntent = new Intent(Intent.ACTION_VIEW);
    updateIntent.setData(Uri.parse(redirectUrl));
    updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
    LogWrapper.v(TAG, "Redirecting to play");
    hostActivity.startActivity(updateIntent);
    updateTaskCache.setProgress(
        UpdateProgressImpl.builder()
            .setApkBytesDownloaded(-1)
            .setApkFileTotalBytes(-1)
            .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
            .build());
    hasBeenSentToPlayForCurrentTask = true;
  }
}
