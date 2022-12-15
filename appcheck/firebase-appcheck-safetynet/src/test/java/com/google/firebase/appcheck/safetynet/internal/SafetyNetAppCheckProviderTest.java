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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.GoogleApiAvailability;
import com.google.android.gms.safetynet.SafetyNetApi;
import com.google.android.gms.safetynet.SafetyNetClient;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.AppCheckTokenResponse;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.io.IOException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Tests for {@link SafetyNetAppCheckProvider}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class SafetyNetAppCheckProviderTest {

  private static final String API_KEY = "apiKey";
  private static final String APP_ID = "appId";
  private static final String PROJECT_ID = "projectId";
  private static final String SAFETY_NET_TOKEN = "safetyNetToken";
  private static final String APP_CHECK_TOKEN = "appCheckToken";
  private static final String TIME_TO_LIVE = "3600s";

  private FirebaseApp firebaseApp;
  @Mock private GoogleApiAvailability mockGoogleApiAvailability;
  @Mock private SafetyNetClient mockSafetyNetClient;
  @Mock private NetworkClient mockNetworkClient;
  @Mock private RetryManager mockRetryManager;
  @Mock private SafetyNetApi.AttestationResponse mockSafetyNetAttestationResponse;
  @Mock private AppCheckTokenResponse mockAppCheckTokenResponse;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();
    firebaseApp =
        initializeFirebaseApp(
            ApplicationProvider.getApplicationContext(), FirebaseApp.DEFAULT_APP_NAME);
  }

  @Test
  public void testPublicConstructor_nullFirebaseApp_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new SafetyNetAppCheckProvider(
              null,
              TestOnlyExecutors.lite(),
              TestOnlyExecutors.background(),
              TestOnlyExecutors.blocking());
        });
  }

  @Test
  public void
      testConstructor_googlePlayServicesIsNotAvailable_expectSafetyNetClientTaskException() {
    when(mockGoogleApiAvailability.isGooglePlayServicesAvailable(any()))
        .thenReturn(ConnectionResult.SERVICE_MISSING);
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockNetworkClient,
            mockGoogleApiAvailability,
            TestOnlyExecutors.lite(),
            TestOnlyExecutors.background(),
            TestOnlyExecutors.blocking());
    assertThat(provider.getSafetyNetClientTask().isSuccessful()).isFalse();
  }

  @Test
  public void testGetToken_googlePlayServicesIsNotAvailable_expectGetTokenTaskException() {
    when(mockGoogleApiAvailability.isGooglePlayServicesAvailable(any()))
        .thenReturn(ConnectionResult.SERVICE_MISSING);
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockNetworkClient,
            mockGoogleApiAvailability,
            TestOnlyExecutors.lite(),
            TestOnlyExecutors.background(),
            TestOnlyExecutors.blocking());
    assertThat(provider.getSafetyNetClientTask().isSuccessful()).isFalse();

    Task<AppCheckToken> tokenTask = provider.getToken();
    assertThat(tokenTask).isNotNull();
    assertThat(tokenTask.isSuccessful()).isFalse();
  }

  @Test
  public void testGetToken_nonNullSafetyNetClient_expectCallsSafetyNetForAttestation() {
    // TODO(b/258273630): Use TestOnlyExecutors instead of MoreExecutors.directExecutor().
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockSafetyNetClient,
            mockNetworkClient,
            /* liteExecutor= */ MoreExecutors.directExecutor(),
            TestOnlyExecutors.blocking(),
            mockRetryManager);
    assertThat(provider.getSafetyNetClientTask().getResult()).isEqualTo(mockSafetyNetClient);

    Task<SafetyNetApi.AttestationResponse> safetyNetTask =
        new TaskCompletionSource<SafetyNetApi.AttestationResponse>().getTask();

    when(mockSafetyNetClient.attest(any(), any())).thenReturn(safetyNetTask);

    Task<AppCheckToken> tokenTask = provider.getToken();
    assertThat(tokenTask).isNotNull();

    verify(mockSafetyNetClient).attest(any(), any());
  }

  @Test
  public void testExchangeSafetyNetJwsForToken_nullAttestationResponse_expectThrows() {
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockSafetyNetClient,
            mockNetworkClient,
            TestOnlyExecutors.lite(),
            TestOnlyExecutors.blocking(),
            mockRetryManager);
    assertThrows(
        NullPointerException.class,
        () -> {
          provider.exchangeSafetyNetAttestationResponseForToken(
              /* attestationResponse= */ (SafetyNetApi.AttestationResponse) null);
        });
  }

  @Test
  public void testExchangeSafetyNetJwsForToken_emptySafetyNetJwsResult_expectThrows() {
    when(mockSafetyNetAttestationResponse.getJwsResult()).thenReturn("");
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockSafetyNetClient,
            mockNetworkClient,
            TestOnlyExecutors.lite(),
            TestOnlyExecutors.blocking(),
            mockRetryManager);
    assertThrows(
        IllegalArgumentException.class,
        () -> {
          provider.exchangeSafetyNetAttestationResponseForToken(mockSafetyNetAttestationResponse);
        });
  }

  @Test
  public void testExchangeSafetyNetJwsForToken_validFields_expectReturnsTask() {
    when(mockSafetyNetAttestationResponse.getJwsResult()).thenReturn(SAFETY_NET_TOKEN);
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockSafetyNetClient,
            mockNetworkClient,
            TestOnlyExecutors.lite(),
            TestOnlyExecutors.blocking(),
            mockRetryManager);
    Task<AppCheckToken> task =
        provider.exchangeSafetyNetAttestationResponseForToken(mockSafetyNetAttestationResponse);
    assertThat(task).isNotNull();
  }

  @Test
  public void exchangeSafetyNetJwsForToken_onSuccess_setsTaskResult() throws Exception {
    when(mockSafetyNetAttestationResponse.getJwsResult()).thenReturn(SAFETY_NET_TOKEN);
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.SAFETY_NET), eq(mockRetryManager)))
        .thenReturn(mockAppCheckTokenResponse);
    when(mockAppCheckTokenResponse.getToken()).thenReturn(APP_CHECK_TOKEN);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(TIME_TO_LIVE);

    // TODO(b/258273630): Use TestOnlyExecutors instead of MoreExecutors.directExecutor().
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockSafetyNetClient,
            mockNetworkClient,
            /* liteExecutor= */ MoreExecutors.directExecutor(),
            /* blockingExecutor= */ MoreExecutors.directExecutor(),
            mockRetryManager);
    Task<AppCheckToken> task =
        provider.exchangeSafetyNetAttestationResponseForToken(mockSafetyNetAttestationResponse);

    verify(mockNetworkClient)
        .exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.SAFETY_NET), eq(mockRetryManager));

    AppCheckToken token = task.getResult();
    assertThat(token).isInstanceOf(DefaultAppCheckToken.class);
    assertThat(token.getToken()).isEqualTo(APP_CHECK_TOKEN);
  }

  @Test
  public void exchangeSafetyNetJwsForToken_onFailure_setsTaskException() throws Exception {
    when(mockSafetyNetAttestationResponse.getJwsResult()).thenReturn(SAFETY_NET_TOKEN);
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.SAFETY_NET), eq(mockRetryManager)))
        .thenThrow(new IOException());

    // TODO(b/258273630): Use TestOnlyExecutors instead of MoreExecutors.directExecutor().
    SafetyNetAppCheckProvider provider =
        new SafetyNetAppCheckProvider(
            firebaseApp,
            mockSafetyNetClient,
            mockNetworkClient,
            /* liteExecutor= */ MoreExecutors.directExecutor(),
            /* blockingExecutor= */ MoreExecutors.directExecutor(),
            mockRetryManager);
    Task<AppCheckToken> task =
        provider.exchangeSafetyNetAttestationResponseForToken(mockSafetyNetAttestationResponse);

    verify(mockNetworkClient)
        .exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.SAFETY_NET), eq(mockRetryManager));

    assertThat(task.isSuccessful()).isFalse();
    Exception exception = task.getException();
    assertThat(exception).isInstanceOf(IOException.class);
  }

  private static FirebaseApp initializeFirebaseApp(Context context, String name) {
    return FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .build(),
        name);
  }
}
