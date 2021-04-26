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

package com.google.firebase.appcheck.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.FirebaseApp;
import com.google.firebase.appcheck.AppCheckProvider;
import com.google.firebase.appcheck.AppCheckProviderFactory;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.interop.AppCheckTokenListener;
import com.google.firebase.heartbeatinfo.HeartBeatInfo;
import com.google.firebase.platforminfo.UserAgentPublisher;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link DefaultFirebaseAppCheck}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class DefaultFirebaseAppCheckTest {

  private static final String EXCEPTION_TEXT = "exceptionText";
  private static final String PERSISTENCE_KEY = "persistenceKey";
  private static final String TOKEN_PAYLOAD = "tokenPayload";
  private static final long EXPIRES_IN_ONE_HOUR = 60L * 60L * 1000L; // 1 hour in millis
  private static final long EXPIRES_NOW = 0L;

  @Mock private FirebaseApp mockFirebaseApp;
  @Mock private AppCheckProviderFactory mockAppCheckProviderFactory;
  @Mock private AppCheckProvider mockAppCheckProvider;
  @Mock private AppCheckTokenListener mockAppCheckTokenListener;
  @Mock private UserAgentPublisher mockUserAgentPublisher;
  @Mock private HeartBeatInfo mockHeartBeatInfo;

  private DefaultAppCheckToken validDefaultAppCheckToken;
  private DefaultFirebaseAppCheck defaultFirebaseAppCheck;

  @Before
  public void setup() {
    validDefaultAppCheckToken = new DefaultAppCheckToken(TOKEN_PAYLOAD, EXPIRES_IN_ONE_HOUR);

    MockitoAnnotations.initMocks(this);
    when(mockFirebaseApp.getApplicationContext())
        .thenReturn(ApplicationProvider.getApplicationContext());
    when(mockFirebaseApp.getPersistenceKey()).thenReturn(PERSISTENCE_KEY);
    when(mockAppCheckProviderFactory.create(any())).thenReturn(mockAppCheckProvider);
    when(mockAppCheckProvider.getToken()).thenReturn(Tasks.forResult(validDefaultAppCheckToken));

    defaultFirebaseAppCheck =
        new DefaultFirebaseAppCheck(
            mockFirebaseApp, () -> mockUserAgentPublisher, () -> mockHeartBeatInfo);
  }

  @Test
  public void testConstructor_nullFirebaseApp_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new DefaultFirebaseAppCheck(null, () -> mockUserAgentPublisher, () -> mockHeartBeatInfo);
        });
  }

  @Test
  public void testConstructor_nullUserAgentPublisherProvider_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new DefaultFirebaseAppCheck(mockFirebaseApp, null, () -> mockHeartBeatInfo);
        });
  }

  @Test
  public void testConstructor_nullHeartBeatInfoProvider_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          new DefaultFirebaseAppCheck(mockFirebaseApp, () -> mockUserAgentPublisher, null);
        });
  }

  @Test
  public void testInstallAppCheckFactory_nullFactory_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          defaultFirebaseAppCheck.installAppCheckProviderFactory(null);
        });
  }

  @Test
  public void testAddAppCheckTokenListener_nullListener_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          defaultFirebaseAppCheck.addAppCheckTokenListener(null);
        });
  }

  @Test
  public void testRemoveAppCheckTokenListener_nullListener_expectThrows() {
    assertThrows(
        NullPointerException.class,
        () -> {
          defaultFirebaseAppCheck.removeAppCheckTokenListener(null);
        });
  }

  @Test
  public void testGetToken_noFactoryInstalled_returnResultWithError() throws Exception {
    Task<AppCheckTokenResult> tokenTask =
        defaultFirebaseAppCheck.getToken(/* forceRefresh= */ false);
    assertThat(tokenTask.isComplete()).isTrue();
    assertThat(tokenTask.isSuccessful()).isTrue();
    assertThat(tokenTask.getResult().getToken()).isNotNull();
    assertThat(tokenTask.getResult().getError()).isNotNull();
  }

  @Test
  public void testGetToken_factoryInstalled_proxiesToAppCheckFactory() {
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);

    defaultFirebaseAppCheck.getToken(/* forceRefresh= */ false);

    verify(mockAppCheckProvider).getToken();
  }

  @Test
  public void testGetInstalledAppCheckProviderFactory_noFactoryInstalled_returnsNull() {
    assertThat(defaultFirebaseAppCheck.getInstalledAppCheckProviderFactory()).isNull();
  }

  @Test
  public void testGetInstalledAppCheckProviderFactory_factoryInstalled_returnsFactory() {
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);

    assertThat(defaultFirebaseAppCheck.getInstalledAppCheckProviderFactory())
        .isEqualTo(mockAppCheckProviderFactory);
  }

  @Test
  public void testGetToken_factoryInstalledAndListenerRegistered_triggersListenerOnSuccess() {
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);
    defaultFirebaseAppCheck.addAppCheckTokenListener(mockAppCheckTokenListener);

    defaultFirebaseAppCheck.getToken(/* forceRefresh= */ false);

    verify(mockAppCheckProvider).getToken();
    ArgumentCaptor<DefaultAppCheckTokenResult> tokenResultCaptor =
        ArgumentCaptor.forClass(DefaultAppCheckTokenResult.class);
    verify(mockAppCheckTokenListener).onAppCheckTokenChanged(tokenResultCaptor.capture());
    assertThat(tokenResultCaptor.getValue().getToken()).isEqualTo(TOKEN_PAYLOAD);
    assertThat(tokenResultCaptor.getValue().getError()).isNull();
  }

  @Test
  public void testGetToken_factoryInstalledAndListenerRegistered_doesNotTriggerListenerOnFailure() {
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);
    defaultFirebaseAppCheck.addAppCheckTokenListener(mockAppCheckTokenListener);

    when(mockAppCheckProvider.getToken())
        .thenReturn(Tasks.forException(new Exception(EXCEPTION_TEXT)));

    defaultFirebaseAppCheck.getToken(/* forceRefresh= */ false);

    verify(mockAppCheckProvider).getToken();
    verify(mockAppCheckTokenListener, never()).onAppCheckTokenChanged(any());
  }

  @Test
  public void testGetToken_existingValidToken_triggersListenerUponAdding() {
    defaultFirebaseAppCheck.setCachedToken(validDefaultAppCheckToken);

    defaultFirebaseAppCheck.addAppCheckTokenListener(mockAppCheckTokenListener);

    ArgumentCaptor<DefaultAppCheckTokenResult> tokenResultCaptor =
        ArgumentCaptor.forClass(DefaultAppCheckTokenResult.class);
    verify(mockAppCheckTokenListener).onAppCheckTokenChanged(tokenResultCaptor.capture());
    assertThat(tokenResultCaptor.getValue().getToken()).isEqualTo(TOKEN_PAYLOAD);
    assertThat(tokenResultCaptor.getValue().getError()).isNull();
  }

  @Test
  public void testGetToken_existingInvalidToken_doesNotTriggerListenerUponAdding() {
    DefaultAppCheckToken invalidDefaultAppCheckToken =
        new DefaultAppCheckToken(TOKEN_PAYLOAD, EXPIRES_NOW);
    defaultFirebaseAppCheck.setCachedToken(invalidDefaultAppCheckToken);

    defaultFirebaseAppCheck.addAppCheckTokenListener(mockAppCheckTokenListener);

    verify(mockAppCheckTokenListener, never()).onAppCheckTokenChanged(any());
  }

  @Test
  public void testGetToken_existingValidToken_doesNotRequestNewToken() {
    defaultFirebaseAppCheck.setCachedToken(validDefaultAppCheckToken);
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);

    defaultFirebaseAppCheck.getToken(/* forceRefresh= */ false);

    verify(mockAppCheckProvider, never()).getToken();
  }

  @Test
  public void testGetToken_existingValidToken_forceRefresh_requestsNewToken() {
    defaultFirebaseAppCheck.setCachedToken(validDefaultAppCheckToken);
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);

    defaultFirebaseAppCheck.getToken(/* forceRefresh= */ true);

    verify(mockAppCheckProvider).getToken();
  }

  @Test
  public void testGetToken_existingInvalidToken_requestsNewToken() {
    DefaultAppCheckToken invalidDefaultAppCheckToken =
        new DefaultAppCheckToken(TOKEN_PAYLOAD, EXPIRES_NOW);
    defaultFirebaseAppCheck.setCachedToken(invalidDefaultAppCheckToken);
    defaultFirebaseAppCheck.installAppCheckProviderFactory(mockAppCheckProviderFactory);

    defaultFirebaseAppCheck.getToken(/* forceRefresh= */ false);

    verify(mockAppCheckProvider).getToken();
  }
}
