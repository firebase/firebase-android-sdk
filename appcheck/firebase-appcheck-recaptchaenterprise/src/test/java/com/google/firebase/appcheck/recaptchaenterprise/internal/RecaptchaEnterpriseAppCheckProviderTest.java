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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Application;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.android.recaptcha.RecaptchaAction;
import com.google.android.recaptcha.RecaptchaTasksClient;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseException;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.AppCheckTokenResponse;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import com.google.firebase.appcheck.internal.RetryManager;
import com.google.firebase.concurrent.TestOnlyExecutors;
import java.io.IOException;
import java.util.concurrent.Executor;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;

/** Tests for {@link RecaptchaEnterpriseAppCheckProvider}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
@LooperMode(LooperMode.Mode.LEGACY)
public class RecaptchaEnterpriseAppCheckProviderTest {
  private static final String APP_CHECK_TOKEN = "appCheckToken";
  private static final String RECAPTCHA_ENTERPRISE_TOKEN = "recaptchaEnterpriseToken";
  private final Executor liteExecutor = MoreExecutors.directExecutor();
  private final Executor blockingExecutor = MoreExecutors.directExecutor();
  private final String siteKey = "siteKey";

  @Mock private NetworkClient mockNetworkClient;
  @Mock private Application mockApplication;
  @Mock FirebaseApp mockFirebaseApp;
  @Mock RecaptchaTasksClient mockRecaptchaTasksClient;
  @Mock RetryManager mockRetryManager;

  @Captor private ArgumentCaptor<RecaptchaAction> recaptchaActionCaptor;
  @Captor private ArgumentCaptor<byte[]> requestCaptor;

  @Before
  public void setup() {
    MockitoAnnotations.openMocks(this);
  }

  @Test
  public void testPublicConstructor_nullFirebaseApp_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RecaptchaEnterpriseAppCheckProvider(
                null,
                mockApplication,
                siteKey,
                TestOnlyExecutors.lite(),
                TestOnlyExecutors.blocking()));
  }

  @Test
  public void testPublicConstructor_nullSiteKey_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () ->
            new RecaptchaEnterpriseAppCheckProvider(
                mockFirebaseApp,
                mockApplication,
                null,
                TestOnlyExecutors.lite(),
                TestOnlyExecutors.blocking()));
  }

  @Test
  public void getToken_onSuccess_setsTaskResult() throws Exception {
    when(mockRecaptchaTasksClient.executeTask(any(RecaptchaAction.class)))
        .thenReturn(Tasks.forResult(RECAPTCHA_ENTERPRISE_TOKEN));
    String jsonResponse =
        new JSONObject().put("token", APP_CHECK_TOKEN).put("ttl", 3600).toString();
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(
            any(byte[].class), eq(NetworkClient.RECAPTCHA_ENTERPRISE), eq(mockRetryManager)))
        .thenReturn(AppCheckTokenResponse.fromJsonString(jsonResponse));

    RecaptchaEnterpriseAppCheckProvider provider =
        new RecaptchaEnterpriseAppCheckProvider(
            liteExecutor,
            blockingExecutor,
            mockRetryManager,
            mockNetworkClient,
            mockRecaptchaTasksClient);
    Task<AppCheckToken> task = provider.getToken();

    assertThat(task.isSuccessful()).isTrue();
    AppCheckToken token = task.getResult();
    assertThat(token).isInstanceOf(DefaultAppCheckToken.class);
    assertThat(token.getToken()).isEqualTo(APP_CHECK_TOKEN);

    verify(mockRecaptchaTasksClient).executeTask(recaptchaActionCaptor.capture());
    assertThat(recaptchaActionCaptor.getValue().getAction()).isEqualTo("fire_app_check");
    verify(mockNetworkClient)
        .exchangeAttestationForAppCheckToken(
            requestCaptor.capture(), eq(NetworkClient.RECAPTCHA_ENTERPRISE), eq(mockRetryManager));
  }

  @Test
  public void getToken_recaptchaFails_returnException() {
    Exception exception = new Exception("Recaptcha error");
    when(mockRecaptchaTasksClient.executeTask(any(RecaptchaAction.class)))
        .thenReturn(Tasks.forException(exception));

    RecaptchaEnterpriseAppCheckProvider provider =
        new RecaptchaEnterpriseAppCheckProvider(
            liteExecutor,
            blockingExecutor,
            mockRetryManager,
            mockNetworkClient,
            mockRecaptchaTasksClient);
    Task<AppCheckToken> task = provider.getToken();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isEqualTo(exception);
  }

  @Test
  public void getToken_networkFails_returnException()
      throws FirebaseException, JSONException, IOException {
    when(mockRecaptchaTasksClient.executeTask(any(RecaptchaAction.class)))
        .thenReturn(Tasks.forResult(RECAPTCHA_ENTERPRISE_TOKEN));
    Exception exception = new IOException("Network error");
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(
            any(byte[].class), eq(NetworkClient.RECAPTCHA_ENTERPRISE), eq(mockRetryManager)))
        .thenThrow(exception);

    RecaptchaEnterpriseAppCheckProvider provider =
        new RecaptchaEnterpriseAppCheckProvider(
            liteExecutor,
            blockingExecutor,
            mockRetryManager,
            mockNetworkClient,
            mockRecaptchaTasksClient);
    Task<AppCheckToken> task = provider.getToken();
    assertThat(task.isSuccessful()).isFalse();
    assertThat(task.getException()).isEqualTo(exception);
  }
}
