// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.debug.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.res.Resources;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.debug.InternalDebugSecretProvider;
import com.google.firebase.appcheck.debug.R;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import com.google.firebase.appcheck.internal.util.Logger;
import com.google.firebase.inject.Provider;
import java.util.UUID;
import java.util.concurrent.Executor;

public class DebugAppCheckProvider implements AppCheckProvider {

  private static final String TAG = DebugAppCheckProvider.class.getName();
  private static final Logger logger = Logger.getLogger();
  private static final String UTF_8 = "UTF-8";

  private final NetworkClient networkClient;
  private final Executor liteExecutor;
  private final Executor blockingExecutor;
  private final RetryManager retryManager;
  private final Task<String> debugSecretTask;

  public DebugAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @NonNull Provider<InternalDebugSecretProvider> debugSecretProvider,
      @Lightweight Executor liteExecutor,
      @Background Executor backgroundExecutor,
      @Blocking Executor blockingExecutor) {
    checkNotNull(firebaseApp);
    this.networkClient = new NetworkClient(firebaseApp);
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = new RetryManager();

    this.debugSecretTask =
        determineDebugSecret(firebaseApp, debugSecretProvider, backgroundExecutor);
    this.debugSecretTask.addOnSuccessListener(liteExecutor, this::logDebugSecret);
  }

  private void logDebugSecret(String secret) {
    String consoleUrl = getConsoleUrl();
    String appId = networkClient.getAppId();
    String projectId = networkClient.getProjectId();
    String message =
        String.format(
            "Firebase App Check debug token: %s%n%n"
                + "To use this token for app debugging, register it with your project.%n%n"
                + "You can do so in the Firebase Console: %n"
                + "%s%n%n"
                + "Or using the Firebase CLI: %n"
                + "firebase appcheck:debugtokens:create %s --project %s --app %s%n%n"
                + "Note: To keep your project secure, please revoke and delete this token using the %n"
                + "Firebase Console or the CLI (`firebase appcheck:debugtokens:delete`) when you finish debugging.%n%n"
                + "Warning: This debug token is a secret and should not be shared or uploaded to source code.%n%n"
                + "Debug Token Guide: https://firebase.google.com/docs/app-check/ios/debug-provider%n"
                + "Firebase CLI install instructions: https://firebase.google.com/docs/cli",
            secret, consoleUrl, secret, projectId, appId);
    Log.d(TAG, message);
  }

  @VisibleForTesting
  DebugAppCheckProvider(
      @NonNull String debugSecret,
      @NonNull NetworkClient networkClient,
      @NonNull Executor liteExecutor,
      @NonNull Executor blockingExecutor,
      @NonNull RetryManager retryManager) {
    this.networkClient = networkClient;
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = retryManager;
    this.debugSecretTask = Tasks.forResult(debugSecret);
  }

  @VisibleForTesting
  @NonNull
  static Task<String> determineDebugSecret(
      @NonNull FirebaseApp firebaseApp, @NonNull Executor executor) {
    return determineDebugSecret(firebaseApp, /* debugSecretProvider= */ null, executor);
  }

  @VisibleForTesting
  @NonNull
  static Task<String> determineDebugSecret(
      @NonNull FirebaseApp firebaseApp,
      @Nullable Provider<InternalDebugSecretProvider> debugSecretProvider,
      @NonNull Executor executor) {
    if (debugSecretProvider != null && debugSecretProvider.get() != null) {
      String providerSecret = debugSecretProvider.get().getDebugSecret();
      if (!isNullOrEmpty(providerSecret)) {
        logger.i("Debug token loaded from InternalDebugSecretProvider.");
        return Tasks.forResult(providerSecret);
      }
    }

    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          Context context = firebaseApp.getApplicationContext();
          if (context != null && context.getResources() != null) {
            String resourceToken = null;
            try {
              resourceToken = context.getString(R.string.firebase_app_check_debug_token);
            } catch (Resources.NotFoundException ignored) {
              // Treat missing resource as unconfigured.
            }
            if (!isNullOrEmpty(resourceToken)) {
              ApplicationInfo appInfo = context.getApplicationInfo();
              boolean isDebuggable =
                  (appInfo != null) && ((appInfo.flags & ApplicationInfo.FLAG_DEBUGGABLE) != 0);
              if (!isDebuggable) {
                logger.e(
                    "SECURITY WARNING: App Check debug token found in a non-debuggable Release build! Ignoring token.");
              } else {
                logger.i("Debug token loaded from R.string.firebase_app_check_debug_token.");
                taskCompletionSource.setResult(resourceToken);
                return;
              }
            }
          }

          StorageHelper storageHelper =
              new StorageHelper(
                  firebaseApp.getApplicationContext(), firebaseApp.getPersistenceKey());
          String debugSecret = storageHelper.retrieveDebugSecret();
          if (!isNullOrEmpty(debugSecret)) {
            logger.i("Debug token loaded from SharedPreferences.");
          } else {
            debugSecret = UUID.randomUUID().toString();
            storageHelper.saveDebugSecret(debugSecret);
            logger.i("Debug token loaded from auto-generated UUID.");
          }
          taskCompletionSource.setResult(debugSecret);
        });
    return taskCompletionSource.getTask();
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getToken() {
    return getToken(/* isLimitedUseToken= */ false);
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getLimitedUseToken() {
    return getToken(/* isLimitedUseToken= */ true);
  }

  private Task<AppCheckToken> getToken(boolean isLimitedUseToken) {
    return debugSecretTask
        .onSuccessTask(
            liteExecutor,
            debugSecret -> {
              ExchangeDebugTokenRequest request =
                  new ExchangeDebugTokenRequest(debugSecret, isLimitedUseToken);
              return Tasks.call(
                      blockingExecutor,
                      () ->
                          networkClient.exchangeAttestationForAppCheckToken(
                              request.toJsonString().getBytes(UTF_8),
                              NetworkClient.DEBUG,
                              retryManager))
                  .addOnFailureListener(
                      liteExecutor, e -> logDebugTokenExchangeError(debugSecret, e));
            })
        .onSuccessTask(
            liteExecutor,
            response ->
                Tasks.forResult(DefaultAppCheckToken.constructFromAppCheckTokenResponse(response)));
  }

  private void logDebugTokenExchangeError(String debugSecret, @Nullable Exception e) {
    if (e != null && e.getMessage() != null && e.getMessage().contains("code: 403")) {
      logger.w(
          "Debug token was rejected by server. Run 'firebase appcheck:debugtokens:create --force' to provision a new token.");
    }
    String consoleUrl = getConsoleUrl();
    if (consoleUrl != null) {
      logger.w(
          "Failed to exchange debug token ("
              + debugSecret
              + "). If you haven't registered it, you can do so in the Firebase Console: "
              + consoleUrl);
    }
  }

  @Nullable
  private String getConsoleUrl() {
    String projectId = networkClient.getProjectId();
    String appId = networkClient.getAppId();
    if (isNullOrEmpty(projectId) || isNullOrEmpty(appId)) {
      return null;
    }
    return "https://console.firebase.google.com/project/"
        + projectId
        + "/appcheck/apps?selectedAppId="
        + appId;
  }

  private static boolean isNullOrEmpty(@Nullable String str) {
    return str == null || str.isEmpty();
  }
}
