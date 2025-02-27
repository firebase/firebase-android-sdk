// Copyright 2021 Google LLC
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
import static com.google.firebase.appcheck.internal.RetryManager.BAD_REQUEST_ERROR_CODE;
import static com.google.firebase.appcheck.internal.RetryManager.MAX_EXP_BACKOFF_MILLIS;
import static com.google.firebase.appcheck.internal.RetryManager.NOT_FOUND_ERROR_CODE;
import static com.google.firebase.appcheck.internal.RetryManager.ONE_DAY_MILLIS;
import static com.google.firebase.appcheck.internal.RetryManager.ONE_SECOND_MILLIS;
import static com.google.firebase.appcheck.internal.RetryManager.UNSET_RETRY_TIME;
import static org.mockito.Mockito.when;

import com.google.firebase.appcheck.internal.util.Clock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link RetryManager}. */
@RunWith(RobolectricTestRunner.class)
public class RetryManagerTest {

  private static final long CURRENT_TIME_MILLIS = 1000L;
  private static final int SERVICE_UNAVAILABLE_ERROR_CODE = 503;
  private static final double MAX_JITTER_COEFFICIENT = 1.5;

  @Mock Clock mockClock;

  private RetryManager retryManager;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    when(mockClock.currentTimeMillis()).thenReturn(CURRENT_TIME_MILLIS);

    retryManager = new RetryManager(mockClock);
  }

  @Test
  public void updateBackoffOnFailure_badRequestError_oneDayRetryStrategy() {
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);

    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + ONE_DAY_MILLIS);
  }

  @Test
  public void updateBackoffOnFailure_notFoundError_oneDayRetryStrategy() {
    retryManager.updateBackoffOnFailure(NOT_FOUND_ERROR_CODE);

    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + ONE_DAY_MILLIS);
  }

  @Test
  public void updateBackoffOnFailure_oneDayRetryStrategy_multipleRetries() {
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);

    // The backoff period should not increase for consecutive failed retries with the ONE_DAY
    // strategy.
    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + ONE_DAY_MILLIS);
  }

  @Test
  public void updateBackoffOnFailure_exponentialRetryStrategy() {
    // 1st retry: ~2 seconds
    retryManager.updateBackoffOnFailure(SERVICE_UNAVAILABLE_ERROR_CODE);
    long backoffInMillis = retryManager.getNextRetryTimeMillis() - CURRENT_TIME_MILLIS;
    assertThat(backoffInMillis).isAtLeast(2 * ONE_SECOND_MILLIS);
    assertThat(backoffInMillis)
        .isAtMost((long) (Math.pow(2, 1 * MAX_JITTER_COEFFICIENT) * ONE_SECOND_MILLIS));

    // 2nd retry: ~4 seconds
    retryManager.updateBackoffOnFailure(SERVICE_UNAVAILABLE_ERROR_CODE);
    backoffInMillis = retryManager.getNextRetryTimeMillis() - CURRENT_TIME_MILLIS;
    assertThat(backoffInMillis).isAtLeast(4 * ONE_SECOND_MILLIS);
    assertThat(backoffInMillis)
        .isAtMost((long) (Math.pow(2, 2 * MAX_JITTER_COEFFICIENT) * ONE_SECOND_MILLIS));

    // 3rd retry: ~8 seconds
    retryManager.updateBackoffOnFailure(SERVICE_UNAVAILABLE_ERROR_CODE);
    backoffInMillis = retryManager.getNextRetryTimeMillis() - CURRENT_TIME_MILLIS;
    assertThat(backoffInMillis).isAtLeast(8 * ONE_SECOND_MILLIS);
    assertThat(backoffInMillis)
        .isAtMost((long) (Math.pow(2, 3 * MAX_JITTER_COEFFICIENT) * ONE_SECOND_MILLIS));

    // Retry 10 more times to bring the total number of retries to 13.
    for (int i = 0; i < 10; i++) {
      retryManager.updateBackoffOnFailure(SERVICE_UNAVAILABLE_ERROR_CODE);
    }
    backoffInMillis = retryManager.getNextRetryTimeMillis() - CURRENT_TIME_MILLIS;
    assertThat(backoffInMillis).isAtLeast(8192 * ONE_SECOND_MILLIS);
    assertThat(backoffInMillis)
        .isAtMost((long) (Math.pow(2, 13 * MAX_JITTER_COEFFICIENT) * ONE_SECOND_MILLIS));

    // Once we hit 14 retries, we return the maximum backoff interval, which is 4 hours.
    retryManager.updateBackoffOnFailure(SERVICE_UNAVAILABLE_ERROR_CODE);
    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + MAX_EXP_BACKOFF_MILLIS);

    // Verify that the backoff does not increase any further, as a sanity check.
    retryManager.updateBackoffOnFailure(SERVICE_UNAVAILABLE_ERROR_CODE);
    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + MAX_EXP_BACKOFF_MILLIS);
  }

  @Test
  public void canRetry_beforeNextRetryTime() {
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);

    // Sanity check.
    assertThat(mockClock.currentTimeMillis()).isEqualTo(CURRENT_TIME_MILLIS);
    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + ONE_DAY_MILLIS);

    assertThat(retryManager.canRetry()).isFalse();
  }

  @Test
  public void canRetry_afterNextRetryTime() {
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);
    long nextRetryMillis = retryManager.getNextRetryTimeMillis();

    when(mockClock.currentTimeMillis()).thenReturn(nextRetryMillis + 1);
    assertThat(retryManager.canRetry()).isTrue();
  }

  @Test
  public void resetBackoffOnSuccess() {
    retryManager.updateBackoffOnFailure(BAD_REQUEST_ERROR_CODE);
    // Sanity check.
    assertThat(retryManager.getNextRetryTimeMillis())
        .isEqualTo(CURRENT_TIME_MILLIS + ONE_DAY_MILLIS);

    retryManager.resetBackoffOnSuccess();
    assertThat(retryManager.getNextRetryTimeMillis()).isEqualTo(UNSET_RETRY_TIME);
    assertThat(retryManager.canRetry()).isTrue();
  }
}
