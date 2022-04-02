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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.AppCheckTokenResponse;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link PlayIntegrityAppCheckProvider}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class PlayIntegrityAppCheckProviderTest {

  private static final String ATTESTATION_TOKEN = "token";
  private static final String TIME_TO_LIVE = "3600s";

  private ExecutorService backgroundExecutor = MoreExecutors.newDirectExecutorService();
  @Mock private NetworkClient mockNetworkClient;
  @Mock private RetryManager mockRetryManager;
  @Mock private AppCheckTokenResponse mockAppCheckTokenResponse;

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testPublicConstructor_nullFirebaseApp_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new PlayIntegrityAppCheckProvider(null);
        });
  }

  @Test
  public void getToken_onSuccess_setsTaskResult() throws Exception {
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.PLAY_INTEGRITY), eq(mockRetryManager)))
        .thenReturn(mockAppCheckTokenResponse);
    when(mockAppCheckTokenResponse.getAttestationToken()).thenReturn(ATTESTATION_TOKEN);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(TIME_TO_LIVE);

    PlayIntegrityAppCheckProvider provider =
        new PlayIntegrityAppCheckProvider(mockNetworkClient, backgroundExecutor, mockRetryManager);
    Task<AppCheckToken> task = provider.getToken();

    verify(mockNetworkClient)
        .exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.PLAY_INTEGRITY), eq(mockRetryManager));

    AppCheckToken token = task.getResult();
    assertThat(token).isInstanceOf(DefaultAppCheckToken.class);
    assertThat(token.getToken()).isEqualTo(ATTESTATION_TOKEN);
  }

  @Test
  public void getToken_onFailure_setsTaskException() throws Exception {
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.PLAY_INTEGRITY), eq(mockRetryManager)))
        .thenThrow(new IOException());

    PlayIntegrityAppCheckProvider provider =
        new PlayIntegrityAppCheckProvider(mockNetworkClient, backgroundExecutor, mockRetryManager);
    Task<AppCheckToken> task = provider.getToken();

    verify(mockNetworkClient)
        .exchangeAttestationForAppCheckToken(
            any(), eq(NetworkClient.PLAY_INTEGRITY), eq(mockRetryManager));

    assertThat(task.isSuccessful()).isFalse();
    Exception exception = task.getException();
    assertThat(exception).isInstanceOf(IOException.class);
  }
}
