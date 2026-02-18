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

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Event;
import com.google.android.datatransport.EventContext;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.firebase.transport.LogEventDropped;
import com.google.android.datatransport.runtime.firebase.transport.LogSourceMetrics;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;
import com.google.android.datatransport.runtime.scheduling.persistence.SQLiteEventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationException;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.stubbing.Answer;

@RunWith(AndroidJUnit4.class)
public class UploaderIntegrationTest {
  private static final String testTransport = "testTransport";
  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");
  private final TransportBackend mockBackend = mock(TransportBackend.class);
  private final BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private final Context context = InstrumentationRegistry.getInstrumentation().getContext();

  private final UploaderTestRuntimeComponent component =
      DaggerUploaderTestRuntimeComponent.builder()
          .setApplicationContext(context)
          .setBackendRegistry(mockRegistry)
          .setEventClock(() -> 3)
          .setUptimeClock(() -> 1)
          .build();

  private final WorkScheduler spyScheduler = component.getWorkScheduler();

  @Rule public final TransportRuntimeRule runtimeRule = new TransportRuntimeRule(component);

  @Before
  public void setUp() {
    when(mockBackend.decorate(any()))
        .thenAnswer(
            (Answer<EventInternal>)
                invocation -> invocation.<EventInternal>getArgument(0).toBuilder().build());
  }

  @After
  public void tearDown() {
    component.getEventStore().clearDb();
    component.getEventStore().resetClientMetrics();
  }

  private String generateBackendName() {
    return UUID.randomUUID().toString().replace("-", "");
  }

  private EventInternal makeEventWithPseudonymousId(String id) {
    return EventInternal.builder()
        .setTransportName(testTransport)
        .setEventMillis(1)
        .setUptimeMillis(2)
        .setEncodedPayload(
            new EncodedPayload(Encoding.of("proto"), "Hello".getBytes(Charset.defaultCharset())))
        .addMetadata("key1", "value1")
        .addMetadata("key2", "value2")
        .setPseudonymousId(id)
        .build();
  }

  @Test
  public void uploader_transientError_shouldReschedule() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    EventStore store = component.getEventStore();
    String mockBackendName = generateBackendName();
    TransportContext transportContext =
        TransportContext.builder()
            .setBackendName(mockBackendName)
            .setPriority(Priority.VERY_LOW)
            .build();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.transientError());
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(2))
        .send(eq(BackendRequest.create(Collections.singletonList(expectedEvent))));
    verify(spyScheduler, times(1)).schedule(any(), eq(2), anyBoolean());
    Iterable<PersistedEvent> eventList = store.loadBatch(transportContext);
    assertThat(eventList).isNotEmpty();
    for (PersistedEvent persistedEvent : eventList) {
      assertThat(persistedEvent.getEvent()).isEqualTo(expectedEvent);
    }
  }

  @Test
  public void uploader_ok_shouldNotReschedule() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    EventStore store = component.getEventStore();
    String mockBackendName = generateBackendName();
    TransportContext transportContext =
        TransportContext.builder()
            .setBackendName(mockBackendName)
            .setPriority(Priority.VERY_LOW)
            .build();

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(1))
        .send(eq(BackendRequest.create(Collections.singletonList(expectedEvent))));
    verify(spyScheduler, times(0)).schedule(any(), eq(2));
    assertThat(store.loadBatch(transportContext)).isEmpty();
    assertThat(store.getNextCallTime(transportContext)).isAtLeast((long) 1000);
    verify(store, times(1)).cleanUp();
  }

  @Test
  public void uploader_nonTransientError_shouldNotReschedule() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    EventStore store = component.getEventStore();
    String mockBackendName = generateBackendName();
    TransportContext transportContext =
        TransportContext.builder()
            .setBackendName(mockBackendName)
            .setPriority(Priority.VERY_LOW)
            .build();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.fatalError());
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();
    transport.send(stringEvent);
    verify(mockBackend, times(1))
        .send(eq(BackendRequest.create(Collections.singletonList(expectedEvent))));
    verify(spyScheduler, times(0)).schedule(any(), eq(2));
    assertThat(store.loadBatch(transportContext)).isEmpty();
  }

  @Test
  public void uploader_dbException_shouldReschedule() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    EventStore store = component.getEventStore();
    String mockBackendName = generateBackendName();

    TransportContext transportContext =
        TransportContext.builder()
            .setBackendName(mockBackendName)
            .setPriority(Priority.VERY_LOW)
            .build();
    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    doThrow(new SynchronizationException("Error", null)).when(store).loadBatch(any());
    TransportFactory factory = runtime.newFactory(mockBackendName);
    Transport<String> transport =
        factory.getTransport(testTransport, String.class, String::getBytes);
    Event<String> stringEvent = Event.ofTelemetry("TelemetryData");

    transport.send(stringEvent);
    verify(spyScheduler, times(1)).schedule(any(), eq(2));
    assertThat(store.getNextCallTime(transportContext)).isEqualTo(0);
  }

  @Test
  public void uploader_invalidPayload_shouldNotReschedule() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();
    String mockBackendName = generateBackendName();
    byte[] mockExtras = "extras".getBytes(Charset.defaultCharset());

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.invalidPayload());

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, mockExtras))
            .getTransport(testTransport, String.class, String::getBytes);
    transport.send(Event.ofTelemetry("TelemetryData"));

    verify(spyScheduler, times(0)).schedule(any(), eq(2));

    List<LogSourceMetrics> logSourceMetrics = store.loadClientMetrics().getLogSourceMetricsList();
    assertThat(logSourceMetrics).hasSize(1);
    LogSourceMetrics metrics = logSourceMetrics.get(0);
    List<LogEventDropped> logEventDroppedList = metrics.getLogEventDroppedList();
    assertThat(logEventDroppedList).hasSize(1);
    LogEventDropped logEventDropped = logEventDroppedList.get(0);
    assertThat(logEventDropped.getEventsDroppedCount()).isEqualTo(1);
    assertThat(logEventDropped.getReason()).isEqualTo(LogEventDropped.Reason.INVALID_PAYLOD);
  }

  @Test
  public void uploader_withPendingClientMetricsAndSuccessfulUpload_shouldResetCounters() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();
    String anotherTransport = "anotherTransport";
    store.recordLogEventDropped(20, LogEventDropped.Reason.CACHE_FULL, testTransport);
    store.recordLogEventDropped(1, LogEventDropped.Reason.MAX_RETRIES_REACHED, testTransport);
    store.recordLogEventDropped(1, LogEventDropped.Reason.INVALID_PAYLOD, anotherTransport);

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(2);

    String mockBackendName = generateBackendName();
    byte[] mockExtras = "extras".getBytes(Charset.defaultCharset());

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1));

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, mockExtras))
            .getTransport(testTransport, String.class, String::getBytes);
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();
    EventInternal metricsEvent = component.getUploader().createMetricsEvent(mockBackend, null);
    transport.send(Event.ofTelemetry("TelemetryData"));
    verify(mockBackend, times(1))
        .send(
            eq(
                BackendRequest.builder()
                    .setEvents(Arrays.asList(expectedEvent, metricsEvent))
                    .setExtras(mockExtras)
                    .build()));

    verify(spyScheduler, times(0)).schedule(any(), eq(2));

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).isEmpty();
  }

  @Test
  public void uploader_withPendingClientMetricsAndInvalidPayload_shouldNotResetCounters() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();
    String anotherTransport = "anotherTransport";
    store.recordLogEventDropped(1, LogEventDropped.Reason.INVALID_PAYLOD, anotherTransport);

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(1);

    String mockBackendName = generateBackendName();
    byte[] mockExtras = "extras".getBytes(Charset.defaultCharset());

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.invalidPayload());

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, mockExtras))
            .getTransport(testTransport, String.class, String::getBytes);
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();
    EventInternal metricsEvent = component.getUploader().createMetricsEvent(mockBackend, null);
    transport.send(Event.ofTelemetry("TelemetryData"));
    verify(mockBackend, times(1))
        .send(
            eq(
                BackendRequest.builder()
                    .setEvents(Arrays.asList(expectedEvent, metricsEvent))
                    .setExtras(mockExtras)
                    .build()));

    verify(spyScheduler, times(0)).schedule(any(), eq(2));

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(2);
  }

  @Test
  public void uploader_withPendingClientMetricsAndFatalError_shouldNotResetCounters() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();
    String anotherTransport = "anotherTransport";
    store.recordLogEventDropped(1, LogEventDropped.Reason.INVALID_PAYLOD, anotherTransport);

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(1);

    String mockBackendName = generateBackendName();
    byte[] mockExtras = "extras".getBytes(Charset.defaultCharset());

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.fatalError());

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, mockExtras))
            .getTransport(testTransport, String.class, String::getBytes);
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();
    EventInternal metricsEvent = component.getUploader().createMetricsEvent(mockBackend, null);
    transport.send(Event.ofTelemetry("TelemetryData"));
    verify(mockBackend, times(1))
        .send(
            eq(
                BackendRequest.builder()
                    .setEvents(Arrays.asList(expectedEvent, metricsEvent))
                    .setExtras(mockExtras)
                    .build()));

    verify(spyScheduler, times(0)).schedule(any(), eq(2));

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(1);
  }

  @Test
  public void
      uploader_withPendingClientMetricsAndSuccessfulUploadButNotLegacyTarget_shouldNotResetCounters() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();
    String anotherTransport = "anotherTransport";
    store.recordLogEventDropped(1, LogEventDropped.Reason.INVALID_PAYLOD, anotherTransport);

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(1);

    String mockBackendName = generateBackendName();

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1));

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, null))
            .getTransport(testTransport, String.class, String::getBytes);
    EventInternal expectedEvent =
        EventInternal.builder()
            .setEventMillis(3)
            .setUptimeMillis(1)
            .setTransportName(testTransport)
            .setEncodedPayload(
                new EncodedPayload(
                    PROTOBUF_ENCODING, "TelemetryData".getBytes(Charset.defaultCharset())))
            .build();

    transport.send(Event.ofTelemetry("TelemetryData"));
    verify(mockBackend, times(1))
        .send(eq(BackendRequest.create(Collections.singletonList(expectedEvent))));

    verify(spyScheduler, times(0)).schedule(any(), eq(2));

    assertThat(store.loadClientMetrics().getLogSourceMetricsList()).hasSize(1);
  }

  @Test
  public void uploader_shouldBatchSamePseudonymousIds() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();

    String mockBackendName = generateBackendName();

    String targetId = "myId";
    String alternativeId = "otherId";

    EventInternal oldestEvent = makeEventWithPseudonymousId(targetId);
    EventInternal siblingEvent = makeEventWithPseudonymousId(targetId);
    EventInternal otherEvent = makeEventWithPseudonymousId(alternativeId);

    EventContext event = EventContext.builder().setPseudonymousId(alternativeId).build();

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, null))
            .getTransport(testTransport, String.class, Encoding.of("proto"), String::getBytes);

    TransportContext TRANSPORT_CONTEXT =
        TransportContext.builder().setBackendName(mockBackendName).build();

    store.persist(TRANSPORT_CONTEXT, oldestEvent);
    store.persist(TRANSPORT_CONTEXT, otherEvent);
    store.persist(TRANSPORT_CONTEXT, siblingEvent);

    transport.send(Event.ofData("", event));
    verify(mockBackend, times(1))
        .send(eq(BackendRequest.create(Arrays.asList(oldestEvent, siblingEvent))));
  }

  @Test
  public void upload_shouldBatchOldestEventType() {
    TransportRuntime runtime = TransportRuntime.getInstance();
    SQLiteEventStore store = component.getEventStore();

    String mockBackendName = generateBackendName();

    String randomId = "myId";

    EventInternal targetEvent = makeEventWithPseudonymousId(null);
    EventInternal otherEvent = makeEventWithPseudonymousId(randomId);
    EventInternal siblingEvent = makeEventWithPseudonymousId(randomId);

    EventContext event = EventContext.builder().setPseudonymousId(randomId).build();

    when(mockRegistry.get(mockBackendName)).thenReturn(mockBackend);

    Transport<String> transport =
        runtime
            .newFactory(new TestDestination(mockBackendName, null))
            .getTransport(testTransport, String.class, Encoding.of("proto"), String::getBytes);

    TransportContext TRANSPORT_CONTEXT =
        TransportContext.builder().setBackendName(mockBackendName).build();

    store.persist(TRANSPORT_CONTEXT, targetEvent);
    store.persist(TRANSPORT_CONTEXT, otherEvent);
    store.persist(TRANSPORT_CONTEXT, siblingEvent);

    transport.send(Event.ofData("", event));
    verify(mockBackend, times(1))
        .send(eq(BackendRequest.create(Collections.singletonList(targetEvent))));
  }
}
