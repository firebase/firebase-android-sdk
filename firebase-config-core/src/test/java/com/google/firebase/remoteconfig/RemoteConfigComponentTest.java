// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.remoteconfig.AbtExperimentHelper.createAbtExperiment;
import static com.google.firebase.remoteconfig.AbtExperimentHelper.createAbtExperiments;
import static com.google.firebase.remoteconfig.RemoteConfigComponent.CONNECTION_TIMEOUT_IN_SECONDS;
import static com.google.firebase.remoteconfig.RemoteConfigComponent.DEFAULT_NAMESPACE;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler;
import com.google.firebase.remoteconfig.internal.ConfigFetchHttpClient;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient;
import java.util.concurrent.ExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/**
 * Unit tests for the Firebase Remote Config Component.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class RemoteConfigComponentTest {
  private static final String API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String DUMMY_API_KEY = "api_key";
  private static final String PROJECT_ID = "fake-frc-test-id";

  @Mock private FirebaseApp mockFirebaseApp;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private FirebaseABTesting mockFirebaseAbt;
  @Mock private AnalyticsConnector mockAnalyticsConnector;
  @Mock private ConfigCacheClient mockFetchedCache;
  @Mock private ConfigCacheClient mockActivatedCache;
  @Mock private ConfigCacheClient mockDefaultsCache;
  @Mock private ConfigFetchHandler mockFetchHandler;
  @Mock private ConfigGetParameterHandler mockGetParameterHandler;
  @Mock private ConfigMetadataClient mockMetadataClient;

  private Context context;
  private ExecutorService directExecutor;
  private FirebaseApp defaultApp;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    context = RuntimeEnvironment.application;
    directExecutor = MoreExecutors.newDirectExecutorService();

    defaultApp = initializeFirebaseApp(context);

    when(mockFirebaseApp.getOptions())
        .thenReturn(new FirebaseOptions.Builder().setApplicationId(APP_ID).build());
    when(mockFirebaseApp.getName()).thenReturn(FirebaseApp.DEFAULT_APP_NAME);
  }

  @Test
  public void frc2p_doesNotCallAbt() throws Exception {

    FirebaseRemoteConfig fireperfFrc =
        getFrcInstanceFromComponent(getNewFrcComponent(), /* namespace= */ "fireperf");
    loadConfigsWithExperimentsForActivate();

    assertWithMessage("Fireperf fetch and activate failed!")
        .that(fireperfFrc.activate().getResult())
        .isTrue();

    verify(mockFirebaseAbt, never()).replaceAllExperiments(any());
  }

  @Test
  public void frcNonMainFirebaseApp_doesNotCallAbt() throws Exception {

    when(mockFirebaseApp.getName()).thenReturn("secondary");
    FirebaseRemoteConfig frc =
        getFrcInstanceFromComponent(getNewFrcComponentWithoutLoadingDefault(), DEFAULT_NAMESPACE);
    loadConfigsWithExperimentsForActivate();

    assertWithMessage("Fetch and activate failed!").that(frc.activate().getResult()).isTrue();

    verify(mockFirebaseAbt, never()).replaceAllExperiments(any());
  }

  @Test
  public void getFetchHandler_nonMainFirebaseApp_doesNotUseAnalytics() {

    when(mockFirebaseApp.getName()).thenReturn("secondary");

    ConfigFetchHandler fetchHandler =
        getNewFrcComponent()
            .getFetchHandler(DEFAULT_NAMESPACE, mockFetchedCache, mockMetadataClient);

    assertThat(fetchHandler.getAnalyticsConnector()).isNull();
  }

  @Test
  public void
      getFrcBackendApiClient_fetchTimeoutIsNotSet_buildsConfigFetchHttpClientWithDefaultConnectionTimeout() {

    RemoteConfigComponent frcComponent = defaultApp.get(RemoteConfigComponent.class);
    when(mockMetadataClient.getFetchTimeoutInSeconds()).thenReturn(CONNECTION_TIMEOUT_IN_SECONDS);

    ConfigFetchHttpClient frcBackendClient =
        frcComponent.getFrcBackendApiClient(DUMMY_API_KEY, DEFAULT_NAMESPACE, mockMetadataClient);

    int actualConnectTimeout = getConnectTimeoutInSeconds(frcBackendClient);
    int actualReadTimeout = getReadTimeoutInSeconds(frcBackendClient);
    assertThat(actualConnectTimeout).isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
    assertThat(actualReadTimeout).isEqualTo(CONNECTION_TIMEOUT_IN_SECONDS);
  }

  @Test
  public void
      getFrcBackendApiClient_fetchTimeoutIsSetToDoubleDefault_buildsConfigFetchHttpClientWithDoubleDefaultConnectionTimeout() {

    RemoteConfigComponent frcComponent = defaultApp.get(RemoteConfigComponent.class);

    long customConnectionTimeoutInSeconds = 2 * CONNECTION_TIMEOUT_IN_SECONDS;
    when(mockMetadataClient.getFetchTimeoutInSeconds())
        .thenReturn(customConnectionTimeoutInSeconds);

    ConfigFetchHttpClient frcBackendClient =
        frcComponent.getFrcBackendApiClient(DUMMY_API_KEY, DEFAULT_NAMESPACE, mockMetadataClient);

    int actualConnectTimeout = getConnectTimeoutInSeconds(frcBackendClient);
    int actualReadTimeout = getReadTimeoutInSeconds(frcBackendClient);
    assertThat(actualConnectTimeout).isEqualTo(customConnectionTimeoutInSeconds);
    assertThat(actualReadTimeout).isEqualTo(customConnectionTimeoutInSeconds);
  }

  private RemoteConfigComponent getNewFrcComponent() {
    return new RemoteConfigComponent(
        context,
        directExecutor,
        mockFirebaseApp,
        mockFirebaseInstallations,
        mockFirebaseAbt,
        mockAnalyticsConnector,
        /* loadGetDefault= */ true);
  }

  private RemoteConfigComponent getNewFrcComponentWithoutLoadingDefault() {
    return new RemoteConfigComponent(
        context,
        directExecutor,
        mockFirebaseApp,
        mockFirebaseInstallations,
        mockFirebaseAbt,
        mockAnalyticsConnector,
        /* loadGetDefault= */ false);
  }

  private FirebaseRemoteConfig getFrcInstanceFromComponent(
      RemoteConfigComponent frcComponent, String namespace) {
    return frcComponent.get(
        mockFirebaseApp,
        namespace,
        mockFirebaseInstallations,
        mockFirebaseAbt,
        directExecutor,
        mockFetchedCache,
        mockActivatedCache,
        mockDefaultsCache,
        mockFetchHandler,
        mockGetParameterHandler,
        mockMetadataClient);
  }

  private void loadConfigsWithExperimentsForActivate() throws Exception {
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder()
            .withAbtExperiments(createAbtExperiments(createAbtExperiment("exp1")))
            .build();

    when(mockFetchedCache.get()).thenReturn(Tasks.forResult(containerWithAbtExperiments));
    when(mockActivatedCache.get()).thenReturn(Tasks.forResult(null));

    when(mockActivatedCache.put(containerWithAbtExperiments))
        .thenReturn(Tasks.forResult(containerWithAbtExperiments));
  }

  private int getConnectTimeoutInSeconds(ConfigFetchHttpClient frcBackendClient) {
    return (int) frcBackendClient.getConnectTimeoutInSeconds();
  }

  private int getReadTimeoutInSeconds(ConfigFetchHttpClient frcBackendClient) {
    return (int) frcBackendClient.getReadTimeoutInSeconds();
  }

  private static FirebaseApp initializeFirebaseApp(Context context) {
    FirebaseApp.clearInstancesForTest();

    return FirebaseApp.initializeApp(
        context,
        new FirebaseOptions.Builder()
            .setApiKey(API_KEY)
            .setApplicationId(APP_ID)
            .setProjectId(PROJECT_ID)
            .build());
  }
}
