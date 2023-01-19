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

import android.app.Application;
import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.common.api.internal.BackgroundDetector;
import com.google.android.gms.common.api.internal.BackgroundDetector.BackgroundStateChangeListener;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appcheck.AppCheckToken;
import com.google.firebase.appcheck.internal.util.Clock;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/** Class to manage whether or not to schedule an {@link AppCheckToken} refresh attempt. */
public final class TokenRefreshManager {

  private static final long REFRESH_BUFFER_ABSOLUTE_MILLIS = 60 * 1000; // 60 seconds
  private static final double REFRESH_BUFFER_FRACTION = 0.5;
  private static final long FIVE_MINUTES_IN_MILLIS = 5 * 60 * 1000;
  private static final long UNSET_REFRESH_TIME = -1;

  private final DefaultTokenRefresher tokenRefresher;
  private final Clock clock;

  private volatile boolean isBackgrounded;
  private volatile int currentListenerCount;
  private volatile long nextRefreshTimeMillis;
  private volatile boolean isAutoRefreshEnabled;

  TokenRefreshManager(
      @NonNull Context context,
      @NonNull DefaultFirebaseAppCheck firebaseAppCheck,
      @Lightweight Executor liteExecutor,
      @Blocking ScheduledExecutorService scheduledExecutorService) {
    this(
        checkNotNull(context),
        new DefaultTokenRefresher(
            checkNotNull(firebaseAppCheck), liteExecutor, scheduledExecutorService),
        new Clock.DefaultClock());
  }

  @VisibleForTesting
  TokenRefreshManager(Context context, DefaultTokenRefresher tokenRefresher, Clock clock) {
    this.tokenRefresher = tokenRefresher;
    this.clock = clock;
    this.nextRefreshTimeMillis = UNSET_REFRESH_TIME;

    BackgroundDetector.initialize((Application) context.getApplicationContext());
    BackgroundDetector.getInstance()
        .addListener(
            new BackgroundStateChangeListener() {
              @Override
              public void onBackgroundStateChanged(boolean background) {
                isBackgrounded = background;
                if (background) {
                  tokenRefresher.cancel();
                } else {
                  if (shouldScheduleRefresh()) {
                    tokenRefresher.scheduleRefresh(
                        nextRefreshTimeMillis - clock.currentTimeMillis());
                  }
                }
              }
            });
  }

  /**
   * Calculates the next refresh time and schedules a refresh of the {@link AppCheckToken} if the
   * necessary conditions are met.
   */
  public void maybeScheduleTokenRefresh(@NonNull AppCheckToken token) {
    DefaultAppCheckToken defaultToken;
    if (token instanceof DefaultAppCheckToken) {
      defaultToken = (DefaultAppCheckToken) token;
    } else {
      defaultToken = DefaultAppCheckToken.constructFromRawToken(token.getToken());
    }

    // The next refresh time is receivedAt + 0.5*expiresIn + 5 minutes.
    nextRefreshTimeMillis =
        defaultToken.getReceivedAtTimestamp()
            + (long) (REFRESH_BUFFER_FRACTION * defaultToken.getExpiresInMillis())
            + FIVE_MINUTES_IN_MILLIS;
    if (nextRefreshTimeMillis > defaultToken.getExpireTimeMillis()) {
      // This shouldn't happen, as the minimum allowed TTL should be at least 15 minutes, but adding
      // this check to be safe.
      nextRefreshTimeMillis = defaultToken.getExpireTimeMillis() - REFRESH_BUFFER_ABSOLUTE_MILLIS;
    }
    if (shouldScheduleRefresh()) {
      tokenRefresher.scheduleRefresh(nextRefreshTimeMillis - clock.currentTimeMillis());
    }
  }

  public void onListenerCountChanged(int newListenerCount) {
    if (currentListenerCount == 0 && newListenerCount > 0) {
      currentListenerCount = newListenerCount;
      if (shouldScheduleRefresh()) {
        tokenRefresher.scheduleRefresh(nextRefreshTimeMillis - clock.currentTimeMillis());
      }
    } else if (currentListenerCount > 0 && newListenerCount == 0) {
      tokenRefresher.cancel();
    }
    currentListenerCount = newListenerCount;
  }

  public void setIsAutoRefreshEnabled(boolean isAutoRefreshEnabled) {
    this.isAutoRefreshEnabled = isAutoRefreshEnabled;
  }

  private boolean shouldScheduleRefresh() {
    return isAutoRefreshEnabled
        && !isBackgrounded
        && currentListenerCount > 0
        && nextRefreshTimeMillis != UNSET_REFRESH_TIME;
  }
}
