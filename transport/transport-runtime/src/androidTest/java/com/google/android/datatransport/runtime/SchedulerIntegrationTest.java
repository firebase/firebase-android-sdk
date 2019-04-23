// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.SchedulerConfig;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public class SchedulerIntegrationTest {
  private static final String TEST_KEY = "test";
  private static final String TEST_VALUE = "test-value";
  private static final String testTransport = "testTransport";
  private TransportInternal transportInternalMock = mock(TransportInternal.class);
  private TransportBackend mockBackend = mock(TransportBackend.class);
  private BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
  private final Uploader mockUploader = mock(Uploader.class);

  @Test
  public void firstAttemptSchedule() {
    int eventMillis = 3;
    int uptimeMillis = 1;
    String mockBackendName = "testBackend";
    TransportRuntime.setInstance(
        DaggerTestRuntimeComponent.builder()
            .setApplicationContext(context)
            .setUploader(mockUploader)
            .setBackendRegistry(mockRegistry)
            .setSchedulerConfig(new SchedulerConfig(500, 100000, 500))
            .setEventClock(() -> eventMillis)
            .setUptimeClock(() -> uptimeMillis)
            .build());
    TransportRuntime runtime = TransportRuntime.getInstance();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.decorate(any()))
        .thenAnswer(
            (Answer<EventInternal>)
                invocation ->
                    invocation
                        .<EventInternal>getArgument(0)
                        .toBuilder()
                        .addMetadata(TEST_KEY, TEST_VALUE)
                        .build());

    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(eventMillis)
            .setUptimeMillis(uptimeMillis)
            .setTransportName(testTransport)
            .setPayload("TelemetryData".getBytes())
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    try {
      TimeUnit.SECONDS.sleep(3);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    }
    verify(mockUploader, times(1)).upload(eq(mockBackendName), eq(1), any());
  }

  @Test
  public void twoBackendstwoEventsSchedule() {

  }

}
