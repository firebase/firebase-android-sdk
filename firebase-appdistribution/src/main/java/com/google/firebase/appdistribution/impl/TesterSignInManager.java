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
import static com.google.firebase.appdistribution.FirebaseAppDistributionException.Status.UNKNOWN;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import androidx.browser.customtabs.CustomTabsIntent;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import java.util.List;
import java.util.concurrent.Executor;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Class that handles signing in the tester. */
// TODO(b/266704696): This currently only supports one FirebaseAppDistribution instance app-wide
@Singleton
class TesterSignInManager {
  private static final String TAG = "TesterSignInManager";

  private static final String SIGNIN_REDIRECT_URL =
      "https://appdistribution.firebase.google.com/pub/testerapps/%s/installations/%s/buildalerts?appName=%s&packageName=%s&newRedirectScheme=true";

  private final Context applicationContext;
  private final FirebaseOptions firebaseOptions;
  private final Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider;
  private final SignInStorage signInStorage;
  private final FirebaseAppDistributionLifecycleNotifier lifecycleNotifier;

  private final DevModeDetector devModeDetector;
  @Lightweight private final Executor lightweightExecutor;
  private final TaskCompletionSourceCache<Void> signInTaskCompletionSourceCache;

  private boolean hasBeenSentToBrowserForCurrentTask = false;

  @Inject
  TesterSignInManager(
      Context applicationContext,
      FirebaseOptions firebaseOptions,
      Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider,
      SignInStorage signInStorage,
      FirebaseAppDistributionLifecycleNotifier lifecycleNotifier,
      DevModeDetector devModeDetector,
      @Lightweight Executor lightweightExecutor) {
    this.applicationContext = applicationContext;
    this.firebaseOptions = firebaseOptions;
    this.firebaseInstallationsApiProvider = firebaseInstallationsApiProvider;
    this.signInStorage = signInStorage;
    this.lifecycleNotifier = lifecycleNotifier;
    this.devModeDetector = devModeDetector;
    this.lightweightExecutor = lightweightExecutor;
    this.signInTaskCompletionSourceCache = new TaskCompletionSourceCache<>(lightweightExecutor);

    lifecycleNotifier.addOnActivityCreatedListener(this::onActivityCreated);
    lifecycleNotifier.addOnActivityResumedListener(this::onActivityResumed);
  }

  @VisibleForTesting
  void onActivityCreated(Activity activity) {
    // We call finish() in the onCreate method of the SignInResultActivity, so we must set the
    // result of the signIn Task in the onActivityCreated callback
    if (activity instanceof SignInResultActivity) {
      LogWrapper.v(TAG, "Sign in completed");
      this.signInStorage
          .setSignInStatus(true)
          .addOnSuccessListener(
              lightweightExecutor, unused -> signInTaskCompletionSourceCache.setResult(null))
          .addOnFailureListener(
              lightweightExecutor,
              handleTaskFailure("Error storing tester sign in state", UNKNOWN));
    }
  }

  @VisibleForTesting
  void onActivityResumed(Activity activity) {
    if (activity instanceof SignInResultActivity || activity instanceof InstallActivity) {
      // SignInResult and InstallActivity are internal to the SDK and should not be treated as
      // reentering the app
      return;
    }

    // Throw error if app reentered during sign in
    if (hasBeenSentToBrowserForCurrentTask) {
      signInTaskCompletionSourceCache
          .setException(
              new FirebaseAppDistributionException(
                  ErrorMessages.AUTHENTICATION_CANCELED, AUTHENTICATION_CANCELED))
          .addOnSuccessListener(
              lightweightExecutor,
              unused -> LogWrapper.e(TAG, "App resumed without sign in flow completing."));
    }
  }

  @NonNull
  public Task<Void> signInTester() {
    return signInStorage
        .getSignInStatus()
        .onSuccessTask(
            lightweightExecutor,
            signedIn -> {
              if (signedIn) {
                LogWrapper.v(TAG, "Tester is already signed in.");
                return Tasks.forResult(null);
              }
              return doSignInTester();
            });
  }

  private Task<Void> doSignInTester() {
    if (devModeDetector.isDevModeEnabled()) {
      LogWrapper.w(TAG, "Skipping actual tester sign in because dev mode is enabled");
      signInStorage.setSignInStatus(true);
      return Tasks.forResult(null);
    }
    return signInTaskCompletionSourceCache.getOrCreateTaskFromCompletionSource(
        () -> {
          TaskCompletionSource<Void> signInTaskCompletionSource = new TaskCompletionSource<>();
          hasBeenSentToBrowserForCurrentTask = false;
          firebaseInstallationsApiProvider
              .get()
              .getId()
              .addOnFailureListener(
                  lightweightExecutor,
                  handleTaskFailure(ErrorMessages.AUTHENTICATION_ERROR, AUTHENTICATION_FAILURE))
              .addOnSuccessListener(
                  lightweightExecutor,
                  fid ->
                      getForegroundActivityAndOpenSignInFlow(fid)
                          .addOnFailureListener(
                              lightweightExecutor,
                              handleTaskFailure(ErrorMessages.UNKNOWN_ERROR, UNKNOWN)));
          return signInTaskCompletionSource;
        });
  }

  private Task<Void> getForegroundActivityAndOpenSignInFlow(String fid) {
    return lifecycleNotifier.consumeForegroundActivity(
        activity -> {
          openSignInFlowInBrowser(fid, activity);
          hasBeenSentToBrowserForCurrentTask = true;
        });
  }

  private OnFailureListener handleTaskFailure(String message, Status status) {
    return e -> {
      LogWrapper.e(TAG, message, e);
      signInTaskCompletionSourceCache.setException(
          new FirebaseAppDistributionException(message, status, e));
    };
  }

  private static String getApplicationName(Context context) {
    try {
      return context.getApplicationInfo().loadLabel(context.getPackageManager()).toString();
    } catch (Exception e) {
      LogWrapper.e(TAG, "Unable to retrieve App name", e);
      return "";
    }
  }

  private void openSignInFlowInBrowser(String fid, Activity activity) {
    Uri uri =
        Uri.parse(
            String.format(
                SIGNIN_REDIRECT_URL,
                firebaseOptions.getApplicationId(),
                fid,
                getApplicationName(applicationContext),
                applicationContext.getPackageName()));
    LogWrapper.v(TAG, "Opening sign in flow in browser at " + uri);
    if (supportsCustomTabs(applicationContext)) {
      // If we can launch a chrome view, try that.
      CustomTabsIntent customTabsIntent = new CustomTabsIntent.Builder().build();
      Intent intent = customTabsIntent.intent;
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      customTabsIntent.launchUrl(activity, uri);
    } else {
      // If we can't launch a chrome view try to launch anything that can handle a URL.
      Intent browserIntent = new Intent(Intent.ACTION_VIEW, uri);
      browserIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      activity.startActivity(browserIntent);
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
