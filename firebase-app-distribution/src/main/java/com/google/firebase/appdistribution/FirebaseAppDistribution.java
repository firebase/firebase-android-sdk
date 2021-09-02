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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.os.Bundle;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

public class FirebaseAppDistribution implements Application.ActivityLifecycleCallbacks {

  private final FirebaseApp firebaseApp;
  private final TesterSignInClient testerSignInClient;
  private final CheckForUpdateClient checkForUpdateClient;
  private final UpdateAppClient updateAppClient;
  private Activity currentActivity;
  private static final int UNKNOWN_RELEASE_FILE_SIZE = -1;
  private static final int UPDATE_THREAD_POOL_SIZE = 4;

  @GuardedBy("updateTaskLock")
  private UpdateTaskImpl cachedUpdateToLatestReleaseTask;

  private final Object updateTaskLock = new Object();
  private Task<AppDistributionRelease> cachedCheckForUpdateTask;

  private AppDistributionReleaseInternal cachedLatestRelease;
  private final SignInStorage signInStorage;
  private final Executor updateIfNewReleaseExecutor;

  /** Constructor for FirebaseAppDistribution */
  @VisibleForTesting
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull TesterSignInClient testerSignInClient,
      @NonNull CheckForUpdateClient checkForUpdateClient,
      @NonNull UpdateAppClient updateAppClient,
      @NonNull SignInStorage signInStorage,
      @NonNull Executor executor) {
    this.firebaseApp = firebaseApp;
    this.testerSignInClient = testerSignInClient;
    this.checkForUpdateClient = checkForUpdateClient;
    this.updateAppClient = updateAppClient;
    this.signInStorage = signInStorage;
    this.updateIfNewReleaseExecutor = executor;
  }

  /** Constructor for FirebaseAppDistribution */
  public FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull SignInStorage signInStorage) {
    this(
        firebaseApp,
        new TesterSignInClient(firebaseApp, firebaseInstallationsApi, signInStorage),
        new CheckForUpdateClient(
            firebaseApp, new FirebaseAppDistributionTesterApiClient(), firebaseInstallationsApi),
        new UpdateAppClient(firebaseApp),
        signInStorage,
        Executors.newFixedThreadPool(UPDATE_THREAD_POOL_SIZE));
  }

  /** Constructor for FirebaseAppDistribution */
  public FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi) {
    this(
        firebaseApp,
        firebaseInstallationsApi,
        new SignInStorage(firebaseApp.getApplicationContext()));
  }

  /** @return a FirebaseAppDistribution instance */
  @NonNull
  public static FirebaseAppDistribution getInstance() {
    return getInstance(FirebaseApp.getInstance());
  }

  /**
   * Returns the {@link FirebaseAppDistribution} initialized with a custom {@link FirebaseApp}.
   *
   * @param app a custom {@link FirebaseApp}
   * @return a {@link FirebaseAppDistribution} instance
   */
  @NonNull
  public static FirebaseAppDistribution getInstance(@NonNull FirebaseApp app) {
    Preconditions.checkArgument(app != null, "Null is not a valid value of FirebaseApp.");
    return app.get(FirebaseAppDistribution.class);
  }

  /**
   * Updates the app to the latest release, if one is available. Returns the release information or
   * null if no update is found. Performs the following actions: 1. If tester is not signed in,
   * presents the tester with a Google sign in UI 2. Checks if a newer release is available. If so,
   * presents the tester with a confirmation dialog to begin the download. 3. For APKs, downloads
   * the binary and starts an installation intent. 4. For AABs, directs the tester to the Play app
   * to complete the download and installation.
   */
  @NonNull
  public synchronized UpdateTask updateIfNewReleaseAvailable() {
    synchronized (updateTaskLock) {
      if (cachedUpdateToLatestReleaseTask != null
          && !cachedUpdateToLatestReleaseTask.isComplete()) {
        return cachedUpdateToLatestReleaseTask;
      }

      cachedUpdateToLatestReleaseTask = new UpdateTaskImpl();
    }
    checkForNewRelease()
        .onSuccessTask(
            release -> {
              if (release == null) {
                synchronized (updateTaskLock) {
                  cachedUpdateToLatestReleaseTask.updateProgress(
                      UpdateProgress.builder()
                          .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
                          .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
                          .setUpdateStatus(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE)
                          .build());
                  cachedUpdateToLatestReleaseTask.setResult();
                }
                return Tasks.forResult(null);
              }
              return showUpdateAlertDialog(release);
            })
        .addOnFailureListener(
            e -> {
              synchronized (updateTaskLock) {
                cachedUpdateToLatestReleaseTask.updateProgress(
                    UpdateProgress.builder()
                        .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
                        .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
                        .setUpdateStatus(UpdateStatus.NEW_RELEASE_CHECK_FAILED)
                        .build());
              }
              setTaskCompletionErrorWithDefault(
                  e,
                  new FirebaseAppDistributionException(
                      Constants.ErrorMessages.NETWORK_ERROR,
                      FirebaseAppDistributionException.Status.NETWORK_FAILURE));
            });
    synchronized (updateTaskLock) {
      return cachedUpdateToLatestReleaseTask;
    }
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    return this.testerSignInClient.signInTester();
  }

  /**
   * Returns an AppDistributionRelease if one is available for the current signed in tester. If no
   * update is found, returns null. If tester is not signed in, presents the tester with a Google
   * sign in UI
   */
  @NonNull
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    if (cachedCheckForUpdateTask != null && !cachedCheckForUpdateTask.isComplete()) {
      LogWrapper.getInstance().v("Response in progress");
      return cachedCheckForUpdateTask;
    }
    cachedCheckForUpdateTask =
        signInTester()
            .onSuccessTask(unused -> this.checkForUpdateClient.checkForUpdate())
            .onSuccessTask(
                appDistributionReleaseInternal -> {
                  setCachedLatestRelease(appDistributionReleaseInternal);
                  return Tasks.forResult(
                      ReleaseUtils.convertToAppDistributionRelease(appDistributionReleaseInternal));
                });

    return cachedCheckForUpdateTask;
  }

  /**
   * Updates app to the latest release. If the latest release is an APK, downloads the binary and
   * starts an installation If the latest release is an AAB, directs the tester to the Play app to
   * complete the download and installation.
   *
   * <p>cancels task with FirebaseAppDistributionException with UPDATE_NOT_AVAILABLE exception if no
   * new release is cached from checkForUpdate
   */
  @NonNull
  public synchronized UpdateTask updateApp() {
    return updateApp(false);
  }

  /**
   * Overloaded updateApp with boolean input showDownloadInNotificationsManager. Set to true for
   * basic configuration and false for advanced configuration.
   */
  private synchronized UpdateTask updateApp(boolean showDownloadInNotificationManager) {
    if (!isTesterSignedIn()) {
      UpdateTaskImpl updateTask = new UpdateTaskImpl();
      updateTask.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE));
      return updateTask;
    }

    return this.updateAppClient.updateApp(cachedLatestRelease, showDownloadInNotificationManager);
  }

  /** Returns true if the App Distribution tester is signed in */
  public boolean isTesterSignedIn() {
    return this.signInStorage.getSignInStatus();
  }

  /** Signs out the App Distribution tester */
  public void signOutTester() {
    this.cachedLatestRelease = null;
    this.signInStorage.setSignInStatus(false);
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
    LogWrapper.getInstance().v("Created activity: " + activity.getClass().getName());
    // if SignInResultActivity is created, sign-in was successful
    if (activity instanceof SignInResultActivity) {
      LogWrapper.getInstance().v("Sign in completed");
      this.testerSignInClient.setSuccessfulSignInResult();
      this.signInStorage.setSignInStatus(true);
    }
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    LogWrapper.getInstance().d("Started activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    LogWrapper.getInstance().d("Resumed activity: " + activity.getClass().getName());
    // If app resumes and aab update task is in progress, assume that installation didn't happen so
    // cancel the task
    updateAppClient.tryCancelAabUpdateTask();

    // SignInResultActivity is only opened after successful redirection from signIn flow,
    // should not be treated as reentering the app
    if (activity instanceof SignInResultActivity) {
      return;
    }

    // Throw error if app reentered during sign in
    if (this.testerSignInClient.isCurrentlySigningIn()) {
      LogWrapper.getInstance().e("App Resumed without sign in flow completing.");
      testerSignInClient.setCanceledAuthenticationError();
    }

    this.currentActivity = activity;
    this.updateAppClient.setCurrentActivity(activity);
    this.testerSignInClient.setCurrentActivity(activity);
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    LogWrapper.getInstance().d("Paused activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    LogWrapper.getInstance().d("Stopped activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
    LogWrapper.getInstance().d("Saved activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    LogWrapper.getInstance().d("Destroyed activity: " + activity.getClass().getName());
    if (this.currentActivity == activity) {
      this.currentActivity = null;
      this.updateAppClient.setCurrentActivity(null);
      this.testerSignInClient.setCurrentActivity(null);
    }
  }

  @VisibleForTesting
  void setCachedLatestRelease(AppDistributionReleaseInternal latestRelease) {
    this.cachedLatestRelease = latestRelease;
  }

  @VisibleForTesting
  AppDistributionReleaseInternal getCachedLatestRelease() {
    return this.cachedLatestRelease;
  }

  private UpdateTaskImpl showUpdateAlertDialog(AppDistributionRelease latestRelease) {
    Context context = firebaseApp.getApplicationContext();
    AlertDialog alertDialog = new AlertDialog.Builder(currentActivity).create();
    alertDialog.setTitle(context.getString(R.string.update_dialog_title));

    StringBuilder message =
        new StringBuilder(
            String.format(
                "Version %s (%s) is available.",
                latestRelease.getDisplayVersion(), latestRelease.getVersionCode()));

    if (latestRelease.getReleaseNotes() != null && !latestRelease.getReleaseNotes().isEmpty()) {
      message.append(String.format("\n\nRelease notes: %s", latestRelease.getReleaseNotes()));
    }

    alertDialog.setMessage(message);
    alertDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.update_yes_button),
        (dialogInterface, i) ->
        // show download progress in notification manager
        {
          synchronized (updateTaskLock) {
            updateApp(true)
                .addOnProgressListener(
                    progress -> cachedUpdateToLatestReleaseTask.updateProgress(progress))
                .addOnSuccessListener(unused -> cachedUpdateToLatestReleaseTask.setResult())
                .addOnFailureListener(cachedUpdateToLatestReleaseTask::setException);
          }
        });

    alertDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.update_no_button),
        (dialogInterface, i) -> {
          dialogInterface.dismiss();
          synchronized (updateTaskLock) {
            cachedUpdateToLatestReleaseTask.updateProgress(
                UpdateProgress.builder()
                    .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
                    .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
                    .setUpdateStatus(UpdateStatus.UPDATE_CANCELED)
                    .build());
            cachedUpdateToLatestReleaseTask.setException(
                new FirebaseAppDistributionException(
                    Constants.ErrorMessages.UPDATE_CANCELED,
                    FirebaseAppDistributionException.Status.INSTALLATION_CANCELED));
          }
        });

    alertDialog.show();
    synchronized (updateTaskLock) {
      return cachedUpdateToLatestReleaseTask;
    }
  }

  void setInstallationResult(int resultCode) {
    this.updateAppClient.setInstallationResult(resultCode);
  }

  private void setTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateTaskLock) {
      if (cachedUpdateToLatestReleaseTask != null
          && !cachedUpdateToLatestReleaseTask.isComplete()) {
        cachedUpdateToLatestReleaseTask.setException(e);
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
}
