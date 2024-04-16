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

import static androidx.test.ext.truth.os.BundleSubject.assertThat;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.remoteconfig.AbtExperimentHelper.createAbtExperiment;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BOOLEAN;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_LONG;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.toExperimentInfoMaps;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_ARM_VALUE_PARAM;
import static com.google.firebase.remoteconfig.internal.Personalization.EXTERNAL_PERSONALIZATION_ID_PARAM;
import static com.google.firebase.remoteconfig.testutil.Assert.assertFalse;
import static com.google.firebase.remoteconfig.testutil.Assert.assertThrows;
import static com.google.firebase.remoteconfig.testutil.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.shadows.common.internal.ShadowPreconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.abt.AbtException;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.internal.ConfigAutoFetch;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient;
import com.google.firebase.remoteconfig.internal.ConfigRealtimeHandler;
import com.google.firebase.remoteconfig.internal.ConfigRealtimeHttpClient;
import com.google.firebase.remoteconfig.internal.FakeHttpURLConnection;
import com.google.firebase.remoteconfig.internal.Personalization;
import com.google.firebase.remoteconfig.internal.rollouts.RolloutsStateSubscriptionsHandler;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.LooperMode;
import org.skyscreamer.jsonassert.JSONAssert;

/** Unit tests for the Firebase Remote Config API. */
@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    shadows = {ShadowPreconditions.class})
@LooperMode(LooperMode.Mode.LEGACY)
public final class FirebaseRemoteConfigTest {
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String PROJECT_ID = "fake-frc-test-id";

  private static final String FIREPERF_NAMESPACE = "fireperf";
  private static final String PERSONALIZATION_NAMESPACE = "personalization";

  private static final String STRING_KEY = "string_key";
  private static final String BOOLEAN_KEY = "boolean_key";
  private static final String BYTE_ARRAY_KEY = "byte_array_key";
  private static final String DOUBLE_KEY = "double_key";
  private static final String LONG_KEY = "long_key";

  private static final String ETAG = "ETag";

  private static final String FORBIDDEN_ERROR_MESSAGE =
      "[{  \"error\": {    \"code\": 403,    \"message\": \"Firebase Remote Config Realtime API has not been used in project 14368190084 before or it is disabled. Enable it by visiting https://console.developers.google.com/apis/api/firebaseremoteconfigrealtime.googleapis.com/overview?project=14368190084 then retry. If you enabled this API recently, wait a few minutes for the action to propagate to our systems and retry.\",    \"status\": \"PERMISSION_DENIED\",    \"details\": [      {        \"@type\": \"type.googleapis.com/google.rpc.Help\",        \"links\": [          {            \"description\": \"Google developers console API activation\",            \"url\": \"https://console.developers.google.com/apis/api/firebaseremoteconfigrealtime.googleapis.com/overview?project=14368190084\"          }        ]      },      {        \"@type\": \"type.googleapis.com/google.rpc.ErrorInfo\",        \"reason\": \"SERVICE_DISABLED\",        \"domain\": \"googleapis.com\",        \"metadata\": {          \"service\": \"firebaseremoteconfigrealtime.googleapis.com\",          \"consumer\": \"projects/14368190084\"        }      }    ]  }}";

  private static final String INSTALLATION_ID = "'fL71_VyL3uo9jNMWu1L60S";
  private static final String INSTALLATION_TOKEN =
      "eyJhbGciOiJF.eyJmaWQiOiJmaXMt.AB2LPV8wRQIhAPs4NvEgA3uhubH";
  private static final InstallationTokenResult INSTALLATION_TOKEN_RESULT =
      InstallationTokenResult.builder()
          .setToken(INSTALLATION_TOKEN)
          .setTokenCreationTimestamp(1)
          .setTokenExpirationTimestamp(1)
          .build();

  // We use a HashMap so that Mocking is easier.
  private static final HashMap<String, Object> DEFAULTS_MAP = new HashMap<>();
  private static final HashMap<String, String> DEFAULTS_STRING_MAP = new HashMap<>();

  @Mock private AnalyticsConnector mockAnalyticsConnector;

  @Mock private ConfigCacheClient mockFetchedCache;
  @Mock private ConfigCacheClient mockActivatedCache;
  @Mock private ConfigCacheClient mockDefaultsCache;
  @Mock private ConfigFetchHandler mockFetchHandler;
  @Mock private ConfigGetParameterHandler mockGetHandler;
  @Mock private ConfigMetadataClient metadataClient;

  @Mock private ConfigRealtimeHandler mockConfigRealtimeHandler;
  @Mock private ConfigAutoFetch mockConfigAutoFetch;
  @Mock private ConfigUpdateListenerRegistration mockRealtimeRegistration;
  @Mock private HttpURLConnection mockHttpURLConnection;
  @Mock private ConfigUpdateListener mockRetryListener;
  @Mock private ConfigUpdateListener mockOnUpdateListener;
  @Mock private ConfigUpdateListener mockStreamErrorEventListener;
  @Mock private ConfigUpdateListener mockInvalidMessageEventListener;
  @Mock private ConfigUpdateListener mockNotFetchedEventListener;
  @Mock private ConfigUpdateListener mockUnavailableEventListener;
  @Mock private ConfigUpdateListener mockForbiddenErrorEventListener;

  @Mock private ConfigCacheClient mockFireperfFetchedCache;
  @Mock private ConfigCacheClient mockFireperfActivatedCache;
  @Mock private ConfigCacheClient mockFireperfDefaultsCache;
  @Mock private ConfigFetchHandler mockFireperfFetchHandler;
  @Mock private ConfigGetParameterHandler mockFireperfGetHandler;

  @Mock private FirebaseRemoteConfigInfo mockFrcInfo;

  @Mock private FirebaseABTesting mockFirebaseAbt;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  @Mock private Provider<AnalyticsConnector> mockAnalyticsConnectorProvider;

  @Mock private RolloutsStateSubscriptionsHandler mockRolloutsStateSubscriptionsHandler;

  private FirebaseRemoteConfig frc;
  private FirebaseRemoteConfig fireperfFrc;
  private FirebaseRemoteConfig personalizationFrc;
  private ConfigContainer firstFetchedContainer;
  private ConfigContainer secondFetchedContainer;
  private FetchResponse realtimeFetchedContainerResponse;
  private ConfigContainer realtimeFetchedContainer;
  private ConfigAutoFetch configAutoFetch;
  private ConfigRealtimeHttpClient configRealtimeHttpClient;
  private ConfigMetadataClient realtimeMetadataClient;

  private FetchResponse firstFetchedContainerResponse;

  private final ScheduledExecutorService scheduledExecutorService =
      Executors.newSingleThreadScheduledExecutor();

  @Before
  public void setUp() throws Exception {
    DEFAULTS_MAP.put("first_default_key", "first_default_value");
    DEFAULTS_MAP.put("second_default_key", "second_default_value");
    DEFAULTS_MAP.put("third_default_key", "third_default_value");
    DEFAULTS_MAP.put("byte_array_default_key", "fourth_default_value".getBytes());

    DEFAULTS_STRING_MAP.put("first_default_key", "first_default_value");
    DEFAULTS_STRING_MAP.put("second_default_key", "second_default_value");
    DEFAULTS_STRING_MAP.put("third_default_key", "third_default_value");
    DEFAULTS_STRING_MAP.put("byte_array_default_key", "fourth_default_value");

    MockitoAnnotations.initMocks(this);

    Executor directExecutor = MoreExecutors.directExecutor();
    Context context = ApplicationProvider.getApplicationContext();
    FirebaseApp firebaseApp = initializeFirebaseApp(context);

    Personalization personalization = new Personalization(mockAnalyticsConnectorProvider);
    ConfigGetParameterHandler parameterHandler =
        new ConfigGetParameterHandler(directExecutor, mockActivatedCache, mockDefaultsCache);
    parameterHandler.addListener(personalization::logArmActive);

    // Catch all to avoid NPEs (the getters should never return null).
    when(mockFetchedCache.get()).thenReturn(Tasks.forResult(null));
    when(mockActivatedCache.get()).thenReturn(Tasks.forResult(null));
    when(mockFireperfFetchedCache.get()).thenReturn(Tasks.forResult(null));
    when(mockFireperfActivatedCache.get()).thenReturn(Tasks.forResult(null));

    frc =
        new FirebaseRemoteConfig(
            context,
            firebaseApp,
            mockFirebaseInstallations,
            mockFirebaseAbt,
            directExecutor,
            mockFetchedCache,
            mockActivatedCache,
            mockDefaultsCache,
            mockFetchHandler,
            mockGetHandler,
            metadataClient,
            mockConfigRealtimeHandler,
            mockRolloutsStateSubscriptionsHandler);

    // Set up an FRC instance for the Fireperf namespace that uses mocked clients.
    fireperfFrc =
        FirebaseApp.getInstance()
            .get(RemoteConfigComponent.class)
            .get(
                firebaseApp,
                FIREPERF_NAMESPACE,
                mockFirebaseInstallations,
                /*firebaseAbt=*/ null,
                directExecutor,
                mockFireperfFetchedCache,
                mockFireperfActivatedCache,
                mockFireperfDefaultsCache,
                mockFireperfFetchHandler,
                mockFireperfGetHandler,
                RemoteConfigComponent.getMetadataClient(context, APP_ID, FIREPERF_NAMESPACE),
                mockRolloutsStateSubscriptionsHandler);

    personalizationFrc =
        FirebaseApp.getInstance()
            .get(RemoteConfigComponent.class)
            .get(
                firebaseApp,
                PERSONALIZATION_NAMESPACE,
                mockFirebaseInstallations,
                /*firebaseAbt=*/ null,
                directExecutor,
                mockFetchedCache,
                mockActivatedCache,
                mockDefaultsCache,
                mockFetchHandler,
                parameterHandler,
                RemoteConfigComponent.getMetadataClient(context, APP_ID, PERSONALIZATION_NAMESPACE),
                mockRolloutsStateSubscriptionsHandler);

    firstFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("long_param", "1L", "string_param", "string_value"))
            .withTemplateVersionNumber(1L)
            .withFetchTime(new Date(1000L))
            .build();

    secondFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of("string_param", "string_value", "double_param", "0.1"))
            .withTemplateVersionNumber(1L)
            .withFetchTime(new Date(5000L))
            .build();

    firstFetchedContainerResponse =
        FetchResponse.forBackendUpdatesFetched(firstFetchedContainer, ETAG);

    realtimeFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of(
                    "long_param",
                    "1L",
                    "string_param",
                    "string_value",
                    "realtime_param",
                    "realtime_value"))
            .withTemplateVersionNumber(1L)
            .build();
    realtimeFetchedContainerResponse =
        FetchResponse.forBackendUpdatesFetched(realtimeFetchedContainer, ETAG);

    HashSet<ConfigUpdateListener> listeners = new HashSet();
    ConfigUpdateListener listener =
        new ConfigUpdateListener() {
          @Override
          public void onUpdate(ConfigUpdate configUpdate) {
            mockOnUpdateListener.onUpdate(configUpdate);
          }

          @Override
          public void onError(@NonNull FirebaseRemoteConfigException error) {
            if (error.getCode() == FirebaseRemoteConfigException.Code.CONFIG_UPDATE_STREAM_ERROR) {
              if (error.getMessage().equals(FORBIDDEN_ERROR_MESSAGE)) {
                mockForbiddenErrorEventListener.onError(error);
              } else {
                mockStreamErrorEventListener.onError(error);
              }
            } else if (error.getCode()
                == FirebaseRemoteConfigException.Code.CONFIG_UPDATE_MESSAGE_INVALID) {
              mockInvalidMessageEventListener.onError(error);
            } else if (error.getCode()
                == FirebaseRemoteConfigException.Code.CONFIG_UPDATE_NOT_FETCHED) {
              mockNotFetchedEventListener.onError(error);
            } else {
              mockUnavailableEventListener.onError(error);
            }
          }
        };

    listeners.add(listener);
    configAutoFetch =
        new ConfigAutoFetch(
            mockHttpURLConnection,
            mockFetchHandler,
            mockActivatedCache,
            listeners,
            mockRetryListener,
            scheduledExecutorService);
    realtimeMetadataClient =
        new ConfigMetadataClient(context.getSharedPreferences("test_file", Context.MODE_PRIVATE));
    configRealtimeHttpClient =
        new ConfigRealtimeHttpClient(
            firebaseApp,
            mockFirebaseInstallations,
            mockFetchHandler,
            mockActivatedCache,
            context,
            "firebase",
            listeners,
            realtimeMetadataClient,
            scheduledExecutorService);
  }

  @Test
  public void ensureInitialized_notInitialized_isNotComplete() {
    loadCacheWithConfig(mockFetchedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);
    loadActivatedCacheWithIncompleteTask();
    loadInstanceIdAndToken();

    Task<FirebaseRemoteConfigInfo> initStatus = frc.ensureInitialized();

    assertWithMessage("FRC is initialized even though activated configs have not loaded!")
        .that(initStatus.isComplete())
        .isFalse();
  }

  @Test
  public void ensureInitialized_initialized_returnsCorrectFrcInfo() {
    loadCacheWithConfig(mockFetchedCache, /*container=*/ null);
    loadCacheWithConfig(mockDefaultsCache, /*container=*/ null);
    loadCacheWithConfig(mockActivatedCache, /*container=*/ null);
    loadInstanceIdAndToken();

    Task<FirebaseRemoteConfigInfo> initStatus = frc.ensureInitialized();

    assertWithMessage("FRC is not initialized even though everything is loaded!")
        .that(initStatus.isComplete())
        .isTrue();
  }

  @Test
  public void fetchAndActivate_hasNetworkError_taskReturnsException() {
    when(mockFetchHandler.fetch())
        .thenReturn(Tasks.forException(new IOException("Network call failed.")));

    Task<Boolean> task = frc.fetchAndActivate();

    assertThat(task.isComplete()).isTrue();
    assertWithMessage("Fetch succeeded even though there's a network error!")
        .that(task.getException())
        .isNotNull();
  }

  @Test
  public void fetchAndActivate_getFetchedFailed_returnsFalse() {
    loadFetchHandlerWithResponse();
    loadCacheWithIoException(mockFetchedCache);
    loadCacheWithConfig(mockActivatedCache, null);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() succeeded with no fetched values!")
        .that(getTaskResult(task))
        .isFalse();

    verify(mockActivatedCache, never()).put(any());
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void fetchAndActivate_noFetchedConfigs_returnsFalse() {
    loadFetchHandlerWithResponse();
    loadCacheWithConfig(mockFetchedCache, null);
    loadCacheWithConfig(mockActivatedCache, null);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() succeeded with no fetched values!")
        .that(getTaskResult(task))
        .isFalse();

    verify(mockActivatedCache, never()).put(any());
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void fetchAndActivate_staleFetchedConfigs_returnsFalse() {
    loadFetchHandlerWithResponse();
    loadCacheWithConfig(mockFetchedCache, firstFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() succeeded with stale values!")
        .that(getTaskResult(task))
        .isFalse();

    verify(mockActivatedCache, never()).put(any());
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void fetchAndActivate_noActivatedConfigs_activatesAndClearsFetched() {
    loadFetchHandlerWithResponse();
    loadCacheWithConfig(mockFetchedCache, firstFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, null);

    cachePutReturnsConfig(mockActivatedCache, firstFetchedContainer);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() failed with no activated values!")
        .that(getTaskResult(task))
        .isTrue();

    verify(mockActivatedCache).put(firstFetchedContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void fetchAndActivate_getActivatedFailed_activatesAndClearsFetched() {
    loadFetchHandlerWithResponse();
    loadCacheWithConfig(mockFetchedCache, firstFetchedContainer);
    loadCacheWithIoException(mockActivatedCache);

    cachePutReturnsConfig(mockActivatedCache, firstFetchedContainer);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() failed with no activated values!")
        .that(getTaskResult(task))
        .isTrue();

    verify(mockActivatedCache).put(firstFetchedContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void fetchAndActivate_freshFetchedConfigs_activatesAndClearsFetched() {
    loadFetchHandlerWithResponse();
    loadCacheWithConfig(mockFetchedCache, secondFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);

    cachePutReturnsConfig(mockActivatedCache, secondFetchedContainer);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() failed!").that(getTaskResult(task)).isTrue();

    verify(mockActivatedCache).put(secondFetchedContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void fetchAndActivate_fileWriteFails_doesNotClearFetchedAndReturnsFalse() {
    loadFetchHandlerWithResponse();
    loadCacheWithConfig(mockFetchedCache, secondFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);

    when(mockActivatedCache.put(secondFetchedContainer))
        .thenReturn(Tasks.forException(new IOException("Should have handled disk error.")));

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() succeeded even though file write failed!")
        .that(getTaskResult(task))
        .isFalse();

    verify(mockActivatedCache).put(secondFetchedContainer);
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void fetchAndActivate_callToAbtFails_activateStillSucceeds() throws Exception {
    loadFetchHandlerWithResponse();
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments())
            .build();

    loadCacheWithConfig(mockFetchedCache, containerWithAbtExperiments);
    cachePutReturnsConfig(mockActivatedCache, containerWithAbtExperiments);

    doThrow(new AbtException("Abt failure!")).when(mockFirebaseAbt).replaceAllExperiments(any());

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() failed!").that(getTaskResult(task)).isTrue();
  }

  @Test
  public void fetchAndActivate_hasAbtExperiments_sendsExperimentsToAbt() throws Exception {
    loadFetchHandlerWithResponse();
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments())
            .build();

    loadCacheWithConfig(mockFetchedCache, containerWithAbtExperiments);
    cachePutReturnsConfig(mockActivatedCache, containerWithAbtExperiments);

    Task<Boolean> task = frc.fetchAndActivate();

    assertWithMessage("fetchAndActivate() failed!").that(getTaskResult(task)).isTrue();

    List<Map<String, String>> expectedExperimentInfoMaps =
        toExperimentInfoMaps(containerWithAbtExperiments.getAbtExperiments());
    verify(mockFirebaseAbt).replaceAllExperiments(expectedExperimentInfoMaps);
  }

  @Test
  public void fetchAndActivate2p_hasNoAbtExperiments_doesNotCallAbt() throws Exception {
    load2pFetchHandlerWithResponse();
    ConfigContainer containerWithNoAbtExperiments =
        ConfigContainer.newBuilder().withFetchTime(new Date(1000L)).build();

    loadCacheWithConfig(mockFireperfFetchedCache, containerWithNoAbtExperiments);
    cachePutReturnsConfig(mockFireperfActivatedCache, containerWithNoAbtExperiments);

    Task<Boolean> task = fireperfFrc.fetchAndActivate();

    assertWithMessage("2p fetchAndActivate() failed!").that(getTaskResult(task)).isTrue();

    verify(mockFirebaseAbt, never()).replaceAllExperiments(any());
  }

  @Test
  public void fetchAndActivate2p_hasAbtExperiments_doesNotCallAbt() throws Exception {
    load2pFetchHandlerWithResponse();
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments())
            .build();

    loadCacheWithConfig(mockFireperfFetchedCache, containerWithAbtExperiments);
    cachePutReturnsConfig(mockFireperfActivatedCache, containerWithAbtExperiments);

    Task<Boolean> task = fireperfFrc.fetchAndActivate();

    assertWithMessage("2p fetchAndActivate() failed!").that(getTaskResult(task)).isTrue();

    verify(mockFirebaseAbt, never()).replaceAllExperiments(any());
  }

  @Test
  public void activate_getFetchedFailed_returnsFalse() {
    loadCacheWithIoException(mockFetchedCache);
    loadCacheWithConfig(mockActivatedCache, null);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() succeeded with no fetched values!")
        .that(activateTask.getResult())
        .isFalse();

    verify(mockActivatedCache, never()).put(any());
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void activate_noFetchedConfigs_returnsFalse() {
    loadCacheWithConfig(mockFetchedCache, null);
    loadCacheWithConfig(mockActivatedCache, null);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() succeeded with no fetched values!")
        .that(activateTask.getResult())
        .isFalse();

    verify(mockActivatedCache, never()).put(any());
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void activate_staleFetchedConfigs_returnsFalse() {
    loadCacheWithConfig(mockFetchedCache, firstFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() succeeded with stale values!")
        .that(activateTask.getResult())
        .isFalse();

    verify(mockActivatedCache, never()).put(any());
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void activate_noActivatedConfigs_activatesAndClearsFetched() {
    loadCacheWithConfig(mockFetchedCache, firstFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, null);

    cachePutReturnsConfig(mockActivatedCache, firstFetchedContainer);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() failed with no activated values!")
        .that(activateTask.getResult())
        .isTrue();

    verify(mockActivatedCache).put(firstFetchedContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void activate_getActivatedFailed_activatesAndClearsFetched() {
    loadCacheWithConfig(mockFetchedCache, firstFetchedContainer);
    loadCacheWithIoException(mockActivatedCache);

    cachePutReturnsConfig(mockActivatedCache, firstFetchedContainer);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() failed with no activated values!")
        .that(activateTask.getResult())
        .isTrue();

    verify(mockActivatedCache).put(firstFetchedContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void activate_freshFetchedConfigs_activatesAndClearsFetched() {
    loadCacheWithConfig(mockFetchedCache, secondFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);

    cachePutReturnsConfig(mockActivatedCache, secondFetchedContainer);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() failed!").that(activateTask.getResult()).isTrue();

    verify(mockActivatedCache).put(secondFetchedContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void activate_fileWriteFails_doesNotClearFetchedAndReturnsFalse() {
    loadCacheWithConfig(mockFetchedCache, secondFetchedContainer);
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);

    when(mockActivatedCache.put(secondFetchedContainer))
        .thenReturn(Tasks.forException(new IOException("Should have handled disk error.")));

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() succeeded even though file write failed!")
        .that(activateTask.getResult())
        .isFalse();

    verify(mockActivatedCache).put(secondFetchedContainer);
    verify(mockFetchedCache, never()).clear();
  }

  @Test
  public void activate_hasNoAbtExperiments_sendsEmptyListToAbt() throws Exception {
    ConfigContainer containerWithNoAbtExperiments =
        ConfigContainer.newBuilder().withFetchTime(new Date(1000L)).build();

    loadCacheWithConfig(mockFetchedCache, containerWithNoAbtExperiments);
    cachePutReturnsConfig(mockActivatedCache, containerWithNoAbtExperiments);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() failed!").that(activateTask.getResult()).isTrue();

    verify(mockFirebaseAbt).replaceAllExperiments(ImmutableList.of());
  }

  @Test
  public void activate_callToAbtFails_activateStillSucceeds() throws Exception {
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments())
            .build();

    loadCacheWithConfig(mockFetchedCache, containerWithAbtExperiments);
    cachePutReturnsConfig(mockActivatedCache, containerWithAbtExperiments);

    doThrow(new AbtException("Abt failure!")).when(mockFirebaseAbt).replaceAllExperiments(any());

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() failed!").that(activateTask.getResult()).isTrue();
  }

  @Test
  public void activate_hasAbtExperiments_sendsExperimentsToAbt() throws Exception {
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments())
            .build();

    loadCacheWithConfig(mockFetchedCache, containerWithAbtExperiments);
    cachePutReturnsConfig(mockActivatedCache, containerWithAbtExperiments);

    Task<Boolean> activateTask = frc.activate();

    assertWithMessage("activate() failed!").that(activateTask.getResult()).isTrue();

    List<Map<String, String>> expectedExperimentInfoMaps =
        toExperimentInfoMaps(containerWithAbtExperiments.getAbtExperiments());
    verify(mockFirebaseAbt).replaceAllExperiments(expectedExperimentInfoMaps);
  }

  @Test
  public void activate_publishesRolloutsStateToSubscribers() throws Exception {
    ConfigContainer configContainer = ConfigContainer.newBuilder().build();

    loadCacheWithConfig(mockFetchedCache, configContainer);
    cachePutReturnsConfig(mockActivatedCache, configContainer);

    Task<Boolean> activateTask = frc.activate();

    assertThat(activateTask.getResult()).isTrue();
    verify(mockRolloutsStateSubscriptionsHandler).publishActiveRolloutsState(configContainer);
  }

  @Test
  public void activate2p_hasNoAbtExperiments_doesNotCallAbt() throws Exception {
    ConfigContainer containerWithNoAbtExperiments =
        ConfigContainer.newBuilder().withFetchTime(new Date(1000L)).build();

    loadCacheWithConfig(mockFireperfFetchedCache, containerWithNoAbtExperiments);
    cachePutReturnsConfig(mockFireperfActivatedCache, containerWithNoAbtExperiments);

    Task<Boolean> activateTask = fireperfFrc.activate();

    assertWithMessage("Fireperf activate() failed!").that(activateTask.getResult()).isTrue();

    verify(mockFirebaseAbt, never()).replaceAllExperiments(any());
  }

  @Test
  public void activate2p_hasAbtExperiments_doesNotCallAbt() throws Exception {
    ConfigContainer containerWithAbtExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments())
            .build();

    loadCacheWithConfig(mockFireperfFetchedCache, containerWithAbtExperiments);
    cachePutReturnsConfig(mockFireperfActivatedCache, containerWithAbtExperiments);

    Task<Boolean> activateTask = fireperfFrc.activate();

    assertWithMessage("Fireperf activate() failed!").that(activateTask.getResult()).isTrue();

    verify(mockFirebaseAbt, never()).replaceAllExperiments(any());
  }

  @Test
  public void activate_fireperfNamespace_noFetchedConfigs_returnsFalse() {
    loadCacheWithConfig(mockFireperfFetchedCache, /*container=*/ null);
    loadCacheWithConfig(mockFireperfActivatedCache, /*container=*/ null);

    Task<Boolean> activateTask = fireperfFrc.activate();

    assertWithMessage("activate(fireperf) succeeded with no fetched values!")
        .that(activateTask.getResult())
        .isFalse();

    verify(mockFireperfActivatedCache, never()).put(any());
    verify(mockFireperfFetchedCache, never()).clear();
  }

  @Test
  public void activate_fireperfNamespace_freshFetchedConfigs_activatesAndClearsFetched() {
    loadCacheWithConfig(mockFireperfFetchedCache, secondFetchedContainer);
    loadCacheWithConfig(mockFireperfActivatedCache, firstFetchedContainer);
    // When the fetched values are activated, they should be put into the activated cache.
    cachePutReturnsConfig(mockFireperfActivatedCache, secondFetchedContainer);

    Task<Boolean> activateTask = fireperfFrc.activate();

    assertWithMessage("activate(fireperf) failed!").that(activateTask.getResult()).isTrue();

    verify(mockFireperfActivatedCache).put(secondFetchedContainer);
    verify(mockFireperfFetchedCache).clear();
  }

  @Test
  public void fetch_hasNoErrors_taskReturnsSuccess() {
    when(mockFetchHandler.fetch()).thenReturn(Tasks.forResult(firstFetchedContainerResponse));

    Task<Void> fetchTask = frc.fetch();

    assertWithMessage("Fetch failed!").that(fetchTask.isSuccessful()).isTrue();
  }

  @Test
  public void fetch_hasNetworkError_taskReturnsException() {
    when(mockFetchHandler.fetch())
        .thenReturn(
            Tasks.forException(new FirebaseRemoteConfigClientException("Network call failed.")));

    Task<Void> fetchTask = frc.fetch();

    assertWithMessage("Fetch succeeded even though there's a network error!")
        .that(fetchTask.isSuccessful())
        .isFalse();
  }

  @Test
  public void fetchWithInterval_hasNoErrors_taskReturnsSuccess() {
    long minimumFetchIntervalInSeconds = 600L;
    when(mockFetchHandler.fetch(minimumFetchIntervalInSeconds))
        .thenReturn(Tasks.forResult(firstFetchedContainerResponse));

    Task<Void> fetchTask = frc.fetch(minimumFetchIntervalInSeconds);

    assertWithMessage("Fetch failed!").that(fetchTask.isSuccessful()).isTrue();
  }

  @Test
  public void fetchWithInterval_hasNetworkError_taskReturnsException() {
    long minimumFetchIntervalInSeconds = 600L;
    when(mockFetchHandler.fetch(minimumFetchIntervalInSeconds))
        .thenReturn(
            Tasks.forException(new FirebaseRemoteConfigClientException("Network call failed.")));

    Task<Void> fetchTask = frc.fetch(minimumFetchIntervalInSeconds);

    assertWithMessage("Fetch succeeded even though there's a network error!")
        .that(fetchTask.isSuccessful())
        .isFalse();
  }

  @Test
  public void getKeysByPrefix_noKeysWithPrefix_returnsEmptySet() {
    when(mockGetHandler.getKeysByPrefix("pre")).thenReturn(ImmutableSet.of());

    assertThat(frc.getKeysByPrefix("pre")).isEmpty();
  }

  @Test
  public void getKeysByPrefix_hasKeysWithPrefix_returnsKeysWithPrefix() {
    Set<String> keysWithPrefix = ImmutableSet.of("pre11", "pre12");
    when(mockGetHandler.getKeysByPrefix("pre")).thenReturn(keysWithPrefix);

    assertThat(frc.getKeysByPrefix("pre")).containsExactlyElementsIn(keysWithPrefix);
  }

  @Test
  public void getString_keyDoesNotExist_returnsDefaultValue() {
    when(mockGetHandler.getString(STRING_KEY)).thenReturn(DEFAULT_VALUE_FOR_STRING);

    assertThat(frc.getString(STRING_KEY)).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @Test
  public void getString_keyExists_returnsRemoteValue() {
    String remoteValue = "remote value";
    when(mockGetHandler.getString(STRING_KEY)).thenReturn(remoteValue);

    assertThat(frc.getString(STRING_KEY)).isEqualTo(remoteValue);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getString_fireperfNamespace_keyDoesNotExist_returnsDefaultValue() {
    when(mockFireperfGetHandler.getString(STRING_KEY)).thenReturn(DEFAULT_VALUE_FOR_STRING);

    assertThat(fireperfFrc.getString(STRING_KEY)).isEqualTo(DEFAULT_VALUE_FOR_STRING);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getString_fireperfNamespace_keyExists_returnsRemoteValue() {
    String remoteValue = "remote value";
    when(mockFireperfGetHandler.getString(STRING_KEY)).thenReturn(remoteValue);

    assertThat(fireperfFrc.getString(STRING_KEY)).isEqualTo(remoteValue);
  }

  @Test
  public void getBoolean_keyDoesNotExist_returnsDefaultValue() {
    when(mockGetHandler.getBoolean(BOOLEAN_KEY)).thenReturn(DEFAULT_VALUE_FOR_BOOLEAN);

    assertThat(frc.getBoolean(BOOLEAN_KEY)).isEqualTo(DEFAULT_VALUE_FOR_BOOLEAN);
  }

  @Test
  public void getBoolean_keyExists_returnsRemoteValue() {
    when(mockGetHandler.getBoolean(BOOLEAN_KEY)).thenReturn(true);

    assertThat(frc.getBoolean(BOOLEAN_KEY)).isTrue();
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getBoolean_fireperfNamespace_keyDoesNotExist_returnsDefaultValue() {
    when(mockFireperfGetHandler.getBoolean(BOOLEAN_KEY)).thenReturn(DEFAULT_VALUE_FOR_BOOLEAN);

    assertThat(fireperfFrc.getBoolean(BOOLEAN_KEY)).isEqualTo(DEFAULT_VALUE_FOR_BOOLEAN);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getBoolean_fireperfNamespace_keyExists_returnsRemoteValue() {
    when(mockFireperfGetHandler.getBoolean(BOOLEAN_KEY)).thenReturn(true);

    assertThat(fireperfFrc.getBoolean(BOOLEAN_KEY)).isTrue();
  }

  @Test
  public void getDouble_keyDoesNotExist_returnsDefaultValue() {
    when(mockGetHandler.getDouble(DOUBLE_KEY)).thenReturn(DEFAULT_VALUE_FOR_DOUBLE);

    assertThat(frc.getDouble(DOUBLE_KEY)).isEqualTo(DEFAULT_VALUE_FOR_DOUBLE);
  }

  @Test
  public void getDouble_keyExists_returnsRemoteValue() {
    double remoteValue = 555.5;
    when(mockGetHandler.getDouble(DOUBLE_KEY)).thenReturn(remoteValue);

    assertThat(frc.getDouble(DOUBLE_KEY)).isEqualTo(remoteValue);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getDouble_fireperfNamespace_keyDoesNotExist_returnsDefaultValue() {
    when(mockFireperfGetHandler.getDouble(DOUBLE_KEY)).thenReturn(DEFAULT_VALUE_FOR_DOUBLE);

    assertThat(fireperfFrc.getDouble(DOUBLE_KEY)).isEqualTo(DEFAULT_VALUE_FOR_DOUBLE);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getDouble_fireperfNamespace_keyExists_returnsRemoteValue() {
    double remoteValue = 555.5;
    when(mockFireperfGetHandler.getDouble(DOUBLE_KEY)).thenReturn(remoteValue);

    assertThat(fireperfFrc.getDouble(DOUBLE_KEY)).isEqualTo(remoteValue);
  }

  @Test
  public void getLong_keyDoesNotExist_returnsDefaultValue() {
    when(mockGetHandler.getLong(LONG_KEY)).thenReturn(DEFAULT_VALUE_FOR_LONG);

    assertThat(frc.getLong(LONG_KEY)).isEqualTo(DEFAULT_VALUE_FOR_LONG);
  }

  @Test
  public void getLong_keyExists_returnsRemoteValue() {
    long remoteValue = 555L;
    when(mockGetHandler.getLong(LONG_KEY)).thenReturn(remoteValue);

    assertThat(frc.getLong(LONG_KEY)).isEqualTo(remoteValue);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getLong_fireperfNamespace_keyDoesNotExist_returnsDefaultValue() {
    when(mockFireperfGetHandler.getLong(LONG_KEY)).thenReturn(DEFAULT_VALUE_FOR_LONG);

    assertThat(fireperfFrc.getLong(LONG_KEY)).isEqualTo(DEFAULT_VALUE_FOR_LONG);
  }

  @SuppressWarnings("deprecation")
  @Test
  public void getLong_fireperfNamespace_keyExists_returnsRemoteValue() {
    long remoteValue = 555L;
    when(mockFireperfGetHandler.getLong(LONG_KEY)).thenReturn(remoteValue);

    assertThat(fireperfFrc.getLong(LONG_KEY)).isEqualTo(remoteValue);
  }

  @Test
  public void getInfo_returnsInfo() {
    when(metadataClient.getInfo()).thenReturn(mockFrcInfo);

    long fetchTimeInMillis = 100L;
    int lastFetchStatus = LAST_FETCH_STATUS_THROTTLED;
    long fetchTimeoutInSeconds = 10L;
    long minimumFetchIntervalInSeconds = 100L;
    when(mockFrcInfo.getFetchTimeMillis()).thenReturn(fetchTimeInMillis);
    when(mockFrcInfo.getLastFetchStatus()).thenReturn(lastFetchStatus);
    when(mockFrcInfo.getConfigSettings())
        .thenReturn(
            new FirebaseRemoteConfigSettings.Builder()
                .setFetchTimeoutInSeconds(fetchTimeoutInSeconds)
                .setMinimumFetchIntervalInSeconds(minimumFetchIntervalInSeconds)
                .build());

    FirebaseRemoteConfigInfo info = frc.getInfo();

    assertThat(info.getFetchTimeMillis()).isEqualTo(fetchTimeInMillis);
    assertThat(info.getLastFetchStatus()).isEqualTo(lastFetchStatus);
    assertThat(info.getConfigSettings().getFetchTimeoutInSeconds())
        .isEqualTo(fetchTimeoutInSeconds);
    assertThat(info.getConfigSettings().getMinimumFetchIntervalInSeconds())
        .isEqualTo(minimumFetchIntervalInSeconds);
  }

  @Test
  public void setDefaultsAsync_withMap_setsDefaults() throws Exception {
    ConfigContainer defaultsContainer = newDefaultsContainer(DEFAULTS_STRING_MAP);
    ArgumentCaptor<ConfigContainer> captor = ArgumentCaptor.forClass(ConfigContainer.class);
    cachePutReturnsConfig(mockDefaultsCache, defaultsContainer);

    boolean isComplete = frc.setDefaultsAsync(ImmutableMap.copyOf(DEFAULTS_MAP)).isComplete();

    assertThat(isComplete).isTrue();
    // Assert defaults were set correctly.
    verify(mockDefaultsCache).put(captor.capture());

    JSONAssert.assertEquals(defaultsContainer.toString(), captor.getValue().toString(), false);
  }

  @Test
  public void clear_hasSettings_clearsEverything() {
    frc.reset();

    verify(mockActivatedCache).clear();
    verify(mockFetchedCache).clear();
    verify(mockDefaultsCache).clear();
    verify(metadataClient).clear();
  }

  @Test
  public void setConfigSettingsAsync_updatesMetadata() {
    long fetchTimeout = 13L;
    long minimumFetchInterval = 666L;
    FirebaseRemoteConfigSettings frcSettings =
        new FirebaseRemoteConfigSettings.Builder()
            .setFetchTimeoutInSeconds(fetchTimeout)
            .setMinimumFetchIntervalInSeconds(minimumFetchInterval)
            .build();

    Task<Void> setterTask = frc.setConfigSettingsAsync(frcSettings);

    assertThat(setterTask.isSuccessful()).isTrue();
    verify(metadataClient).setConfigSettings(frcSettings);
  }

  @Test
  public void personalization_hasMetadata_successful() throws Exception {
    List<Bundle> fakeLogs = new ArrayList<>();
    when(mockAnalyticsConnectorProvider.get()).thenReturn(null).thenReturn(mockAnalyticsConnector);
    doAnswer(invocation -> fakeLogs.add(invocation.getArgument(2)))
        .when(mockAnalyticsConnector)
        .logEvent(
            eq(Personalization.ANALYTICS_ORIGIN_PERSONALIZATION),
            eq(Personalization.EXTERNAL_EVENT),
            any(Bundle.class));

    ConfigContainer configContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(new JSONObject("{key1: 'value1', key2: 'value2', key3: 'value3'}"))
            .withFetchTime(new Date(1))
            .withPersonalizationMetadata(
                new JSONObject(
                    "{key1: {personalizationId: 'id1', choiceId: '1'}, key2: {personalizationId: 'id2', choiceId: '2'}}"))
            .build();

    when(mockFetchHandler.fetch())
        .thenReturn(
            Tasks.forResult(FetchResponse.forBackendUpdatesFetched(configContainer, "Etag")));

    when(mockActivatedCache.getBlocking()).thenReturn(configContainer);

    personalizationFrc
        .fetchAndActivate()
        .addOnCompleteListener(
            success -> {
              personalizationFrc.getString("key1");
              personalizationFrc.getString("key2");

              // Since the first time we tried to get the Analytics connector we got `null` (not
              // available) we should only get the values for the second `getString` call.
              verify(mockAnalyticsConnector, times(1))
                  .logEvent(
                      eq(Personalization.ANALYTICS_ORIGIN_PERSONALIZATION),
                      eq(Personalization.EXTERNAL_EVENT),
                      any(Bundle.class));
              assertThat(fakeLogs).hasSize(1);

              assertThat(fakeLogs.get(0))
                  .string(EXTERNAL_PERSONALIZATION_ID_PARAM)
                  .isEqualTo("id2");
              assertThat(fakeLogs.get(0)).string(EXTERNAL_ARM_VALUE_PARAM).isEqualTo("value2");
            });
  }

  @Test
  public void personalization_hasMetadata_successful_without_analytics() throws Exception {
    List<Bundle> fakeLogs = new ArrayList<>();
    when(mockAnalyticsConnectorProvider.get()).thenReturn(null);

    ConfigContainer configContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(new JSONObject("{key1: 'value1', key2: 'value2'}"))
            .withFetchTime(new Date(1))
            .withPersonalizationMetadata(
                new JSONObject("{key1: {personalizationId: 'id1', choiceId: '1'}}"))
            .build();

    when(mockFetchHandler.fetch())
        .thenReturn(
            Tasks.forResult(FetchResponse.forBackendUpdatesFetched(configContainer, "Etag")));

    when(mockActivatedCache.getBlocking()).thenReturn(configContainer);

    personalizationFrc
        .fetchAndActivate()
        .addOnCompleteListener(
            success -> {
              personalizationFrc.getString("key1");
              personalizationFrc.getString("key2");
              verify(mockAnalyticsConnector, never())
                  .logEvent(anyString(), anyString(), any(Bundle.class));
              assertThat(fakeLogs).isEmpty();
            });
  }

  @Test
  public void activate_configWithRolloutMetadata_storedInActivatedCacheSuccessfully()
      throws Exception {
    JSONArray affectedParameterKeys = new JSONArray();
    affectedParameterKeys.put("key_1");
    affectedParameterKeys.put("key_2");

    JSONArray rolloutsMetadata = new JSONArray();
    rolloutsMetadata.put(
        new JSONObject()
            .put("rolloutId", "1")
            .put("variantId", "A")
            .put("affectedParameterKeys", affectedParameterKeys));

    ConfigContainer fetchedConfigContainer =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withRolloutMetadata(rolloutsMetadata)
            .build();

    loadCacheWithConfig(mockFetchedCache, fetchedConfigContainer);
    loadCacheWithConfig(mockActivatedCache, null);
    cachePutReturnsConfig(mockActivatedCache, fetchedConfigContainer);

    frc.activate();

    verify(mockActivatedCache).put(fetchedConfigContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void fetchAndActivate_configWithRolloutMetadata_storedInActivatedCacheSuccessfully()
      throws Exception {
    JSONArray affectedParameterKeys = new JSONArray();
    affectedParameterKeys.put("key_1");
    affectedParameterKeys.put("key_2");

    JSONArray rolloutsMetadata = new JSONArray();
    rolloutsMetadata.put(
        new JSONObject()
            .put("rolloutId", "1")
            .put("variantId", "A")
            .put("affectedParameterKeys", affectedParameterKeys));

    ConfigContainer fetchedConfigContainer =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withRolloutMetadata(rolloutsMetadata)
            .build();

    loadFetchHandlerWithResponse(fetchedConfigContainer);
    loadCacheWithConfig(mockFetchedCache, fetchedConfigContainer);
    loadCacheWithConfig(mockActivatedCache, null);
    cachePutReturnsConfig(mockActivatedCache, fetchedConfigContainer);

    frc.fetchAndActivate();

    verify(mockActivatedCache).put(fetchedConfigContainer);
    verify(mockFetchedCache).clear();
  }

  @Test
  public void realtime_frc_full_test() {
    ConfigUpdateListener eventListener = generateEmptyRealtimeListener();
    when(mockConfigRealtimeHandler.addRealtimeConfigUpdateListener(eventListener))
        .thenReturn(mockRealtimeRegistration);

    ConfigUpdateListenerRegistration registration = frc.addOnConfigUpdateListener(eventListener);
    registration.remove();

    verify(mockRealtimeRegistration).remove();
    verify(mockConfigRealtimeHandler).addRealtimeConfigUpdateListener(eventListener);
  }

  @Test
  public void realtime_client_addListener_success() {
    ConfigUpdateListener eventListener = generateEmptyRealtimeListener();
    when(mockConfigRealtimeHandler.addRealtimeConfigUpdateListener(eventListener))
        .thenReturn(
            new ConfigUpdateListenerRegistration() {
              @Override
              public void remove() {}
            });
    ConfigUpdateListenerRegistration registration =
        mockConfigRealtimeHandler.addRealtimeConfigUpdateListener(eventListener);
    verify(mockConfigRealtimeHandler).addRealtimeConfigUpdateListener(eventListener);
    assertThat(registration).isNotNull();
  }

  @Test
  public void realtime_client_removeListener_success() {
    ConfigUpdateListener eventListener = generateEmptyRealtimeListener();
    when(mockConfigRealtimeHandler.addRealtimeConfigUpdateListener(eventListener))
        .thenReturn(mockRealtimeRegistration);

    ConfigUpdateListenerRegistration registration =
        mockConfigRealtimeHandler.addRealtimeConfigUpdateListener(eventListener);
    registration.remove();

    verify(mockRealtimeRegistration).remove();
  }

  @Test
  public void realtime_stream_listen_and_end_connection() throws Exception {
    when(mockHttpURLConnection.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(
                "{ \"latestTemplateVersionNumber\": 1 }".getBytes(StandardCharsets.UTF_8)));
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 1))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));
    configAutoFetch.listenForNotifications();

    verify(mockHttpURLConnection).disconnect();
  }

  @Test
  public void realtime_fetchesWithoutChangedParams_doesNotCallOnUpdate() throws Exception {
    when(mockHttpURLConnection.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(
                "{ \"latestTemplateVersionNumber\": 1 }".getBytes(StandardCharsets.UTF_8)));
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetch(0)).thenReturn(Tasks.forResult(firstFetchedContainerResponse));
    configAutoFetch.listenForNotifications();

    verifyNoInteractions(mockOnUpdateListener);
  }

  @Test
  public void realtime_redirectStatusCode_noRetries() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(301);

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    verify(configRealtimeHttpClientSpy, never()).startAutoFetch(any());
    verify(configRealtimeHttpClientSpy, never()).retryHttpConnectionWhenBackoffEnds();
    verify(mockStreamErrorEventListener).onError(any(FirebaseRemoteConfigServerException.class));
  }

  @Test
  public void realtime_okStatusCode_startAutofetchAndRetries() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doReturn(mockConfigAutoFetch).when(configRealtimeHttpClientSpy).startAutoFetch(any());
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    verify(mockConfigAutoFetch).listenForNotifications();
    verify(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
  }

  @Test
  public void realtime_badGatewayStatusCode_noAutofetchButRetries() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(502);

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    verify(configRealtimeHttpClientSpy, never()).startAutoFetch(any());
    verify(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
  }

  @Test
  public void realtime_retryableStatusCode_increasesConfigMetadataFailedStreams() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(502);
    int failedStreams = configRealtimeHttpClientSpy.getNumberOfFailedStreams();

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    assertThat(configRealtimeHttpClientSpy.getNumberOfFailedStreams()).isEqualTo(failedStreams + 1);
  }

  @Test
  public void realtime_retryableStatusCode_increasesConfigMetadataBackoffDate() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(502);
    Date backoffDate = configRealtimeHttpClientSpy.getBackoffEndTime();

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    assertTrue(configRealtimeHttpClientSpy.getBackoffEndTime().after(backoffDate));
  }

  @Test
  public void realtime_successfulStatusCode_doesNotIncreaseConfigMetadataFailedStreams()
      throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doReturn(mockConfigAutoFetch).when(configRealtimeHttpClientSpy).startAutoFetch(any());
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);
    int failedStreams = configRealtimeHttpClientSpy.getNumberOfFailedStreams();

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    assertThat(configRealtimeHttpClientSpy.getNumberOfFailedStreams()).isEqualTo(failedStreams);
  }

  @Test
  public void realtime_successfulStatusCode_doesNotIncreaseConfigMetadataBackoffDate()
      throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doReturn(mockConfigAutoFetch).when(configRealtimeHttpClientSpy).startAutoFetch(any());
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);
    Date backoffDate = configRealtimeHttpClientSpy.getBackoffEndTime();

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    assertFalse(configRealtimeHttpClientSpy.getBackoffEndTime().after(backoffDate));
  }

  @Test
  public void realtime_forbiddenStatusCode_returnsStreamError() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forResult(mockHttpURLConnection))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));
    when(mockHttpURLConnection.getErrorStream())
        .thenReturn(
            new ByteArrayInputStream(FORBIDDEN_ERROR_MESSAGE.getBytes(StandardCharsets.UTF_8)));
    when(mockHttpURLConnection.getResponseCode()).thenReturn(403);

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    verify(configRealtimeHttpClientSpy, never()).startAutoFetch(any());
    verify(configRealtimeHttpClientSpy, never()).retryHttpConnectionWhenBackoffEnds();
    verify(mockForbiddenErrorEventListener).onError(any(FirebaseRemoteConfigServerException.class));
  }

  @Test
  public void realtime_exceptionThrown_noAutofetchButRetries() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    doReturn(Tasks.forException(new IOException()))
        .when(configRealtimeHttpClientSpy)
        .createRealtimeConnection();
    doNothing().when(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
    doNothing()
        .when(configRealtimeHttpClientSpy)
        .closeRealtimeHttpStream(any(HttpURLConnection.class));

    configRealtimeHttpClientSpy.beginRealtimeHttpStream();
    flushScheduledTasks();

    verify(configRealtimeHttpClientSpy, never()).startAutoFetch(any());
    verify(configRealtimeHttpClientSpy).retryHttpConnectionWhenBackoffEnds();
  }

  @Test
  public void realtime_stream_listen_and_failsafe_enabled() throws Exception {
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpURLConnection.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(
                "{ \"featureDisabled\": true }".getBytes(StandardCharsets.UTF_8)));
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    configAutoFetch.listenForNotifications();

    verify(mockRetryListener).onError(any(FirebaseRemoteConfigServerException.class));
    verify(mockFetchHandler, never()).fetch(0);
  }

  @Test
  public void realtime_stream_listen_and_failsafe_disabled() throws Exception {
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpURLConnection.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(
                "{ \"featureDisabled\": false,  \"latestTemplateVersionNumber\": 2 }"
                    .getBytes(StandardCharsets.UTF_8)));
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 1))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));
    configAutoFetch.listenForNotifications();

    verify(mockUnavailableEventListener, never())
        .onError(any(FirebaseRemoteConfigServerException.class));
    verify(mockFetchHandler).getTemplateVersionNumber();
  }

  @Test
  public void realtimeStreamListen_andUnableToParseMessage() throws Exception {
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpURLConnection.getInputStream())
        .thenReturn(
            new ByteArrayInputStream(
                "{ \"featureDisabled\": false,  \"latestTemplateVersionNumber: 2 } }"
                    .getBytes(StandardCharsets.UTF_8)));
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 1))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));
    configAutoFetch.listenForNotifications();

    verify(mockInvalidMessageEventListener).onError(any(FirebaseRemoteConfigClientException.class));
  }

  @Test
  public void realtime_stream_listen_get_inputstream_fail() throws Exception {
    when(mockHttpURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpURLConnection.getInputStream()).thenThrow(IOException.class);
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 1))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));
    configAutoFetch.listenForNotifications();

    verify(mockHttpURLConnection).disconnect();
  }

  @Test
  public void realtime_stream_autofetch_success() throws Exception {
    // Setup activated configs with keys "string_param", "long_param"
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 1))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));
    configAutoFetch.fetchLatestConfig(3, 1);

    flushScheduledTasks();

    Set<String> updatedKeys = Sets.newHashSet("realtime_param");
    verify(mockOnUpdateListener)
        .onUpdate(argThat(configUpdate -> configUpdate.getUpdatedKeys().equals(updatedKeys)));
  }

  @Test
  public void realtime_autofetchBeforeActivate_callsOnUpdateWithAllFetchedParams()
      throws Exception {
    // The first call to get() returns null while the cache is loading.
    loadCacheWithConfig(mockActivatedCache, null);
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 3))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));

    configAutoFetch.fetchLatestConfig(1, 1);
    flushScheduledTasks();

    Set<String> updatedKeys = Sets.newHashSet("string_param", "long_param", "realtime_param");
    verify(mockOnUpdateListener)
        .onUpdate(argThat(configUpdate -> configUpdate.getUpdatedKeys().equals(updatedKeys)));
  }

  @Test
  public void realtime_stream_autofetch_failure() throws Exception {
    loadCacheWithConfig(mockActivatedCache, firstFetchedContainer);
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    when(mockFetchHandler.fetchNowWithTypeAndAttemptNumber(
            ConfigFetchHandler.FetchType.REALTIME, 3))
        .thenReturn(Tasks.forResult(realtimeFetchedContainerResponse));

    configAutoFetch.fetchLatestConfig(1, 1000);
    flushScheduledTasks();

    verify(mockNotFetchedEventListener).onError(any(FirebaseRemoteConfigServerException.class));
  }

  @Test
  public void realtimeStream_getInstallationToken_tokenTaskThrowsException() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forException(new IOException("SERVICE_NOT_AVAILABLE")));

    Task<HttpURLConnection> httpURLConnectionTask =
        configRealtimeHttpClientSpy.createRealtimeConnection();
    flushScheduledTasks();

    FirebaseRemoteConfigClientException frcException =
        assertThrows(
            FirebaseRemoteConfigClientException.class,
            () -> httpURLConnectionTask.getResult(FirebaseRemoteConfigClientException.class));
    assertThat(frcException)
        .hasMessageThat()
        .contains(
            "Firebase Installations failed to get installation auth token for config update listener connection.");
  }

  @Test
  public void realtimeStream_getInstallationID_idTaskThrowsException() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    when(mockFirebaseInstallations.getId())
        .thenReturn(Tasks.forException(new IOException("SERVICE_NOT_AVAILABLE")));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));

    Task<HttpURLConnection> httpURLConnectionTask =
        configRealtimeHttpClientSpy.createRealtimeConnection();
    flushScheduledTasks();

    FirebaseRemoteConfigClientException frcException =
        assertThrows(
            FirebaseRemoteConfigClientException.class,
            () -> httpURLConnectionTask.getResult(FirebaseRemoteConfigClientException.class));
    assertThat(frcException)
        .hasMessageThat()
        .contains(
            "Firebase Installations failed to get installation ID for config update listener connection.");
  }

  @Test
  public void realtimeRequest_setRequestParams_succeedsWithCorrectParams() throws Exception {
    ConfigRealtimeHttpClient configRealtimeHttpClientSpy = spy(configRealtimeHttpClient);
    when(mockFetchHandler.getTemplateVersionNumber()).thenReturn(1L);
    FakeHttpURLConnection fakeConnection =
        new FakeHttpURLConnection(new URL("https://firebase.google.com"));

    configRealtimeHttpClientSpy.setRequestParams(
        fakeConnection, "fid-is-over-iid", INSTALLATION_TOKEN);
    String expectedBody = fakeConnection.getOutputStream().toString();
    Map<String, String> headerFields = fakeConnection.getRequestHeaders();

    Map<String, String> body = new HashMap<>();
    body.put("project", "14368190084");
    body.put("namespace", "firebase");
    body.put("lastKnownVersionNumber", "1");
    body.put("appId", APP_ID);
    body.put("sdkVersion", BuildConfig.VERSION_NAME);
    body.put("appInstanceId", "fid-is-over-iid");

    assertThat(new JSONObject(body).toString()).isEqualTo(expectedBody);
    assertThat(headerFields.get("X-Goog-Firebase-Installations-Auth"))
        .isEqualTo(INSTALLATION_TOKEN);
    assertThat(headerFields.get("X-Goog-Api-Key")).isEqualTo(API_KEY);
    assertThat(fakeConnection.getRequestMethod()).isEqualTo("POST");
  }

  private static void loadCacheWithConfig(
      ConfigCacheClient cacheClient, ConfigContainer container) {
    when(cacheClient.getBlocking()).thenReturn(container);
    when(cacheClient.get()).thenReturn(Tasks.forResult(container));
  }

  private static void loadCacheWithIoException(ConfigCacheClient cacheClient) {
    when(cacheClient.getBlocking()).thenReturn(null);
    when(cacheClient.get())
        .thenReturn(Tasks.forException(new IOException("Should have handled disk error.")));
  }

  private void loadActivatedCacheWithIncompleteTask() {
    TaskCompletionSource<ConfigContainer> taskSource = new TaskCompletionSource<>();
    when(mockActivatedCache.get()).thenReturn(taskSource.getTask());
  }

  private static void cachePutReturnsConfig(
      ConfigCacheClient cacheClient, ConfigContainer container) {
    when(cacheClient.put(container)).thenReturn(Tasks.forResult(container));
  }

  private void loadFetchHandlerWithResponse() {
    when(mockFetchHandler.fetch()).thenReturn(Tasks.forResult(firstFetchedContainerResponse));
  }

  private void loadFetchHandlerWithResponse(ConfigContainer configContainer) {
    FetchResponse fetchResponse =
        FetchResponse.forBackendUpdatesFetched(firstFetchedContainer, ETAG);
    when(mockFetchHandler.fetch()).thenReturn(Tasks.forResult(fetchResponse));
  }

  private void load2pFetchHandlerWithResponse() {
    when(mockFireperfFetchHandler.fetch())
        .thenReturn(Tasks.forResult(firstFetchedContainerResponse));
  }

  private void loadInstanceIdAndToken() {
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
  }

  private static int getResourceId(String xmlResourceName) {
    Resources r = ApplicationProvider.getApplicationContext().getResources();
    return r.getIdentifier(
        xmlResourceName, "xml", ApplicationProvider.getApplicationContext().getPackageName());
  }

  private static ConfigContainer newDefaultsContainer(Map<String, String> configsMap)
      throws Exception {
    return ConfigContainer.newBuilder()
        .replaceConfigsWith(configsMap)
        .withFetchTime(new Date(0L))
        .build();
  }

  private <T> T getTaskResult(Task<T> task) {
    assertThat(task.isComplete()).isTrue();
    assertThat(task.getResult()).isNotNull();
    return task.getResult();
  }

  private static JSONArray generateAbtExperiments() throws JSONException {
    JSONArray experiments = new JSONArray();
    for (int experimentNum = 1; experimentNum <= 5; experimentNum++) {
      experiments.put(createAbtExperiment("exp" + experimentNum));
    }
    return experiments;
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

  /**
   * Flush tasks on the {@code scheduledExecutorService}'s thread.
   *
   * @throws InterruptedException if the thread is interrupted while waiting.
   */
  private void flushScheduledTasks() throws InterruptedException {
    // Create a latch with a count of 1 and submit an execution request to countdown.
    // When the existing tasks have been executed, the countdown will execute and release the latch.
    CountDownLatch latch = new CountDownLatch(1);
    scheduledExecutorService.execute(latch::countDown);
    assertTrue("Task didn't finish.", latch.await(1000, TimeUnit.MILLISECONDS));
  }

  private ConfigUpdateListener generateEmptyRealtimeListener() {
    return new ConfigUpdateListener() {
      @Override
      public void onUpdate(ConfigUpdate configUpdate) {}

      @Override
      public void onError(@NonNull FirebaseRemoteConfigException error) {}
    };
  }
}
