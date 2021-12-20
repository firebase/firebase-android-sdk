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

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.app.distribution.internal.InstallActivity;
import com.google.firebase.app.distribution.internal.LogWrapper;
import com.google.firebase.app.distribution.internal.SignInResultActivity;
import java.io.IOException;
import java.net.URL;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.net.ssl.HttpsURLConnection;

/**
 * Class that handles updateApp functionality for AABs in {@link FirebaseAppDistribution}.
 */
class AabUpdater {
  private static final String TAG = "UpdateAabClient:";

  private final Executor updateExecutor;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  @GuardedBy("updateAabLock")
  private UpdateTaskImpl cachedUpdateTask;

  @GuardedBy("updateAabLock")
  private AppDistributionReleaseInternal aabReleaseInProgress;

  private final Object updateAabLock = new Object();

  AabUpdater() {
    this(
        Executors.newSingleThreadExecutor(),
        FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  AabUpdater(
      @NonNull Executor updateExecutor,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this.updateExecutor = updateExecutor;
    this.lifecycleNotifier = lifecycleNotifier;
    lifecycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
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

  private void redirectToPlayForAabUpdate(String downloadUrl) {
    synchronized (updateAabLock) {
      if (lifecycleNotifier.getCurrentActivity() == null) {
        safeSetTaskException(
            cachedUpdateTask,
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.APP_BACKGROUNDED,
                FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
        return;
      }
    }

    // The 302 redirect is obtained here to open the play store directly and avoid opening chrome
    updateExecutor.execute(
        () -> {
          HttpsURLConnection connection;
          String redirect;
          try {
            connection = openHttpsUrlConnection(downloadUrl);

            // To get url to play without redirect we do this connection
            connection.setInstanceFollowRedirects(false);
            redirect = connection.getHeaderField("Location");
            connection.disconnect();
            connection.getInputStream().close();
          } catch (FirebaseAppDistributionException | IOException e) {
            setUpdateTaskCompletionErrorWithDefault(
                e,
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
            return;
          }

          if (!redirect.isEmpty()) {
            Intent updateIntent = new Intent(Intent.ACTION_VIEW);
            updateIntent.setData(Uri.parse(redirect));
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
          } else {
            setUpdateTaskCompletionError(
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.NETWORK_ERROR,
                    FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
          }
        });
  }

  private void setUpdateTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateAabLock) {
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

  void tryCancelAabUpdateTask() {
    synchronized (updateAabLock) {
      safeSetTaskException(
          cachedUpdateTask,
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.UPDATE_CANCELED,
              FirebaseAppDistributionException.Status.INSTALLATION_CANCELED,
              ReleaseUtils.convertToAppDistributionRelease(aabReleaseInProgress)));
    }
  }
}
