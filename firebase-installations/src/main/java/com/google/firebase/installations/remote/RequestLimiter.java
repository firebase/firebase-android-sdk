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
 * The {@link RequestLimiter} class calculates the next allowed request time. Also, decides whether
 * a network request to FIS servers is allowed to execute.
 *
 * @hide
 */
class RequestLimiter {
  private static final long BACKOFF_TIME_24H_IN_MILLIS = TimeUnit.HOURS.toMillis(24);
  private static final long BACKOFF_TIME_30_MINS_IN_MILLIS = TimeUnit.MINUTES.toMillis(30);
  private final Utils utils;

  @GuardedBy("this")
  private long nextRequestTime;

  @GuardedBy("this")
  private int attemptCount;

  RequestLimiter(Utils utils) {
    this.utils = utils;
  }

  public synchronized void setNextRequestTime(int responseCode) {
    if (isSuccessful(responseCode)) {
      resetAttemptCount();
      return;
    }
    attemptCount++;
    long backOffTime = getBackoffDuration(responseCode);
    nextRequestTime = utils.currentTimeInMillis() + backOffTime;
  }

  private synchronized void resetAttemptCount() {
    attemptCount = 0;
  }

  private synchronized long getBackoffDuration(int responseCode) {
    // Fixed 24 hours silence period for non-retryable errors. Read more: b/160751425.
    if (!isRetryableError(responseCode)) {
      return BACKOFF_TIME_24H_IN_MILLIS;
    }
    // Quickly increasing dynamically configured back-off strategy for Retryable errors. Read more:
    // https://cloud.google.com/storage/docs/exponential-backoff.
    return (long)
        Math.min(
            Math.pow(2, attemptCount) + utils.getRandomMillis(), BACKOFF_TIME_30_MINS_IN_MILLIS);
  }

  // Response codes classified as retryable for FIS API. Read more on FIS response codes:
  // go/fis-api-error-code-classification.
  private static boolean isRetryableError(int responseCode) {
    return responseCode == 429 || (responseCode >= 500 && responseCode < 600);
  }

  // Response codes classified as success for FIS API. Read more on FIS response codes:
  // go/fis-api-error-code-classification.
  private static boolean isSuccessful(int responseCode) {
    return responseCode >= 200 && responseCode < 300;
  }

  public boolean isRequestAllowed() {
    // NOTE: If the end-users changes the System time, requests to FIS servers will not be allowed.
    // This problem can be fixed by restarting the app or the end-users changing System time.
    return attemptCount == 0 || utils.currentTimeInMillis() > nextRequestTime;
  }
}
