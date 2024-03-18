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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.firebase.transport.ClientMetrics;
import com.google.android.datatransport.runtime.firebase.transport.LogEventDropped;
import com.google.android.datatransport.runtime.scheduling.persistence.ClientHealthMetricsStore;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.AdditionalAnswers;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class UploaderTest {
  private static final SynchronizationGuard guard =
      new SynchronizationGuard() {
        @Override
        public <T> T runCriticalSection(CriticalSection<T> criticalSection) {
          return criticalSection.execute();
        }
      };
  private static final String BACKEND_NAME = "backend1";
  private static final String ANOTHER_BACKEND_NAME = "backend1";
  private static final String CLIENT_HEALTH_METRICS_LOG_SOURCE = "GDT_CLIENT_METRICS";
  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName(BACKEND_NAME).build();
  private static final TransportContext ANOTHER_TRANSPORT_CONTEXT =
      TransportContext.builder()
          .setBackendName(ANOTHER_BACKEND_NAME)
          .setExtras("foo".getBytes())
          .build();
  private static final EventInternal EVENT =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setEncodedPayload(
              new EncodedPayload(Encoding.of("proto"), "Hello".getBytes(Charset.defaultCharset())))
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();
  private static final EventInternal ANOTHER_EVENT =
      EventInternal.builder()
          .setTransportName("43")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setEncodedPayload(
              new EncodedPayload(Encoding.of("proto"), "Hello".getBytes(Charset.defaultCharset())))
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();
  private static final int MANY_EVENT_COUNT = 1000;

  private final InMemoryEventStore store = spy(new InMemoryEventStore());
  private final EventStore mockStore = mock(EventStore.class);
  private BackendRegistry mockRegistry = mock(BackendRegistry.class);
  private TransportBackend mockBackend = mock(TransportBackend.class);
  private WorkScheduler mockScheduler = mock(WorkScheduler.class);
  private Runnable mockRunnable = mock(Runnable.class);
  private ClientHealthMetricsStore mockClientHealthMetricsStore =
      mock(ClientHealthMetricsStore.class);
  private Uploader uploader =
      spy(
          new Uploader(
              ApplicationProvider.getApplicationContext(),
              mockRegistry,
              store,
              mockScheduler,
              Runnable::run,
              guard,
              () -> 2,
              () -> 2,
              mockClientHealthMetricsStore));

  private EventInternal makeEventWithPseudonymousId(String id) {
    return EventInternal.builder()
        .setTransportName("43")
        .setEventMillis(1)
        .setUptimeMillis(2)
        .setEncodedPayload(
            new EncodedPayload(Encoding.of("proto"), "Hello".getBytes(Charset.defaultCharset())))
        .addMetadata("key1", "value1")
        .addMetadata("key2", "value2")
        .setPseudonymousId(id)
        .build();
  }

  @Before
  public void setUp() {
    when(mockRegistry.get(BACKEND_NAME)).thenReturn(mockBackend);
  }

  @After
  public void cleanUp() {
    store.reset();
  }

  @Test
  public void upload_noNetwork() {
    when(uploader.isNetworkAvailable()).thenReturn(Boolean.FALSE);
    uploader.upload(TRANSPORT_CONTEXT, 1, mockRunnable);
    // Scheduler must be called with the attempt number incremented.
    verify(mockScheduler, times(1)).schedule(TRANSPORT_CONTEXT, 2);
    verify(mockRunnable, times(1)).run();
  }

  @Test
  public void upload_yesNetwork() {
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    when(uploader.isNetworkAvailable()).thenReturn(Boolean.TRUE);
    uploader.upload(TRANSPORT_CONTEXT, 1, mockRunnable);
    verify(uploader, times(1)).logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(mockRunnable, times(1)).run();
  }

  @Test
  public void logAndUpdateStatus_okResponse() {
    store.persist(TRANSPORT_CONTEXT, EVENT);
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(store, times(1)).recordSuccess(any());
    verify(store, times(1)).recordNextCallTime(TRANSPORT_CONTEXT, 1002);
  }

  @Test
  public void logAndUpdateStatus_nontransientResponse() {
    store.persist(TRANSPORT_CONTEXT, EVENT);
    when(mockBackend.send(any())).thenReturn(BackendResponse.fatalError());
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(store, times(1)).recordSuccess(any());
  }

  @Test
  public void logAndUpdateStatus_transientReponse() {
    store.persist(TRANSPORT_CONTEXT, EVENT);
    when(mockBackend.send(any())).thenReturn(BackendResponse.transientError());
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(store, times(1)).recordFailure(any());
    verify(mockScheduler, times(1)).schedule(TRANSPORT_CONTEXT, 2, true);
  }

  @Test
  public void
      upload_singleEvent_withInvalidPayloadResponse_shouldRecordLogEventDroppedDueToInvalidPayload() {
    store.persist(TRANSPORT_CONTEXT, EVENT);
    when(mockBackend.send(any())).thenReturn(BackendResponse.invalidPayload());
    uploader.upload(TRANSPORT_CONTEXT, 1, mockRunnable);
    verify(mockClientHealthMetricsStore, times(1))
        .recordLogEventDropped(1, LogEventDropped.Reason.INVALID_PAYLOD, EVENT.getTransportName());
  }

  @Test
  public void
      upload_multipleEvents_withInvalidPayloadResponse_shouldRecordLogEventDroppedDueToInvalidPayload() {
    store.persist(TRANSPORT_CONTEXT, EVENT);
    store.persist(TRANSPORT_CONTEXT, EVENT);
    store.persist(TRANSPORT_CONTEXT, ANOTHER_EVENT);
    when(mockBackend.send(any())).thenReturn(BackendResponse.invalidPayload());
    uploader.upload(TRANSPORT_CONTEXT, 1, mockRunnable);
    verify(mockClientHealthMetricsStore, times(1))
        .recordLogEventDropped(2, LogEventDropped.Reason.INVALID_PAYLOD, EVENT.getTransportName());
    verify(mockClientHealthMetricsStore, times(1))
        .recordLogEventDropped(
            1, LogEventDropped.Reason.INVALID_PAYLOD, ANOTHER_EVENT.getTransportName());
  }

  @Test
  public void logAndUpdateStatus_manyEvents_shouldUploadAll() {
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    for (int i = 0; i < MANY_EVENT_COUNT; i++) {
      store.persist(TRANSPORT_CONTEXT, EVENT);
    }

    Iterable<PersistedEvent> persistedEvents = store.loadBatch(TRANSPORT_CONTEXT);
    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    assertThat(store.hasPendingEventsFor(TRANSPORT_CONTEXT)).isFalse();
  }

  @Test
  public void upload_toFlgServer_shouldIncludeClientHealthMetrics() {
    final ClientMetrics expectedClientMetrics = ClientMetrics.getDefaultInstance();
    when(mockRegistry.get(BACKEND_NAME)).thenReturn(mockBackend);
    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));
    when(mockBackend.decorate(any())).then(AdditionalAnswers.returnsFirstArg());
    when(mockClientHealthMetricsStore.loadClientMetrics()).thenReturn(expectedClientMetrics);

    store.persist(ANOTHER_TRANSPORT_CONTEXT, EVENT);
    uploader.upload(ANOTHER_TRANSPORT_CONTEXT, 0, mockRunnable);

    verify(mockClientHealthMetricsStore, times(1)).loadClientMetrics();
    verify(mockBackend, times(1))
        .send(
            argThat(
                (backendRequest -> {
                  for (EventInternal eventInternal : backendRequest.getEvents()) {
                    if (eventInternal.getTransportName().equals(CLIENT_HEALTH_METRICS_LOG_SOURCE)
                        && Arrays.equals(
                            eventInternal.getEncodedPayload().getBytes(),
                            expectedClientMetrics.toByteArray())) {
                      return true;
                    }
                  }
                  return false;
                })));
  }

  @Test
  public void upload_shouldBatchSamePseudonymousIds() {
    String targetId = "myId";
    String alternativeId = "otherId";

    EventInternal oldestEvent = makeEventWithPseudonymousId(targetId);
    EventInternal siblingEvent = makeEventWithPseudonymousId(targetId);
    EventInternal otherEvent = makeEventWithPseudonymousId(alternativeId);

    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));

    store.persist(TRANSPORT_CONTEXT, oldestEvent);
    store.persist(TRANSPORT_CONTEXT, EVENT);
    store.persist(TRANSPORT_CONTEXT, siblingEvent);
    store.persist(TRANSPORT_CONTEXT, otherEvent);

    List<EventInternal> targetEvents = Arrays.asList(oldestEvent, siblingEvent);

    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(mockBackend, times(1))
        .send(
            argThat(
                (backendRequest -> {
                  List<EventInternal> events =
                      StreamSupport.stream(backendRequest.getEvents().spliterator(), false)
                          .collect(Collectors.toList());

                  return events.equals(targetEvents);
                })));
  }

  @Test
  public void upload_shouldLeaveOtherPseudonymousIds() {
    String targetId = "myId";
    String alternativeId = "otherId";

    EventInternal oldestEvent = makeEventWithPseudonymousId(targetId);
    EventInternal otherEvent = makeEventWithPseudonymousId(alternativeId);

    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));

    store.persist(TRANSPORT_CONTEXT, oldestEvent);
    store.persist(TRANSPORT_CONTEXT, EVENT);
    store.persist(TRANSPORT_CONTEXT, otherEvent);

    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    assertThat(store.hasPendingEventsFor(TRANSPORT_CONTEXT)).isTrue();
  }

  @Test
  public void upload_shouldBatchOldestEventType() {
    String targetId = "myId";

    EventInternal oldestEvent = makeEventWithPseudonymousId(targetId);
    EventInternal siblingEvent = makeEventWithPseudonymousId(targetId);

    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));

    store.persist(TRANSPORT_CONTEXT, EVENT);
    store.persist(TRANSPORT_CONTEXT, oldestEvent);
    store.persist(TRANSPORT_CONTEXT, ANOTHER_EVENT);
    store.persist(TRANSPORT_CONTEXT, siblingEvent);

    List<EventInternal> targetEvents = Arrays.asList(EVENT, ANOTHER_EVENT);

    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    verify(mockBackend, times(1))
        .send(
            argThat(
                (backendRequest -> {
                  List<EventInternal> events =
                      StreamSupport.stream(backendRequest.getEvents().spliterator(), false)
                          .collect(Collectors.toList());

                  return events.equals(targetEvents);
                })));
  }

  @Test
  public void upload_shouldRescheduleIfThereAreAdditionalEventsPending() {
    String targetId = "myId";
    String alternativeId = "otherId";

    EventInternal oldestEvent = makeEventWithPseudonymousId(targetId);
    EventInternal otherEvent = makeEventWithPseudonymousId(alternativeId);

    when(mockBackend.send(any())).thenReturn(BackendResponse.ok(1000));

    store.persist(TRANSPORT_CONTEXT, oldestEvent);
    store.persist(TRANSPORT_CONTEXT, EVENT);
    store.persist(TRANSPORT_CONTEXT, otherEvent);

    uploader.logAndUpdateState(TRANSPORT_CONTEXT, 1);
    assertThat(store.hasPendingEventsFor(TRANSPORT_CONTEXT)).isTrue();
    verify(mockScheduler, times(1)).schedule(TRANSPORT_CONTEXT, 1, true);
  }
}
