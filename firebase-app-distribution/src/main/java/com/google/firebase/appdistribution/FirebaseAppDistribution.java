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
import android.os.Handler;
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
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class FirebaseAppDistribution implements Application.ActivityLifecycleCallbacks {

  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallationsApi;
  private static final String TAG = "FirebaseAppDistribution";
  private Activity currentActivity;
  @VisibleForTesting private boolean currentlySigningIn = false;
  private TaskCompletionSource<Void> signInTaskCompletionSource = null;
  private CancellationTokenSource signInCancellationSource;

  private TaskCompletionSource<AppDistributionRelease> checkForUpdateTaskCompletionSource = null;
  private CancellationTokenSource checkForUpdateCancellationSource;
  private Handler checkForUpdateHandler;
  private Executor checkForUpdateExecutor;

  /** Constructor for FirebaseAppDistribution */
  public FirebaseAppDistribution(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.checkForUpdateExecutor = Executors.newFixedThreadPool(4);
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
    return (FirebaseAppDistribution) app.get(FirebaseAppDistribution.class);
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

    TaskCompletionSource<AppDistributionRelease> taskCompletionSource =
        new TaskCompletionSource<>();

    signInTester()
        .addOnSuccessListener(
            new OnSuccessListener<Void>() {
              @Override
              public void onSuccess(Void unused) {
                taskCompletionSource.setResult(null);
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull @NotNull Exception e) {
                taskCompletionSource.setException(e);
              }
            });

    return taskCompletionSource.getTask();
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
    checkForUpdateHandler = HandlerCompat.createAsync(Looper.getMainLooper());

    firebaseInstallationsApi
        .getId()
        .addOnSuccessListener(
            new OnSuccessListener<String>() {
              @Override
              public void onSuccess(String fid) {
                firebaseInstallationsApi
                    .getToken(true)
                    .addOnSuccessListener(
                        new OnSuccessListener<InstallationTokenResult>() {
                          @Override
                          public void onSuccess(InstallationTokenResult installationTokenResult) {

                            String authToken = installationTokenResult.getToken();
                            String appId = firebaseApp.getOptions().getApplicationId();
                            String apiKey = firebaseApp.getOptions().getApiKey();

                            //make into method parameters
                            FirebaseAppDistributionTesterApiClient client =
                                new FirebaseAppDistributionTesterApiClient(fid, appId, apiKey);

                            handleLatestReleaseFromClient(client, authToken);
                          }
                        });
              }
            })
        .addOnFailureListener(
            new OnFailureListener() {
              @Override
              public void onFailure(@NonNull Exception e) {
                setCheckForUpdateTaskCompletionError(
                    new FirebaseAppDistributionException(
                        FirebaseAppDistributionException.Status.UNKNOWN));
              }
            });

    return checkForUpdateTaskCompletionSource.getTask();
  }

  private void handleLatestReleaseFromClient(
      FirebaseAppDistributionTesterApiClient client, String authToken) {
    checkForUpdateExecutor.execute(
        new Runnable() {
          @Override
          public void run() {
            try {
              AppDistributionRelease latestRelease = client.fetchLatestRelease(authToken);

              Context context = firebaseApp.getApplicationContext();
              long currentInstalledVersionCode = getInstalledAppVersionCode(context);

              //move logic outside,
              checkForUpdateHandler.post(
                  new Runnable() {
                    @Override
                    public void run() {
                      if (currentInstalledVersionCode
                          < Long.parseLong(latestRelease.getBuildVersion())) {
                        // latest release is newer
                        checkForUpdateTaskCompletionSource.setResult(latestRelease);
                      } else {
                        checkForUpdateTaskCompletionSource.setResult(null);
                      }
                    }
                  });
            } catch (Exception e) {
              setCheckForUpdateTaskCompletionError(
                  new FirebaseAppDistributionException(
                      FirebaseAppDistributionException.Status.UNKNOWN));
            }
          }
        });
  }

  private long getInstalledAppVersionCode(Context context) {
    PackageInfo pInfo = null;
    try {
      pInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0);
    } catch (PackageManager.NameNotFoundException e) {
      checkForUpdateTaskCompletionSource.setException(
          new FirebaseAppDistributionException(FirebaseAppDistributionException.Status.UNKNOWN));
    }
    return PackageInfoCompat.getLongVersionCode(pInfo);
  }

  private void setCheckForUpdateTaskCompletionError(FirebaseAppDistributionException e) {
    if (checkForUpdateTaskCompletionSource != null
        && !checkForUpdateTaskCompletionSource.getTask().isComplete()) {
      this.checkForUpdateTaskCompletionSource.setException(e);
    }
  }

  /**
   * Updates app to the latest release. If the latest release is an APK, downloads the binary and
   * starts an installation If the latest release is an AAB, directs the tester to the Play app to
   * complete the download and installation.
   *
   * @throws FirebaseAppDistributionException with UPDATE_NOT_AVAIALBLE exception if no new release
   *     is cached from checkForUpdate
   */
  @NonNull
  public UpdateTask updateApp() {
    return (UpdateTask) Tasks.forResult(new UpdateState(0, 0, UpdateStatus.PENDING));
  }

  private boolean supportsCustomTabs(Context context) {
    Intent customTabIntent = new Intent("android.support.customtabs.action.CustomTabsService");
    customTabIntent.setPackage("com.android.chrome");
    List<ResolveInfo> resolveInfos =
        context.getPackageManager().queryIntentServices(customTabIntent, 0);
    return resolveInfos != null && !resolveInfos.isEmpty();
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
      ResolveInfo info = currentActivity.getPackageManager().resolveActivity(browserIntent, 0);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      currentActivity.startActivity(browserIntent);
    }
  }

  private OnSuccessListener<String> getFidGenerationOnSuccessListener(Context context) {
    return new OnSuccessListener<String>() {
      @Override
      public void onSuccess(String fid) {
        Uri uri =
            Uri.parse(
                String.format(
                    "https://appdistribution.firebase.google.com/pub/apps/%s/installations/%s/buildalerts?appName=%s&packageName=%s",
                    firebaseApp.getOptions().getApplicationId(),
                    fid,
                    getApplicationName(context),
                    context.getPackageName()));
        openSignInFlowInBrowser(uri);
      }
    };
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
                      public void onFailure(@NonNull @NotNull Exception e) {
                        setSignInTaskCompletionError(
                            new FirebaseAppDistributionException(AUTHENTICATION_FAILURE));
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
                new FirebaseAppDistributionException(AUTHENTICATION_CANCELED));
            dialogInterface.dismiss();
          }
        });
    return alertDialog;
  }

  /** Signs in the App Distribution tester. Presents the tester with a Google sign in UI */
  @NonNull
  public Task<Void> signInTester() {
    if (signInTaskCompletionSource != null && !signInTaskCompletionSource.getTask().isComplete()) {
      signInCancellationSource.cancel();
    }

    signInCancellationSource = new CancellationTokenSource();
    signInTaskCompletionSource = new TaskCompletionSource<>(signInCancellationSource.getToken());

    Context context = firebaseApp.getApplicationContext();
    AlertDialog alertDialog = getSignInAlertDialog(context);
    alertDialog.show();

    return signInTaskCompletionSource.getTask();
  }

  private void setSignInTaskCompletionError(FirebaseAppDistributionException e) {
    if (signInTaskCompletionSource != null && !signInTaskCompletionSource.getTask().isComplete()) {
      signInTaskCompletionSource.setException(e);
    }
  }

  /** Returns true if the App Distribution tester is signed in */
  @NonNull
  public boolean isTesterSignedIn() {
    return false;
  }

  /** Signs out the App Distribution tester */
  public void signOutTester() {}

  @Override
  public void onActivityCreated(
      @NonNull @NotNull Activity activity, @androidx.annotation.Nullable @Nullable Bundle bundle) {
    Log.d(TAG, "Created activity: " + activity.getClass().getName());
    // if signinactivity is created, sign-in was succesful
    if (currentlySigningIn && activity instanceof SignInResultActivity) {
      currentlySigningIn = false;
      signInTaskCompletionSource.setResult(null);
    }
  }

  @Override
  public void onActivityStarted(@NonNull @NotNull Activity activity) {
    Log.d(TAG, "Started activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityResumed(@NonNull @NotNull Activity activity) {
    Log.d(TAG, "Resumed activity: " + activity.getClass().getName());

    // signInActivity is only opened after successful redirection from signIn flow,
    // should not be treated as reentering the app
    if (activity instanceof SignInResultActivity) {
      return;
    }

    // throw error if app reentered during signin
    if (currentlySigningIn) {
      currentlySigningIn = false;
      setSignInTaskCompletionError(new FirebaseAppDistributionException(AUTHENTICATION_FAILURE));
    }
    this.currentActivity = activity;
  }

  @Override
  public void onActivityPaused(@NonNull @NotNull Activity activity) {
    Log.d(TAG, "Paused activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityStopped(@NonNull @NotNull Activity activity) {
    Log.d(TAG, "Stopped activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivitySaveInstanceState(
      @NonNull @NotNull Activity activity, @NonNull @NotNull Bundle bundle) {
    Log.d(TAG, "Saved activity: " + activity.getClass().getName());
  }

  @Override
  public void onActivityDestroyed(@NonNull @NotNull Activity activity) {
    Log.d(TAG, "Destroyed activity: " + activity.getClass().getName());
    if (this.currentActivity == activity) {
      this.currentActivity = null;
    }
  }
}
