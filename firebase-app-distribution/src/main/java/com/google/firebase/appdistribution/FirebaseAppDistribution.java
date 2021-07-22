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

import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.NETWORK_FAILURE;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.os.HandlerCompat;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.internal.AppDistributionReleaseInternal;
import com.google.firebase.appdistribution.internal.ReleaseIdentificationUtils;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.Nullable;

public class FirebaseAppDistribution implements Application.ActivityLifecycleCallbacks {

  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallationsApi;
  private final FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient;
  private final int UPDATE_THREAD_POOL_SIZE = 4;
  private static final String TAG = "FirebaseAppDistribution";
  private Activity currentActivity;
  private boolean currentlySigningIn = false;
  private TaskCompletionSource<Void> signInTaskCompletionSource = null;
  private CancellationTokenSource signInCancellationSource;
  private final String SIGNIN_REDIRECT_URL =
      "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts?appName=%s&packageName=%s";

  private TaskCompletionSource<AppDistributionRelease> checkForUpdateTaskCompletionSource = null;
  private CancellationTokenSource checkForUpdateCancellationSource;
  private final Executor checkForUpdateExecutor;

  private TaskCompletionSource<UpdateState> updateAppTaskCompletionSource = null;
  private CancellationTokenSource updateAppCancellationSource;
  private UpdateTaskImpl updateTask;
  private AppDistributionReleaseInternal cachedLatestRelease;

  private TaskCompletionSource<AppDistributionRelease> updateToLatestReleaseTaskCompletionSource =
      null;
  private CancellationTokenSource updateToLatestReleaseCancellationSource;

  /** Constructor for FirebaseAppDistribution */
  @VisibleForTesting
  FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull FirebaseAppDistributionTesterApiClient firebaseAppDistributionTesterApiClient) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.firebaseAppDistributionTesterApiClient = firebaseAppDistributionTesterApiClient;
    // todo: verify if this is best way to use executorservice here
    this.checkForUpdateExecutor = Executors.newFixedThreadPool(UPDATE_THREAD_POOL_SIZE);
  }

  /** Constructor for FirebaseAppDistribution */
  public FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi) {
    this(firebaseApp, firebaseInstallationsApi, new FirebaseAppDistributionTesterApiClient());
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
  public Task<AppDistributionRelease> updateToLatestRelease() {

    if (updateToLatestReleaseTaskCompletionSource != null
        && !updateToLatestReleaseTaskCompletionSource.getTask().isComplete()) {
      updateToLatestReleaseCancellationSource.cancel();
    }

    updateToLatestReleaseCancellationSource = new CancellationTokenSource();
    updateToLatestReleaseTaskCompletionSource =
        new TaskCompletionSource<>(updateToLatestReleaseCancellationSource.getToken());

    // todo: when persistence is implemented and user already signed in, skip signInTester
    signInTester()
        .addOnSuccessListener(
            unused -> {
              checkForUpdate()
                  .addOnSuccessListener(
                      latestRelease -> {
                        if (latestRelease != null) {
                          showUpdateAlertDialog(latestRelease);
                        } else {
                          updateToLatestReleaseTaskCompletionSource.setResult(null);
                        }
                      })
                  .addOnFailureListener(
                      e ->
                          setUpdateToLatestReleaseErrorWithDefault(
                              e,
                              new FirebaseAppDistributionException(
                                  Constants.ErrorMessages.NETWORK_ERROR, NETWORK_FAILURE)));
            })
        .addOnFailureListener(
            e ->
                setUpdateToLatestReleaseErrorWithDefault(
                    e,
                    new FirebaseAppDistributionException(
                        Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE)));

    return updateToLatestReleaseTaskCompletionSource.getTask();
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    if (signInTaskCompletionSource != null && !signInTaskCompletionSource.getTask().isComplete()) {
      signInCancellationSource.cancel();
    }

    signInCancellationSource = new CancellationTokenSource();
    signInTaskCompletionSource = new TaskCompletionSource<>(signInCancellationSource.getToken());

    AlertDialog alertDialog = getSignInAlertDialog(firebaseApp.getApplicationContext());
    alertDialog.show();

    return signInTaskCompletionSource.getTask();
  }

  /**
   * Returns an AppDistributionRelease if one is available for the current signed in tester. If no
   * update is found, returns null. If tester is not signed in, presents the tester with a Google
   * sign in UI
   */
  @NonNull
  public Task<AppDistributionRelease> checkForUpdate() {

    if (checkForUpdateTaskCompletionSource != null
        && !checkForUpdateTaskCompletionSource.getTask().isComplete()) {
      checkForUpdateCancellationSource.cancel();
    }

    checkForUpdateCancellationSource = new CancellationTokenSource();
    checkForUpdateTaskCompletionSource =
        new TaskCompletionSource<>(checkForUpdateCancellationSource.getToken());

    Task<String> installationIdTask = firebaseInstallationsApi.getId();
    // forceRefresh is false to get locally cached token if available
    Task<InstallationTokenResult> installationAuthTokenTask =
        firebaseInstallationsApi.getToken(false);

    Tasks.whenAllSuccess(installationIdTask, installationAuthTokenTask)
        .addOnSuccessListener(
            tasks -> {
              String fid = installationIdTask.getResult();
              InstallationTokenResult installationTokenResult =
                  installationAuthTokenTask.getResult();
              checkForUpdateExecutor.execute(
                  () -> {
                    try {
                      AppDistributionReleaseInternal latestRelease =
                          getLatestReleaseFromClient(
                              fid,
                              firebaseApp.getOptions().getApplicationId(),
                              firebaseApp.getOptions().getApiKey(),
                              installationTokenResult.getToken());
                      updateOnUiThread(
                          () -> {
                            setCachedLatestRelease(latestRelease);
                            if (checkForUpdateTaskCompletionSource != null
                                && !checkForUpdateTaskCompletionSource.getTask().isComplete())
                              checkForUpdateTaskCompletionSource.setResult(
                                  convertToAppDistributionRelease(latestRelease));
                          });
                    } catch (FirebaseAppDistributionException ex) {
                      updateOnUiThread(() -> setCheckForUpdateTaskCompletionError(ex));
                    }
                  });
            })
        .addOnFailureListener(
            e ->
                setCheckForUpdateTaskCompletionError(
                    new FirebaseAppDistributionException(
                        Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE, e)));

    return checkForUpdateTaskCompletionSource.getTask();
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

    if (updateAppTaskCompletionSource != null
        && !updateAppTaskCompletionSource.getTask().isComplete()) {
      updateAppCancellationSource.cancel();
    }

    updateAppCancellationSource = new CancellationTokenSource();
    updateAppTaskCompletionSource =
        new TaskCompletionSource<>(updateAppCancellationSource.getToken());
    Context context = firebaseApp.getApplicationContext();
    this.updateTask = new UpdateTaskImpl(updateAppTaskCompletionSource.getTask());

    if (cachedLatestRelease == null) {
      setUpdateAppTaskCompletionError(
          new FirebaseAppDistributionException(
              context.getString(R.string.no_update_available),
              FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE));
    } else {
      if (cachedLatestRelease.getBinaryType() == BinaryType.AAB) {
        redirectToPlayForAabUpdate(cachedLatestRelease.getDownloadUrl());
      } else {
        // todo: create update class when implementing APK
        throw new UnsupportedOperationException("Not yet implemented.");
      }
    }

    return this.updateTask;
  }

  /** Returns true if the App Distribution tester is signed in */
  public boolean isTesterSignedIn() {
    // todo: implement when signIn persistence is done
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  /** Signs out the App Distribution tester */
  public void signOutTester() {
    // todo: implement when signIn persistence is done
    throw new UnsupportedOperationException("Not yet implemented.");
  }

  @Override
  public void onActivityCreated(@NonNull Activity activity, @Nullable Bundle bundle) {
    Log.d(TAG, "Created activity: " + activity.getClass().getName());
    // if signinactivity is created, sign-in was succesful
    if (activity instanceof SignInResultActivity) {
      currentlySigningIn = false;
      signInTaskCompletionSource.setResult(null);
    }
  }

  @Override
  public void onActivityStarted(@NonNull Activity activity) {
    Log.d(TAG, "Started activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityResumed(@NonNull Activity activity) {
    Log.d(TAG, "Resumed activity: " + activity.getClass().getName());

    // signInActivity is only opened after successful redirection from signIn flow,
    // should not be treated as reentering the app
    if (activity instanceof SignInResultActivity) {
      return;
    }

    // throw error if app reentered during signin
    if (currentlySigningIn) {
      currentlySigningIn = false;
      setSignInTaskCompletionError(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.AUTHENTICATION_CANCELLED, AUTHENTICATION_CANCELED));
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

  private static String getApplicationName(Context context) {
    try {
      return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
    } catch (Exception e) {
      Log.e(TAG, "Unable to retrieve App name");
      return "";
    }
  }

  private void openSignInFlowInBrowser(Uri uri) {
    currentlySigningIn = true;
    if (supportsCustomTabs(firebaseApp.getApplicationContext())) {
      // If we can launch a chrome view, try that.
      CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
      Intent intent = customTabsIntent.intent;
      intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      customTabsIntent.launchUrl(currentActivity, uri);

    } else {
      // If we can't launch a chrome view try to launch anything that can handle a URL.
      Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      currentActivity.startActivity(browserIntent);
    }
  }

  private boolean supportsCustomTabs(Context context) {
    Intent customTabIntent = new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    List<ResolveInfo> resolveInfos =
        context.getPackageManager().queryIntentServices(customTabIntent, 0);
    return resolveInfos != null && !resolveInfos.isEmpty();
  }

  private AlertDialog getSignInAlertDialog(Context context) {
    AlertDialog alertDialog = new AlertDialog.Builder(currentActivity).create();
    alertDialog.setTitle(context.getString(R.string.signin_dialog_title));
    alertDialog.setMessage(context.getString(R.string.singin_dialog_message));
    alertDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.singin_yes_button),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            firebaseInstallationsApi
                .getId()
                .addOnSuccessListener(getFidGenerationOnSuccessListener(context))
                .addOnFailureListener(
                    new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        setSignInTaskCompletionError(
                            new FirebaseAppDistributionException(
                                Constants.ErrorMessages.AUTHENTICATION_ERROR,
                                AUTHENTICATION_FAILURE,
                                e));
                      }
                    });
          }
        });
    alertDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.singin_no_button),
        new DialogInterface.OnClickListener() {
          @Override
          public void onClick(DialogInterface dialogInterface, int i) {
            setSignInTaskCompletionError(
                new FirebaseAppDistributionException(
                    "Tester cancelled authentication flow", AUTHENTICATION_CANCELED));
            dialogInterface.dismiss();
          }
        });
    return alertDialog;
  }

  private OnSuccessListener<String> getFidGenerationOnSuccessListener(Context context) {
    return new OnSuccessListener<String>() {
      @Override
      public void onSuccess(String fid) {
        Uri uri =
            Uri.parse(
                String.format(
                    SIGNIN_REDIRECT_URL,
                    firebaseApp.getOptions().getApplicationId(),
                    fid,
                    getApplicationName(context),
                    context.getPackageName()));
        openSignInFlowInBrowser(uri);
      }
    };
  }

  @VisibleForTesting
  AppDistributionReleaseInternal getLatestReleaseFromClient(
      String fid, String appId, String apiKey, String authToken)
      throws FirebaseAppDistributionException {
    try {
      AppDistributionReleaseInternal retrievedLatestRelease =
          firebaseAppDistributionTesterApiClient.fetchLatestRelease(fid, appId, apiKey, authToken);

      if (isNewerBuildVersion(retrievedLatestRelease)
          && !isInstalledRelease(retrievedLatestRelease)) {
        return retrievedLatestRelease;
      } else {
        // Return null if retrieved latest release is older or currently installed
        return null;
      }
    } catch (NumberFormatException e) {
      throw new FirebaseAppDistributionException(
          Constants.ErrorMessages.NETWORK_ERROR,
          FirebaseAppDistributionException.Status.NETWORK_FAILURE,
          e);
    }
  }

  @VisibleForTesting
  void setCachedLatestRelease(AppDistributionReleaseInternal latestRelease) {
    this.cachedLatestRelease = latestRelease;
  }

  private AppDistributionRelease convertToAppDistributionRelease(
      AppDistributionReleaseInternal internalRelease) {
    return AppDistributionRelease.builder()
        .setBuildVersion(internalRelease.getBuildVersion())
        .setDisplayVersion(internalRelease.getDisplayVersion())
        .setReleaseNotes(internalRelease.getReleaseNotes())
        .setBinaryType(internalRelease.getBinaryType())
        .build();
  }

  private void updateOnUiThread(Runnable runnable) {
    HandlerCompat.createAsync(Looper.getMainLooper()).post(runnable);
  }

  private long getInstalledAppVersionCode(Context context) {
    PackageInfo pInfo = null;
    try {
      pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      checkForUpdateTaskCompletionSource.setException(
          new FirebaseAppDistributionException(
              Constants.ErrorMessages.UNKNOWN_ERROR,
              FirebaseAppDistributionException.Status.UNKNOWN,
              e));
    }
    return PackageInfoCompat.getLongVersionCode(pInfo);
  }

  private void setCheckForUpdateTaskCompletionError(FirebaseAppDistributionException e) {
    if (checkForUpdateTaskCompletionSource != null
        && !checkForUpdateTaskCompletionSource.getTask().isComplete()) {
      this.checkForUpdateTaskCompletionSource.setException(e);
    }
  }

  private void setSignInTaskCompletionError(FirebaseAppDistributionException e) {
    if (signInTaskCompletionSource != null && !signInTaskCompletionSource.getTask().isComplete()) {
      signInTaskCompletionSource.setException(e);
    }
  }

  private void setUpdateAppTaskCompletionError(FirebaseAppDistributionException e) {
    if (updateAppTaskCompletionSource != null
        && !updateAppTaskCompletionSource.getTask().isComplete()) {
      updateAppTaskCompletionSource.setException(e);
    }
  }

  private void setUpdateToLatestReleaseTaskCompletionError(FirebaseAppDistributionException e) {
    if (updateToLatestReleaseTaskCompletionSource != null
        && !updateToLatestReleaseTaskCompletionSource.getTask().isComplete()) {
      updateToLatestReleaseTaskCompletionSource.setException(e);
    }
  }

  private void setUpdateToLatestReleaseErrorWithDefault(
      Exception e, FirebaseAppDistributionException defaultFirebaseException) {
    if (e instanceof FirebaseAppDistributionException) {
      setUpdateToLatestReleaseTaskCompletionError((FirebaseAppDistributionException) e);
    } else {
      setUpdateToLatestReleaseTaskCompletionError(defaultFirebaseException);
    }
  }

  private boolean isNewerBuildVersion(AppDistributionReleaseInternal latestRelease) {
    return Long.parseLong(latestRelease.getBuildVersion())
        >= getInstalledAppVersionCode(firebaseApp.getApplicationContext());
  }

  private boolean isInstalledRelease(AppDistributionReleaseInternal latestRelease) {
    if (latestRelease.getBinaryType().equals(BinaryType.APK)) {
      // TODO(rachelprince): APK codehash verification
      return false;
    }

    if (latestRelease.getIasArtifactId() == null) {
      return false;
    }
    // AAB BinaryType
    return latestRelease
        .getIasArtifactId()
        .equals(
            ReleaseIdentificationUtils.extractInternalAppSharingArtifactId(
                firebaseApp.getApplicationContext()));
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

  private void showUpdateAlertDialog(AppDistributionRelease latestRelease) {
    Context context = firebaseApp.getApplicationContext();
    AlertDialog alertDialog = new AlertDialog.Builder(currentActivity).create();
    alertDialog.setTitle(context.getString(R.string.update_dialog_title));

    alertDialog.setMessage(
        String.format(
            "Version %s (%s) is available.",
            latestRelease.getDisplayVersion(), latestRelease.getBuildVersion()));

    alertDialog.setButton(
        AlertDialog.BUTTON_POSITIVE,
        context.getString(R.string.update_yes_button),
        (dialogInterface, i) -> {
          try {
            updateApp()
                .addOnSuccessListener(
                    updateState ->
                        updateToLatestReleaseTaskCompletionSource.setResult(latestRelease))
                .addOnFailureListener(
                    e ->
                        setUpdateToLatestReleaseErrorWithDefault(
                            e,
                            new FirebaseAppDistributionException(
                                Constants.ErrorMessages.NETWORK_ERROR, NETWORK_FAILURE)));
          } catch (FirebaseAppDistributionException e) {
            setUpdateToLatestReleaseTaskCompletionError(e);
          }
        });
    alertDialog.setButton(
        AlertDialog.BUTTON_NEGATIVE,
        context.getString(R.string.update_no_button),
        (dialogInterface, i) -> {
          dialogInterface.dismiss();
          setUpdateToLatestReleaseTaskCompletionError(
              new FirebaseAppDistributionException(
                  Constants.ErrorMessages.UPDATE_CANCELLED,
                  FirebaseAppDistributionException.Status.INSTALLATION_CANCELED));
        });

    alertDialog.show();
  }
}
