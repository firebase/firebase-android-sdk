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

import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskException;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.app.distribution.Constants.ErrorMessages;
import com.google.firebase.app.distribution.internal.InstallActivity;
import com.google.firebase.app.distribution.internal.LogWrapper;
import com.google.firebase.app.distribution.internal.SignInResultActivity;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/** Class that handles updateApp functionality for AABs in {@link FirebaseAppDistribution}. */
class AabUpdater {
  private static final String TAG = "UpdateAabClient:";

  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final HttpsUrlConnectionFactory httpsUrlConnectionFactory;
  private final Executor executor;

  @GuardedBy("updateAabLock")
  private UpdateTaskImpl cachedUpdateTask;

  @GuardedBy("updateAabLock")
  private AppDistributionReleaseInternal aabReleaseInProgress;

  private final Object updateAabLock = new Object();

  AabUpdater() {
    this(FirebaseAppDistributionLifecycleNotifier.getInstance(), new HttpsUrlConnectionFactory(), Executors.newSingleThreadExecutor());
  }

  AabUpdater(
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      @NonNull HttpsUrlConnectionFactory httpsUrlConnectionFactory,
      @NonNull Executor executor) {
    this.lifecycleNotifier = lifecycleNotifier;
    this.httpsUrlConnectionFactory = httpsUrlConnectionFactory;
    lifecycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
    this.executor = executor;
  }

  @VisibleForTesting
  void onActivityStarted(Activity activity) {
    if (activity instanceof SignInResultActivity || activity instanceof InstallActivity) {
      return;
    }
    // If app resumes and aab update task is in progress, assume that installation didn't happen so
    // cancel the task
    this.tryCancelAabUpdateTask();
  }

  UpdateTaskImpl updateAab(@NonNull AppDistributionReleaseInternal newRelease) {
    synchronized (updateAabLock) {
      if (cachedUpdateTask != null && !cachedUpdateTask.isComplete()) {
        return cachedUpdateTask;
      }

      cachedUpdateTask = new UpdateTaskImpl();
      aabReleaseInProgress = newRelease;
      redirectToPlayForAabUpdate(newRelease.getDownloadUrl());

      return cachedUpdateTask;
    }
  }

  private String fetchDownloadRedirectUrl(String downloadUrl)
      throws FirebaseAppDistributionException {
    HttpsURLConnection httpsURLConnection;
    int responseCode;

    try {
      httpsURLConnection = httpsUrlConnectionFactory.openConnection(downloadUrl);
      httpsURLConnection.setInstanceFollowRedirects(false);
      responseCode = httpsURLConnection.getResponseCode();
    } catch (IOException e) {
      throw new FirebaseAppDistributionException(
          "Failed to open connection to: " + downloadUrl, NETWORK_FAILURE, e);
    }

    if (!isRedirectResponse(responseCode)) {
      throw new FirebaseAppDistributionException(
          "Expected redirect response code, but got: " + responseCode, DOWNLOAD_FAILURE);
    }
    String redirect = httpsURLConnection.getHeaderField("Location");
    httpsURLConnection.disconnect();

    if (redirect == null) {
      throw new FirebaseAppDistributionException(
          "No Location header found in response from: " + downloadUrl, DOWNLOAD_FAILURE);
    } else if (redirect.isEmpty()) {
      throw new FirebaseAppDistributionException(
          "Empty Location header found in response from: " + downloadUrl, DOWNLOAD_FAILURE);
    }

    return redirect;
  }

  private static boolean isRedirectResponse(int responseCode) {
    return responseCode >= 300 && responseCode < 400;
  }

  private void redirectToPlayForAabUpdate(String downloadUrl) {
    synchronized (updateAabLock) {
      if (lifecycleNotifier.getCurrentActivity() == null) {
        safeSetTaskException(
            cachedUpdateTask,
            new FirebaseAppDistributionException(ErrorMessages.APP_BACKGROUNDED, DOWNLOAD_FAILURE));
        return;
      }
    }

    // The 302 redirect is obtained here to open the play store directly and avoid opening chrome
    executor
        .execute( // Execute the network calls on a background thread
            () -> {
              String redirectUrl;
              try {
                redirectUrl = fetchDownloadRedirectUrl(downloadUrl);
              } catch (FirebaseAppDistributionException e) {
                setUpdateTaskCompletionError(e);
                return;
              }

              Intent updateIntent = new Intent(Intent.ACTION_VIEW);
              updateIntent.setData(Uri.parse(redirectUrl));
              updateIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
              LogWrapper.getInstance().v(TAG + "Redirecting to play");

              synchronized (updateAabLock) {
                lifecycleNotifier.getCurrentActivity().startActivity(updateIntent);
                cachedUpdateTask.updateProgress(
                    UpdateProgress.builder()
                        .setApkBytesDownloaded(-1)
                        .setApkFileTotalBytes(-1)
                        .setUpdateStatus(UpdateStatus.REDIRECTED_TO_PLAY)
                        .build());
              }
            });
  }

  private void setUpdateTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateAabLock) {
      safeSetTaskException(cachedUpdateTask, e);
    }
  }

  void tryCancelAabUpdateTask() {
    synchronized (updateAabLock) {
      safeSetTaskException(
          cachedUpdateTask,
          new FirebaseAppDistributionException(
              ErrorMessages.UPDATE_CANCELED,
              FirebaseAppDistributionException.Status.INSTALLATION_CANCELED,
              ReleaseUtils.convertToAppDistributionRelease(aabReleaseInProgress)));
    }
  }
}
