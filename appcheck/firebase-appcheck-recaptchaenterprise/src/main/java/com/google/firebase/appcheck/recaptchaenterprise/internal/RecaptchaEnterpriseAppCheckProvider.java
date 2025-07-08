package com.google.firebase.appcheck.recaptchaenterprise.internal;

import android.app.Application;

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

public class RecaptchaEnterpriseAppCheckProvider implements AppCheckProvider {

  private final RecaptchaAction recaptchaAction = RecaptchaAction.custom("fire_app_check");
  private final Task<RecaptchaTasksClient> recaptchaTasksClientTask;
  private final Executor liteExecutor;
  private final Executor blockingExecutor;
  private final RetryManager retryManager;
  private final NetworkClient networkClient;

  public RecaptchaEnterpriseAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @NonNull Application application,
      @NonNull SiteKey siteKey,
      @Lightweight Executor liteExecutor,
      @Blocking Executor blockingExecutor) {
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = new RetryManager();
    this.networkClient = new NetworkClient(firebaseApp);
    recaptchaTasksClientTask = Recaptcha.fetchTaskClient(application, siteKey.value());
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
    return recaptchaTasksClientTask.continueWithTask(
        blockingExecutor,
        task -> {
          if (task.isSuccessful()) {
            RecaptchaTasksClient client = task.getResult();
            return client.executeTask(recaptchaAction);
          } else {
            throw Objects.requireNonNull(task.getException());
          }
        });
  }
}
