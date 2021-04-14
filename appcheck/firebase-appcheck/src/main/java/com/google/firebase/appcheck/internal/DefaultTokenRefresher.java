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
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.appcheck.AppCheckTokenResult;
import com.google.firebase.appcheck.internal.util.Logger;
import java.util.concurrent.Executors;
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
  private final ScheduledExecutorService scheduledExecutorService;

  private volatile ScheduledFuture<?> refreshFuture;
  private volatile long delayAfterFailureSeconds;

  DefaultTokenRefresher(@NonNull DefaultFirebaseAppCheck firebaseAppCheck) {
    this(checkNotNull(firebaseAppCheck), Executors.newScheduledThreadPool(/* corePoolSize= */ 1));
  }

  @VisibleForTesting
  DefaultTokenRefresher(
      DefaultFirebaseAppCheck firebaseAppCheck, ScheduledExecutorService scheduledExecutorService) {
    this.firebaseAppCheck = firebaseAppCheck;
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
    Task<AppCheckTokenResult> task = firebaseAppCheck.fetchTokenFromProvider();
    task.addOnCompleteListener(
        new OnCompleteListener<AppCheckTokenResult>() {
          @Override
          public void onComplete(@NonNull Task<AppCheckTokenResult> task) {
            if (task.isSuccessful()) {
              AppCheckTokenResult tokenResult = task.getResult();
              if (tokenResult.getError() != null) {
                scheduleRefreshAfterFailure();
              }
            } else {
              // Task was not successful; this should not happen.
              Logger.getLogger().e("Unexpected failure while fetching token.");
            }
          }
        });
  }

  /** Cancels the in-flight scheduled refresh. */
  public void cancel() {
    if (refreshFuture != null && !refreshFuture.isDone()) {
      refreshFuture.cancel(/* mayInterruptIfRunning= */ false);
    }
  }
}
