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

import static com.google.firebase.app.distribution.DialogUtils.getSignInDialog;
import static com.google.firebase.app.distribution.DialogUtils.getUpdateDialog;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskException;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskResult;

import android.app.Activity;
import android.app.AlertDialog;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.Constants.ErrorMessages;
import com.google.firebase.app.distribution.FirebaseAppDistributionException.Status;
import com.google.firebase.app.distribution.internal.LogWrapper;
import com.google.firebase.app.distribution.internal.SignInResultActivity;
import com.google.firebase.app.distribution.internal.SignInStorage;
import com.google.firebase.installations.FirebaseInstallationsApi;

public class FirebaseAppDistribution {
  private static final int UNKNOWN_RELEASE_FILE_SIZE = -1;

  private final FirebaseApp firebaseApp;
  private final TesterSignInManager testerSignInManager;
  private final NewReleaseFetcher newReleaseFetcher;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final ApkUpdater apkUpdater;
  private final AabUpdater aabUpdater;
  private final SignInStorage signInStorage;

  private final Object updateIfNewReleaseTaskLock = new Object();

  @GuardedBy("updateIfNewReleaseTaskLock")
  private UpdateTaskImpl cachedUpdateIfNewReleaseTask;

  private final Object cachedNewReleaseLock = new Object();

  @GuardedBy("cachedNewReleaseLock")
  private AppDistributionReleaseInternal cachedNewRelease;

  private Task<AppDistributionRelease> cachedCheckForNewReleaseTask;
  private AlertDialog updateDialog;
  private boolean updateDialogShown;
  private AlertDialog signInDialog;

  /** Constructor for FirebaseAppDistribution */
  @VisibleForTesting
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull TesterSignInManager testerSignInManager,
      @NonNull NewReleaseFetcher newReleaseFetcher,
      @NonNull ApkUpdater apkUpdater,
      @NonNull AabUpdater aabUpdater,
      @NonNull SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this.firebaseApp = firebaseApp;
    this.testerSignInManager = testerSignInManager;
    this.newReleaseFetcher = newReleaseFetcher;
    this.apkUpdater = apkUpdater;
    this.aabUpdater = aabUpdater;
    this.signInStorage = signInStorage;
    this.lifecycleNotifier = lifecycleNotifier;
    lifecycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
  }

  /** Constructor for FirebaseAppDistribution */
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this(
        firebaseApp,
        new TesterSignInManager(firebaseApp, firebaseInstallationsApi, signInStorage),
        new NewReleaseFetcher(
            firebaseApp, new FirebaseAppDistributionTesterApiClient(), firebaseInstallationsApi),
        new ApkUpdater(firebaseApp, new ApkInstaller()),
        new AabUpdater(),
        signInStorage,
        lifecycleNotifier);
  }

  /** Constructor for FirebaseAppDistribution */
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi) {
    this(
        firebaseApp,
        firebaseInstallationsApi,
        new SignInStorage(firebaseApp.getApplicationContext()),
        FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  /** @return a FirebaseAppDistribution instance */
  @NonNull
  public static FirebaseAppDistribution getInstance() {
    return FirebaseApp.getInstance().get(FirebaseAppDistribution.class);
  }

  /**
   * Updates the app to the newest release, if one is available. Returns the release information or
   * null if no update is found. Performs the following actions: 1. If tester is not signed in,
   * presents the tester with a Google sign in UI 2. Checks if a newer release is available. If so,
   * presents the tester with a confirmation dialog to begin the download. 3. For APKs, downloads
   * the binary and starts an installation intent. 4. For AABs, directs the tester to the Play app
   * to complete the download and installation.
   */
  @NonNull
  public UpdateTask updateIfNewReleaseAvailable() {
    synchronized (updateIfNewReleaseTaskLock) {
      if (cachedUpdateIfNewReleaseTask != null && !cachedUpdateIfNewReleaseTask.isComplete()) {
        return cachedUpdateIfNewReleaseTask;
      }

      cachedUpdateIfNewReleaseTask = new UpdateTaskImpl();
    }

    if (!isTesterSignedIn()) {
      Activity currentActivity = lifecycleNotifier.getCurrentActivity();
      if (currentActivity == null) {
        LogWrapper.getInstance().e("No foreground activity found.");
        return getErrorUpdateTask(
            new FirebaseAppDistributionException(
                ErrorMessages.APP_BACKGROUNDED,
                FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE));
      }

      signInDialog =
          getSignInDialog(
              currentActivity,
              firebaseApp,
              (dialogInterface, i) ->
                  checkForNewRelease() // check for new release will call sign in tester
                      .onSuccessTask(this::handleNewRelease)
                      .addOnFailureListener(this::onUpdateIfNewReleaseFailure),
              (dialogInterface, i) -> dismissSignInDialogCallback(),
              (dialogInterface) -> dismissSignInDialogCallback());
      signInDialog.show();
    } else {
      checkForNewRelease() // check for new release will call sign in tester
          .onSuccessTask(this::handleNewRelease)
          .addOnFailureListener(this::onUpdateIfNewReleaseFailure);
    }

    synchronized (updateIfNewReleaseTaskLock) {
      return cachedUpdateIfNewReleaseTask;
    }
  }

  private UpdateTask handleNewRelease(AppDistributionRelease release) {
    if (release == null) {
      postProgressToCachedUpdateIfNewReleaseTask(
          UpdateProgress.builder()
              .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
              .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
              .setUpdateStatus(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE)
              .build());
      setCachedUpdateIfNewReleaseResult();
      UpdateTaskImpl updateTask = new UpdateTaskImpl();
      updateTask.setResult();
      return updateTask;
    }
    return showUpdateAlertDialog(release);
  }

  private void onUpdateIfNewReleaseFailure(Exception e) {
    postProgressToCachedUpdateIfNewReleaseTask(
        UpdateProgress.builder()
            .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
            .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
            .setUpdateStatus(UpdateStatus.NEW_RELEASE_CHECK_FAILED)
            .build());
    setCachedUpdateIfNewReleaseCompletionError(
        e,
        new FirebaseAppDistributionException(
            Constants.ErrorMessages.NETWORK_ERROR,
            FirebaseAppDistributionException.Status.NETWORK_FAILURE));
  }

  private void dismissSignInDialogCallback() {
    LogWrapper.getInstance().v("Sign in has been canceled.");
    setCachedUpdateIfNewReleaseCompletionError(
        new FirebaseAppDistributionException(
            ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED));
  }

  private UpdateTaskImpl showUpdateAlertDialog(AppDistributionRelease newRelease) {
    Activity currentActivity = lifecycleNotifier.getCurrentActivity();
    if (currentActivity == null) {
      LogWrapper.getInstance().e("No foreground activity found.");
      return getErrorUpdateTask(
          new FirebaseAppDistributionException(
              ErrorMessages.APP_BACKGROUNDED,
              FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE));
    }

    updateDialog =
        getUpdateDialog(
            currentActivity,
            firebaseApp,
            newRelease,
            ((dialogInterface, i) -> {
              synchronized (updateIfNewReleaseTaskLock) {
                // show download progress in notification manager
                updateApp(true)
                    .addOnProgressListener(this::postProgressToCachedUpdateIfNewReleaseTask)
                    .addOnSuccessListener(unused -> setCachedUpdateIfNewReleaseResult())
                    .addOnFailureListener(cachedUpdateIfNewReleaseTask::setException);
              }
            }),
            (dialogInterface, i) -> dismissUpdateDialogCallback(),
            dialogInterface -> dismissUpdateDialogCallback());

    updateDialog.show();
    updateDialogShown = true;
    synchronized (updateIfNewReleaseTaskLock) {
      return cachedUpdateIfNewReleaseTask;
    }
  }

  private void dismissUpdateDialogCallback() {
    synchronized (updateIfNewReleaseTaskLock) {
      postProgressToCachedUpdateIfNewReleaseTask(
          UpdateProgress.builder()
              .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
              .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
              .setUpdateStatus(UpdateStatus.UPDATE_CANCELED)
              .build());
      setCachedUpdateIfNewReleaseCompletionError(
          new FirebaseAppDistributionException(
              ErrorMessages.UPDATE_CANCELED, Status.INSTALLATION_CANCELED));
    }
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    return this.testerSignInManager.signInTester();
  }

  /**
   * Returns an AppDistributionRelease if one is available for the current signed in tester. If no
   * update is found, returns null. If tester is not signed in, presents the tester with a Google
   * sign in UI
   */
  @NonNull
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    if (cachedCheckForNewReleaseTask != null && !cachedCheckForNewReleaseTask.isComplete()) {
      LogWrapper.getInstance().v("Response in progress");
      return cachedCheckForNewReleaseTask;
    }
    cachedCheckForNewReleaseTask =
        signInTester()
            .onSuccessTask(unused -> this.newReleaseFetcher.checkForNewRelease())
            .onSuccessTask(
                appDistributionReleaseInternal -> {
                  setCachedNewRelease(appDistributionReleaseInternal);
                  return Tasks.forResult(
                      ReleaseUtils.convertToAppDistributionRelease(appDistributionReleaseInternal));
                })
            .addOnFailureListener(
                e -> {
                  if (e instanceof FirebaseAppDistributionException
                      && ((FirebaseAppDistributionException) e).getErrorCode()
                          == AUTHENTICATION_FAILURE) {
                    // If CheckForNewRelease returns authentication error, the FID is no longer
                    // valid or does not have access to the latest release. So sign out the tester
                    // to force FID re-registration
                    signOutTester();
                  }
                });

    return cachedCheckForNewReleaseTask;
  }

  /**
   * Updates app to the newest release. If the newest release is an APK, downloads the binary and
   * starts an installation If the newest release is an AAB, directs the tester to the Play app to
   * complete the download and installation.
   *
   * <p>cancels task with FirebaseAppDistributionException with UPDATE_NOT_AVAILABLE exception if no
   * new release is cached from checkForNewRelease
   */
  @NonNull
  public UpdateTask updateApp() {
    return updateApp(false);
  }

  /**
   * Overloaded updateApp with boolean input showDownloadInNotificationsManager. Set to true for
   * basic configuration and false for advanced configuration.
   */
  private UpdateTask updateApp(boolean showDownloadInNotificationManager) {
    if (!isTesterSignedIn()) {
      UpdateTaskImpl updateTask = new UpdateTaskImpl();
      updateTask.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE));
      return updateTask;
    }
    synchronized (cachedNewReleaseLock) {
      if (cachedNewRelease == null) {
        LogWrapper.getInstance().v("New release not found.");
        return getErrorUpdateTask(
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.NOT_FOUND_ERROR, UPDATE_NOT_AVAILABLE));
      }
      if (cachedNewRelease.getDownloadUrl() == null) {
        LogWrapper.getInstance().v("Download failed to execute");
        return getErrorUpdateTask(
            new FirebaseAppDistributionException(
                Constants.ErrorMessages.DOWNLOAD_URL_NOT_FOUND,
                FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
      }

      if (cachedNewRelease.getBinaryType() == BinaryType.AAB) {
        return this.aabUpdater.updateAab(cachedNewRelease);
      } else {
        return this.apkUpdater.updateApk(cachedNewRelease, showDownloadInNotificationManager);
      }
    }
  }

  /** Returns true if the App Distribution tester is signed in */
  public boolean isTesterSignedIn() {
    return this.signInStorage.getSignInStatus();
  }

  /** Signs out the App Distribution tester */
  public void signOutTester() {
    setCachedNewRelease(null);
    this.signInStorage.setSignInStatus(false);
  }

  @VisibleForTesting
  void onActivityDestroyed(@NonNull Activity activity) {
    if (activity instanceof SignInResultActivity) {
      // SignInResult is internal to the SDK and is destroyed after creation
      return;
    }
    if (updateDialogShown) {
      setCachedUpdateIfNewReleaseCompletionError(
          new FirebaseAppDistributionException(
              ErrorMessages.UPDATE_CANCELED, Status.INSTALLATION_CANCELED));
    }
  }

  @VisibleForTesting
  void setCachedNewRelease(@Nullable AppDistributionReleaseInternal newRelease) {
    synchronized (cachedNewReleaseLock) {
      this.cachedNewRelease = newRelease;
    }
  }

  @VisibleForTesting
  AppDistributionReleaseInternal getCachedNewRelease() {
    synchronized (cachedNewReleaseLock) {
      return this.cachedNewRelease;
    }
  }

  private void setCachedUpdateIfNewReleaseCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateIfNewReleaseTaskLock) {
      safeSetTaskException(cachedUpdateIfNewReleaseTask, e);
    }
    dismissDialogs();
  }

  private void setCachedUpdateIfNewReleaseCompletionError(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setCachedUpdateIfNewReleaseCompletionError((FirebaseAppDistributionException) e);
    } else {
      setCachedUpdateIfNewReleaseCompletionError(defaultFirebaseException);
    }
  }

  private void postProgressToCachedUpdateIfNewReleaseTask(UpdateProgress progress) {
    synchronized (updateIfNewReleaseTaskLock) {
      cachedUpdateIfNewReleaseTask.updateProgress(progress);
    }
  }

  private void setCachedUpdateIfNewReleaseResult() {
    synchronized (updateIfNewReleaseTaskLock) {
      safeSetTaskResult(cachedUpdateIfNewReleaseTask);
    }
    dismissDialogs();
  }

  private void dismissDialogs() {
    if (signInDialog != null && signInDialog.isShowing()) {
      signInDialog.dismiss();
    }
    if (updateDialog != null) {
      updateDialog.dismiss();
      updateDialogShown = false;
    }
  }

  private UpdateTaskImpl getErrorUpdateTask(Exception e) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.setException(e);
    return updateTask;
  }
}
