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
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_BOOLEAN;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_DOUBLE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_LONG;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.DEFAULT_VALUE_FOR_STRING;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.toExperimentInfoMaps;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import com.google.android.gms.shadows.common.internal.ShadowPreconditions;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.abt.AbtException;
import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.internal.ConfigCacheClient;
import com.google.firebase.remoteconfig.internal.ConfigContainer;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse;
import com.google.firebase.remoteconfig.internal.ConfigGetParameterHandler;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import org.json.JSONArray;
import org.json.JSONException;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Unit tests for the Firebase Remote Config API.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(
    manifest = Config.NONE,
    shadows = {ShadowPreconditions.class})
public final class FirebaseRemoteConfigTest {
  private static final String APP_ID = "1:14368190084:android:09cb977358c6f241";
  private static final String API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String PROJECT_ID = "fake-frc-test-id";

  private static final String FIREPERF_NAMESPACE = "fireperf";

  private static final String STRING_KEY = "string_key";
  private static final String BOOLEAN_KEY = "boolean_key";
  private static final String BYTE_ARRAY_KEY = "byte_array_key";
  private static final String DOUBLE_KEY = "double_key";
  private static final String LONG_KEY = "long_key";

  private static final String ETAG = "ETag";

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

  @Mock private ConfigCacheClient mockFetchedCache;
  @Mock private ConfigCacheClient mockActivatedCache;
  @Mock private ConfigCacheClient mockDefaultsCache;
  @Mock private ConfigFetchHandler mockFetchHandler;
  @Mock private ConfigGetParameterHandler mockGetHandler;
  @Mock private ConfigMetadataClient metadataClient;

  @Mock private ConfigCacheClient mockFireperfFetchedCache;
  @Mock private ConfigCacheClient mockFireperfActivatedCache;
  @Mock private ConfigCacheClient mockFireperfDefaultsCache;
  @Mock private ConfigFetchHandler mockFireperfFetchHandler;
  @Mock private ConfigGetParameterHandler mockFireperfGetHandler;

  @Mock private FirebaseRemoteConfigInfo mockFrcInfo;

  @Mock private FirebaseABTesting mockFirebaseAbt;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;

  private FirebaseRemoteConfig frc;
  private FirebaseRemoteConfig fireperfFrc;
  private ConfigContainer firstFetchedContainer;
  private ConfigContainer secondFetchedContainer;

  private FetchResponse firstFetchedContainerResponse;

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
    Context context = RuntimeEnvironment.application;
    FirebaseApp firebaseApp = initializeFirebaseApp(context);

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
            metadataClient);

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
                RemoteConfigComponent.getMetadataClient(context, APP_ID, FIREPERF_NAMESPACE));

    firstFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(ImmutableMap.of("long_param", "1L", "string_param", "string_value"))
            .withFetchTime(new Date(1000L))
            .build();

    secondFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                ImmutableMap.of("string_param", "string_value", "double_param", "0.1"))
            .withFetchTime(new Date(5000L))
            .build();

    firstFetchedContainerResponse =
        FetchResponse.forBackendUpdatesFetched(firstFetchedContainer, ETAG);
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
    Resources r = RuntimeEnvironment.application.getResources();
    return r.getIdentifier(xmlResourceName, "xml", RuntimeEnvironment.application.getPackageName());
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
}
