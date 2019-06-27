// Copyright 2018 Google LLC
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

package com.google.firebase.storage.internal;

import android.content.Context;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.gms.common.internal.Preconditions;
import com.google.android.gms.common.util.Clock;
import com.google.android.gms.common.util.DefaultClock;
import com.google.firebase.auth.internal.InternalAuthProvider;
import com.google.firebase.storage.network.NetworkRequest;
import java.util.Random;

/**
 * This is a Network request sender that uses exponential backoff, but also retries without backoff
 * if the network is unavailable in the client and instead uses simple polling. In both cases, the
 * retry time is capped by a setting which if exceeded will result in the task failing.
 */
public class ExponentialBackoffSender {
  private static final String TAG = "ExponenentialBackoff";

  public static final int RND_MAX = 250;
  private static final int NETWORK_STATUS_POLL_INTERVAL = 1000;
  private static final int MAXIMUM_WAIT_TIME_MILLI = 30000;
  private static final Random random = new Random();
  /*package*/ static Sleeper sleeper = new SleeperImpl();
  /*package*/ static Clock clock = DefaultClock.getInstance();
  private final Context context;
  @Nullable private final InternalAuthProvider authProvider;
  private long retryTime;
  private volatile boolean canceled;

  public ExponentialBackoffSender(
      Context context, @Nullable InternalAuthProvider authProvider, long retryTime) {
    this.context = context;
    this.authProvider = authProvider;
    this.retryTime = retryTime;
  }

  public boolean isRetryableError(int resultCode) {
    return (resultCode >= 500 && resultCode < 600)
        || resultCode == Util.NETWORK_UNAVAILABLE
        || resultCode == 429
        || resultCode == 408;
  }

  public void sendWithExponentialBackoff(@NonNull NetworkRequest request) {
    sendWithExponentialBackoff(request, true);
  }

  public void sendWithExponentialBackoff(
      @NonNull NetworkRequest request, final boolean closeRequest) {
    Preconditions.checkNotNull(request);
    long deadLine = clock.elapsedRealtime() + retryTime;
    if (closeRequest) {
      request.performRequest(Util.getCurrentAuthToken(authProvider), context);
    } else {
      request.performRequestStart(Util.getCurrentAuthToken(authProvider));
    }

    int currentSleepTime = NETWORK_STATUS_POLL_INTERVAL;
    while (clock.elapsedRealtime() + currentSleepTime <= deadLine
        && !request.isResultSuccess()
        && isRetryableError(request.getResultCode())) {

      try {
        sleeper.sleep(currentSleepTime + random.nextInt(RND_MAX));
      } catch (InterruptedException e) {
        Log.w(TAG, "thread interrupted during exponential backoff.");

        Thread.currentThread().interrupt();
        return;
      }
      if (currentSleepTime < MAXIMUM_WAIT_TIME_MILLI) {
        if (request.getResultCode() != Util.NETWORK_UNAVAILABLE) {
          currentSleepTime = currentSleepTime * 2;
          Log.w(TAG, "network error occurred, backing off/sleeping.");
        } else {
          currentSleepTime = NETWORK_STATUS_POLL_INTERVAL;
          Log.w(TAG, "network unavailable, sleeping.");
        }
      }

      if (canceled) {
        return;
      }
      request.reset();
      if (closeRequest) {
        request.performRequest(Util.getCurrentAuthToken(authProvider), context);
      } else {
        request.performRequestStart(Util.getCurrentAuthToken(authProvider));
      }
    }
  }

  public void cancel() {
    canceled = true;
  }

  public void reset() {
    canceled = false;
  }
}
