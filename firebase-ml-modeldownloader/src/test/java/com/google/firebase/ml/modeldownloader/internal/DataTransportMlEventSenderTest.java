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

package com.google.firebase.ml.modeldownloader.internal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.EventName;
import com.google.firebase.ml.modeldownloader.internal.FirebaseMlLogEvent.SystemInfo;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class DataTransportMlEventSenderTest {

  @Mock private TransportFactory mockTransportFactory;
  @Mock private Transport<FirebaseMlLogEvent> mockTransport;

  private DataTransportMlEventSender statsSender;

  private static final SystemInfo SYSTEM_INFO =
      SystemInfo.builder()
          .setAppId("FakeAppId6578")
          .setAppVersion("0.1.0")
          .setApiKey("fakeKey5645")
          .setFirebaseProjectId("FakeFirebaseId")
          .setMlSdkVersion("3.4.5")
          .build();

  @Before
  public void setup() {
    MockitoAnnotations.initMocks(this);
    when(mockTransportFactory.getTransport(
            any(), ArgumentMatchers.<Class<FirebaseMlLogEvent>>any(), any(), any()))
        .thenReturn(mockTransport);
    statsSender = new DataTransportMlEventSender(() -> mockTransportFactory);
  }

  @Test
  public void testSendStatsSuccessful() {
    doNothing().when(mockTransport).send(any());

    final FirebaseMlLogEvent stat1 =
        FirebaseMlLogEvent.builder()
            .setSystemInfo(SYSTEM_INFO)
            .setEventName(EventName.MODEL_UPDATE)
            .build();
    final FirebaseMlLogEvent stat2 =
        FirebaseMlLogEvent.builder()
            .setSystemInfo(SYSTEM_INFO)
            .setEventName(EventName.MODEL_DOWNLOAD)
            .build();

    statsSender.sendEvent(stat1);
    statsSender.sendEvent(stat2);

    verify(mockTransport, times(2)).send(any());
  }
}
