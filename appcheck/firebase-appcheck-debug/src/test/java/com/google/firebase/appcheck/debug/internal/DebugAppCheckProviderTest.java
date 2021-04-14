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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.SharedPreferences;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.AppCheckTokenResponse;
import com.google.firebase.appcheck.internal.DefaultAppCheckToken;
import com.google.firebase.appcheck.internal.NetworkClient;
import java.io.IOException;
import java.util.concurrent.ExecutorService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link DebugAppCheckProvider}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DebugAppCheckProviderTest {

  private static final String DEBUG_SECRET = "debugSecret";
  private static final String ATTESTATION_TOKEN = "token";
  private static final String TIME_TO_LIVE = "3600s";
  private static final String API_KEY = "apiKey";
  private static final String APP_ID = "appId";
  private static final String PROJECT_ID = "projectId";
  private static final String PERSISTENCE_KEY = "persistenceKey";
  private static final FirebaseOptions FIREBASE_OPTIONS =
      new FirebaseOptions.Builder()
          .setApiKey(API_KEY)
          .setApplicationId(APP_ID)
          .setProjectId(PROJECT_ID)
          .build();

  @Mock FirebaseApp mockFirebaseApp;
  @Mock NetworkClient mockNetworkClient;
  @Mock AppCheckTokenResponse mockAppCheckTokenResponse;

  private StorageHelper storageHelper;
  private SharedPreferences sharedPreferences;
  private ExecutorService backgroundExecutor = MoreExecutors.newDirectExecutorService();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);

    when(mockFirebaseApp.getApplicationContext())
        .thenReturn(ApplicationProvider.getApplicationContext());
    when(mockFirebaseApp.getPersistenceKey()).thenReturn(PERSISTENCE_KEY);
    when(mockFirebaseApp.getOptions()).thenReturn(FIREBASE_OPTIONS);

    storageHelper = new StorageHelper(ApplicationProvider.getApplicationContext(), PERSISTENCE_KEY);
  }

  @After
  public void tearDown() {
    sharedPreferences =
        ApplicationProvider.getApplicationContext()
            .getSharedPreferences(
                String.format(StorageHelper.PREFS_TEMPLATE, PERSISTENCE_KEY), Context.MODE_PRIVATE);
    sharedPreferences.edit().clear().commit();
  }

  @Test
  public void testPublicConstructor_nullFirebaseApp_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new DebugAppCheckProvider(null, null);
        });
  }

  @Test
  public void testPublicConstructor_nullProjectId_expectThrows() {
    FirebaseOptions firebaseOptionsNoProjectId =
        new FirebaseOptions.Builder().setApiKey(API_KEY).setApplicationId(APP_ID).build();
    when(mockFirebaseApp.getOptions()).thenReturn(firebaseOptionsNoProjectId);

    assertThrows(
        IllegalArgumentException.class,
        () -> {
          new DebugAppCheckProvider(mockFirebaseApp, null);
        });
  }

  @Test
  public void testDetermineDebugSecret_noStoredSecret_createsNewSecret() {
    // Sanity check
    assertThat(storageHelper.retrieveDebugSecret()).isNull();

    Task<String> debugSecretTask =
        DebugAppCheckProvider.determineDebugSecret(mockFirebaseApp, backgroundExecutor);
    assertThat(storageHelper.retrieveDebugSecret()).isNotNull();
    assertThat(storageHelper.retrieveDebugSecret()).isEqualTo(debugSecretTask.getResult());
  }

  @Test
  public void testDetermineDebugSecret_storedSecret_usesExistingSecret() {
    storageHelper.saveDebugSecret(DEBUG_SECRET);

    Task<String> debugSecretTask =
        DebugAppCheckProvider.determineDebugSecret(mockFirebaseApp, backgroundExecutor);
    assertThat(debugSecretTask.getResult()).isEqualTo(DEBUG_SECRET);
    assertThat(storageHelper.retrieveDebugSecret()).isEqualTo(DEBUG_SECRET);
  }

  @Test
  public void exchangeDebugToken_onSuccess_setsTaskResult() throws Exception {
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(any(), eq(NetworkClient.DEBUG)))
        .thenReturn(mockAppCheckTokenResponse);
    when(mockAppCheckTokenResponse.getAttestationToken()).thenReturn(ATTESTATION_TOKEN);
    when(mockAppCheckTokenResponse.getTimeToLive()).thenReturn(TIME_TO_LIVE);

    DebugAppCheckProvider provider =
        new DebugAppCheckProvider(DEBUG_SECRET, mockNetworkClient, backgroundExecutor);
    Task<AppCheckToken> task = provider.getToken();

    verify(mockNetworkClient).exchangeAttestationForAppCheckToken(any(), eq(NetworkClient.DEBUG));

    AppCheckToken token = task.getResult();
    assertThat(token).isInstanceOf(DefaultAppCheckToken.class);
    assertThat(token.getToken()).isEqualTo(ATTESTATION_TOKEN);
  }

  @Test
  public void exchangeDebugToken_onFailure_setsTaskException() throws Exception {
    when(mockNetworkClient.exchangeAttestationForAppCheckToken(any(), eq(NetworkClient.DEBUG)))
        .thenThrow(new IOException());

    DebugAppCheckProvider provider =
        new DebugAppCheckProvider(DEBUG_SECRET, mockNetworkClient, backgroundExecutor);
    Task<AppCheckToken> task = provider.getToken();

    verify(mockNetworkClient).exchangeAttestationForAppCheckToken(any(), eq(NetworkClient.DEBUG));

    assertThat(task.isSuccessful()).isFalse();
    Exception exception = task.getException();
    assertThat(exception).isInstanceOf(IOException.class);
  }
}
