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

import android.annotation.SuppressLint;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.play.core.integrity.IntegrityManager;
import com.google.android.play.core.integrity.IntegrityManagerFactory;
import com.google.android.play.core.integrity.IntegrityTokenRequest;
import com.google.android.play.core.integrity.IntegrityTokenResponse;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import java.util.concurrent.Executor;

public class PlayIntegrityAppCheckProvider implements AppCheckProvider {

  private static final String UTF_8 = "UTF-8";

  private final String projectNumber;
  private final IntegrityManager integrityManager;
  private final NetworkClient networkClient;
  private final Executor blockingExecutor;
  private final RetryManager retryManager;

  public PlayIntegrityAppCheckProvider(
      @NonNull FirebaseApp firebaseApp, @Blocking Executor blockingExecutor) {
    this(
        firebaseApp.getOptions().getGcmSenderId(),
        IntegrityManagerFactory.create(firebaseApp.getApplicationContext()),
        new NetworkClient(firebaseApp),
        blockingExecutor,
        new RetryManager());
  }

  @VisibleForTesting
  PlayIntegrityAppCheckProvider(
      @NonNull String projectNumber,
      @NonNull IntegrityManager integrityManager,
      @NonNull NetworkClient networkClient,
      @NonNull Executor blockingExecutor,
      @NonNull RetryManager retryManager) {
    this.projectNumber = projectNumber;
    this.integrityManager = integrityManager;
    this.networkClient = networkClient;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = retryManager;
  }

  // TODO(b/261013814): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @NonNull
  @Override
  public Task<AppCheckToken> getToken() {
    return getPlayIntegrityAttestation()
        .onSuccessTask(
            integrityTokenResponse -> {
              ExchangePlayIntegrityTokenRequest request =
                  new ExchangePlayIntegrityTokenRequest(integrityTokenResponse.token());
              return Tasks.call(
                  blockingExecutor,
                  () ->
                      networkClient.exchangeAttestationForAppCheckToken(
                          request.toJsonString().getBytes(UTF_8),
                          NetworkClient.PLAY_INTEGRITY,
                          retryManager));
            })
        .onSuccessTask(
            appCheckTokenResponse -> {
              return Tasks.forResult(
                  DefaultAppCheckToken.constructFromAppCheckTokenResponse(appCheckTokenResponse));
            });
  }

  // TODO(b/261013814): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  @NonNull
  private Task<IntegrityTokenResponse> getPlayIntegrityAttestation() {
    GeneratePlayIntegrityChallengeRequest generateChallengeRequest =
        new GeneratePlayIntegrityChallengeRequest();
    Task<GeneratePlayIntegrityChallengeResponse> generateChallengeTask =
        Tasks.call(
            blockingExecutor,
            () ->
                GeneratePlayIntegrityChallengeResponse.fromJsonString(
                    networkClient.generatePlayIntegrityChallenge(
                        generateChallengeRequest.toJsonString().getBytes(UTF_8), retryManager)));
    return generateChallengeTask.onSuccessTask(
        generatePlayIntegrityChallengeResponse -> {
          return integrityManager.requestIntegrityToken(
              IntegrityTokenRequest.builder()
                  .setCloudProjectNumber(Long.parseLong(projectNumber))
                  .setNonce(generatePlayIntegrityChallengeResponse.getChallenge())
                  .build());
        });
  }
}
