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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.firebase.appcheck.internal.util.Clock;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TokenRefreshManagerTest {

  private static final String TOKEN_PAYLOAD = "tokenPayload";
  private static final long EXPIRES_IN_ONE_HOUR_MILLIS = 60L * 60L * 1000L;
  private static final long EXPIRE_IN_THREE_MINUTES_MILLIS = 3L * 60L * 1000L;
  private static final long THIRTY_FIVE_MINUTES_MILLIS = 35L * 60L * 1000L;
  private static final long TWO_MINUTES_MILLIS = 2L * 60L * 1000L;
  private static final long CURRENT_TIME_MILLIS = 1000L;

  @Mock DefaultTokenRefresher mockTokenRefresher;
  @Mock Clock mockClock;

  private TokenRefreshManager tokenRefreshManager;
  private DefaultAppCheckToken validAppCheckToken;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    when(mockClock.currentTimeMillis()).thenReturn(CURRENT_TIME_MILLIS);

    tokenRefreshManager =
        new TokenRefreshManager(
            ApplicationProvider.getApplicationContext(), mockTokenRefresher, mockClock);
    tokenRefreshManager.setIsAutoRefreshEnabled(/* isAutoRefreshEnabled= */ true);
    validAppCheckToken =
        new DefaultAppCheckToken(
            TOKEN_PAYLOAD, EXPIRES_IN_ONE_HOUR_MILLIS, mockClock.currentTimeMillis());

    // Sanity check
    assertThat(BackgroundDetector.getInstance().isInBackground()).isFalse();
  }

  @Test
  public void maybeScheduleTokenRefresh_useExpirationTimeFractionBuffer() {
    DefaultAppCheckToken expiresInOneHourToken =
        new DefaultAppCheckToken(
            TOKEN_PAYLOAD, EXPIRES_IN_ONE_HOUR_MILLIS, mockClock.currentTimeMillis());

    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 1);
    tokenRefreshManager.maybeScheduleTokenRefresh(expiresInOneHourToken);

    verify(mockTokenRefresher).scheduleRefresh(THIRTY_FIVE_MINUTES_MILLIS);
  }

  @Test
  public void maybeScheduleTokenRefresh_useExpirationTimeAbsoluteBuffer() {
    // This should not occur, as the minimum TTL should be at least 15 minutes, but this test case
    // checks to make sure that in the case we receive a shorter-than-expected TTL from the backend,
    // we handle it gracefully.
    DefaultAppCheckToken expiresInThreeMinutesToken =
        new DefaultAppCheckToken(
            TOKEN_PAYLOAD, EXPIRE_IN_THREE_MINUTES_MILLIS, mockClock.currentTimeMillis());

    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 1);
    tokenRefreshManager.maybeScheduleTokenRefresh(expiresInThreeMinutesToken);

    verify(mockTokenRefresher).scheduleRefresh(TWO_MINUTES_MILLIS);
  }

  @Test
  public void maybeScheduleTokenRefresh_noListeners_doesNotScheduleRefresh() {
    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 0);
    tokenRefreshManager.maybeScheduleTokenRefresh(validAppCheckToken);

    verify(mockTokenRefresher, never()).scheduleRefresh(anyLong());
  }

  @Test
  public void maybeScheduleTokenRefresh_autoRefreshDisabled_doesNotScheduleRefresh() {
    tokenRefreshManager.setIsAutoRefreshEnabled(/* isAutoRefreshEnabled= */ false);
    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 1);
    tokenRefreshManager.maybeScheduleTokenRefresh(validAppCheckToken);

    verify(mockTokenRefresher, never()).scheduleRefresh(anyLong());
  }

  @Test
  public void
      onListenerCountChanged_fromZeroToNonZero_withPreviousScheduleAttempt_schedulesRefresh() {
    tokenRefreshManager.maybeScheduleTokenRefresh(validAppCheckToken);
    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 1);

    verify(mockTokenRefresher).scheduleRefresh(anyLong());
  }

  @Test
  public void
      onListenerCountChanged_fromZeroToNonZero_noPreviousScheduleAttempt_doesNotScheduleRefresh() {
    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 1);

    verify(mockTokenRefresher, never()).scheduleRefresh(anyLong());
  }

  @Test
  public void onListenerCountChanged_fromNonZeroToZero_cancelsRefresh() {
    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 1);
    tokenRefreshManager.maybeScheduleTokenRefresh(validAppCheckToken);
    tokenRefreshManager.onListenerCountChanged(/* newListenerCount= */ 0);

    verify(mockTokenRefresher).cancel();
  }
}
