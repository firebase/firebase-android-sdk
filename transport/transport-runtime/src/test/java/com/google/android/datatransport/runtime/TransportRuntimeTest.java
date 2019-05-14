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

package com.google.android.datatransport.runtime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.ImmediateScheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TransportRuntimeTest {
  private static final String TEST_KEY = "test";
  private static final String TEST_VALUE = "test-value";
  private TransportInternal transportInternalMock = mock(TransportInternal.class);
  private TransportBackend mockBackend = mock(TransportBackend.class);
  private BackendRegistry mockRegistry = mock(BackendRegistry.class);

  @Test
  public void testTransportInternalSend() {
    TransportContext transportContext =
        TransportContext.builder().setBackendName("backendMock").build();
    String testTransport = "testTransport";
    TransportFactory factory = new TransportFactoryImpl(transportContext, transportInternalMock);
    Event<String> event = Event.ofTelemetry("TelemetryData");
    Transformer<String, byte[]> transformer = String::getBytes;
    Transport<String> transport = factory.getTransport(testTransport, String.class, transformer);
    transport.send(event);
    SendRequest request =
        SendRequest.builder()
            .setTransportContext(transportContext)
            .setEvent(event, transformer)
            .setTransportName(testTransport)
            .build();
    verify(transportInternalMock, times(1)).send(request);
  }

  @Test
  public void testTransportRuntimeBackendDiscovery() {
    int eventMillis = 3;
    int uptimeMillis = 1;
    String mockBackendName = "backend";
    String testTransport = "testTransport";

    TransportRuntime runtime =
        new TransportRuntime(
            () -> eventMillis,
            () -> uptimeMillis,
            new ImmediateScheduler(Runnable::run, mockRegistry),
            new Uploader(null, null, null, null, null, null, () -> 2));
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
    Event<String> stringEvent = Event.ofTelemetry(12, "TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(eventMillis)
            .setUptimeMillis(uptimeMillis)
            .setTransportName(testTransport)
            .setPayload("TelemetryData".getBytes())
            .setCode(12)
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockBackend, times(1))
        .send(
            eq(
                BackendRequest.create(
                    Collections.singleton(
                        expectedEvent.toBuilder().addMetadata(TEST_KEY, TEST_VALUE).build()))));
  }
}
