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
import static com.google.firebase.appdistribution.TaskUtils.safeSetTaskException;
import static com.google.firebase.appdistribution.TaskUtils.safeSetTaskResult;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appdistribution.Constants.ErrorMessages;
import com.google.firebase.appdistribution.internal.InstallActivity;
import com.google.firebase.appdistribution.internal.LogWrapper;
import com.google.firebase.appdistribution.internal.SignInResultActivity;
import com.google.firebase.appdistribution.internal.SignInStorage;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.List;

/** Class that handles signing in the tester */
class TesterSignInManager {
  private static final String TAG = "TesterSignIn:";
  private static final String SIGNIN_REDIRECT_URL =
      "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts?appName=%s&packageName=%s";

  private final FirebaseApp firebaseApp;
  private final Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider;
  private final SignInStorage signInStorage;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  private final Object signInTaskLock = new Object();

  @GuardedBy("signInTaskLock")
  private TaskCompletionSource<Void> signInTaskCompletionSource = null;

  TesterSignInManager(
      @NonNull FirebaseApp firebaseApp,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      @NonNull final SignInStorage signInStorage) {
    this(
        firebaseApp,
        firebaseInstallationsApiProvider,
        signInStorage,
        FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  @VisibleForTesting
  TesterSignInManager(
      @NonNull FirebaseApp firebaseApp,
      @NonNull Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      @NonNull final SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstallationsApiProvider = firebaseInstallationsApiProvider;
    this.signInStorage = signInStorage;
    this.lifecycleNotifier = lifecycleNotifier;

    lifecycleNotifier.addOnActivityCreatedListener(this::onActivityCreated);
    lifecycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
  }

  @VisibleForTesting
  void onActivityCreated(Activity activity) {
    // We call finish() in the onCreate method of the SignInResultActivity, so we must set the
    // result
    // of the signIn Task in the onActivityCreated callback
    if (activity instanceof SignInResultActivity) {
      LogWrapper.getInstance().v("Sign in completed");
      this.setSuccessfulSignInResult();
      this.signInStorage.setSignInStatus(true);
    }
  }

  @VisibleForTesting
  void onActivityStarted(Activity activity) {
    if (activity instanceof SignInResultActivity || activity instanceof InstallActivity) {
      // SignInResult and InstallActivity are internal to the SDK and should not be treated as
      // reentering the app
      return;
    } else {
      // Throw error if app reentered during sign in
      if (this.isCurrentlySigningIn()) {
        LogWrapper.getInstance().e("App Resumed without sign in flow completing.");
        this.setCanceledAuthenticationError();
      }
    }
  }

  @NonNull
  public Task<Void> signInTester() {

    if (signInStorage.getSignInStatus()) {
      LogWrapper.getInstance().v(TAG + "Tester is already signed in.");
      return Tasks.forResult(null);
    }

    synchronized (signInTaskLock) {
      if (this.isCurrentlySigningIn()) {
        LogWrapper.getInstance()
            .v(TAG + "Detected In-Progress sign in task. Returning the same task.");
        return signInTaskCompletionSource.getTask();
      }

      // TODO(rachelprince): Change this to getCurrentNonNullActivity
      Activity currentActivity = lifecycleNotifier.getCurrentActivity();
      if (currentActivity == null) {
        LogWrapper.getInstance().e(TAG + "No foreground activity found.");
        return Tasks.forException(
            new FirebaseAppDistributionException(
                ErrorMessages.APP_BACKGROUNDED,
                FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE));
      }

      signInTaskCompletionSource = new TaskCompletionSource<>();

      firebaseInstallationsApiProvider
          .get()
          .getId()
          .addOnSuccessListener(getFidGenerationOnSuccessListener(currentActivity))
          .addOnFailureListener(
              e -> {
                LogWrapper.getInstance().e(TAG + "Fid retrieval failed.", e);
                setSignInTaskCompletionError(
                    new FirebaseAppDistributionException(
                        Constants.ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE, e));
              });

      return signInTaskCompletionSource.getTask();
    }
  }

  private boolean isCurrentlySigningIn() {
    synchronized (signInTaskLock) {
      return signInTaskCompletionSource != null
          && !signInTaskCompletionSource.getTask().isComplete();
    }
  }

  private void setSignInTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (signInTaskLock) {
      safeSetTaskException(signInTaskCompletionSource, e);
    }
  }

  private void setCanceledAuthenticationError() {
    setSignInTaskCompletionError(
        new FirebaseAppDistributionException(
            Constants.ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED));
  }

  private void setSuccessfulSignInResult() {
    synchronized (signInTaskLock) {
      safeSetTaskResult(signInTaskCompletionSource, null);
    }
  }

  private OnSuccessListener<String> getFidGenerationOnSuccessListener(Activity currentActivity) {
    return fid -> {
      Context context = firebaseApp.getApplicationContext();
      Uri uri =
          Uri.parse(
              String.format(
                  SIGNIN_REDIRECT_URL,
                  firebaseApp.getOptions().getApplicationId(),
                  fid,
                  getApplicationName(context),
                  context.getPackageName()));
      openSignInFlowInBrowser(currentActivity, uri);
    };
  }

  private static String getApplicationName(Context context) {
    try {
      return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
    } catch (Exception e) {
      LogWrapper.getInstance().e(TAG + "Unable to retrieve App name", e);
      return "";
    }
  }

  private void openSignInFlowInBrowser(Activity currentActivity, Uri uri) {
    LogWrapper.getInstance().v(TAG + "Opening sign in flow in browser at " + uri);
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
}
