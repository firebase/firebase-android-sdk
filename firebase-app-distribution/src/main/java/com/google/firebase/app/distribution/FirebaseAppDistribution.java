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

import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskException;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskResult;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.Constants.ErrorMessages;
import com.google.firebase.app.distribution.FirebaseAppDistributionException.Status;
import com.google.firebase.app.distribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.app.distribution.internal.FirebaseAppDistributionLifecycleNotifier;
import com.google.firebase.installations.FirebaseInstallationsApi;

public class FirebaseAppDistribution {
  private static final int UNKNOWN_RELEASE_FILE_SIZE = -1;

  private final FirebaseApp firebaseApp;
  private final TesterSignInClient testerSignInClient;
  private final CheckForNewReleaseClient checkForNewReleaseClient;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final UpdateApkClient updateApkClient;
  private final UpdateAabClient updateAabClient;
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

  /** Constructor for FirebaseAppDistribution */
  @VisibleForTesting
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull TesterSignInClient testerSignInClient,
      @NonNull CheckForNewReleaseClient checkForNewReleaseClient,
      @NonNull UpdateApkClient updateApkClient,
      @NonNull UpdateAabClient updateAabClient,
      @NonNull SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this.firebaseApp = firebaseApp;
    this.testerSignInClient = testerSignInClient;
    this.checkForNewReleaseClient = checkForNewReleaseClient;
    this.updateApkClient = updateApkClient;
    this.updateAabClient = updateAabClient;
    this.signInStorage = signInStorage;
    this.lifecycleNotifier = lifecycleNotifier;
    lifecycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
  }

  /** Constructor for FirebaseAppDistribution */
  public FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this(
        firebaseApp,
        new TesterSignInClient(firebaseApp, firebaseInstallationsApi, signInStorage),
        new CheckForNewReleaseClient(
            firebaseApp, new FirebaseAppDistributionTesterApiClient(), firebaseInstallationsApi),
        new UpdateApkClient(firebaseApp, new InstallApkClient()),
        new UpdateAabClient(),
        signInStorage,
        lifecycleNotifier);
  }

  /** Constructor for FirebaseAppDistribution */
  public FirebaseAppDistribution(
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
    checkForNewRelease()
        .onSuccessTask(
            release -> {
              if (release == null) {
                postProgressToCachedUpdateIfNewReleaseTask(
                    UpdateProgress.builder()
                        .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
                        .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
                        .setUpdateStatus(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE)
                        .build());
                setCachedUpdateIfNewReleaseResult();
                return Tasks.forResult(null);
              }
              return showUpdateAlertDialog(release);
            })
        .addOnFailureListener(
            e -> {
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
            });

    synchronized (updateIfNewReleaseTaskLock) {
      return cachedUpdateIfNewReleaseTask;
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
    if (cachedCheckForNewReleaseTask != null && !cachedCheckForNewReleaseTask.isComplete()) {
      LogWrapper.getInstance().v("Response in progress");
      return cachedCheckForNewReleaseTask;
    }
    cachedCheckForNewReleaseTask =
        signInTester()
            .onSuccessTask(unused -> this.checkForNewReleaseClient.checkForNewRelease())
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
  public UpdateTask updateApp(boolean showDownloadInNotificationManager) {
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
        return this.updateAabClient.updateAab(cachedNewRelease);
      } else {
        return this.updateApkClient.updateApk(cachedNewRelease, showDownloadInNotificationManager);
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

  private UpdateTaskImpl showUpdateAlertDialog(AppDistributionRelease newRelease) {
    Context context = firebaseApp.getApplicationContext();
    Activity currentActivity = lifecycleNotifier.getCurrentActivity();
    if (currentActivity == null) {
      LogWrapper.getInstance().e("No foreground activity found.");
      UpdateTaskImpl updateTask = new UpdateTaskImpl();
      updateTask.setException(
          new FirebaseAppDistributionException(
              ErrorMessages.APP_BACKGROUNDED,
              FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE));
      return updateTask;
    }
    updateDialog = new AlertDialog.Builder(currentActivity).create();
    updateDialog.setTitle(context.getString(R.string.update_dialog_title));

    StringBuilder message =
        new StringBuilder(
            String.format(
                "Version %s (%s) is available.",
                newRelease.getDisplayVersion(), newRelease.getVersionCode()));

    if (newRelease.getReleaseNotes() != null && !newRelease.getReleaseNotes().isEmpty()) {
      message.append(String.format("\n\nRelease notes: %s", newRelease.getReleaseNotes()));
    }

    updateDialog.setMessage(message);
    updateDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.update_yes_button),
        (dialogInterface, i) -> {
          synchronized (updateIfNewReleaseTaskLock) {
            // show download progress in notification manager
            updateApp(true)
                .addOnProgressListener(this::postProgressToCachedUpdateIfNewReleaseTask)
                .addOnSuccessListener(unused -> setCachedUpdateIfNewReleaseResult())
                .addOnFailureListener(cachedUpdateIfNewReleaseTask::setException);
          }
        });

    updateDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.update_no_button),
        (dialogInterface, i) -> {
          dialogInterface.dismiss();
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
        });

    updateDialog.show();
    updateDialogShown = true;
    synchronized (updateIfNewReleaseTaskLock) {
      return cachedUpdateIfNewReleaseTask;
    }
  }

  private void setCachedUpdateIfNewReleaseCompletionError(FirebaseAppDistributionException e) {
    synchronized (updateIfNewReleaseTaskLock) {
      safeSetTaskException(cachedUpdateIfNewReleaseTask, e);
    }
    dismissUpdateDialog();
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
    dismissUpdateDialog();
  }

  private void dismissUpdateDialog() {
    if (updateDialog != null) {
      updateDialog.dismiss();
      updateDialogShown = false;
    }
  }

  private UpdateTask getErrorUpdateTask(Exception e) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.setException(e);
    return updateTask;
  }
}
