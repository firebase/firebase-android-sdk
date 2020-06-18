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

package com.google.firebase.crashlytics.internal.analytics;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.crashlytics.internal.Logger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Analytics event logger which logs an event to Firebase Analytics using the Crashlytics origin,
 * and blocks until it receives that event back from the Analytics Event Receiver, or until the
 * timeout has elapsed.
 */
public class BlockingAnalyticsEventLogger implements AnalyticsEventReceiver, AnalyticsEventLogger {

  static final String APP_EXCEPTION_EVENT_NAME = "_ae";

  private final CrashlyticsOriginAnalyticsEventLogger baseAnalyticsEventLogger;
  private final int timeout;
  private final TimeUnit timeUnit;

  private final Object latchLock = new Object();

  private CountDownLatch eventLatch;
  private boolean callbackReceived = false;

  public BlockingAnalyticsEventLogger(
      @NonNull CrashlyticsOriginAnalyticsEventLogger baseAnalyticsEventLogger,
      int timeout,
      TimeUnit timeUnit) {
    this.baseAnalyticsEventLogger = baseAnalyticsEventLogger;
    this.timeout = timeout;
    this.timeUnit = timeUnit;
  }

  @Override
  public void logEvent(@NonNull String name, @Nullable Bundle params) {
    synchronized (latchLock) {
      Logger.getLogger().d("Logging Crashlytics event to Firebase");
      this.eventLatch = new CountDownLatch(1);
      this.callbackReceived = false;

      baseAnalyticsEventLogger.logEvent(name, params);

      Logger.getLogger().d("Awaiting app exception callback from FA...");
      try {
        if (eventLatch.await(timeout, timeUnit)) {
          callbackReceived = true;
          Logger.getLogger().d("App exception callback received from FA listener.");
        } else {
          Logger.getLogger()
              .d("Timeout exceeded while awaiting app exception callback from FA listener.");
        }
      } catch (InterruptedException ie) {
        Logger.getLogger().d("Interrupted while awaiting app exception callback from FA listener.");
      }

      this.eventLatch = null;
    }
  }

  /** Must be called on a different thread than logEvent. */
  @Override
  public void onEvent(@NonNull String name, @NonNull Bundle params) {
    final CountDownLatch eventLatch = this.eventLatch;

    if (eventLatch == null) {
      return;
    }

    if (APP_EXCEPTION_EVENT_NAME.equals(name)) {
      eventLatch.countDown();
    }
  }

  /* For testing */
  boolean isCallbackReceived() {
    return callbackReceived;
  }
}
