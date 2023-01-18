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

package com.google.firebase.appcheck.safetynet.internal;

import static com.google.android.gms.common.internal.Preconditions.checkNotEmpty;
import static com.google.android.gms.common.internal.Preconditions.checkNotNull;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.safetynet.SafetyNet;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.safetynet.SafetyNetClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import java.util.concurrent.Executor;

public class SafetyNetAppCheckProvider implements AppCheckProvider {

  // The SafetyNet nonce is used to associate a SafetyNet attestation with a request. However, since
  // we do not have any fields in ExchangeSafetyNetTokenRequest that are not already covered in the
  // AttestationResponse, the nonce does not provide much additional benefit. Because of this, we
  // will leave the nonce empty in the v1 flow.
  private static final String NONCE = "";
  private static final String UTF_8 = "UTF-8";

  private final Task<SafetyNetClient> safetyNetClientTask;
  private final NetworkClient networkClient;
  private final Executor liteExecutor;
  private final Executor blockingExecutor;
  private final RetryManager retryManager;
  private final String apiKey;

  /** @param firebaseApp the FirebaseApp to which this Factory is tied. */
  public SafetyNetAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @Lightweight Executor liteExecutor,
      @Background Executor backgroundExecutor,
      @Blocking Executor blockingExecutor) {
    this(
        firebaseApp,
        new NetworkClient(firebaseApp),
        GoogleApiAvailability.getInstance(),
        liteExecutor,
        backgroundExecutor,
        blockingExecutor);
  }

  @VisibleForTesting
  SafetyNetAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @NonNull NetworkClient networkClient,
      @NonNull GoogleApiAvailability googleApiAvailability,
      @NonNull Executor liteExecutor,
      @NonNull Executor backgroundExecutor,
      @NonNull Executor blockingExecutor) {
    checkNotNull(firebaseApp);
    checkNotNull(networkClient);
    checkNotNull(googleApiAvailability);
    checkNotNull(backgroundExecutor);
    this.apiKey = firebaseApp.getOptions().getApiKey();
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.safetyNetClientTask =
        initSafetyNetClient(
            firebaseApp.getApplicationContext(), googleApiAvailability, backgroundExecutor);
    this.networkClient = networkClient;
    this.retryManager = new RetryManager();
  }

  @VisibleForTesting
  SafetyNetAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @NonNull SafetyNetClient safetyNetClient,
      @NonNull NetworkClient networkClient,
      @NonNull Executor liteExecutor,
      @NonNull Executor blockingExecutor,
      @NonNull RetryManager retryManager) {
    this.apiKey = firebaseApp.getOptions().getApiKey();
    this.safetyNetClientTask = Tasks.forResult(safetyNetClient);
    this.networkClient = networkClient;
    this.liteExecutor = liteExecutor;
    this.blockingExecutor = blockingExecutor;
    this.retryManager = retryManager;
  }

  private static Task<SafetyNetClient> initSafetyNetClient(
      Context context, GoogleApiAvailability googleApiAvailability, Executor executor) {
    TaskCompletionSource<SafetyNetClient> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          int connectionResult = googleApiAvailability.isGooglePlayServicesAvailable(context);
          if (connectionResult == ConnectionResult.SUCCESS) {
            taskCompletionSource.setResult(SafetyNet.getClient(context));
          } else {
            taskCompletionSource.setException(
                new IllegalStateException(
                    "SafetyNet unavailable; unable to connect to Google Play Services: "
                        + getGooglePlayServicesConnectionErrorString(connectionResult)));
          }
        });
    return taskCompletionSource.getTask();
  }

  private static String getGooglePlayServicesConnectionErrorString(int connectionResult) {
    switch (connectionResult) {
      case ConnectionResult.SERVICE_MISSING:
        return "Google Play services is missing on this device.";
      case ConnectionResult.SERVICE_UPDATING:
        return "Google Play services is currently being updated on this device.";
      case ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED:
        return "The installed version of Google Play services is out of date.";
      case ConnectionResult.SERVICE_DISABLED:
        return "The installed version of Google Play services has been disabled on this device.";
      case ConnectionResult.SERVICE_INVALID:
        return "The version of the Google Play services installed on this device is not authentic.";
      default:
        return "Unknown error.";
    }
  }

  @VisibleForTesting
  Task<SafetyNetClient> getSafetyNetClientTask() {
    return safetyNetClientTask;
  }

  @NonNull
  @Override
  public Task<AppCheckToken> getToken() {
    return safetyNetClientTask
        .onSuccessTask(
            liteExecutor, safetyNetClient -> safetyNetClient.attest(NONCE.getBytes(), apiKey))
        .onSuccessTask(liteExecutor, this::exchangeSafetyNetAttestationResponseForToken);
  }

  @NonNull
  Task<AppCheckToken> exchangeSafetyNetAttestationResponseForToken(
      @NonNull SafetyNetApi.AttestationResponse attestationResponse) {
    checkNotNull(attestationResponse);
    String safetyNetJwsResult = attestationResponse.getJwsResult();
    checkNotEmpty(safetyNetJwsResult);

    ExchangeSafetyNetTokenRequest request = new ExchangeSafetyNetTokenRequest(safetyNetJwsResult);

    return Tasks.call(
            blockingExecutor,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    request.toJsonString().getBytes(UTF_8), NetworkClient.SAFETY_NET, retryManager))
        .onSuccessTask(
            liteExecutor,
            response ->
                Tasks.forResult(DefaultAppCheckToken.constructFromAppCheckTokenResponse(response)));
  }
}
