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

import android.util.Log;
import androidx.annotation.NonNull;
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
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import com.google.firebase.inject.Provider;
import java.util.UUID;
import java.util.concurrent.Executor;

public class DebugAppCheckProvider implements AppCheckProvider {

  private static final String TAG = DebugAppCheckProvider.class.getName();
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

    String debugSecret = null;
    if (debugSecretProvider.get() != null) {
      debugSecret = debugSecretProvider.get().getDebugSecret();
    }
    this.debugSecretTask =
        debugSecret == null
            ? determineDebugSecret(firebaseApp, backgroundExecutor)
            : Tasks.forResult(debugSecret);
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
    TaskCompletionSource<String> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          StorageHelper storageHelper =
              new StorageHelper(
                  firebaseApp.getApplicationContext(), firebaseApp.getPersistenceKey());
          String debugSecret = storageHelper.retrieveDebugSecret();
          if (debugSecret == null) {
            debugSecret = UUID.randomUUID().toString();
            storageHelper.saveDebugSecret(debugSecret);
          }
          Log.d(
              TAG,
              "Enter this debug secret into the allow list in the Firebase Console for your project: "
                  + debugSecret);
          taskCompletionSource.setResult(debugSecret);
        });
    return taskCompletionSource.getTask();
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getToken() {
    return debugSecretTask
        .onSuccessTask(
            liteExecutor,
            debugSecret -> {
              ExchangeDebugTokenRequest request = new ExchangeDebugTokenRequest(debugSecret);
              return Tasks.call(
                  blockingExecutor,
                  () ->
                      networkClient.exchangeAttestationForAppCheckToken(
                          request.toJsonString().getBytes(UTF_8),
                          NetworkClient.DEBUG,
                          retryManager));
            })
        .onSuccessTask(
            liteExecutor,
            response ->
                Tasks.forResult(DefaultAppCheckToken.constructFromAppCheckTokenResponse(response)));
  }
}
