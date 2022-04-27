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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.when;

import com.google.firebase.installations.Utils;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link RequestLimiter}. */
@RunWith(RobolectricTestRunner.class)
public class RequestLimiterTest {
  private static final int NON_RETRYABLE_RESPONSE_CODE_EXAMPLE = 403;
  private static final int RETRYABLE_RESPONSE_CODE_EXAMPLE = 500;
  private static final int OK_RESPONSE_CODE_EXAMPLE = 200;
  private static final long CURRENT_TIME_IN_MILLIS = 100000L;
  private static final long NEXT_REQUEST_TIME_IN_MILLIS_LESSER_THAN_24H =
      CURRENT_TIME_IN_MILLIS + TimeUnit.HOURS.toMillis(2);
  private static final long NEXT_REQUEST_TIME_IN_MILLIS_GREATER_THAN_24H =
      CURRENT_TIME_IN_MILLIS + TimeUnit.HOURS.toMillis(25);

  @Mock private Utils mockUtils;
  private RequestLimiter requestLimiter;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
    requestLimiter = new RequestLimiter(mockUtils);
  }

  @Test
  public void nonRetryableError_lessThan24Hr_doesNotRetry() {
    when(mockUtils.currentTimeInMillis())
        .thenReturn(CURRENT_TIME_IN_MILLIS, NEXT_REQUEST_TIME_IN_MILLIS_LESSER_THAN_24H);
    requestLimiter.setNextRequestTime(NON_RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertFalse(requestLimiter.isRequestAllowed());
  }

  @Test
  public void nonRetryableError_greaterThan24Hr_retries() {
    when(mockUtils.currentTimeInMillis())
        .thenReturn(CURRENT_TIME_IN_MILLIS, NEXT_REQUEST_TIME_IN_MILLIS_GREATER_THAN_24H);
    requestLimiter.setNextRequestTime(NON_RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertTrue(requestLimiter.isRequestAllowed());
  }

  @Test
  public void retryableError_greaterThanNextRequestTime_retries() {
    when(mockUtils.getRandomDelayForSyncPrevention()).thenReturn(TimeUnit.MINUTES.toMillis(2));
    when(mockUtils.currentTimeInMillis())
        .thenReturn(CURRENT_TIME_IN_MILLIS, addMinutesToCurrentTime(5));
    requestLimiter.setNextRequestTime(RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertTrue(requestLimiter.isRequestAllowed());
  }

  @Test
  public void retryableError_lesserThanNextRequestTime_doesNotRetry() {
    when(mockUtils.getRandomDelayForSyncPrevention()).thenReturn(TimeUnit.MINUTES.toMillis(6));
    when(mockUtils.currentTimeInMillis())
        .thenReturn(CURRENT_TIME_IN_MILLIS, addMinutesToCurrentTime(5));
    requestLimiter.setNextRequestTime(RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertFalse(requestLimiter.isRequestAllowed());
  }

  @Test
  public void retryableError_exponentialBackoff_retries() {
    when(mockUtils.getRandomDelayForSyncPrevention()).thenReturn(TimeUnit.MINUTES.toMillis(5));
    when(mockUtils.currentTimeInMillis())
        .thenReturn(
            CURRENT_TIME_IN_MILLIS,
            addMinutesToCurrentTime(5),
            addMinutesToCurrentTime(6),
            addMinutesToCurrentTime(12));
    requestLimiter.setNextRequestTime(RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertFalse(requestLimiter.isRequestAllowed());
    requestLimiter.setNextRequestTime(RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertTrue(requestLimiter.isRequestAllowed());
  }

  @Test
  public void nonRetryableError_followedBySuccess_resetsRequestTime() {
    when(mockUtils.currentTimeInMillis()).thenReturn(CURRENT_TIME_IN_MILLIS);
    requestLimiter.setNextRequestTime(NON_RETRYABLE_RESPONSE_CODE_EXAMPLE);
    assertFalse(requestLimiter.isRequestAllowed());
    requestLimiter.setNextRequestTime(OK_RESPONSE_CODE_EXAMPLE);
    assertTrue(requestLimiter.isRequestAllowed());
  }

  // Adds specified minutes to the CURRENT_TIME_IN_MILLIS.
  private long addMinutesToCurrentTime(int minutes) {
    return CURRENT_TIME_IN_MILLIS + TimeUnit.MINUTES.toMillis(minutes);
  }
}
