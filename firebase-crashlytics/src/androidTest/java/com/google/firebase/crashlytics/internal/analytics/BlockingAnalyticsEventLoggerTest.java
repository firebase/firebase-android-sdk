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

import static com.google.firebase.crashlytics.internal.analytics.BlockingAnalyticsEventLogger.APP_EXCEPTION_EVENT_NAME;
import static org.junit.Assert.*;

import android.os.Bundle;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class BlockingAnalyticsEventLoggerTest {

  private static final int SHORT_TIMEOUT = 500;
  private static final int LONG_TIMEOUT = 5000;

  @Mock private CrashlyticsOriginAnalyticsEventLogger mockLogger;

  private BlockingAnalyticsEventLogger blockingAnalyticsEventLogger;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testBlockingAnalyticsEventLoggerCompletesAfterTimeoutWhenNoEventReturned() {
    final String eventName = "event";
    final Bundle eventBundle = new Bundle();
    blockingAnalyticsEventLogger =
        new BlockingAnalyticsEventLogger(mockLogger, SHORT_TIMEOUT, TimeUnit.MILLISECONDS);
    blockingAnalyticsEventLogger.logEvent(eventName, eventBundle);
    Mockito.verify(mockLogger).logEvent(eventName, eventBundle);
    assertFalse(blockingAnalyticsEventLogger.isCallbackReceived());
  }

  @Test
  public void
      testBlockingAnalyticsEventLoggerCompletesWhenOnEventCalledWithAppExceptionFromSeparateThread() {
    final Bundle eventBundle = new Bundle();
    Mockito.doAnswer(
            invocation -> {
              callOnEventOnBackgroundThread(
                  blockingAnalyticsEventLogger, APP_EXCEPTION_EVENT_NAME, eventBundle);
              return null;
            })
        .when(mockLogger)
        .logEvent(APP_EXCEPTION_EVENT_NAME, eventBundle);
    blockingAnalyticsEventLogger =
        new BlockingAnalyticsEventLogger(mockLogger, LONG_TIMEOUT, TimeUnit.MILLISECONDS);
    blockingAnalyticsEventLogger.logEvent(APP_EXCEPTION_EVENT_NAME, eventBundle);
    Mockito.verify(mockLogger).logEvent(APP_EXCEPTION_EVENT_NAME, eventBundle);
    assertTrue(blockingAnalyticsEventLogger.isCallbackReceived());
  }

  private static void callOnEventOnBackgroundThread(
      BlockingAnalyticsEventLogger logger, String name, Bundle params) {
    Executor ex = Executors.newFixedThreadPool(1);
    ex.execute(
        () -> {
          logger.onEvent(name, params);
        });
  }
}
