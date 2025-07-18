// Copyright 2025 Google LLC
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

package com.google.firebase.appcheck.recaptchaenterprise.internal;

import android.app.Application;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.recaptcha.Recaptcha;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaTasksClient;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.Executor;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedInject;

/**
 * An implementation of {@link AppCheckProvider} that uses reCAPTCHA Enterprise for device
 * attestation.
 *
 * <p>This class orchestrates the flow:
 *
 * <ol>
 *   <li>Obtain a reCAPTCHA token via {@code RecaptchaTasksClient}.
 *   <li>Exchange the reCAPTCHA token with the Firebase App Check backend to receive a Firebase App
 *       Check token.
 * </ol>
 */
public class RecaptchaEnterpriseAppCheckProvider implements AppCheckProvider {

  private final RecaptchaAction recaptchaAction = RecaptchaAction.custom("fire_app_check");
  private volatile Task<RecaptchaTasksClient> recaptchaTasksClientTask;
  private final Executor liteExecutor;
  private final Executor blockingExecutor;
  private final RetryManager retryManager;
  private final NetworkClient networkClient;
  private String siteKey;
  private Application application;
  private static final String TAG = "rCEAppCheckProvider";

  @AssistedInject
  public RecaptchaEnterpriseAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @Assisted @NonNull String siteKey,
      @Lightweight Executor liteExecutor,
      @Blocking Executor blockingExecutor) {
    this.application = (Application) firebaseApp.getApplicationContext();
    this.siteKey = siteKey;
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = new RetryManager();
    this.networkClient = new NetworkClient(firebaseApp);
  }

  @VisibleForTesting
  RecaptchaEnterpriseAppCheckProvider(
      @Lightweight Executor liteExecutor,
      @Blocking Executor blockingExecutor,
      @NonNull RetryManager retryManager,
      @NonNull NetworkClient networkClient,
      @NonNull RecaptchaTasksClient recaptchaTasksClient) {
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = retryManager;
    this.networkClient = networkClient;
    this.recaptchaTasksClientTask = Tasks.forResult(recaptchaTasksClient);
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getToken() {
    return getRecaptchaEnterpriseAttestation()
        .onSuccessTask(
            liteExecutor,
            recaptchaEnterpriseToken -> {
              ExchangeRecaptchaEnterpriseTokenRequest request =
                  new ExchangeRecaptchaEnterpriseTokenRequest(recaptchaEnterpriseToken);
              return Tasks.call(
                  blockingExecutor,
                  () ->
                      networkClient.exchangeAttestationForAppCheckToken(
                          request.toJsonString().getBytes(StandardCharsets.UTF_8),
                          NetworkClient.RECAPTCHA_ENTERPRISE,
                          retryManager));
            })
        .onSuccessTask(
            liteExecutor,
            appCheckTokenResponse ->
                Tasks.forResult(
                    DefaultAppCheckToken.constructFromAppCheckTokenResponse(
                        appCheckTokenResponse)));
  }

  @NonNull
  private Task<String> getRecaptchaEnterpriseAttestation() {
    if (recaptchaTasksClientTask == null) {
      synchronized (this) {
        if (recaptchaTasksClientTask == null) {
          recaptchaTasksClientTask = Recaptcha.fetchTaskClient(application, siteKey);
        }
      }
    }
    return recaptchaTasksClientTask.continueWithTask(
        blockingExecutor,
        task -> {
          if (task.isSuccessful()) {
            RecaptchaTasksClient client = task.getResult();
            return client.executeTask(recaptchaAction);
          } else {
            Log.w(TAG, "Recaptcha task failed", task.getException());
            return Tasks.forException((Objects.requireNonNull(task.getException())));
          }
        });
  }
}
