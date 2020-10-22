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

package com.google.firebase.remoteconfig.internal;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_FAILURE;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_SUCCESS;
import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.LAST_FETCH_STATUS_THROTTLED;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.EXPERIMENT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ExperimentDescriptionFieldKey.VARIANT_ID;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.ENTRIES;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.EXPERIMENT_DESCRIPTIONS;
import static com.google.firebase.remoteconfig.RemoteConfigConstants.ResponseFieldKey.STATE;
import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.BACKOFF_TIME_DURATIONS_IN_MINUTES;
import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS;
import static com.google.firebase.remoteconfig.internal.ConfigFetchHandler.HTTP_TOO_MANY_REQUESTS;
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.LAST_FETCH_TIME_NO_FETCH_YET;
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.NO_BACKOFF_TIME;
import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.NO_FAILED_FETCHES;
import static com.google.firebase.remoteconfig.testutil.Assert.assertThrows;
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_NOT_FOUND;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.annotation.Nullable;
import com.google.android.gms.common.util.MockClock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigServerException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient.BackoffMetadata;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;
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
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.skyscreamer.jsonassert.JSONAssert;

/**
 * Unit tests for the Firebase Remote Config (FRC) Fetch handler.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ConfigFetchHandlerTest {
  private static final String INSTALLATION_ID = "'fL71_VyL3uo9jNMWu1L60S";
  private static final String INSTALLATION_AUTH_TOKEN =
      "eyJhbGciOiJF.eyJmaWQiOiJmaXMt.AB2LPV8wRQIhAPs4NvEgA3uhubH";
  private static final InstallationTokenResult INSTALLATION_TOKEN_RESULT =
      InstallationTokenResult.builder()
          .setToken(INSTALLATION_AUTH_TOKEN)
          .setTokenCreationTimestamp(1)
          .setTokenExpirationTimestamp(1)
          .build();
  private static final long DEFAULT_CACHE_EXPIRATION_IN_MILLISECONDS =
      SECONDS.toMillis(DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS);

  private static final Date FIRST_FETCH_TIME = new Date(HOURS.toMillis(1L));
  private static final Date SECOND_FETCH_TIME = new Date(HOURS.toMillis(12L));

  private Executor directExecutor;
  private MockClock mockClock;
  @Mock private Random mockRandom;
  @Mock private ConfigCacheClient mockFetchedCache;

  @Mock private ConfigFetchHttpClient mockBackendFetchApiClient;

  private Context context;
  @Mock private FirebaseInstallationsApi mockFirebaseInstallations;
  private ConfigMetadataClient metadataClient;

  private ConfigFetchHandler fetchHandler;

  private ConfigContainer firstFetchedContainer;
  private ConfigContainer secondFetchedContainer;
  private String responseETag = "";

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    directExecutor = MoreExecutors.directExecutor();
    context = RuntimeEnvironment.application.getApplicationContext();
    mockClock = new MockClock(0L);
    metadataClient =
        new ConfigMetadataClient(context.getSharedPreferences("test_file", Context.MODE_PRIVATE));

    loadBackendApiClient();
    loadInstallationIdAndAuthToken();

    /*
     * Every fetch starts with a call to retrieve the cached fetch values. Return successfully in
     * the base case.
     */
    when(mockFetchedCache.get()).thenReturn(Tasks.forResult(null));

    // Assume there is no analytics SDK for most of the tests.
    fetchHandler = getNewFetchHandler(/*analyticsConnector=*/ null);

    firstFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                new JSONObject(ImmutableMap.of("string_param", "string_value", "long_param", "1L")))
            .withFetchTime(FIRST_FETCH_TIME)
            .build();

    secondFetchedContainer =
        ConfigContainer.newBuilder()
            .replaceConfigsWith(
                new JSONObject(
                    ImmutableMap.of("string_param", "string_value", "double_param", "0.1")))
            .withFetchTime(SECOND_FETCH_TIME)
            .build();
  }

  @Test
  public void fetch_noPreviousSuccessfulFetch_fetchesFromBackend() throws Exception {
    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);

    assertWithMessage("Fetch() failed for first fetch!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetch_firstFetch_includesInstallationAuthToken() throws Exception {
    fetchCallToHttpClientReturnsConfigWithCurrentTime(firstFetchedContainer);

    assertWithMessage("Fetch() does not include installation auth token.")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verify(mockBackendFetchApiClient)
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ eq(INSTALLATION_AUTH_TOKEN),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
  }

  @Test
  public void fetch_failToGetInstallationAuthToken_throwsRemoteConfigException() throws Exception {
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forException(new IOException("SERVICE_NOT_AVAILABLE")));
    fetchCallToHttpClientReturnsConfigWithCurrentTime(firstFetchedContainer);

    assertThrowsClientException(
        fetchHandler.fetch(),
        "Firebase Installations failed to get installation auth token for fetch.");

    verifyBackendIsNeverCalled();
  }

  @Test
  public void fetch_cacheHasNotExpired_doesNotFetchFromBackend() throws Exception {
    loadCacheAndClockWithConfig(mockFetchedCache, firstFetchedContainer);

    // Don't wait long enough for cache to expire.
    mockClock.advance(DEFAULT_CACHE_EXPIRATION_IN_MILLISECONDS - 1);

    assertWithMessage("Fetch() failed even though cache has not expired!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsNeverCalled();
  }

  @Test
  public void fetch_cacheHasNotExpiredAndEmptyFetchCache_doesNotFetchFromBackend()
      throws Exception {
    simulateFetchAndActivate(mockFetchedCache, firstFetchedContainer);

    // Don't wait long enough for cache to expire.
    mockClock.advance(DEFAULT_CACHE_EXPIRATION_IN_MILLISECONDS - 1);

    assertWithMessage("Fetch() failed even though cache has not expired!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsNeverCalled();
  }

  @Test
  public void fetch_cacheHasExpired_fetchesFromBackend() throws Exception {
    loadCacheAndClockWithConfig(mockFetchedCache, firstFetchedContainer);

    // Wait long enough for cache to expire.
    mockClock.advance(DEFAULT_CACHE_EXPIRATION_IN_MILLISECONDS);
    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);

    assertWithMessage("Fetch() failed after cache expired!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetch_cacheHasExpiredAndEmptyFetchCache_fetchesFromBackend() throws Exception {
    simulateFetchAndActivate(mockFetchedCache, firstFetchedContainer);

    // Wait long enough for cache to expire.
    mockClock.advance(DEFAULT_CACHE_EXPIRATION_IN_MILLISECONDS);
    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);

    assertWithMessage("Fetch() failed after cache expired!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetch_userSetMinimumFetchIntervalHasPassed_fetchesFromBackend() throws Exception {
    long minimumFetchIntervalInSeconds = 600L;
    setMinimumFetchIntervalInMetadata(minimumFetchIntervalInSeconds);

    simulateFetchAndActivate(mockFetchedCache, firstFetchedContainer);
    // Wait long enough for cache to expire.
    mockClock.advance(SECONDS.toMillis(minimumFetchIntervalInSeconds));

    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);
    assertWithMessage("Fetch() failed after cache expired!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetch_userSetMinimumFetchIntervalHasNotPassed_doesNotFetchFromBackend()
      throws Exception {
    long minimumFetchIntervalInSeconds = 600L;
    setMinimumFetchIntervalInMetadata(minimumFetchIntervalInSeconds);

    loadCacheAndClockWithConfig(mockFetchedCache, firstFetchedContainer);
    // Don't wait long enough for cache to expire.
    mockClock.advance(SECONDS.toMillis(minimumFetchIntervalInSeconds) - 1);

    assertWithMessage("Fetch() failed even though cache has not expired!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verifyBackendIsNeverCalled();
  }

  @Test
  public void fetchWithExpiration_noPreviousSuccessfulFetch_fetchesFromBackend() throws Exception {
    // Wait long enough for cache to expire.
    long cacheExpirationInHours = 1;
    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);

    assertWithMessage("Fetch() failed for first fetch!")
        .that(fetchHandler.fetch(HOURS.toSeconds(cacheExpirationInHours)).isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetchWithExpiration_cacheHasNotExpired_doesNotFetchFromBackend() throws Exception {
    loadCacheAndClockWithConfig(mockFetchedCache, firstFetchedContainer);

    // Don't wait long enough for cache to expire.
    long cacheExpirationInHours = 1;
    mockClock.advance(HOURS.toMillis(cacheExpirationInHours) - 1);

    assertWithMessage("Fetch() failed even though cache has not expired!")
        .that(fetchHandler.fetch(HOURS.toSeconds(cacheExpirationInHours)).isSuccessful())
        .isTrue();

    verifyBackendIsNeverCalled();
  }

  @Test
  public void fetchWithExpiration_cacheHasNotExpiredAndEmptyFetchCache_doesNotFetchFromBackend()
      throws Exception {
    simulateFetchAndActivate(mockFetchedCache, firstFetchedContainer);

    // Don't wait long enough for cache to expire.
    long cacheExpirationInHours = 1;
    mockClock.advance(HOURS.toMillis(cacheExpirationInHours) - 1);

    assertWithMessage("Fetch() failed even though cache has not expired!")
        .that(fetchHandler.fetch(HOURS.toSeconds(cacheExpirationInHours)).isSuccessful())
        .isTrue();
    verifyBackendIsNeverCalled();
  }

  @Test
  public void fetchWithExpiration_cacheHasExpired_fetchesFromBackend() throws Exception {
    loadCacheAndClockWithConfig(mockFetchedCache, firstFetchedContainer);

    // Wait long enough for cache to expire.
    long cacheExpirationInHours = 1;
    mockClock.advance(HOURS.toMillis(cacheExpirationInHours));
    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);

    assertWithMessage("Fetch() failed after cache expired!")
        .that(fetchHandler.fetch(HOURS.toSeconds(cacheExpirationInHours)).isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetchWithExpiration_cacheHasExpiredAndEmptyFetchCache_fetchesFromBackend()
      throws Exception {
    simulateFetchAndActivate(mockFetchedCache, firstFetchedContainer);

    // Wait long enough for cache to expire.
    long cacheExpirationInHours = 1;
    mockClock.advance(HOURS.toMillis(cacheExpirationInHours));
    fetchCallToHttpClientReturnsConfigWithCurrentTime(secondFetchedContainer);

    assertWithMessage("Fetch() failed after cache expired!")
        .that(fetchHandler.fetch(HOURS.toSeconds(cacheExpirationInHours)).isSuccessful())
        .isTrue();

    verifyBackendIsCalled();
  }

  @Test
  public void fetch_gettingFetchCacheFails_doesNotThrowException() throws Exception {
    when(mockFetchedCache.get())
        .thenReturn(Tasks.forException(new IOException("Disk read failed.")));

    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);

    assertWithMessage("Fetch() failed when fetch cache could not be read!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();
  }

  @Test
  public void fetch_fetchBackendCallFails_taskThrowsException() throws Exception {
    when(mockBackendFetchApiClient.fetch(any(), any(), any(), any(), any(), any(), any()))
        .thenThrow(
            new FirebaseRemoteConfigClientException("Fetch failed due to an unexpected error."));

    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    assertThrowsClientException(fetchTask, "unexpected error");
  }

  @Test
  public void fetch_noChangeSinceLastFetch_doesNotUpdateCache() throws Exception {
    setBackendResponseToNoChange(new Date(mockClock.currentTimeMillis()));

    assertWithMessage("Fetch() failed after no changes were returned from backend!")
        .that(fetchHandler.fetch().isSuccessful())
        .isTrue();

    verify(mockFetchedCache, never()).put(any());
  }

  @Test
  public void fetch_fetchedCachePutFails_taskThrowsException() throws Exception {
    IOException expectedException = new IOException("Network call failed.");
    setBackendResponseConfigsTo(firstFetchedContainer);
    when(mockFetchedCache.put(any())).thenReturn(Tasks.forException(expectedException));

    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    IOException actualException =
        assertThrows(IOException.class, () -> fetchTask.getResult(IOException.class));
    assertThat(actualException).isEqualTo(expectedException);
  }

  @Test
  public void fetch_HasNoErrors_everythingWorks() throws Exception {
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);

    assertWithMessage("Fetch() failed!").that(fetchHandler.fetch().isSuccessful()).isTrue();

    verify(mockFetchedCache).put(firstFetchedContainer);
  }

  @Test
  public void fetch_HasETag_sendsETagAndSavesResponseETag() throws Exception {
    String requestETag = "Request eTag";
    String responseETag = "Response eTag";
    loadETags(requestETag, responseETag);
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);

    assertWithMessage("Fetch() failed!").that(fetchHandler.fetch().isSuccessful()).isTrue();

    verifyETags(requestETag, responseETag);
  }

  @Test
  public void fetch_HasNoETag_doesNotSendETagAndSavesResponseETag() throws Exception {
    String responseETag = "Response eTag";
    loadETags(/*requestETag=*/ null, responseETag);
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);

    assertWithMessage("Fetch() failed!").that(fetchHandler.fetch().isSuccessful()).isTrue();

    verifyETags(/*requestETag=*/ null, responseETag);
  }

  @Test
  public void fetch_hasAbtExperiments_storesExperiments() throws Exception {
    ConfigContainer containerWithExperiments =
        ConfigContainer.newBuilder(firstFetchedContainer)
            .withAbtExperiments(generateAbtExperiments(/*numExperiments=*/ 5))
            .build();
    ArgumentCaptor<ConfigContainer> captor = ArgumentCaptor.forClass(ConfigContainer.class);

    fetchCallToHttpClientUpdatesClockAndReturnsConfig(containerWithExperiments);
    fetchHandler.fetch();

    verify(mockFetchedCache).put(captor.capture());

    JSONAssert.assertEquals(
        containerWithExperiments.toString(), captor.getValue().toString(), false);
  }

  @Test
  public void fetch_getsThrottledResponseFromServer_backsOffOnSecondCall() throws Exception {
    fetchCallToBackendThrowsException(HTTP_TOO_MANY_REQUESTS);
    long backoffDurationInMillis = loadAndGetNextBackoffDuration(/*numFailedFetches=*/ 1);

    FirebaseRemoteConfigFetchThrottledException actualException =
        getThrottledException(fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0L));

    assertThat(actualException.getThrottleEndTimeMillis())
        .isEqualTo(mockClock.currentTimeMillis() + backoffDurationInMillis);
  }

  @Test
  public void fetch_getsMultipleThrottledResponsesFromServer_exponentiallyBacksOff()
      throws Exception {
    for (int numFetch = 1; numFetch <= BACKOFF_TIME_DURATIONS_IN_MINUTES.length; numFetch++) {
      fetchCallToBackendThrowsException(HTTP_TOO_MANY_REQUESTS);
      long backoffDurationInMillis = loadAndGetNextBackoffDuration(numFetch);

      assertThrowsThrottledException(
          fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0L),
          mockClock.currentTimeMillis() + backoffDurationInMillis);

      // Wait long enough for throttling to clear.
      mockClock.advance(backoffDurationInMillis);
    }
  }

  @Test
  public void fetch_getsMultipleFailedResponsesFromServer_resetsBackoffAfterSuccessfulFetch()
      throws Exception {
    callFetchAssertThrottledAndAdvanceClock(HTTP_TOO_MANY_REQUESTS);
    callFetchAssertThrottledAndAdvanceClock(HTTP_BAD_GATEWAY);
    callFetchAssertThrottledAndAdvanceClock(HTTP_UNAVAILABLE);
    callFetchAssertThrottledAndAdvanceClock(HTTP_GATEWAY_TIMEOUT);

    fetchCallToHttpClientReturnsConfigWithCurrentTime(firstFetchedContainer);

    Task<FetchResponse> fetchTask = fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0L);

    assertWithMessage("Fetch() failed!").that(fetchTask.isSuccessful()).isTrue();

    BackoffMetadata backoffMetadata = metadataClient.getBackoffMetadata();
    assertThat(backoffMetadata.getNumFailedFetches()).isEqualTo(NO_FAILED_FETCHES);
    assertThat(backoffMetadata.getBackoffEndTime()).isEqualTo(NO_BACKOFF_TIME);
  }

  @Test
  public void getRandomizedBackoffDuration_callOverMaxTimes_returnsUpToMaxInterval()
      throws Exception {
    int backoffDurationsLength = BACKOFF_TIME_DURATIONS_IN_MINUTES.length;
    for (int numFetch = 1; numFetch <= backoffDurationsLength + 2; numFetch++) {
      long backoffDurationInterval =
          MINUTES.toMillis(
              BACKOFF_TIME_DURATIONS_IN_MINUTES[Math.min(numFetch, backoffDurationsLength) - 1]);

      fetchCallToBackendThrowsException(HTTP_TOO_MANY_REQUESTS);
      when(mockRandom.nextInt((int) backoffDurationInterval))
          .thenReturn(new Random().nextInt((int) backoffDurationInterval));

      FirebaseRemoteConfigFetchThrottledException actualException =
          getThrottledException(fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0L));

      long actualBackoffDuration =
          actualException.getThrottleEndTimeMillis() - mockClock.currentTimeMillis();
      assertThat(actualBackoffDuration)
          .isAtLeast(backoffDurationInterval - backoffDurationInterval / 2);
      assertThat(actualBackoffDuration)
          .isLessThan(backoffDurationInterval + backoffDurationInterval / 2);

      // Wait long enough for throttling to clear.
      mockClock.advance(actualBackoffDuration);
    }
  }

  @Test
  public void fetch_serverReturnsUnauthorizedCode_throwsServerUnauthenticatedException()
      throws Exception {
    // The 401 HTTP Code is mapped from UNAUTHENTICATED in the gRPC world.
    fetchCallToBackendThrowsException(HTTP_UNAUTHORIZED);

    assertThrowsServerException(
        fetchHandler.fetch(), HTTP_UNAUTHORIZED, "did not have the required credentials");
  }

  @Test
  public void fetch_serverReturnsForbiddenCode_throwsServerUnauthorizedException()
      throws Exception {
    fetchCallToBackendThrowsException(HTTP_FORBIDDEN);

    assertThrowsServerException(fetchHandler.fetch(), HTTP_FORBIDDEN, "is not authorized");
  }

  @Test
  public void fetch_serverReturnsBadGatewayCode_throwsServerUnavailableException()
      throws Exception {
    fetchCallToBackendThrowsException(HTTP_BAD_GATEWAY);

    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    assertThrowsServerException(fetchTask, HTTP_BAD_GATEWAY, "unavailable");
  }

  @Test
  public void fetch_serverReturnsUnavailableCode_throwsServerUnavailableException()
      throws Exception {
    fetchCallToBackendThrowsException(HTTP_UNAVAILABLE);

    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    assertThrowsServerException(fetchTask, HTTP_UNAVAILABLE, "unavailable");
  }

  @Test
  public void fetch_serverReturnsGatewayTimeoutCode_throwsServerUnavailableException()
      throws Exception {
    fetchCallToBackendThrowsException(HTTP_GATEWAY_TIMEOUT);

    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    assertThrowsServerException(fetchTask, HTTP_GATEWAY_TIMEOUT, "unavailable");
  }

  @Test
  public void fetch_serverReturnsThrottleableErrorTwice_throwsThrottledException()
      throws Exception {
    fetchCallToBackendThrowsException(HTTP_UNAVAILABLE);
    fetchHandler.fetch();

    fetchCallToBackendThrowsException(HTTP_UNAVAILABLE);

    Task<FetchResponse> fetchTask = fetchHandler.fetch();

    assertThrowsThrottledException(fetchTask);
  }

  @Test
  public void fetch_serverReturnsInternalErrorCode_throwsServerInternalException()
      throws Exception {
    fetchCallToBackendThrowsException(HTTP_INTERNAL_ERROR);

    assertThrowsServerException(fetchHandler.fetch(), HTTP_INTERNAL_ERROR, "internal server error");
  }

  @Test
  public void fetch_serverReturnsUnexpectedCode_throwsServerException() throws Exception {
    fetchCallToBackendThrowsException(HTTP_NOT_FOUND);

    assertThrowsServerException(fetchHandler.fetch(), HTTP_NOT_FOUND, "unexpected error");
  }

  @Test
  public void fetch_hasAnalyticsSdk_sendsUserProperties() throws Exception {
    // Provide the mock Analytics SDK.
    AnalyticsConnector mockAnalyticsConnector = mock(AnalyticsConnector.class);
    fetchHandler = getNewFetchHandler(mockAnalyticsConnector);

    Map<String, String> userProperties =
        ImmutableMap.of("up_key1", "up_val1", "up_key2", "up_val2");
    when(mockAnalyticsConnector.getUserProperties(/*includeInternal=*/ false))
        .thenReturn(ImmutableMap.copyOf(userProperties));

    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);

    assertWithMessage("Fetch() failed!").that(fetchHandler.fetch().isSuccessful()).isTrue();

    verifyBackendIsCalled(userProperties);
  }

  @Test
  public void fetch_firstAndOnlyFetchFails_metadataFailStatusAndNoFetchYetTime() throws Exception {
    fetchCallToBackendThrowsException(HTTP_NOT_FOUND);

    fetchHandler.fetch();

    assertThat(metadataClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(metadataClient.getLastSuccessfulFetchTime()).isEqualTo(LAST_FETCH_TIME_NO_FETCH_YET);
  }

  @Test
  public void fetch_fetchSucceeds_metadataSuccessStatusAndFetchTimeUpdated() throws Exception {
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);

    fetchHandler.fetch();

    assertThat(metadataClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
    assertThat(metadataClient.getLastSuccessfulFetchTime())
        .isEqualTo(firstFetchedContainer.getFetchTime());
  }

  @Test
  public void fetch_firstFetchSucceedsSecondFetchFails_failStatusAndFirstFetchTime()
      throws Exception {
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);
    fetchHandler.fetch();

    fetchCallToBackendThrowsException(HTTP_NOT_FOUND);

    fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0);

    assertThat(metadataClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_FAILURE);
    assertThat(metadataClient.getLastSuccessfulFetchTime())
        .isEqualTo(firstFetchedContainer.getFetchTime());
  }

  @Test
  public void getInfo_twoFetchesSucceed_successStatusAndSecondFetchTime() throws Exception {
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);
    fetchHandler.fetch();

    fetchCallToHttpClientUpdatesClockAndReturnsConfig(secondFetchedContainer);

    fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0);

    assertThat(metadataClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_SUCCESS);
    assertThat(metadataClient.getLastSuccessfulFetchTime())
        .isEqualTo(secondFetchedContainer.getFetchTime());
  }

  @Test
  public void getInfo_hitsThrottleLimit_throttledStatus() throws Exception {
    fetchCallToHttpClientUpdatesClockAndReturnsConfig(firstFetchedContainer);
    fetchHandler.fetch();

    fetchCallToBackendThrowsException(HTTP_TOO_MANY_REQUESTS);

    fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0);

    assertThat(metadataClient.getLastFetchStatus()).isEqualTo(LAST_FETCH_STATUS_THROTTLED);
    assertThat(metadataClient.getLastSuccessfulFetchTime())
        .isEqualTo(firstFetchedContainer.getFetchTime());
  }

  private ConfigFetchHandler getNewFetchHandler(AnalyticsConnector analyticsConnector) {
    ConfigFetchHandler fetchHandler =
        spy(
            new ConfigFetchHandler(
                mockFirebaseInstallations,
                analyticsConnector,
                directExecutor,
                mockClock,
                mockRandom,
                mockFetchedCache,
                mockBackendFetchApiClient,
                metadataClient,
                /* customHttpHeaders= */ ImmutableMap.of()));
    return fetchHandler;
  }

  private void setBackendResponseConfigsTo(ConfigContainer container) throws Exception {
    Map<String, String> configEntries = new HashMap<>();

    Iterator<String> keyIt = container.getConfigs().keys();
    while (keyIt.hasNext()) {
      String key = keyIt.next();
      configEntries.put(key, container.getConfigs().getString(key));
    }

    JSONArray experiments = container.getAbtExperiments();

    doReturn(
            toFetchResponse(
                "UPDATE",
                new JSONObject(configEntries),
                experiments,
                responseETag,
                container.getFetchTime()))
        .when(mockBackendFetchApiClient)
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
  }

  private void setBackendResponseToNoChange(Date date) throws Exception {
    when(mockBackendFetchApiClient.fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any()))
        .thenReturn(FetchResponse.forBackendHasNoUpdates(date));
  }

  private void fetchCallToBackendThrowsException(int httpErrorCode) throws Exception {
    doThrow(new FirebaseRemoteConfigServerException(httpErrorCode, "Server error"))
        .when(mockBackendFetchApiClient)
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
  }

  /**
   * Sets the {@link ConfigContainer} to be returned by the backend to {@code container}, with the
   * fetch time set to the current time in {@link #mockClock}.
   */
  private void fetchCallToHttpClientReturnsConfigWithCurrentTime(ConfigContainer container)
      throws Exception {
    ConfigContainer containerToBeReturned =
        ConfigContainer.newBuilder(container)
            .withFetchTime(new Date(mockClock.currentTimeMillis()))
            .build();
    setBackendResponseConfigsTo(containerToBeReturned);
    cachePutReturnsConfig(mockFetchedCache, containerToBeReturned);
  }

  /**
   * Sets the {@link ConfigContainer} to be returned by the backend to {@code container}, and
   * updates {@link #mockClock}'s current time to {@code container}'s {@code fetchTime}.
   */
  private void fetchCallToHttpClientUpdatesClockAndReturnsConfig(ConfigContainer container)
      throws Exception {
    setBackendResponseConfigsTo(container);
    cachePutReturnsConfig(mockFetchedCache, container);
    mockClock.setCurrentTime(container.getFetchTime().getTime());
  }

  /**
   * Sets the server error response to {@code httpCode}, calls fetch, asserts that the fetch returns
   * a throttled exception, and then advances the clock so the next call will not be throttled.
   *
   * <p>The first server response is always {@link ConfigFetchHandler#HTTP_TOO_MANY_REQUESTS}.
   */
  private void callFetchAssertThrottledAndAdvanceClock(int httpCode) throws Exception {
    fetchCallToBackendThrowsException(httpCode);

    long backoffDurationInMillis =
        loadAndGetNextBackoffDuration(
            /*numFailedFetches=*/ metadataClient.getBackoffMetadata().getNumFailedFetches() + 1);

    assertThrowsThrottledException(fetchHandler.fetch(/*minimumFetchIntervalInSeconds=*/ 0L));

    // Wait long enough for throttling to clear.
    mockClock.advance(backoffDurationInMillis);
  }

  private long loadAndGetNextBackoffDuration(int numFailedFetches) {
    int numOfBackoffDurations = BACKOFF_TIME_DURATIONS_IN_MINUTES.length;
    int backoffDurationInterval =
        (int)
            MINUTES.toMillis(
                BACKOFF_TIME_DURATIONS_IN_MINUTES[
                    Math.min(numFailedFetches, numOfBackoffDurations) - 1]);

    // The backoff Duration is equal to "backoffDurationInterval/2 + randomVal", so make randomVal
    // equal to "backoffDurationInterval/2" to have the actual backoffDuration be
    // backoffDurationInterval.
    when(mockRandom.nextInt(backoffDurationInterval)).thenReturn(backoffDurationInterval / 2);

    return backoffDurationInterval;
  }

  private void setMinimumFetchIntervalInMetadata(long minimumFetchIntervalInSeconds) {
    metadataClient.setConfigSettings(
        new FirebaseRemoteConfigSettings.Builder()
            .setMinimumFetchIntervalInSeconds(minimumFetchIntervalInSeconds)
            .build());
  }

  private void verifyBackendIsCalled() throws Exception {
    verify(mockBackendFetchApiClient)
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
  }

  private void verifyBackendIsCalled(Map<String, String> userProperties) throws Exception {
    verify(mockBackendFetchApiClient)
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ eq(userProperties),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
  }

  private void verifyBackendIsNeverCalled() throws Exception {
    verify(mockBackendFetchApiClient, never())
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ any(),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
  }

  private void verifyETags(@Nullable String requestETag, String responseETag) throws Exception {
    verify(mockBackendFetchApiClient)
        .fetch(
            any(HttpURLConnection.class),
            /* instanceId= */ any(),
            /* instanceIdToken= */ any(),
            /* analyticsUserProperties= */ any(),
            /* lastFetchETag= */ eq(requestETag),
            /* customHeaders= */ any(),
            /* currentTime= */ any());
    assertThat(metadataClient.getLastFetchETag()).isEqualTo(responseETag);
  }

  private void loadBackendApiClient() throws Exception {
    when(mockBackendFetchApiClient.createHttpURLConnection())
        .thenReturn(new FakeHttpURLConnection(new URL("https://firebase.google.com")));
  }

  private void loadETags(String requestETag, String responseETag) {
    metadataClient.setLastFetchETag(requestETag);
    this.responseETag = responseETag;
  }

  private void loadInstallationIdAndAuthToken() {
    when(mockFirebaseInstallations.getId()).thenReturn(Tasks.forResult(INSTALLATION_ID));
    when(mockFirebaseInstallations.getToken(false))
        .thenReturn(Tasks.forResult(INSTALLATION_TOKEN_RESULT));
  }

  private void loadCacheAndClockWithConfig(
      ConfigCacheClient cacheClient, ConfigContainer container) {
    when(cacheClient.getBlocking()).thenReturn(container);
    when(cacheClient.get()).thenReturn(Tasks.forResult(container));
    mockClock.setCurrentTime(container.getFetchTime().getTime());
    metadataClient.updateLastFetchAsSuccessfulAt(container.getFetchTime());
  }

  private static void cachePutReturnsConfig(
      ConfigCacheClient cacheClient, ConfigContainer container) {
    when(cacheClient.put(container)).thenReturn(Tasks.forResult(container));
  }

  private void simulateFetchAndActivate(ConfigCacheClient cacheClient, ConfigContainer container) {
    loadCacheAndClockWithConfig(cacheClient, container);
    // During a successful activateFetched() call, the fetch cache is cleared.
    when(cacheClient.getBlocking()).thenReturn(container);
    when(cacheClient.get()).thenReturn(Tasks.forResult(null));
  }

  private static FirebaseRemoteConfigFetchThrottledException getThrottledException(
      Task<FetchResponse> fetchTask) {
    return assertThrows(
        FirebaseRemoteConfigFetchThrottledException.class,
        () -> fetchTask.getResult(FirebaseRemoteConfigFetchThrottledException.class));
  }

  private static void assertThrowsClientException(Task<FetchResponse> fetchTask, String message) {
    FirebaseRemoteConfigClientException frcException =
        assertThrows(
            FirebaseRemoteConfigClientException.class,
            () -> fetchTask.getResult(FirebaseRemoteConfigClientException.class));

    assertThat(frcException).hasMessageThat().contains(message);
  }

  private void assertThrowsThrottledException(Task<FetchResponse> fetchTask) {
    assertThrows(
        FirebaseRemoteConfigFetchThrottledException.class,
        () -> fetchTask.getResult(FirebaseRemoteConfigFetchThrottledException.class));
  }

  private void assertThrowsThrottledException(
      Task<FetchResponse> fetchTask, long backoffEndTimeInMillis) {
    FirebaseRemoteConfigFetchThrottledException actualException = getThrottledException(fetchTask);
    assertThat(actualException.getThrottleEndTimeMillis()).isEqualTo(backoffEndTimeInMillis);
  }

  private static void assertThrowsServerException(
      Task<FetchResponse> fetchTask, int httpStatusCode, @Nullable String httpStatusMessage) {
    FirebaseRemoteConfigServerException frcException =
        assertThrows(
            FirebaseRemoteConfigServerException.class,
            () -> fetchTask.getResult(FirebaseRemoteConfigServerException.class));

    if (httpStatusMessage != null) {
      assertThat(frcException.getHttpStatusCode()).isEqualTo(httpStatusCode);
      assertThat(frcException).hasMessageThat().contains(httpStatusMessage);
    }
  }

  private static JSONArray generateAbtExperiments(int numExperiments) throws JSONException {
    JSONArray experiments = new JSONArray();
    for (int experimentNum = 1; experimentNum <= numExperiments; experimentNum++) {
      experiments.put(
          new JSONObject().put(EXPERIMENT_ID, "exp" + experimentNum).put(VARIANT_ID, "var1"));
    }
    return experiments;
  }

  private static FetchResponse toFetchResponse(
      String status, JSONObject entries, JSONArray experiments, String eTag, Date currentTime)
      throws Exception {
    JSONObject fetchResponse = new JSONObject();
    fetchResponse.put(STATE, status);
    fetchResponse.put(ENTRIES, entries);
    fetchResponse.put(EXPERIMENT_DESCRIPTIONS, experiments);
    ConfigContainer fetchedConfigs = extractConfigs(fetchResponse, currentTime);
    return FetchResponse.forBackendUpdatesFetched(fetchedConfigs, eTag);
  }

  private static ConfigContainer extractConfigs(JSONObject fetchResponse, Date fetchTime)
      throws Exception {
    ConfigContainer.Builder containerBuilder =
        ConfigContainer.newBuilder().withFetchTime(fetchTime);

    JSONObject entries = fetchResponse.getJSONObject(ENTRIES);
    containerBuilder.replaceConfigsWith(entries);

    JSONArray experimentDescriptions = fetchResponse.getJSONArray(EXPERIMENT_DESCRIPTIONS);
    containerBuilder.withAbtExperiments(experimentDescriptions);

    return containerBuilder.build();
  }
}
