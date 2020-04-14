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

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.eq;

import android.os.Bundle;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

public class AnalyticsConnectorAppExceptionEventRecorderTest {

  @Mock private AnalyticsConnector mockAnalyticsConnector;

  private AnalyticsConnectorAppExceptionEventRecorder recorder;

  @Before
  public void setUp() throws Exception {
    MockitoAnnotations.initMocks(this);

    recorder = new AnalyticsConnectorAppExceptionEventRecorder(mockAnalyticsConnector);
  }

  @Test
  public void testRecorderRecordsProperEventToAnalyticsConnector() {
    final long timestamp = System.currentTimeMillis();

    recorder.recordAppExceptionEvent(timestamp);

    final ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
    Mockito.verify(mockAnalyticsConnector, Mockito.times(1))
        .logEvent(eq("clx"), eq("_ae"), bundleCaptor.capture());
    Bundle event = bundleCaptor.getValue();
    assertEquals(1, event.getInt("fatal"));
    assertEquals(timestamp, event.getLong("timestamp"));
  }
}
