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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.SystemClock;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.SchedulerConfig;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import java.nio.charset.Charset;
import java.util.Random;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class SchedulerIntegrationTest {
  private static final String TEST_KEY = "test";
  private static final String TEST_VALUE = "test-value";
  private static final String testTransport = "testTransport";
  private final TransportInternal transportInternalMock = mock(TransportInternal.class);
  private final TransportBackend mockBackend = mock(TransportBackend.class);
  private final TransportBackend mockBackend2 = mock(TransportBackend.class);
  private final BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private final Context context = InstrumentationRegistry.getInstrumentation().getContext();
  private final Uploader mockUploader = mock(Uploader.class);
  private TransportRuntime runtime;

  @Rule
  public final TransportRuntimeRule runtimeRule =
      new TransportRuntimeRule(
          DaggerTestRuntimeComponent.builder()
              .setApplicationContext(context)
              .setUploader(mockUploader)
              .setBackendRegistry(mockRegistry)
              .setSchedulerConfig(
                  SchedulerConfig.builder()
                      .setDelta(500)
                      .setMaxAllowedTime(100000)
                      .setMaximumDelay(500)
                      .build())
              .setEventClock(() -> 3)
              .setUptimeClock(() -> 1)
              .build());

  @Before
  public void setUp() {
    when(mockBackend.decorate(any()))
        .thenAnswer(
            (Answer<EventInternal>)
                invocation ->
                    invocation
                        .<EventInternal>getArgument(0)
                        .toBuilder()
                        .addMetadata(TEST_KEY, TEST_VALUE)
                        .build());
    when(mockBackend2.decorate(any()))
        .thenAnswer(
            (Answer<EventInternal>)
                invocation ->
                    invocation
                        .<EventInternal>getArgument(0)
                        .toBuilder()
                        .addMetadata(TEST_KEY, TEST_VALUE)
                        .build());
    runtime = TransportRuntime.getInstance();
  }

  private String generateBackendName() {
    byte[] array = new byte[8]; // length is bounded by 8
    new Random().nextBytes(array);
    return new String(array, Charset.forName("UTF-8"));
  }

  @Test
  public void scheduler_whenEventScheduledForFirstTime_shouldUploadEventWithinExpectedTime() {

    String mockBackendName = generateBackendName();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setPayload("TelemetryData".getBytes())
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    SystemClock.sleep(5000);
    verify(mockUploader, times(1)).upload(eq(mockBackendName), eq(1), any());
  }

  @Test
  public void scheduler_whenEventsScheduledWithSametBackend_shouldUploadOnce() {
    String mockBackendName = generateBackendName();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setPayload("TelemetryData".getBytes())
            .build();
    Event<String> stringEvent2 = Event.ofTelemetry("TelemetryData2");
    EventInternal expectedEvent2 =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setPayload("TelemetryData2".getBytes())
            .build();
    transport.send(stringEvent);
    transport.send(stringEvent2);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockBackend, times(1)).decorate(eq(expectedEvent2));
    SystemClock.sleep(5000);
    verify(mockUploader, times(1)).upload(eq(mockBackendName), eq(1), any());
  }

  @Test
  public void scheduler_whenEventsScheduledWithDifferentBackends_shouldUploadTwice() {
    String firstBackendName = generateBackendName();
    String secondBackendName = generateBackendName();
    when(mockRegistry.get(firstBackendName)).thenReturn(mockBackend);
    when(mockRegistry.get(secondBackendName)).thenReturn(mockBackend2);
    TransportFactory factory = runtime.newFactory(firstBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setPayload("TelemetryData".getBytes())
            .build();
    transport.send(stringEvent);
    TransportFactory factory2 = runtime.newFactory(secondBackendName);
    Transport<String> transport2 =
        factory2.getTransport(testTransport, String.class, String::getBytes);
    transport2.send(stringEvent);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockBackend2, times(1)).decorate(eq(expectedEvent));
    SystemClock.sleep(5000);
    verify(mockUploader, times(2)).upload(any(), eq(1), any());
  }
}
