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

import static com.google.firebase.remoteconfig.internal.ConfigMetadataClient.LAST_FETCH_TIME_NO_FETCH_YET;
import static java.net.HttpURLConnection.HTTP_BAD_GATEWAY;
import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
import static java.net.HttpURLConnection.HTTP_INTERNAL_ERROR;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;
import static java.net.HttpURLConnection.HTTP_UNAVAILABLE;
import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;
import static java.util.concurrent.TimeUnit.SECONDS;

import android.text.format.DateUtils;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.installations.InstallationTokenResult;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigClientException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigFetchThrottledException;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigServerException;
import com.google.firebase.remoteconfig.internal.ConfigFetchHandler.FetchResponse.Status;
import com.google.firebase.remoteconfig.internal.ConfigMetadataClient.BackoffMetadata;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.net.HttpURLConnection;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;

/**
 * A handler for fetch requests to the Firebase Remote Config backend.
 *
 * <p>Checks cache and throttling status before sending a request to the backend.
 *
 * @author Miraziz Yusupov
 */
public class ConfigFetchHandler {
  /** The default minimum interval between fetch requests to the Firebase Remote Config server. */
  public static final long DEFAULT_MINIMUM_FETCH_INTERVAL_IN_SECONDS = HOURS.toSeconds(12);

  /**
   * The exponential backoff intervals, up to ~4 hours.
   *
   * <p>Every value must be even.
   */
  @VisibleForTesting
  static final int[] BACKOFF_TIME_DURATIONS_IN_MINUTES = {2, 4, 8, 16, 32, 64, 128, 256};

  /**
   * HTTP status code for a throttled request.
   *
   * <p>Defined here since {@link HttpURLConnection} does not provide this code.
   */
  @VisibleForTesting static final int HTTP_TOO_MANY_REQUESTS = 429;

  private final FirebaseInstallationsApi firebaseInstallations;
  @Nullable private final AnalyticsConnector analyticsConnector;

  private final Executor executor;
  private final Clock clock;
  private final Random randomGenerator;
  private final ConfigCacheClient fetchedConfigsCache;
  private final ConfigFetchHttpClient frcBackendApiClient;
  private final ConfigMetadataClient frcMetadata;

  private final Map<String, String> customHttpHeaders;

  /** FRC Fetch Handler constructor. */
  public ConfigFetchHandler(
      FirebaseInstallationsApi firebaseInstallations,
      @Nullable AnalyticsConnector analyticsConnector,
      Executor executor,
      Clock clock,
      Random randomGenerator,
      ConfigCacheClient fetchedConfigsCache,
      ConfigFetchHttpClient frcBackendApiClient,
      ConfigMetadataClient frcMetadata,
      Map<String, String> customHttpHeaders) {
    this.firebaseInstallations = firebaseInstallations;
    this.analyticsConnector = analyticsConnector;
    this.executor = executor;
    this.clock = clock;
    this.randomGenerator = randomGenerator;
    this.fetchedConfigsCache = fetchedConfigsCache;
    this.frcBackendApiClient = frcBackendApiClient;
    this.frcMetadata = frcMetadata;

    this.customHttpHeaders = customHttpHeaders;
  }

  /**
   * Calls {@link #fetch(long)} with the {@link
   * ConfigMetadataClient#getMinimumFetchIntervalInSeconds()}.
   */
  public Task<FetchResponse> fetch() {
    return fetch(frcMetadata.getMinimumFetchIntervalInSeconds());
  }

  /**
   * Starts fetching configs from the Firebase Remote Config server.
   *
   * <p>Guarantees consistency between memory and disk; fetched configs are saved to memory only
   * after they have been written to disk.
   *
   * <p>Fetches even if the read of the fetch cache fails (assumes there are no cached fetched
   * configs in that case).
   *
   * <p>If the fetch request could not be created or there was error connecting to the server, the
   * returned Task throws a {@link FirebaseRemoteConfigClientException}.
   *
   * <p>If the server responds with an error, the returned Task throws a {@link
   * FirebaseRemoteConfigServerException}.
   *
   * <p>If any of the following is true, then the returned Task throws a {@link
   * FirebaseRemoteConfigFetchThrottledException}:
   *
   * <ul>
   *   <li>The backoff duration from a previous throttled exception has not expired,
   *   <li>The backend responded with a throttled error, or
   *   <li>The backend responded with unavailable errors for the last two fetch requests.
   * </ul>
   *
   * @return A {@link Task} representing the fetch call that returns a {@link FetchResponse} with
   *     the configs fetched from the backend. If the backend was not called or the backend had no
   *     updates, the {@link FetchResponse}'s configs will be {@code null}.
   */
  public Task<FetchResponse> fetch(long minimumFetchIntervalInSeconds) {
    return fetchedConfigsCache
        .get()
        .continueWithTask(
            executor,
            (cachedFetchConfigsTask) ->
                fetchIfCacheExpiredAndNotThrottled(
                    cachedFetchConfigsTask, minimumFetchIntervalInSeconds));
  }

  /**
   * Fetches from the backend if the fetched configs cache has expired and the client is not
   * currently throttled.
   *
   * <p>If a fetch request is made to the backend, updates the last fetch status, last successful
   * fetch time and {@link BackoffMetadata} in {@link ConfigMetadataClient}.
   */
  private Task<FetchResponse> fetchIfCacheExpiredAndNotThrottled(
      Task<ConfigContainer> cachedFetchConfigsTask, long minimumFetchIntervalInSeconds) {
    Date currentTime = new Date(clock.currentTimeMillis());
    if (cachedFetchConfigsTask.isSuccessful()
        && areCachedFetchConfigsValid(minimumFetchIntervalInSeconds, currentTime)) {
      // Keep the cached fetch values if the cache has not expired yet.
      return Tasks.forResult(FetchResponse.forLocalStorageUsed(currentTime));
    }

    Task<FetchResponse> fetchResponseTask;

    Date backoffEndTime = getBackoffEndTimeInMillis(currentTime);
    if (backoffEndTime != null) {
      // TODO(issues/260): Provide a way for users to check for throttled status so exceptions
      // aren't the only way for users to determine if they're throttled.
      fetchResponseTask =
          Tasks.forException(
              new FirebaseRemoteConfigFetchThrottledException(
                  createThrottledMessage(backoffEndTime.getTime() - currentTime.getTime()),
                  backoffEndTime.getTime()));
    } else {
      Task<String> installationIdTask = firebaseInstallations.getId();
      Task<InstallationTokenResult> installationAuthTokenTask =
          firebaseInstallations.getToken(false);
      fetchResponseTask =
          Tasks.whenAllComplete(installationIdTask, installationAuthTokenTask)
              .continueWithTask(
                  executor,
                  (completedInstallationsTasks) -> {
                    if (!installationIdTask.isSuccessful()) {
                      return Tasks.forException(
                          new FirebaseRemoteConfigClientException(
                              "Firebase Installations failed to get installation ID for fetch.",
                              installationIdTask.getException()));
                    }

                    if (!installationAuthTokenTask.isSuccessful()) {
                      return Tasks.forException(
                          new FirebaseRemoteConfigClientException(
                              "Firebase Installations failed to get installation auth token for fetch.",
                              installationAuthTokenTask.getException()));
                    }

                    String installationId = installationIdTask.getResult();
                    String installationToken = installationAuthTokenTask.getResult().getToken();
                    return fetchFromBackendAndCacheResponse(
                        installationId, installationToken, currentTime);
                  });
    }

    return fetchResponseTask.continueWithTask(
        executor,
        (completedFetchTask) -> {
          updateLastFetchStatusAndTime(completedFetchTask, currentTime);
          return completedFetchTask;
        });
  }

  /**
   * Returns true if the last successfully fetched configs are not stale, or if developer mode is
   * on.
   */
  private boolean areCachedFetchConfigsValid(long cacheExpirationInSeconds, Date newFetchTime) {
    Date lastSuccessfulFetchTime = frcMetadata.getLastSuccessfulFetchTime();
    // RC always fetches if the client has not previously had a successful fetch.

    if (lastSuccessfulFetchTime.equals(LAST_FETCH_TIME_NO_FETCH_YET)) {
      return false;
    }

    Date cacheExpirationTime =
        new Date(lastSuccessfulFetchTime.getTime() + SECONDS.toMillis(cacheExpirationInSeconds));

    return newFetchTime.before(cacheExpirationTime);
  }

  /**
   * Returns the earliest possible time, in millis since epoch, when a fetch request won't be
   * throttled by the server, or {@code null} if the client is not currently throttled by the
   * server.
   */
  @Nullable
  private Date getBackoffEndTimeInMillis(Date currentTime) {
    Date backoffEndTime = frcMetadata.getBackoffMetadata().getBackoffEndTime();
    if (currentTime.before(backoffEndTime)) {
      return backoffEndTime;
    }

    return null;
  }

  /**
   * Returns a human-readable throttled message with how long the client has to wait before fetching
   * again.
   */
  private String createThrottledMessage(long throttledDurationInMillis) {
    return String.format(
        "Fetch is throttled. Please wait before calling fetch again: %s",
        DateUtils.formatElapsedTime(MILLISECONDS.toSeconds(throttledDurationInMillis)));
  }

  /**
   * Fetches configs from the FRC backend. If there are any updates, writes the configs to the
   * {@code fetchedConfigsCache}.
   */
  private Task<FetchResponse> fetchFromBackendAndCacheResponse(
      String installationId, String installationToken, Date fetchTime) {
    try {
      FetchResponse fetchResponse = fetchFromBackend(installationId, installationToken, fetchTime);
      if (fetchResponse.getStatus() != Status.BACKEND_UPDATES_FETCHED) {
        return Tasks.forResult(fetchResponse);
      }
      return fetchedConfigsCache
          .put(fetchResponse.getFetchedConfigs())
          .onSuccessTask(executor, (putContainer) -> Tasks.forResult(fetchResponse));
    } catch (FirebaseRemoteConfigException frce) {
      return Tasks.forException(frce);
    }
  }

  /**
   * Creates a fetch request, sends it to the FRC backend and converts the server's response into a
   * {@link FetchResponse}.
   *
   * @return The {@link FetchResponse} from the FRC backend.
   * @throws FirebaseRemoteConfigServerException if the server returned an error.
   * @throws FirebaseRemoteConfigClientException if the request could not be created or there's an
   *     error connecting to the server.
   */
  @WorkerThread
  private FetchResponse fetchFromBackend(
      String installationId, String installationToken, Date currentTime)
      throws FirebaseRemoteConfigException {
    try {
      HttpURLConnection urlConnection = frcBackendApiClient.createHttpURLConnection();

      FetchResponse response =
          frcBackendApiClient.fetch(
              urlConnection,
              installationId,
              installationToken,
              getUserProperties(),
              frcMetadata.getLastFetchETag(),
              customHttpHeaders,
              currentTime);

      if (response.getLastFetchETag() != null) {
        frcMetadata.setLastFetchETag(response.getLastFetchETag());
      }
      // If the execute method did not throw exceptions, then the server sent a successful response
      // and the client can stop backing off.
      frcMetadata.resetBackoff();

      return response;
    } catch (FirebaseRemoteConfigServerException serverHttpError) {
      BackoffMetadata backoffMetadata =
          updateAndReturnBackoffMetadata(serverHttpError.getHttpStatusCode(), currentTime);

      if (shouldThrottle(backoffMetadata, serverHttpError.getHttpStatusCode())) {
        throw new FirebaseRemoteConfigFetchThrottledException(
            backoffMetadata.getBackoffEndTime().getTime());
      }
      // TODO(issues/264): Move the generic message logic to the ConfigFetchHttpClient.
      throw createExceptionWithGenericMessage(serverHttpError);
    }
  }

  /**
   * Returns a {@link FirebaseRemoteConfigServerException} with a generic message based on the
   * {@code statusCode}.
   *
   * @throws FirebaseRemoteConfigClientException if {@code statusCode} is {@link
   *     #HTTP_TOO_MANY_REQUESTS}. Throttled responses should be handled before calls to this
   *     method.
   */
  private FirebaseRemoteConfigServerException createExceptionWithGenericMessage(
      FirebaseRemoteConfigServerException httpError) throws FirebaseRemoteConfigClientException {
    String errorMessage;
    switch (httpError.getHttpStatusCode()) {
      case HTTP_UNAUTHORIZED:
        // The 401 HTTP Code is mapped from UNAUTHENTICATED in the gRPC world.
        errorMessage =
            "The request did not have the required credentials. "
                + "Please make sure your google-services.json is valid.";
        break;
      case HTTP_FORBIDDEN:
        errorMessage =
            "The user is not authorized to access the project. Please make sure "
                + "you are using the API key that corresponds to your Firebase project.";
        break;
      case HTTP_INTERNAL_ERROR:
        errorMessage = "There was an internal server error.";
        break;
      case HTTP_BAD_GATEWAY:
      case HTTP_UNAVAILABLE:
      case HTTP_GATEWAY_TIMEOUT:
        // The 504 HTTP Code is mapped from DEADLINE_EXCEEDED in the gRPC world.
        errorMessage = "The server is unavailable. Please try again later.";
        break;
      case HTTP_TOO_MANY_REQUESTS:
        // Should never happen.
        // The throttled response should be handled before the call to this method.
        throw new FirebaseRemoteConfigClientException(
            "The throttled response from the server was not handled correctly by the FRC SDK.");
      default:
        errorMessage = "The server returned an unexpected error.";
        break;
    }

    return new FirebaseRemoteConfigServerException(
        httpError.getHttpStatusCode(), "Fetch failed: " + errorMessage, httpError);
  }

  /**
   * Updates and returns the backoff metadata if the server returned a throttle-able error.
   *
   * <p>The list of throttle-able errors:
   *
   * <ul>
   *   <li>{@link #HTTP_TOO_MANY_REQUESTS},
   *   <li>{@link HttpURLConnection#HTTP_BAD_GATEWAY},
   *   <li>{@link HttpURLConnection#HTTP_UNAVAILABLE},
   *   <li>{@link HttpURLConnection#HTTP_GATEWAY_TIMEOUT}.
   * </ul>
   */
  private BackoffMetadata updateAndReturnBackoffMetadata(int statusCode, Date currentTime) {
    if (isThrottleableServerError(statusCode)) {
      updateBackoffMetadataWithLastFailedFetchTime(currentTime);
    }
    return frcMetadata.getBackoffMetadata();
  }

  /**
   * Returns true for server errors that are throttle-able.
   *
   * <p>The {@link HttpURLConnection#HTTP_GATEWAY_TIMEOUT} error is included here since it is
   * similar to the other unavailable errors in the previously linked doc.
   */
  private boolean isThrottleableServerError(int httpStatusCode) {
    return httpStatusCode == HTTP_TOO_MANY_REQUESTS
        || httpStatusCode == HttpURLConnection.HTTP_BAD_GATEWAY
        || httpStatusCode == HttpURLConnection.HTTP_UNAVAILABLE
        || httpStatusCode == HttpURLConnection.HTTP_GATEWAY_TIMEOUT;
  }

  // TODO(issues/265): Make this an atomic operation within the Metadata class to avoid possible
  // concurrency issues.
  /**
   * Increment the number of failed fetch attempts, increase the backoff duration, set the backoff
   * end time to "backoff duration" after {@code lastFailedFetchTime} and persist the new values to
   * disk-backed metadata.
   */
  private void updateBackoffMetadataWithLastFailedFetchTime(Date lastFailedFetchTime) {
    int numFailedFetches = frcMetadata.getBackoffMetadata().getNumFailedFetches();

    numFailedFetches++;

    long backoffDurationInMillis = getRandomizedBackoffDurationInMillis(numFailedFetches);
    Date backoffEndTime = new Date(lastFailedFetchTime.getTime() + backoffDurationInMillis);

    frcMetadata.setBackoffMetadata(numFailedFetches, backoffEndTime);
  }

  /**
   * Returns a random backoff duration from the range {@code timeoutDuration} +/- 50% of {@code
   * timeoutDuration}, where {@code timeoutDuration = }{@link
   * #BACKOFF_TIME_DURATIONS_IN_MINUTES}{@code [numFailedFetches-1]}.
   */
  private long getRandomizedBackoffDurationInMillis(int numFailedFetches) {
    // The backoff duration length after numFailedFetches.
    long timeOutDurationInMillis =
        MINUTES.toMillis(
            BACKOFF_TIME_DURATIONS_IN_MINUTES[
                Math.min(numFailedFetches, BACKOFF_TIME_DURATIONS_IN_MINUTES.length) - 1]);

    // A random duration that is in the range: timeOutDuration +/- 50% of timeOutDuration.
    return timeOutDurationInMillis / 2 + randomGenerator.nextInt((int) timeOutDurationInMillis);
  }

  /**
   * Determines whether a given {@code httpStatusCode} should be throttled based on recent fetch
   * results.
   *
   * <p>A fetch is considered throttle-able if the {@code httpStatusCode} is {@link
   * #HTTP_TOO_MANY_REQUESTS}, or if the fetch is the second consecutive request to receive an
   * unavailable response from the server.
   *
   * <p>The two fetch requirement guards against the possibility of a transient error from the
   * server. In such cases, an immediate retry should fix the problem. If the retry also fails, then
   * the error is probably not transient and the client should enter exponential backoff mode.
   *
   * <p>So, unless the server explicitly responds with a throttled error, the client should not
   * throttle on the first throttle-able error from the server.
   *
   * @return True if the current fetch request should be throttled.
   */
  private boolean shouldThrottle(BackoffMetadata backoffMetadata, int httpStatusCode) {
    return backoffMetadata.getNumFailedFetches() > 1 || httpStatusCode == HTTP_TOO_MANY_REQUESTS;
  }

  /**
   * Updates last fetch status and last successful fetch time in FRC metadata based on the result of
   * {@code completedFetchTask}.
   */
  private void updateLastFetchStatusAndTime(
      Task<FetchResponse> completedFetchTask, Date fetchTime) {
    if (completedFetchTask.isSuccessful()) {
      frcMetadata.updateLastFetchAsSuccessfulAt(fetchTime);
      return;
    }

    Exception fetchException = completedFetchTask.getException();
    if (fetchException == null) {
      // Fetch was cancelled, which should never happen.
      return;
    }

    if (fetchException instanceof FirebaseRemoteConfigFetchThrottledException) {
      frcMetadata.updateLastFetchAsThrottled();
    } else {
      frcMetadata.updateLastFetchAsFailed();
    }
  }

  /**
   * Returns the list of user properties in Analytics. If the Analytics SDK is not available,
   * returns an empty list.
   */
  @WorkerThread
  private Map<String, String> getUserProperties() {
    Map<String, String> userPropertiesMap = new HashMap<>();
    if (analyticsConnector == null) {
      return userPropertiesMap;
    }

    for (Map.Entry<String, Object> userPropertyEntry :
        analyticsConnector.getUserProperties(/*includeInternal=*/ false).entrySet()) {
      userPropertiesMap.put(userPropertyEntry.getKey(), userPropertyEntry.getValue().toString());
    }
    return userPropertiesMap;
  }

  /** Used to verify that the fetch handler is getting Analytics as expected. */
  @VisibleForTesting
  @Nullable
  public AnalyticsConnector getAnalyticsConnector() {
    return analyticsConnector;
  }

  /**
   * The response of a fetch call that contains the configs fetched from the backend as well as
   * metadata about the fetch operation.
   */
  public static class FetchResponse {
    private final Date fetchTime;
    @Status private final int status;
    private final ConfigContainer fetchedConfigs;
    @Nullable private final String lastFetchETag;

    /** Creates a fetch response with the given parameters. */
    private FetchResponse(
        Date fetchTime,
        @Status int status,
        ConfigContainer fetchedConfigs,
        @Nullable String lastFetchETag) {
      this.fetchTime = fetchTime;
      this.status = status;
      this.fetchedConfigs = fetchedConfigs;
      this.lastFetchETag = lastFetchETag;
    }

    public static FetchResponse forBackendUpdatesFetched(
        ConfigContainer fetchedConfigs, String lastFetchETag) {
      return new FetchResponse(
          fetchedConfigs.getFetchTime(),
          Status.BACKEND_UPDATES_FETCHED,
          fetchedConfigs,
          lastFetchETag);
    }

    public static FetchResponse forBackendHasNoUpdates(Date fetchTime) {
      return new FetchResponse(
          fetchTime,
          Status.BACKEND_HAS_NO_UPDATES,
          /*fetchedConfigs=*/ null,
          /*lastFetchETag=*/ null);
    }

    public static FetchResponse forLocalStorageUsed(Date fetchTime) {
      return new FetchResponse(
          fetchTime, Status.LOCAL_STORAGE_USED, /*fetchedConfigs=*/ null, /*lastFetchETag=*/ null);
    }

    Date getFetchTime() {
      return fetchTime;
    }

    @Nullable
    String getLastFetchETag() {
      return lastFetchETag;
    }

    @Status
    int getStatus() {
      return status;
    }

    /**
     * Returns the configs fetched from the backend, or {@code null} if the backend wasn't called or
     * there were no updates from the backend.
     */
    public ConfigContainer getFetchedConfigs() {
      return fetchedConfigs;
    }

    /** The response status of a fetch operation. */
    @IntDef({
      Status.BACKEND_UPDATES_FETCHED,
      Status.BACKEND_HAS_NO_UPDATES,
      Status.LOCAL_STORAGE_USED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Status {
      int BACKEND_UPDATES_FETCHED = 0;
      int BACKEND_HAS_NO_UPDATES = 1;
      int LOCAL_STORAGE_USED = 2;
    }
  }
}
