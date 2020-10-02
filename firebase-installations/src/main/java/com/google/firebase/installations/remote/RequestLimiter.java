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

package com.google.firebase.installations.remote;

import androidx.annotation.GuardedBy;
import com.google.firebase.installations.Utils;
import java.util.concurrent.TimeUnit;

/**
 * The {@link RequestLimiter} class calculates the next request time. Also, decides whether a
 * network request to FIS servers is allowed to execute.
 *
 * @hide
 */
class RequestLimiter {
  private static final long MAXIMUM_BACKOFF_DURATION_FOR_CONFIGURATION_ERRORS =
      TimeUnit.HOURS.toMillis(24);
  private static final long MAXIMUM_BACKOFF_DURATION_FOR_SERVER_ERRORS =
      TimeUnit.MINUTES.toMillis(30);
  private final Utils utils;

  @GuardedBy("this")
  private long nextRequestTime;

  @GuardedBy("this")
  private int attemptCount;

  RequestLimiter(Utils utils) {
    // Util class is injected to ease mocking & testing the system time.
    this.utils = utils;
  }

  RequestLimiter() {
    // Util class is injected to ease mocking & testing the system time.
    this.utils = Utils.getInstance();
  }

  // Based on the response code, calculates the next request time to communicate with the FIS
  // servers.
  public synchronized void setNextRequestTime(int responseCode) {
    if (isSuccessfulOrRequiresNewFidCreation(responseCode)) {
      resetBackoffStrategy();
      return;
    }
    attemptCount++;
    long backOffTime = getBackoffDuration(responseCode);
    nextRequestTime = utils.currentTimeInMillis() + backOffTime;
  }

  private synchronized void resetBackoffStrategy() {
    attemptCount = 0;
  }

  private synchronized long getBackoffDuration(int responseCode) {
    // Fixed 24 hours silence period for non-retryable server errors. Read more: b/160751425.
    if (!isRetryableError(responseCode)) {
      return MAXIMUM_BACKOFF_DURATION_FOR_CONFIGURATION_ERRORS;
    }
    // Quickly increasing dynamically configured back-off strategy for Retryable errors. Read more:
    // https://cloud.google.com/storage/docs/exponential-backoff.
    return (long)
        Math.min(
            Math.pow(2, attemptCount) + utils.getRandomDelayForSyncPrevention(),
            MAXIMUM_BACKOFF_DURATION_FOR_SERVER_ERRORS);
  }

  // Response codes classified as retryable for FIS API. 5xx: Server errors and 429:
  // TOO_MANY_REQUESTS . Read more on FIS response codes: go/fis-api-error-code-classification.
  private static boolean isRetryableError(int responseCode) {
    return responseCode == 429 || (responseCode >= 500 && responseCode < 600);
  }

  // 2xx Response codes are classified as success for FIS API. Also, FIS GenerateAuthToken endpoint
  // responds with 401 & 404 for auth config errors which requires clients to follow up with a
  // request to create a new FID. So, we don't limit the next requests for 401 & 404 response codes
  // as well. Read more on FIS response codes: go/fis-api-error-code-classification.
  private static boolean isSuccessfulOrRequiresNewFidCreation(int responseCode) {
    return ((responseCode >= 200 && responseCode < 300)
        || responseCode == 401
        || responseCode == 404);
  }

  // Decides whether a network request to FIS servers is allowed to execute.
  public synchronized boolean isRequestAllowed() {
    // NOTE: If the end-users changes the System time, requests to FIS servers will not be allowed.
    // This problem can be fixed by restarting the app or the end-users changing System time.
    return attemptCount == 0 || utils.currentTimeInMillis() > nextRequestTime;
  }
}
