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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.google.android.gms.tasks.Tasks;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.firebase.appcheck.AppCheckToken;
import java.util.concurrent.ScheduledExecutorService;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.LooperMode;

@RunWith(RobolectricTestRunner.class)
@LooperMode(LooperMode.Mode.LEGACY)
public class DefaultTokenRefresherTest {

  private static final long TIME_TO_REFRESH_MILLIS = 1000L;
  private static final long SIXTY_SECONDS = 60L;
  private static final long TWO_MINUTES_SECONDS = 2L * 60L;
  private static final long FOUR_MINUTES_SECONDS = 4L * 60L;
  private static final long EIGHT_MINUTES_SECONDS = 8L * 60L;

  @Mock DefaultFirebaseAppCheck mockFirebaseAppCheck;
  @Mock ScheduledExecutorService mockScheduledExecutorService;
  @Mock AppCheckToken mockAppCheckToken;

  private DefaultTokenRefresher defaultTokenRefresher;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);

    when(mockFirebaseAppCheck.fetchTokenFromProvider())
        .thenReturn(Tasks.forResult(mockAppCheckToken));

    // TODO(b/258273630): Use TestOnlyExecutors.
    defaultTokenRefresher =
        new DefaultTokenRefresher(
            mockFirebaseAppCheck, MoreExecutors.directExecutor(), mockScheduledExecutorService);
  }

  @Test
  public void scheduleRefresh_success() {
    defaultTokenRefresher.scheduleRefresh(TIME_TO_REFRESH_MILLIS);

    ArgumentCaptor<Runnable> onRefreshCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(TIME_TO_REFRESH_MILLIS), eq(MILLISECONDS));
    onRefreshCaptor.getValue().run();

    verify(mockFirebaseAppCheck).fetchTokenFromProvider();
    verifyNoMoreInteractions(mockScheduledExecutorService);
  }

  @Test
  public void scheduleRefresh_taskFails_schedulesRefreshAfterFailure() {
    when(mockFirebaseAppCheck.fetchTokenFromProvider())
        .thenReturn(Tasks.forException(new Exception()));
    defaultTokenRefresher.scheduleRefresh(TIME_TO_REFRESH_MILLIS);

    ArgumentCaptor<Runnable> onRefreshCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(TIME_TO_REFRESH_MILLIS), eq(MILLISECONDS));
    onRefreshCaptor.getValue().run();

    verify(mockFirebaseAppCheck).fetchTokenFromProvider();
    verify(mockScheduledExecutorService)
        .schedule(
            any(Runnable.class), eq(DefaultTokenRefresher.INITIAL_DELAY_SECONDS), eq(SECONDS));
    verifyNoMoreInteractions(mockScheduledExecutorService);
  }

  @Test
  public void scheduleRefreshAfterFailure_exponentialBackoff() {
    when(mockFirebaseAppCheck.fetchTokenFromProvider())
        .thenReturn(Tasks.forException(new Exception()));
    defaultTokenRefresher.scheduleRefresh(TIME_TO_REFRESH_MILLIS);

    ArgumentCaptor<Runnable> onRefreshCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(TIME_TO_REFRESH_MILLIS), eq(MILLISECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(
            onRefreshCaptor.capture(),
            eq(DefaultTokenRefresher.INITIAL_DELAY_SECONDS),
            eq(SECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(SIXTY_SECONDS), eq(SECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(TWO_MINUTES_SECONDS), eq(SECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(FOUR_MINUTES_SECONDS), eq(SECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(EIGHT_MINUTES_SECONDS), eq(SECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(
            onRefreshCaptor.capture(), eq(DefaultTokenRefresher.MAX_DELAY_SECONDS), eq(SECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService, times(2))
        .schedule(any(Runnable.class), eq(DefaultTokenRefresher.MAX_DELAY_SECONDS), eq(SECONDS));

    verifyNoMoreInteractions(mockScheduledExecutorService);
  }

  @Test
  public void scheduleRefresh_resetsDelay() {
    when(mockFirebaseAppCheck.fetchTokenFromProvider())
        .thenReturn(Tasks.forException(new Exception()));
    defaultTokenRefresher.scheduleRefresh(TIME_TO_REFRESH_MILLIS);

    ArgumentCaptor<Runnable> onRefreshCaptor = ArgumentCaptor.forClass(Runnable.class);
    verify(mockScheduledExecutorService)
        .schedule(onRefreshCaptor.capture(), eq(TIME_TO_REFRESH_MILLIS), eq(MILLISECONDS));
    onRefreshCaptor.getValue().run();
    verify(mockScheduledExecutorService)
        .schedule(
            any(Runnable.class), eq(DefaultTokenRefresher.INITIAL_DELAY_SECONDS), eq(SECONDS));

    // Schedule a new refresh attempt.
    defaultTokenRefresher.scheduleRefresh(TIME_TO_REFRESH_MILLIS);
    verify(mockScheduledExecutorService, times(2))
        .schedule(onRefreshCaptor.capture(), eq(TIME_TO_REFRESH_MILLIS), eq(MILLISECONDS));
    onRefreshCaptor.getValue().run();

    // The backoff delay is reset.
    verify(mockScheduledExecutorService, times(2))
        .schedule(
            any(Runnable.class), eq(DefaultTokenRefresher.INITIAL_DELAY_SECONDS), eq(SECONDS));
    verifyNoMoreInteractions(mockScheduledExecutorService);
  }
}
