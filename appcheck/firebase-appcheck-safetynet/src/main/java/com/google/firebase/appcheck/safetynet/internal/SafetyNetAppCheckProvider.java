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
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.AppCheckTokenResponse;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SafetyNetAppCheckProvider implements AppCheckProvider {

  // The SafetyNet nonce is used to associate a SafetyNet attestation with a request. However, since
  // we do not have any fields in ExchangeSafetyNetTokenRequest that are not already covered in the
  // AttestationResponse, the nonce does not provide much additional benefit. Because of this, we
  // will leave the nonce empty in the v1 flow.
  private static final String NONCE = "";
  private static final String UTF_8 = "UTF-8";

  private final Context context;
  private final Task<SafetyNetClient> safetyNetClientTask;
  private final NetworkClient networkClient;
  private final ExecutorService backgroundExecutor;
  private final String apiKey;

  /** @param firebaseApp the FirebaseApp to which this Factory is tied. */
  public SafetyNetAppCheckProvider(@NonNull FirebaseApp firebaseApp) {
    this(firebaseApp, GoogleApiAvailability.getInstance(), Executors.newCachedThreadPool());
  }

  @VisibleForTesting
  SafetyNetAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @NonNull GoogleApiAvailability googleApiAvailability,
      @NonNull ExecutorService backgroundExecutor) {
    checkNotNull(firebaseApp);
    checkNotNull(googleApiAvailability);
    this.context = firebaseApp.getApplicationContext();
    this.apiKey = firebaseApp.getOptions().getApiKey();
    this.backgroundExecutor = backgroundExecutor;
    this.safetyNetClientTask = initSafetyNetClient(googleApiAvailability, this.backgroundExecutor);
    this.networkClient = new NetworkClient(firebaseApp);
  }

  @VisibleForTesting
  SafetyNetAppCheckProvider(
      @NonNull FirebaseApp firebaseApp,
      @NonNull SafetyNetClient safetyNetClient,
      @NonNull NetworkClient networkClient,
      @NonNull ExecutorService backgroundExecutor) {
    this.context = firebaseApp.getApplicationContext();
    this.apiKey = firebaseApp.getOptions().getApiKey();
    this.safetyNetClientTask = Tasks.forResult(safetyNetClient);
    this.networkClient = networkClient;
    this.backgroundExecutor = backgroundExecutor;
  }

  private Task<SafetyNetClient> initSafetyNetClient(
      GoogleApiAvailability googleApiAvailability, ExecutorService executor) {
    TaskCompletionSource<SafetyNetClient> taskCompletionSource = new TaskCompletionSource<>();
    executor.submit(
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

  private String getGooglePlayServicesConnectionErrorString(int connectionResult) {
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
        .continueWithTask(
            new Continuation<SafetyNetClient, Task<SafetyNetApi.AttestationResponse>>() {
              @Override
              public Task<SafetyNetApi.AttestationResponse> then(
                  @NonNull Task<SafetyNetClient> task) {
                if (task.isSuccessful()) {
                  return task.getResult().attest(NONCE.getBytes(), apiKey);
                }
                return Tasks.forException(task.getException());
              }
            })
        .continueWithTask(
            new Continuation<SafetyNetApi.AttestationResponse, Task<AppCheckToken>>() {
              @Override
              public Task<AppCheckToken> then(
                  @NonNull Task<SafetyNetApi.AttestationResponse> task) {
                if (!task.isSuccessful()) {
                  // Proxies errors to the client directly; need to wrap to get the
                  // types right.
                  // TODO: more specific error mapping to help clients debug more
                  //       easily.
                  return Tasks.forException(task.getException());
                } else {
                  return exchangeSafetyNetAttestationResponseForToken(task.getResult());
                }
              }
            });
  }

  @NonNull
  public Task<AppCheckToken> exchangeSafetyNetAttestationResponseForToken(
      @NonNull SafetyNetApi.AttestationResponse attestationResponse) {
    checkNotNull(attestationResponse);
    return exchangeSafetyNetJwsResultForToken(attestationResponse.getJwsResult());
  }

  @NonNull
  public Task<AppCheckToken> exchangeSafetyNetJwsResultForToken(
      @NonNull String safetyNetJwsResult) {
    checkNotEmpty(safetyNetJwsResult);
    ExchangeSafetyNetTokenRequest request = new ExchangeSafetyNetTokenRequest(safetyNetJwsResult);

    Task<AppCheckTokenResponse> networkTask =
        Tasks.call(
            backgroundExecutor,
            () ->
                networkClient.exchangeAttestationForAppCheckToken(
                    request.toJsonString().getBytes(UTF_8), NetworkClient.SAFETY_NET));
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
