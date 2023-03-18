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
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
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
import javax.inject.Inject;
import javax.inject.Singleton;

/**
 * This class is the "real" implementation of the Firebase App Distribution API which should only be
 * included in pre-release builds.
 */
// TODO(b/266704696): This currently only supports one FirebaseAppDistribution instance app-wide
@Singleton
class FirebaseAppDistributionImpl implements FirebaseAppDistribution {

  private static final String TAG = "Impl";
  private static final int UNKNOWN_RELEASE_FILE_SIZE = -1;

  private final Context applicationContext;
  private final TesterSignInManager testerSignInManager;
  private final NewReleaseFetcher newReleaseFetcher;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;
  private final ApkUpdater apkUpdater;
  private final AabUpdater aabUpdater;
  private final SignInStorage signInStorage;
  private final ReleaseIdentifier releaseIdentifier;
  private final ScreenshotTaker screenshotTaker;
  @Lightweight private final Executor lightweightExecutor;
  @UiThread private final Executor uiThreadExecutor;
  private final SequentialReference<AppDistributionReleaseInternal> cachedNewRelease;
  private final TaskCache<AppDistributionRelease> checkForNewReleaseTaskCache;
  private final UpdateTaskCache updateIfNewReleaseAvailableTaskCache;
  private final FirebaseAppDistributionNotificationsManager notificationsManager;
  private final AtomicBoolean feedbackInProgress = new AtomicBoolean(false);

  @Nullable private AlertDialog updateConfirmationDialog;
  @Nullable private AlertDialog signInConfirmationDialog;
  @Nullable private Activity dialogHostActivity;
  private boolean remakeSignInConfirmationDialog = false;
  private boolean remakeUpdateConfirmationDialog = false;
  @Nullable private TaskCompletionSource<Void> showSignInDialogTask;
  @Nullable private TaskCompletionSource<Void> showUpdateDialogTask;

  @Inject
  FirebaseAppDistributionImpl(
      @NonNull Context applicationContext,
      @NonNull TesterSignInManager testerSignInManager,
      @NonNull NewReleaseFetcher newReleaseFetcher,
      @NonNull ApkUpdater apkUpdater,
      @NonNull AabUpdater aabUpdater,
      @NonNull SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      @NonNull ReleaseIdentifier releaseIdentifier,
      @NonNull ScreenshotTaker screenshotTaker,
      @NonNull FirebaseAppDistributionNotificationsManager notificationsManager,
      @NonNull @Lightweight Executor lightweightExecutor,
      @NonNull @UiThread Executor uiThreadExecutor) {
    this.applicationContext = applicationContext;
    this.testerSignInManager = testerSignInManager;
    this.newReleaseFetcher = newReleaseFetcher;
    this.apkUpdater = apkUpdater;
    this.aabUpdater = aabUpdater;
    this.signInStorage = signInStorage;
    this.releaseIdentifier = releaseIdentifier;
    this.lifecycleNotifier = lifecycleNotifier;
    this.screenshotTaker = screenshotTaker;
    this.lightweightExecutor = lightweightExecutor;
    this.uiThreadExecutor = uiThreadExecutor;
    this.cachedNewRelease = new SequentialReference<>(lightweightExecutor);
    this.checkForNewReleaseTaskCache = new TaskCache<>(lightweightExecutor);
    this.updateIfNewReleaseAvailableTaskCache = new UpdateTaskCache(lightweightExecutor);
    this.notificationsManager = notificationsManager;
    lifecycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
    lifecycleNotifier.addOnActivityPausedListener(this::onActivityPaused);
    lifecycleNotifier.addOnActivityResumedListener(this::onActivityResumed);
  }

  @Override
  @NonNull
  public UpdateTask updateIfNewReleaseAvailable() {
    return updateIfNewReleaseAvailableTaskCache.getOrCreateUpdateTask(
        () -> {
          UpdateTaskImpl updateTask = new UpdateTaskImpl();
          remakeSignInConfirmationDialog = false;
          remakeUpdateConfirmationDialog = false;
          dialogHostActivity = null;

          signInWithConfirmationIfNeeded()
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
  private Task<Void> signInWithConfirmationIfNeeded() {
    return signInStorage
        .getSignInStatus()
        .onSuccessTask(
            lightweightExecutor,
            signedIn -> {
              if (signedIn) {
                return Tasks.forResult(null);
              }
              return lifecycleNotifier
                  .applyToForegroundActivityTask(this::showSignInConfirmationDialog)
                  .onSuccessTask(lightweightExecutor, unused -> signInTester());
            });
  }

  @NonNull
  private Task<Void> showSignInConfirmationDialog(Activity hostActivity) {
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

          signInConfirmationDialog.setTitle(
              applicationContext.getString(R.string.signin_dialog_title));
          signInConfirmationDialog.setMessage(
              applicationContext.getString(R.string.singin_dialog_message));

          signInConfirmationDialog.setButton(
              AlertDialog.BUTTON_POSITIVE,
              applicationContext.getString(R.string.singin_yes_button),
              (dialogInterface, i) -> showSignInDialogTask.setResult(null));

          signInConfirmationDialog.setButton(
              AlertDialog.BUTTON_NEGATIVE,
              applicationContext.getString(R.string.singin_no_button),
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
    return signInStorage.getSignInStatusBlocking();
  }

  @Override
  @NonNull
  public Task<Void> signInTester() {
    return testerSignInManager.signInTester();
  }

  @Override
  public void signOutTester() {
    // Set sign in status first so isTesterSigned returns the correct state as soon as possible
    signInStorage.setSignInStatus(false);
    cachedNewRelease.set(null);
  }

  @Override
  public Task<AppDistributionRelease> checkForNewRelease() {
    return checkForNewReleaseTaskCache.getOrCreateTask(
        () ->
            assertTesterIsSignedIn()
                .onSuccessTask(
                    lightweightExecutor, unused -> newReleaseFetcher.checkForNewRelease())
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

  private Task<Void> assertTesterIsSignedIn() {
    return signInStorage
        .getSignInStatus()
        .onSuccessTask(
            lightweightExecutor,
            signedIn -> {
              if (!signedIn) {
                return Tasks.forException(
                    new FirebaseAppDistributionException(
                        "Tester is not signed in", AUTHENTICATION_FAILURE));
              }
              return Tasks.forResult(null);
            });
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
    return TaskUtils.onSuccessUpdateTask(
        assertTesterIsSignedIn(),
        lightweightExecutor,
        unused ->
            TaskUtils.onSuccessUpdateTask(
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
                }));
  }

  @Override
  public void startFeedback(@StringRes int additionalFormText) {
    startFeedback(applicationContext.getText(additionalFormText), FeedbackTrigger.CUSTOM);
  }

  @Override
  public void startFeedback(@NonNull CharSequence additionalFormText) {
    startFeedback(additionalFormText, FeedbackTrigger.CUSTOM);
  }

  void startFeedback(@NonNull CharSequence additionalFormText, FeedbackTrigger trigger) {
    if (!feedbackInProgress.compareAndSet(/* expect= */ false, /* update= */ true)) {
      LogWrapper.i(TAG, "Ignoring startFeedback() call because feedback is already in progress");
      return;
    }
    LogWrapper.i(TAG, "Starting feedback");
    screenshotTaker
        .takeScreenshot()
        .addOnFailureListener(
            lightweightExecutor,
            e -> {
              LogWrapper.e(TAG, "Failed to take screenshot for feedback", e);
              doStartFeedback(additionalFormText, null, trigger);
            })
        .addOnSuccessListener(
            lightweightExecutor,
            screenshotUri -> doStartFeedback(additionalFormText, screenshotUri, trigger));
  }

  @Override
  public void startFeedback(@StringRes int additionalFormText, @Nullable Uri screenshotUri) {
    startFeedback(getText(additionalFormText), screenshotUri, FeedbackTrigger.CUSTOM);
  }

  @Override
  public void startFeedback(@NonNull CharSequence additionalFormText, @Nullable Uri screenshotUri) {
    startFeedback(additionalFormText, screenshotUri, FeedbackTrigger.CUSTOM);
  }

  private void startFeedback(
      @NonNull CharSequence additionalFormText,
      @Nullable Uri screenshotUri,
      FeedbackTrigger trigger) {
    if (!screenshotUri.getScheme().equals("content") && !screenshotUri.getScheme().equals("file")) {
      LogWrapper.e(
          TAG,
          String.format(
              "Screenshot URI %s was not a content or file URI. Not starting feedback.",
              screenshotUri));
      return;
    }
    if (!feedbackInProgress.compareAndSet(/* expect= */ false, /* update= */ true)) {
      LogWrapper.i(TAG, "Ignoring startFeedback() call because feedback is already in progress");
      return;
    }
    if (screenshotUri == null) {
      LogWrapper.w(
          TAG, "Screenshot URI was null. No default screenshot will be provided for feedback.");
    }
    doStartFeedback(additionalFormText, screenshotUri, trigger);
  }

  @Override
  public void showFeedbackNotification(
      @StringRes int additionalFormText, @NonNull InterruptionLevel interruptionLevel) {
    showFeedbackNotification(getText(additionalFormText), interruptionLevel);
  }

  @Override
  public void showFeedbackNotification(
      @NonNull CharSequence additionalFormText, @NonNull InterruptionLevel interruptionLevel) {
    notificationsManager.showFeedbackNotification(additionalFormText, interruptionLevel);
  }

  @Override
  public void cancelFeedbackNotification() {
    notificationsManager.cancelFeedbackNotification();
  }

  private void doStartFeedback(
      CharSequence additionalFormText, @Nullable Uri screenshotUri, FeedbackTrigger trigger) {
    testerSignInManager
        .signInTester()
        .addOnFailureListener(
            lightweightExecutor,
            e -> {
              feedbackInProgress.set(false);
              LogWrapper.e(TAG, "Failed to sign in tester. Could not collect feedback.", e);
            })
        .onSuccessTask(
            lightweightExecutor,
            unused ->
                releaseIdentifier
                    .identifyRelease()
                    .addOnFailureListener(
                        uiThreadExecutor,
                        e -> {
                          feedbackInProgress.set(false);
                          LogWrapper.e(TAG, "Failed to identify release", e);
                          Toast.makeText(
                                  applicationContext,
                                  R.string.feedback_unidentified_release,
                                  Toast.LENGTH_LONG)
                              .show();
                        })
                    .onSuccessTask(
                        lightweightExecutor,
                        releaseName ->
                            // in development-mode the releaseName might be null
                            launchFeedbackActivity(
                                    releaseName, additionalFormText, screenshotUri, trigger)
                                .addOnFailureListener(
                                    uiThreadExecutor,
                                    e -> {
                                      feedbackInProgress.set(false);
                                      LogWrapper.e(TAG, "Failed to launch feedback flow", e);
                                      Toast.makeText(
                                              applicationContext,
                                              R.string.feedback_launch_failed,
                                              Toast.LENGTH_LONG)
                                          .show();
                                    })));
  }

  private Task<Void> launchFeedbackActivity(
      @Nullable String releaseName,
      CharSequence additionalFormText,
      @Nullable Uri screenshotUri,
      FeedbackTrigger trigger) {
    LogWrapper.d(TAG, "Getting activity to launch feedback");
    return lifecycleNotifier.consumeForegroundActivity(
        activity -> {
          LogWrapper.i(TAG, "Launching feedback activity");
          Intent intent = new Intent(activity, FeedbackActivity.class);
          // in development-mode the releaseName might be null
          intent.putExtra(FeedbackActivity.RELEASE_NAME_KEY, releaseName);
          intent.putExtra(FeedbackActivity.ADDITIONAL_FORM_TEXT_KEY, additionalFormText);
          intent.putExtra(FeedbackActivity.FEEDBACK_TRIGGER_KEY, trigger.toString());
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

    dialogHostActivity = hostActivity;

    // We should already be on the main (UI) thread here, but be explicit just to be safe. If we are
    // already on the main thread, this will execute immediately.
    hostActivity.runOnUiThread(
        () -> {
          updateConfirmationDialog = new AlertDialog.Builder(hostActivity).create();
          updateConfirmationDialog.setTitle(
              applicationContext.getString(R.string.update_dialog_title));

          StringBuilder message =
              new StringBuilder(
                  String.format(
                      applicationContext.getString(R.string.update_version_available),
                      newRelease.getDisplayVersion(),
                      newRelease.getVersionCode()));

          if (newRelease.getReleaseNotes() != null && !newRelease.getReleaseNotes().isEmpty()) {
            message
                .append("\n\n")
                .append(applicationContext.getString(R.string.update_release_notes))
                .append(" ")
                .append(newRelease.getReleaseNotes());
          }
          updateConfirmationDialog.setMessage(message);

          updateConfirmationDialog.setButton(
              AlertDialog.BUTTON_POSITIVE,
              applicationContext.getString(R.string.update_yes_button),
              (dialogInterface, i) -> showUpdateDialogTask.setResult(null));

          updateConfirmationDialog.setButton(
              AlertDialog.BUTTON_NEGATIVE,
              applicationContext.getString(R.string.update_no_button),
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
    return applicationContext.getText(resourceId);
  }
}
