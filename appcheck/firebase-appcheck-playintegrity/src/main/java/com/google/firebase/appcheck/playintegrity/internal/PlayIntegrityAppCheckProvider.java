// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.playintegrity.internal;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.AppCheckTokenResponse;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class PlayIntegrityAppCheckProvider implements AppCheckProvider {

  private static final String UTF_8 = "UTF-8";

  private final NetworkClient networkClient;
  private final ExecutorService backgroundExecutor;
  private final RetryManager retryManager;

  public PlayIntegrityAppCheckProvider(@NonNull FirebaseApp firebaseApp) {
    this(new NetworkClient(firebaseApp), Executors.newCachedThreadPool(), new RetryManager());
  }

  @VisibleForTesting
  PlayIntegrityAppCheckProvider(
      @NonNull NetworkClient networkClient,
      @NonNull ExecutorService backgroundExecutor,
      @NonNull RetryManager retryManager) {
    this.networkClient = networkClient;
    this.backgroundExecutor = backgroundExecutor;
    this.retryManager = retryManager;
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getToken() {
    // TODO(rosalyntan): Obtain the Play Integrity challenge nonce.
    ExchangePlayIntegrityTokenRequest request =
        new ExchangePlayIntegrityTokenRequest("placeholder");
    Task<AppCheckTokenResponse> networkTask =
        Tasks.call(
            backgroundExecutor,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    request.toJsonString().getBytes(UTF_8),
                    NetworkClient.PLAY_INTEGRITY,
                    retryManager));
    return networkTask.continueWithTask(
        new Continuation<AppCheckTokenResponse, Task<AppCheckToken>>() {
          @Override
          public Task<AppCheckToken> then(@NonNull Task<AppCheckTokenResponse> task) {
            if (task.isSuccessful()) {
              return Tasks.forResult(
                  DefaultAppCheckToken.constructFromAppCheckTokenResponse(task.getResult()));
            }
            // TODO: Surface more error details.
            return Tasks.forException(task.getException());
          }
        });
  }
}
