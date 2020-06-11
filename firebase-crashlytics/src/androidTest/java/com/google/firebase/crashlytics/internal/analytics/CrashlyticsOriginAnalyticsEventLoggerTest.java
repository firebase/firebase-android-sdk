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

import static com.google.firebase.crashlytics.internal.analytics.CrashlyticsOriginAnalyticsEventLogger.FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS;
import static org.junit.Assert.*;

import android.os.Bundle;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class CrashlyticsOriginAnalyticsEventLoggerTest {

  @Mock private AnalyticsConnector mockAnalyticsConnector;

  private CrashlyticsOriginAnalyticsEventLogger logger;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    logger = new CrashlyticsOriginAnalyticsEventLogger(mockAnalyticsConnector);
  }

  @Test
  public void testLoggerCallsAnalyticsConnectorWithCrashlyticsOrigin() {
    final String eventName = "_ae";
    final Bundle params = new Bundle();
    logger.logEvent(eventName, params);
    Mockito.verify(mockAnalyticsConnector, Mockito.times(1))
        .logEvent(FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS, eventName, params);
  }
}
