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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.HOST_ACTIVITY_INTERRUPTED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;
import static com.google.firebase.appdistribution.impl.TaskUtils.safeSetTaskException;
import static com.google.firebase.appdistribution.impl.TaskUtils.safeSetTaskResult;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.AppDistributionRelease;
import com.google.firebase.appdistribution.BinaryType;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.InterruptionLevel;
import com.google.firebase.appdistribution.UpdateProgress;
import com.google.firebase.appdistribution.UpdateStatus;
import com.google.firebase.appdistribution.UpdateTask;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * This class is the "real" implementation of the Firebase App Distribution API which should only be
 * included in pre-release builds.
 */
class FirebaseAppDistributionImpl implements FirebaseAppDistribution {

  private static final String TAG = "Impl";
  private static final int UNKNOWN_RELEASE_FILE_SIZE = -1;

  private final FirebaseApp firebaseApp;
  private final TesterSignInManager testerSignInManager;
  private final NewReleaseFetcher newReleaseFetcher;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final ApkUpdater apkUpdater;
  private final AabUpdater aabUpdater;
  private final SignInStorage signInStorage;
  private final SequentialReference<AppDistributionReleaseInternal> cachedNewRelease;
  private final ReleaseIdentifier releaseIdentifier;
  private final ScreenshotTaker screenshotTaker;
  private final Executor taskExecutor;
  private final FirebaseAppDistributionNotificationsManager notificationsManager;
  private TaskCache<UpdateTask> updateIfNewReleaseAvailableTaskCache = new TaskCache<>();
  private TaskCache<Task<AppDistributionRelease>> checkForNewReleaseTaskCache = new TaskCache<>();
  @Lightweight private Executor lightweightExecutor;
  private AlertDialog updateConfirmationDialog;
  private AlertDialog signInConfirmationDialog;
  @Nullable private Activity dialogHostActivity = null;
  private boolean remakeSignInConfirmationDialog = false;
  private boolean remakeUpdateConfirmationDialog = false;
  private TaskCompletionSource<Void> showSignInDialogTask = null;
  private TaskCompletionSource<Void> showUpdateDialogTask = null;
  private final AtomicBoolean feedbackInProgress = new AtomicBoolean(false);

  @VisibleForTesting
  FirebaseAppDistributionImpl(
      @NonNull FirebaseApp firebaseApp,
      @NonNull TesterSignInManager testerSignInManager,
      @NonNull NewReleaseFetcher newReleaseFetcher,
      @NonNull ApkUpdater apkUpdater,
      @NonNull AabUpdater aabUpdater,
      @NonNull SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      @NonNull ReleaseIdentifier releaseIdentifier,
      @NonNull ScreenshotTaker screenshotTaker,
      @NonNull @Lightweight Executor lightweightExecutor,
      @NonNull Executor taskExecutor) {
    this.firebaseApp = firebaseApp;
    this.testerSignInManager = testerSignInManager;
    this.newReleaseFetcher = newReleaseFetcher;
    this.apkUpdater = apkUpdater;
    this.aabUpdater = aabUpdater;
    this.signInStorage = signInStorage;
    this.releaseIdentifier = releaseIdentifier;
    this.lifecycleNotifier = lifecycleNotifier;
    this.cachedNewRelease = new SequentialReference<>(lightweightExecutor);
    this.lightweightExecutor = lightweightExecutor;
    this.screenshotTaker = screenshotTaker;
    this.taskExecutor = taskExecutor;
    this.notificationsManager =
        new FirebaseAppDistributionNotificationsManager(firebaseApp.getApplicationContext());
    lifecycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
    lifecycleNotifier.addOnActivityPausedListener(this::onActivityPaused);
    lifecycleNotifier.addOnActivityResumedListener(this::onActivityResumed);
  }

  @Override
  @NonNull
  // TODO(b/261014422): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public UpdateTask updateIfNewReleaseAvailable() {
    return updateIfNewReleaseAvailableTaskCache.getOrCreateTask(
        () -> {
          UpdateTaskImpl updateTask = new UpdateTaskImpl();
          remakeSignInConfirmationDialog = false;
          remakeUpdateConfirmationDialog = false;
          dialogHostActivity = null;

          lifecycleNotifier
              .applyToForegroundActivityTask(this::showSignInConfirmationDialog)
              .onSuccessTask(lightweightExecutor, unused -> signInTester())
              .onSuccessTask(lightweightExecutor, unused -> checkForNewRelease())
              .continueWithTask(
                  lightweightExecutor,
                  task -> {
                    if (!task.isSuccessful()) {
                      postProgressToCachedUpdateIfNewReleaseTask(
                          updateTask,
                          UpdateProgressImpl.builder()
                              .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
                              .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
                              .setUpdateStatus(UpdateStatus.NEW_RELEASE_CHECK_FAILED)
                              .build());
                    }
                    // if the task failed, this get() will cause the error to propagate to the
                    // handler below
                    AppDistributionRelease release = task.getResult();
                    if (release == null) {
                      postProgressToCachedUpdateIfNewReleaseTask(
                          updateTask,
                          UpdateProgressImpl.builder()
                              .setApkFileTotalBytes(UNKNOWN_RELEASE_FILE_SIZE)
                              .setApkBytesDownloaded(UNKNOWN_RELEASE_FILE_SIZE)
                              .setUpdateStatus(UpdateStatus.NEW_RELEASE_NOT_AVAILABLE)
                              .build());
                      setCachedUpdateIfNewReleaseResult(updateTask);
                      return Tasks.forResult(null);
                    }
                    return lifecycleNotifier.applyToForegroundActivityTask(
                        activity -> showUpdateConfirmationDialog(activity, release));
                  })
              .onSuccessTask(
                  lightweightExecutor,
                  unused ->
                      updateApp(true)
                          .addOnProgressListener(
                              lightweightExecutor,
                              updateProgress ->
                                  postProgressToCachedUpdateIfNewReleaseTask(
                                      updateTask, updateProgress)))
              .addOnFailureListener(
                  lightweightExecutor,
                  exception -> setCachedUpdateIfNewReleaseCompletionError(updateTask, exception));

          return updateTask;
        });
  }

  @NonNull
  private Task<Void> showSignInConfirmationDialog(Activity hostActivity) {
    if (isTesterSignedIn()) {
      return Tasks.forResult(null);
    }

    if (showSignInDialogTask == null || showSignInDialogTask.getTask().isComplete()) {
      showSignInDialogTask = new TaskCompletionSource<>();
    }

    dialogHostActivity = hostActivity;

    // We may not be on the main (UI) thread in some cases, specifically if the developer calls
    // the basic config from the background. If we are already on the main thread, this will
    // execute immediately.
    hostActivity.runOnUiThread(
        () -> {
          signInConfirmationDialog = new AlertDialog.Builder(hostActivity).create();

          Context context = firebaseApp.getApplicationContext();
          signInConfirmationDialog.setTitle(context.getString(R.string.signin_dialog_title));
          signInConfirmationDialog.setMessage(context.getString(R.string.singin_dialog_message));

          signInConfirmationDialog.setButton(
              AlertDialog.BUTTON_POSITIVE,
              context.getString(R.string.singin_yes_button),
              (dialogInterface, i) -> showSignInDialogTask.setResult(null));

          signInConfirmationDialog.setButton(
              AlertDialog.BUTTON_NEGATIVE,
              context.getString(R.string.singin_no_button),
              (dialogInterface, i) ->
                  showSignInDialogTask.setException(
                      new FirebaseAppDistributionException(
                          ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED)));

          signInConfirmationDialog.setOnCancelListener(
              dialogInterface ->
                  showSignInDialogTask.setException(
                      new FirebaseAppDistributionException(
                          ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED)));

          signInConfirmationDialog.show();
        });
    return showSignInDialogTask.getTask();
  }

  @Override
  public boolean isTesterSignedIn() {
    return signInStorage.getSignInStatus();
  }

  @Override
  @NonNull
  public Task<Void> signInTester() {
    return testerSignInManager.signInTester();
  }

  @Override
  public void signOutTester() {
    cachedNewRelease
        .set(null)
        .addOnSuccessListener(lightweightExecutor, unused -> signInStorage.setSignInStatus(false));
  }

  @Override
  @NonNull
  // TODO(b/261014422): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public synchronized Task<AppDistributionRelease> checkForNewRelease() {
    if (!isTesterSignedIn()) {
      return Tasks.forException(
          new FirebaseAppDistributionException("Tester is not signed in", AUTHENTICATION_FAILURE));
    }

    return checkForNewReleaseTaskCache.getOrCreateTask(
        () ->
            newReleaseFetcher
                .checkForNewRelease()
                .onSuccessTask(lightweightExecutor, release -> cachedNewRelease.set(release))
                .onSuccessTask(
                    lightweightExecutor,
                    release ->
                        Tasks.forResult(ReleaseUtils.convertToAppDistributionRelease(release)))
                .addOnFailureListener(
                    lightweightExecutor,
                    e -> {
                      if (e instanceof FirebaseAppDistributionException
                          && ((FirebaseAppDistributionException) e).getErrorCode()
                              == AUTHENTICATION_FAILURE) {
                        // If CheckForNewRelease returns authentication error, the FID is no longer
                        // valid or does not have access to the latest release. So sign out the
                        // tester to force FID re-registration
                        signOutTester();
                      }
                    }));
  }

  @Override
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
          new FirebaseAppDistributionException("Tester is not signed in", AUTHENTICATION_FAILURE));
      return updateTask;
    }
    return TaskUtils.onSuccessUpdateTask(
        cachedNewRelease.get(),
        lightweightExecutor,
        release -> {
          if (release == null) {
            LogWrapper.v(TAG, "New release not found.");
            return getErrorUpdateTask(
                new FirebaseAppDistributionException(
                    ErrorMessages.RELEASE_NOT_FOUND_ERROR, UPDATE_NOT_AVAILABLE));
          }
          if (release.getDownloadUrl() == null) {
            LogWrapper.v(TAG, "Download failed to execute.");
            return getErrorUpdateTask(
                new FirebaseAppDistributionException(
                    ErrorMessages.DOWNLOAD_URL_NOT_FOUND,
                    FirebaseAppDistributionException.Status.DOWNLOAD_FAILURE));
          }

          if (release.getBinaryType() == BinaryType.AAB) {
            return aabUpdater.updateAab(release);
          } else {
            return apkUpdater.updateApk(release, showDownloadInNotificationManager);
          }
        });
  }

  @Override
  public void startFeedback(@StringRes int infoTextResourceId) {
    startFeedback(firebaseApp.getApplicationContext().getText(infoTextResourceId));
  }

  @Override
  public void startFeedback(@NonNull CharSequence infoText) {
    if (!feedbackInProgress.compareAndSet(/* expect= */ false, /* update= */ true)) {
      LogWrapper.i(TAG, "Ignoring startFeedback() call because feedback is already in progress");
      return;
    }
    LogWrapper.i(TAG, "Starting feedback");
    screenshotTaker
        .takeScreenshot()
        .addOnFailureListener(
            taskExecutor,
            e -> {
              LogWrapper.w(TAG, "Failed to take screenshot for feedback", e);
              doStartFeedback(infoText, null);
            })
        .addOnSuccessListener(
            taskExecutor, screenshotUri -> doStartFeedback(infoText, screenshotUri));
  }

  @Override
  public void startFeedback(@StringRes int infoTextResourceId, @Nullable Uri screenshotUri) {
    startFeedback(getText(infoTextResourceId), screenshotUri);
  }

  @Override
  public void startFeedback(@NonNull CharSequence infoText, @Nullable Uri screenshotUri) {
    if (!feedbackInProgress.compareAndSet(/* expect= */ false, /* update= */ true)) {
      LogWrapper.i(TAG, "Ignoring startFeedback() call because feedback is already in progress");
      return;
    }
    doStartFeedback(infoText, screenshotUri);
  }

  @Override
  public void showFeedbackNotification(
      @StringRes int infoTextResourceId, @NonNull InterruptionLevel interruptionLevel) {
    showFeedbackNotification(getText(infoTextResourceId), interruptionLevel);
  }

  @Override
  public void showFeedbackNotification(
      @NonNull CharSequence infoText, @NonNull InterruptionLevel interruptionLevel) {
    notificationsManager.showFeedbackNotification(infoText, interruptionLevel);
  }

  @Override
  public void cancelFeedbackNotification() {
    notificationsManager.cancelFeedbackNotification();
  }

  // TODO(b/261014422): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  private void doStartFeedback(CharSequence infoText, @Nullable Uri screenshotUri) {
    testerSignInManager
        .signInTester()
        .addOnFailureListener(
            taskExecutor,
            e -> {
              feedbackInProgress.set(false);
              LogWrapper.e(TAG, "Failed to sign in tester. Could not collect feedback.", e);
            })
        .onSuccessTask(
            taskExecutor,
            unused ->
                releaseIdentifier
                    .identifyRelease()
                    .addOnFailureListener(
                        e -> {
                          feedbackInProgress.set(false);
                          LogWrapper.e(TAG, "Failed to identify release", e);
                          Toast.makeText(
                                  firebaseApp.getApplicationContext(),
                                  R.string.feedback_unidentified_release,
                                  Toast.LENGTH_LONG)
                              .show();
                        })
                    .onSuccessTask(
                        taskExecutor,
                        releaseName ->
                            // in development-mode the releaseName might be null
                            launchFeedbackActivity(releaseName, infoText, screenshotUri)
                                .addOnFailureListener(
                                    e -> {
                                      feedbackInProgress.set(false);
                                      LogWrapper.e(TAG, "Failed to launch feedback flow", e);
                                      Toast.makeText(
                                              firebaseApp.getApplicationContext(),
                                              R.string.feedback_launch_failed,
                                              Toast.LENGTH_LONG)
                                          .show();
                                    })));
  }

  private Task<Void> launchFeedbackActivity(
      @Nullable String releaseName, CharSequence infoText, @Nullable Uri screenshotUri) {
    return lifecycleNotifier.consumeForegroundActivity(
        activity -> {
          LogWrapper.i(TAG, "Launching feedback activity");
          Intent intent = new Intent(activity, FeedbackActivity.class);
          // in development-mode the releaseName might be null
          intent.putExtra(FeedbackActivity.RELEASE_NAME_KEY, releaseName);
          intent.putExtra(FeedbackActivity.INFO_TEXT_KEY, infoText);
          if (screenshotUri != null) {
            intent.putExtra(FeedbackActivity.SCREENSHOT_URI_KEY, screenshotUri.toString());
          }
          activity.startActivity(intent);
        });
  }

  @VisibleForTesting
  boolean isFeedbackInProgress() {
    return feedbackInProgress.get();
  }

  @VisibleForTesting
  void onActivityResumed(Activity activity) {
    if (awaitingSignInDialogConfirmation()) {
      if (dialogHostActivity != null && dialogHostActivity != activity) {
        showSignInDialogTask.setException(
            new FirebaseAppDistributionException(
                ErrorMessages.HOST_ACTIVITY_INTERRUPTED, HOST_ACTIVITY_INTERRUPTED));
      } else {
        showSignInConfirmationDialog(activity);
      }
    }

    if (awaitingUpdateDialogConfirmation()) {
      if (dialogHostActivity != null && dialogHostActivity != activity) {
        showUpdateDialogTask.setException(
            new FirebaseAppDistributionException(
                ErrorMessages.HOST_ACTIVITY_INTERRUPTED, HOST_ACTIVITY_INTERRUPTED));
      } else {
        cachedNewRelease
            .get()
            .addOnSuccessListener(
                lightweightExecutor,
                release ->
                    showUpdateConfirmationDialog(
                        activity, ReleaseUtils.convertToAppDistributionRelease(release)));
      }
    }
  }

  @VisibleForTesting
  void onActivityPaused(Activity activity) {
    if (activity == dialogHostActivity) {
      remakeSignInConfirmationDialog =
          signInConfirmationDialog != null && signInConfirmationDialog.isShowing();
      remakeUpdateConfirmationDialog =
          updateConfirmationDialog != null && updateConfirmationDialog.isShowing();
      dismissDialogs();
    }
  }

  @VisibleForTesting
  void onActivityDestroyed(@NonNull Activity activity) {
    // If the dialogHostActivity is being destroyed it is set to null. This is to ensure onResume
    // shows the dialog on a configuration change and does not check the activity reference.
    if (activity == dialogHostActivity) {
      dialogHostActivity = null;
    }

    if (activity instanceof FeedbackActivity) {
      LogWrapper.i(TAG, "FeedbackActivity destroyed");
      if (activity.isFinishing()) {
        feedbackInProgress.set(false);

        // If the feedback activity finishes, clean up the screenshot that was taken before starting
        // the activity. If this does not happen for some reason it will be cleaned up the next time
        // before taking a new screenshot.
        screenshotTaker.deleteScreenshot();
      }
    }
  }

  @VisibleForTesting
  SequentialReference<AppDistributionReleaseInternal> getCachedNewRelease() {
    return cachedNewRelease;
  }

  private Task<Void> showUpdateConfirmationDialog(
      Activity hostActivity, AppDistributionRelease newRelease) {

    if (showUpdateDialogTask == null || showUpdateDialogTask.getTask().isComplete()) {
      showUpdateDialogTask = new TaskCompletionSource<>();
    }

    Context context = firebaseApp.getApplicationContext();
    dialogHostActivity = hostActivity;

    // We should already be on the main (UI) thread here, but be explicit just to be safe. If we are
    // already on the main thread, this will execute immediately.
    hostActivity.runOnUiThread(
        () -> {
          updateConfirmationDialog = new AlertDialog.Builder(hostActivity).create();
          updateConfirmationDialog.setTitle(context.getString(R.string.update_dialog_title));

          StringBuilder message =
              new StringBuilder(
                  String.format(
                      context.getString(R.string.update_version_available),
                      newRelease.getDisplayVersion(),
                      newRelease.getVersionCode()));

          if (newRelease.getReleaseNotes() != null && !newRelease.getReleaseNotes().isEmpty()) {
            message
                .append("\n\n")
                .append(context.getString(R.string.update_release_notes))
                .append(" ")
                .append(newRelease.getReleaseNotes());
          }
          updateConfirmationDialog.setMessage(message);

          updateConfirmationDialog.setButton(
              AlertDialog.BUTTON_POSITIVE,
              context.getString(R.string.update_yes_button),
              (dialogInterface, i) -> showUpdateDialogTask.setResult(null));

          updateConfirmationDialog.setButton(
              AlertDialog.BUTTON_NEGATIVE,
              context.getString(R.string.update_no_button),
              (dialogInterface, i) ->
                  showUpdateDialogTask.setException(
                      new FirebaseAppDistributionException(
                          ErrorMessages.UPDATE_CANCELED, Status.INSTALLATION_CANCELED)));

          updateConfirmationDialog.setOnCancelListener(
              dialogInterface ->
                  showUpdateDialogTask.setException(
                      new FirebaseAppDistributionException(
                          ErrorMessages.UPDATE_CANCELED, Status.INSTALLATION_CANCELED)));

          updateConfirmationDialog.show();
        });

    return showUpdateDialogTask.getTask();
  }

  private void setCachedUpdateIfNewReleaseCompletionError(UpdateTaskImpl updateTask, Exception e) {
    safeSetTaskException(updateTask, e);
    dismissDialogs();
  }

  private void postProgressToCachedUpdateIfNewReleaseTask(
      UpdateTaskImpl updateTask, UpdateProgress progress) {
    if (updateTask != null && !updateTask.isComplete()) {
      updateTask.updateProgress(progress);
    }
  }

  private void setCachedUpdateIfNewReleaseResult(UpdateTaskImpl updateTask) {
    safeSetTaskResult(updateTask);
    dismissDialogs();
  }

  private void dismissDialogs() {
    if (signInConfirmationDialog != null && signInConfirmationDialog.isShowing()) {
      signInConfirmationDialog.dismiss();
    }
    if (updateConfirmationDialog != null && updateConfirmationDialog.isShowing()) {
      updateConfirmationDialog.dismiss();
    }
  }

  private UpdateTaskImpl getErrorUpdateTask(Exception e) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    updateTask.setException(e);
    return updateTask;
  }

  private boolean awaitingSignInDialogConfirmation() {
    return (showSignInDialogTask != null
        && !showSignInDialogTask.getTask().isComplete()
        && remakeSignInConfirmationDialog);
  }

  private boolean awaitingUpdateDialogConfirmation() {
    return (showUpdateDialogTask != null
        && !showUpdateDialogTask.getTask().isComplete()
        && remakeUpdateConfirmationDialog);
  }

  private CharSequence getText(int resourceId) {
    return firebaseApp.getApplicationContext().getText(resourceId);
  }
}
