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
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.installations.FirebaseInstallationsApi;
import org.jetbrains.annotations.Nullable;

public class FirebaseAppDistribution implements Application.ActivityLifecycleCallbacks {
  private static final String TAG = "FirebaseAppDistribution";

  private final FirebaseApp firebaseApp;
  private final TesterSignInClient testerSignInClient;
  private final CheckForUpdateClient checkForUpdateClient;
  private Activity currentActivity;

  private TaskCompletionSource<UpdateState> updateAppTaskCompletionSource = null;
  private CancellationTokenSource updateAppCancellationSource;
  private UpdateTaskImpl updateTask;
  private Task<AppDistributionRelease> cachedUpdateToLatestReleaseTask;
  private Task<AppDistributionRelease> cachedCheckForUpdateTask;
  private AppDistributionReleaseInternal cachedLatestRelease;
  private final SignInStorage signInStorage;

  /** Constructor for FirebaseAppDistribution */
  @VisibleForTesting
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull TesterSignInClient testerSignInClient,
      @NonNull CheckForUpdateClient checkForUpdateClient,
      @NonNull SignInStorage signInStorage) {
    this.firebaseApp = firebaseApp;
    this.testerSignInClient = testerSignInClient;
    this.checkForUpdateClient = checkForUpdateClient;
    this.signInStorage = signInStorage;
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
        signInStorage);
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
  public synchronized Task<AppDistributionRelease> updateToLatestRelease() {
    if (cachedUpdateToLatestReleaseTask != null && !cachedUpdateToLatestReleaseTask.isComplete()) {
      return cachedUpdateToLatestReleaseTask;
    }

    cachedUpdateToLatestReleaseTask =
        checkForUpdate()
            .onSuccessTask(
                release -> {
                  if (release == null) {
                    return Tasks.forResult(null);
                  }

                  return showUpdateAlertDialog(release);
                });

    return cachedUpdateToLatestReleaseTask;
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    return this.testerSignInClient.signInTester(currentActivity);
  }

  /**
   * Returns an AppDistributionRelease if one is available for the current signed in tester. If no
   * update is found, returns null. If tester is not signed in, presents the tester with a Google
   * sign in UI
   */
  @NonNull
  public synchronized Task<AppDistributionRelease> checkForUpdate() {
    if (cachedCheckForUpdateTask != null && !cachedCheckForUpdateTask.isComplete()) {
      return cachedCheckForUpdateTask;
    }

    cachedCheckForUpdateTask =
        signInTester()
            .onSuccessTask(unused -> this.checkForUpdateClient.checkForUpdate())
            .onSuccessTask(
                appDistributionReleaseInternal -> {
                  setCachedLatestRelease(appDistributionReleaseInternal);
                  return Tasks.forResult(
                      convertToAppDistributionRelease(appDistributionReleaseInternal));
                });

    return cachedCheckForUpdateTask;
  }

  /**
   * Updates app to the latest release. If the latest release is an APK, downloads the binary and
   * starts an installation If the latest release is an AAB, directs the tester to the Play app to
   * complete the download and installation.
   *
   * <p>cancels task with FirebaseAppDistributionException with UPDATE_NOT_AVAIALBLE exception if no
   * new release is cached from checkForUpdate
   */
  @NonNull
  public UpdateTask updateApp() throws FirebaseAppDistributionException {

    if (!isTesterSignedIn()) {
      return new UpdateTaskImpl(
          Tasks.forException(
              new FirebaseAppDistributionException(
                  Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE)));
    }

    AppDistributionReleaseInternal cachedRelease = getCachedLatestRelease();
    if (cachedRelease == null) {
      return new UpdateTaskImpl(
          Tasks.forException(
              new FirebaseAppDistributionException(
                  Constants.ErrorMessages.NOT_FOUND_ERROR, UPDATE_NOT_AVAILABLE)));
    }

    if (updateAppTaskCompletionSource != null
        && !updateAppTaskCompletionSource.getTask().isComplete()) {
      updateAppCancellationSource.cancel();
    }

    updateAppCancellationSource = new CancellationTokenSource();
    updateAppTaskCompletionSource =
        new TaskCompletionSource<>(updateAppCancellationSource.getToken());
    this.updateTask = new UpdateTaskImpl(updateAppTaskCompletionSource.getTask());

    if (cachedRelease.getBinaryType() == BinaryType.AAB) {
      redirectToPlayForAabUpdate(cachedRelease.getDownloadUrl());
    } else {
      // todo: create update class when implementing APK
      throw new UnsupportedOperationException("Not yet implemented.");
    }

    return this.updateTask;
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
    Log.d(TAG, "Created activity: " + activity.getClass().getName());
    // if SignInResultActivity is created, sign-in was successful
    if (activity instanceof SignInResultActivity) {
      this.testerSignInClient.setSuccessfulSignInResult();
      this.signInStorage.setSignInStatus(true);
    }
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    Log.d(TAG, "Started activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    Log.d(TAG, "Resumed activity: " + activity.getClass().getName());

    // SignInResultActivity is only opened after successful redirection from signIn flow,
    // should not be treated as reentering the app
    if (activity instanceof SignInResultActivity) {
      return;
    }

    // Throw error if app reentered during sign in
    if (this.testerSignInClient.isCurrentlySigningIn()) {
      testerSignInClient.setCanceledAuthenticationError();
    }

    this.currentActivity = activity;
  }

  @Override
  public void onActivityPaused(@NonNull Activity activity) {
    Log.d(TAG, "Paused activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityStopped(@NonNull Activity activity) {
    Log.d(TAG, "Stopped activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivitySaveInstanceState(@NonNull Activity activity, @NonNull Bundle bundle) {
    Log.d(TAG, "Saved activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityDestroyed(@NonNull Activity activity) {
    Log.d(TAG, "Destroyed activity: " + activity.getClass().getName());
    if (this.currentActivity == activity) {
      this.currentActivity = null;
    }
  }

  private AppDistributionRelease convertToAppDistributionRelease(
      AppDistributionReleaseInternal internalRelease) {
    if (internalRelease == null) {
      return null;
    }
    return AppDistributionRelease.builder()
        .setBuildVersion(internalRelease.getBuildVersion())
        .setDisplayVersion(internalRelease.getDisplayVersion())
        .setReleaseNotes(internalRelease.getReleaseNotes())
        .setBinaryType(internalRelease.getBinaryType())
        .build();
  }

  @VisibleForTesting
  synchronized void setCachedLatestRelease(AppDistributionReleaseInternal latestRelease) {
    this.cachedLatestRelease = latestRelease;
  }

  @VisibleForTesting
  synchronized AppDistributionReleaseInternal getCachedLatestRelease() {
    return this.cachedLatestRelease;
  }

  private void redirectToPlayForAabUpdate(String downloadUrl)
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

  private Task<AppDistributionRelease> showUpdateAlertDialog(AppDistributionRelease latestRelease) {
    TaskCompletionSource<AppDistributionRelease> updateAlertDialogTask =
        new TaskCompletionSource<>();
    Context context = firebaseApp.getApplicationContext();
    AlertDialog alertDialog = new AlertDialog.Builder(currentActivity).create();
    alertDialog.setTitle(context.getString(R.string.update_dialog_title));

    StringBuilder message =
        new StringBuilder(
            String.format(
                "Version %s (%s) is available.",
                latestRelease.getDisplayVersion(), latestRelease.getBuildVersion()));

    if (latestRelease.getReleaseNotes() != null && !latestRelease.getReleaseNotes().isEmpty()) {
      message.append(String.format("\n\nRelease notes: %s", latestRelease.getReleaseNotes()));
    }

    alertDialog.setMessage(message);
    alertDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.update_yes_button),
        (dialogInterface, i) -> {
          try {
            updateApp()
                .addOnSuccessListener(unused -> updateAlertDialogTask.setResult(latestRelease))
                .addOnFailureListener(updateAlertDialogTask::setException);
          } catch (FirebaseAppDistributionException e) {
            updateAlertDialogTask.setException(e);
          }
        });
    alertDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.update_no_button),
        (dialogInterface, i) -> {
          dialogInterface.dismiss();
          updateAlertDialogTask.setException(
              new FirebaseAppDistributionException(
                  Constants.ErrorMessages.UPDATE_CANCELED,
                  FirebaseAppDistributionException.Status.INSTALLATION_CANCELED));
        });

    alertDialog.show();
    return updateAlertDialogTask.getTask();
  }
}
