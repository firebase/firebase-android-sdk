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

import static com.google.android.gms.common.internal.Preconditions.checkNotNull;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;

/**
 * Class to proactively refresh {@link com.google.firebase.appcheck.AppCheckToken}s before they
 * expire and handle retry attempts should the refresh fail.
 */
public class DefaultTokenRefresher {

  private static final long UNSET_DELAY = -1;
  @VisibleForTesting static final long INITIAL_DELAY_SECONDS = 30;
  @VisibleForTesting static final long MAX_DELAY_SECONDS = 16 * 60; // 16 minutes

  private final DefaultFirebaseAppCheck firebaseAppCheck;
  private final Executor liteExecutor;
  private final ScheduledExecutorService scheduledExecutorService;

  private volatile ScheduledFuture<?> refreshFuture;
  private volatile long delayAfterFailureSeconds;

  DefaultTokenRefresher(
      @NonNull DefaultFirebaseAppCheck firebaseAppCheck,
      @Lightweight Executor liteExecutor,
      @Blocking ScheduledExecutorService scheduledExecutorService) {
    this.firebaseAppCheck = checkNotNull(firebaseAppCheck);
    this.liteExecutor = liteExecutor;
    this.scheduledExecutorService = scheduledExecutorService;
    this.delayAfterFailureSeconds = UNSET_DELAY;
  }

  /**
   * Schedules an {@link com.google.firebase.appcheck.AppCheckToken} refresh after the given delay.
   *
   * @param timeToRefreshMillis the delay before attempting to refresh the {@link
   *     com.google.firebase.appcheck.AppCheckToken}
   */
  public void scheduleRefresh(long timeToRefreshMillis) {
    // Cancel any previously scheduled refresh attempt.
    cancel();
    delayAfterFailureSeconds = UNSET_DELAY;
    refreshFuture =
        scheduledExecutorService.schedule(
            this::onRefresh, Math.max(0, timeToRefreshMillis), MILLISECONDS);
  }

  /**
   * Schedules a retry attempt to refresh the {@link com.google.firebase.appcheck.AppCheckToken}
   * with exponential backoff.
   */
  private void scheduleRefreshAfterFailure() {
    // Cancel any previously scheduled refresh.
    cancel();
    delayAfterFailureSeconds = getNextRefreshMillis();
    refreshFuture =
        scheduledExecutorService.schedule(this::onRefresh, delayAfterFailureSeconds, SECONDS);
  }

  private long getNextRefreshMillis() {
    if (delayAfterFailureSeconds == UNSET_DELAY) {
      return INITIAL_DELAY_SECONDS;
    } else if (delayAfterFailureSeconds * 2 < MAX_DELAY_SECONDS) {
      return delayAfterFailureSeconds * 2;
    } else {
      return MAX_DELAY_SECONDS;
    }
  }

  private void onRefresh() {
    firebaseAppCheck
        .fetchTokenFromProvider()
        .addOnFailureListener(liteExecutor, e -> scheduleRefreshAfterFailure());
  }

  /** Cancels the in-flight scheduled refresh. */
  public void cancel() {
    if (refreshFuture != null && !refreshFuture.isDone()) {
      refreshFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
  }
}
