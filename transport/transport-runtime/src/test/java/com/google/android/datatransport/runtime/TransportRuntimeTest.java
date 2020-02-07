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

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transformer;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.DefaultScheduler;
import com.google.android.datatransport.runtime.scheduling.ImmediateScheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkInitializer;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TransportRuntimeTest {
  private static final String TEST_KEY = "test";
  private static final String TEST_VALUE = "test-value";
  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");
  private static final long EVENT_MILLIS = 3;
  private static final long UPTIME_MILLIS = 1;

  private final TransportInternal transportInternalMock = mock(TransportInternal.class);
  private final TransportBackend mockBackend = mock(TransportBackend.class);
  private final BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private final WorkInitializer mockInitializer = mock(WorkInitializer.class);
  private final WorkScheduler mockWorkScheduler = mock(WorkScheduler.class);
  private final EventStore mockEventStore = mock(EventStore.class);
  private static final SynchronizationGuard guard =
      new SynchronizationGuard() {
        @Override
        public <T> T runCriticalSection(CriticalSection<T> criticalSection) {
          return criticalSection.execute();
        }
      };

  private static class StatefulTransportScheduleCallback implements TransportScheduleCallback {
    public boolean called = false;
    public Exception exception = null;

    @Override
    public void onSchedule(@Nullable Exception error) {
      called = true;
      exception = error;
    }
  }

  private static Clock fixedClock(long value) {
    return () -> value;
  }

  @Test
  public void testTransportInternalSend() {
    TransportContext transportContext =
        TransportContext.builder().setBackendName("backendMock").build();
    String testTransport = "testTransport";
    TransportFactory factory =
        new TransportFactoryImpl(
            Collections.singleton(PROTOBUF_ENCODING), transportContext, transportInternalMock);
    Event<String> event = Event.ofTelemetry("TelemetryData");
    Transformer<String, byte[]> transformer = String::getBytes;
    Transport<String> transport = factory.getTransport(testTransport, String.class, transformer);

    StatefulTransportScheduleCallback callback = new StatefulTransportScheduleCallback();
    transport.schedule(event, callback);
    SendRequest request =
        SendRequest.builder()
            .setTransportContext(transportContext)
            .setEvent(event, PROTOBUF_ENCODING, transformer)
            .setTransportName(testTransport)
            .build();
    verify(transportInternalMock, times(1)).send(request, callback);
  }

  @Test
  public void testTransportRuntimeBackendDiscovery() {
    String mockBackendName = "backend";
    String testTransport = "testTransport";

    TransportRuntime runtime =
        new TransportRuntime(
            fixedClock(EVENT_MILLIS),
            fixedClock(UPTIME_MILLIS),
            new ImmediateScheduler(Runnable::run, mockRegistry),
            new Uploader(null, null, null, null, null, null, () -> 2),
            mockInitializer);

    verify(mockInitializer, times(1)).ensureContextsScheduled();

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
            .setEventMillis(EVENT_MILLIS)
            .setUptimeMillis(UPTIME_MILLIS)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .setCode(12)
            .build();

    StatefulTransportScheduleCallback callback = new StatefulTransportScheduleCallback();
    transport.schedule(stringEvent, callback);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockBackend, times(1))
        .send(
            eq(
                BackendRequest.create(
                    Collections.singleton(
                        expectedEvent.toBuilder().addMetadata(TEST_KEY, TEST_VALUE).build()))));
    assertThat(callback.called).isTrue();
    assertThat(callback.exception).isNull();
  }

  @Test
  public void testTransportRuntimeTaskFailure() {
    String mockBackendName = "backend";
    String testTransport = "testTransport";

    TransportRuntime runtime =
        new TransportRuntime(
            fixedClock(EVENT_MILLIS),
            fixedClock(UPTIME_MILLIS),
            new ImmediateScheduler(Runnable::run, mockRegistry),
            new Uploader(null, null, null, null, null, null, () -> 2),
            mockInitializer);

    verify(mockInitializer, times(1)).ensureContextsScheduled();

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.decorate(any())).thenThrow(new IllegalArgumentException());

    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry(12, "TelemetryData");

    StatefulTransportScheduleCallback callback = new StatefulTransportScheduleCallback();
    transport.schedule(stringEvent, callback);
    assertThat(callback.called).isTrue();
    assertThat(callback.exception).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void testTransportRuntimeTaskUsingDefaultScheduler() {
    String mockBackendName = "backend";
    String testTransport = "testTransport";

    TransportRuntime runtime =
        new TransportRuntime(
            fixedClock(EVENT_MILLIS),
            fixedClock(UPTIME_MILLIS),
            new DefaultScheduler(
                Runnable::run, mockRegistry, mockWorkScheduler, mockEventStore, guard),
            new Uploader(null, null, null, null, null, null, () -> 2),
            mockInitializer);

    verify(mockInitializer, times(1)).ensureContextsScheduled();

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
            .setEventMillis(EVENT_MILLIS)
            .setUptimeMillis(UPTIME_MILLIS)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .setCode(12)
            .build();

    StatefulTransportScheduleCallback callback = new StatefulTransportScheduleCallback();
    transport.schedule(stringEvent, callback);
    verify(mockBackend, times(1)).decorate(eq(expectedEvent));
    verify(mockEventStore, times(1)).persist(any(TransportContext.class), any(EventInternal.class));
    verify(mockBackend, never()).send(any(BackendRequest.class));
    assertThat(callback.called).isTrue();
    assertThat(callback.exception).isNull();
  }

  @Test
  public void testTransportRuntimeTaskFailsUsingDefaultScheduler() {
    String mockBackendName = "backend";
    String testTransport = "testTransport";

    TransportRuntime runtime =
        new TransportRuntime(
            fixedClock(EVENT_MILLIS),
            fixedClock(UPTIME_MILLIS),
            new DefaultScheduler(
                Runnable::run, mockRegistry, mockWorkScheduler, mockEventStore, guard),
            new Uploader(null, null, null, null, null, null, () -> 2),
            mockInitializer);

    verify(mockInitializer, times(1)).ensureContextsScheduled();

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.decorate(any())).thenThrow(new IllegalArgumentException());

    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry(12, "TelemetryData");

    StatefulTransportScheduleCallback callback = new StatefulTransportScheduleCallback();
    transport.schedule(stringEvent, callback);
    assertThat(callback.called).isTrue();
    assertThat(callback.exception).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  public void newFactory_withDestination_shouldSupportProtobuf() {
    TransportRuntime runtime =
        new TransportRuntime(
            fixedClock(EVENT_MILLIS),
            fixedClock(UPTIME_MILLIS),
            new ImmediateScheduler(Runnable::run, mockRegistry),
            new Uploader(null, null, null, null, null, null, () -> 2),
            mockInitializer);

    TransportFactory transportFactory = runtime.newFactory(new TestDestination());
    Transport<String> transport =
        transportFactory.getTransport(
            "hello", String.class, Encoding.of("proto"), String::getBytes);
    assertThat(transport).isNotNull();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            transportFactory.getTransport(
                "hello", String.class, Encoding.of("json"), String::getBytes));
  }

  @Test
  public void newFactory_withExtendedDestination_shouldSupportYaml() {
    TransportRuntime runtime =
        new TransportRuntime(
            fixedClock(EVENT_MILLIS),
            fixedClock(UPTIME_MILLIS),
            new ImmediateScheduler(Runnable::run, mockRegistry),
            new Uploader(null, null, null, null, null, null, () -> 2),
            mockInitializer);

    TransportFactory transportFactory = runtime.newFactory(new YamlEncodedDestination());
    Transport<String> transport =
        transportFactory.getTransport("hello", String.class, Encoding.of("yaml"), String::getBytes);
    assertThat(transport).isNotNull();
    assertThrows(
        IllegalArgumentException.class,
        () ->
            transportFactory.getTransport(
                "hello", String.class, Encoding.of("json"), String::getBytes));
  }

  private static class TestDestination implements Destination {
    @NonNull
    @Override
    public String getName() {
      return "test";
    }

    @Nullable
    @Override
    public byte[] getExtras() {
      return new byte[0];
    }
  }

  private static class YamlEncodedDestination implements EncodedDestination {

    @NonNull
    @Override
    public String getName() {
      return "test";
    }

    @Nullable
    @Override
    public byte[] getExtras() {
      return new byte[0];
    }

    @Override
    public Set<Encoding> getSupportedEncodings() {
      return Collections.singleton(Encoding.of("yaml"));
    }
  }
}
