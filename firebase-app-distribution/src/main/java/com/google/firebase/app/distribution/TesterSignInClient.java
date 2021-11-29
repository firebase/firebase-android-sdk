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

import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_CANCELED;
import static com.google.firebase.app.distribution.FirebaseAppDistributionException.Status.AUTHENTICATION_FAILURE;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskException;
import static com.google.firebase.app.distribution.TaskUtils.safeSetTaskResult;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.app.distribution.Constants.ErrorMessages;
import com.google.firebase.app.distribution.internal.FirebaseAppDistributionLifecycleNotifier;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.List;

class TesterSignInClient {
  private static final String TAG = "TesterSignIn:";
  private static final String SIGNIN_REDIRECT_URL =
      "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts?appName=%s&packageName=%s";

  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallationsApi;
  private final SignInStorage signInStorage;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  private final Object signInTaskLock = new Object();

  @GuardedBy("signInTaskLock")
  private TaskCompletionSource<Void> signInTaskCompletionSource = null;

  private AlertDialog alertDialog;

  TesterSignInClient(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull final SignInStorage signInStorage) {
    this(
        firebaseApp,
        firebaseInstallationsApi,
        signInStorage,
        FirebaseAppDistributionLifecycleNotifier.getInstance());
  }

  @VisibleForTesting
  TesterSignInClient(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallationsApi,
      @NonNull final SignInStorage signInStorage,
      @NonNull FirebaseAppDistributionLifecycleNotifier lifecycleNotifier) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstallationsApi = firebaseInstallationsApi;
    this.signInStorage = signInStorage;
    this.lifecycleNotifier = lifecycleNotifier;

    lifecycleNotifier.addOnActivityCreatedListener(this::onActivityCreated);
    lifecycleNotifier.addOnActivityStartedListener(this::onActivityStarted);
    lifecycleNotifier.addOnActivityDestroyedListener(this::onActivityDestroyed);
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

  private void onActivityDestroyed(Activity activity) {
    this.dismissAlertDialog();
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
      Activity currentActivity = lifecycleNotifier.getCurrentActivity();
      if (currentActivity == null) {
        LogWrapper.getInstance().e(TAG + "No foreground activity found.");
        return Tasks.forException(
            new FirebaseAppDistributionException(
                ErrorMessages.APP_BACKGROUNDED,
                FirebaseAppDistributionException.Status.UPDATE_NOT_AVAILABLE));
      }

      signInTaskCompletionSource = new TaskCompletionSource<>();

      alertDialog = getSignInAlertDialog(currentActivity);
      alertDialog.show();

      return signInTaskCompletionSource.getTask();
    }
  }

  private boolean isCurrentlySigningIn() {
    synchronized (signInTaskLock) {
      return signInTaskCompletionSource != null
          && !signInTaskCompletionSource.getTask().isComplete();
    }
  }

  private AlertDialog getSignInAlertDialog(Activity currentActivity) {
    alertDialog = new AlertDialog.Builder(currentActivity).create();
    Context context = firebaseApp.getApplicationContext();
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
                .addOnSuccessListener(getFidGenerationOnSuccessListener(currentActivity))
                .addOnFailureListener(
                    new OnFailureListener() {
                      @Override
                      public void onFailure(@NonNull Exception e) {
                        LogWrapper.getInstance().e(TAG + "Fid retrieval failed.", e);
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
            LogWrapper.getInstance().v("Sign in has been canceled.");
            setSignInTaskCompletionError(
                new FirebaseAppDistributionException(
                    ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED));
            dialogInterface.dismiss();
          }
        });
    return alertDialog;
  }

  private void setSignInTaskCompletionError(FirebaseAppDistributionException e) {
    synchronized (signInTaskLock) {
      safeSetTaskException(signInTaskCompletionSource, e);
    }
    dismissAlertDialog();
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
    dismissAlertDialog();
  }

  private void dismissAlertDialog() {
    if (alertDialog != null && alertDialog.isShowing()) {
      alertDialog.dismiss();
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
