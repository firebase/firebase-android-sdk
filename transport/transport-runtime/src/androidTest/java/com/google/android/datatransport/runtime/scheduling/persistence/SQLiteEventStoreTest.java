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

package com.google.android.datatransport.runtime.scheduling.persistence;

import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.SCHEMA_VERSION;
import static com.google.common.truth.Truth.assertThat;

import android.database.DatabaseUtils;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collections;
import java.util.UUID;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class SQLiteEventStoreTest {
  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").build();
  private static final TransportContext ANOTHER_TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend2").build();
  private static final Encoding JSON_ENCODING = Encoding.of("json");
  private static final EventInternal EVENT =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setEncodedPayload(
              new EncodedPayload(JSON_ENCODING, "Hello".getBytes(Charset.defaultCharset())))
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();

  private static final long HOUR = 60 * 60 * 1000;
  private static final int MAX_BLOB_SIZE_BYTES = 6;
  private static final EventStoreConfig CONFIG =
      EventStoreConfig.DEFAULT
          .toBuilder()
          .setLoadBatchSize(5)
          .setEventCleanUpAge(HOUR)
          .setMaxBlobByteSizePerRow(MAX_BLOB_SIZE_BYTES)
          .build();

  private final TestClock clock = new TestClock(1);
  private final SQLiteEventStore store = newStoreWithConfig(clock, CONFIG);

  private static SQLiteEventStore newStoreWithConfig(Clock clock, EventStoreConfig config) {
    return new SQLiteEventStore(
        clock,
        new UptimeClock(),
        config,
        new SchemaManager(
            ApplicationProvider.getApplicationContext(),
            UUID.randomUUID().toString(),
            SCHEMA_VERSION));
  }

  @Test
  public void persist_correctlyRoundTrips() {
    PersistedEvent newEvent = store.persist(TRANSPORT_CONTEXT, EVENT);
    Iterable<PersistedEvent> events = store.loadBatch(TRANSPORT_CONTEXT);

    assertThat(newEvent.getEvent()).isEqualTo(EVENT);
    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void persist_withNonInlineBlob_correctlyRoundTrips() {
    byte[] payload = "LongerThanSixBytes".getBytes(Charset.defaultCharset());
    EventInternal event =
        EVENT.toBuilder().setEncodedPayload(new EncodedPayload(JSON_ENCODING, payload)).build();
    PersistedEvent newEvent = store.persist(TRANSPORT_CONTEXT, event);
    Iterable<PersistedEvent> events = store.loadBatch(TRANSPORT_CONTEXT);

    assertThat(newEvent.getEvent()).isEqualTo(event);
    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void persist_withNonInlineBlob_correctlyStoresPayloadInSeparateTable() {
    byte[] payload = "LongerThanSixBytes".getBytes(Charset.defaultCharset());
    EventInternal event =
        EVENT.toBuilder().setEncodedPayload(new EncodedPayload(JSON_ENCODING, payload)).build();
    PersistedEvent newEvent = store.persist(TRANSPORT_CONTEXT, event);

    long expectedRows = payload.length / MAX_BLOB_SIZE_BYTES;
    if (payload.length % MAX_BLOB_SIZE_BYTES != 0) {
      expectedRows += 1;
    }

    long payloadRows =
        DatabaseUtils.queryNumEntries(
            store.getDb(),
            "event_payloads",
            "event_id = ?",
            new String[] {String.valueOf(newEvent.getId())});
    assertThat(payloadRows).isEqualTo(expectedRows);

    store.recordSuccess(store.loadBatch(TRANSPORT_CONTEXT));
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).isEmpty();
    payloadRows =
        DatabaseUtils.queryNumEntries(
            store.getDb(),
            "event_payloads",
            "event_id = ?",
            new String[] {String.valueOf(newEvent.getId())});
    assertThat(payloadRows).isEqualTo(0);
  }

  @Test
  public void persist_withEventsOfDifferentPriority_shouldEndBeStoredUnderDifferentContexts() {
    TransportContext ctx1 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .setPriority(Priority.VERY_LOW)
            .build();

    EventInternal event1 = EVENT;
    EventInternal event2 =
        EVENT
            .toBuilder()
            .setEncodedPayload(
                new EncodedPayload(JSON_ENCODING, "World".getBytes(Charset.defaultCharset())))
            .build();

    PersistedEvent newEvent1 = store.persist(ctx1, event1);
    PersistedEvent newEvent2 = store.persist(ctx2, event2);

    assertThat(store.loadBatch(ctx1)).containsExactly(newEvent1);
    assertThat(store.loadBatch(ctx2)).containsExactly(newEvent2);
  }

  @Test
  public void persist_withEventsOfDifferentExtras_shouldEndBeStoredUnderDifferentContexts() {
    TransportContext ctx1 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e2".getBytes(Charset.defaultCharset()))
            .build();

    EventInternal event1 = EVENT;
    EventInternal event2 =
        EVENT
            .toBuilder()
            .setEncodedPayload(
                new EncodedPayload(JSON_ENCODING, "World".getBytes(Charset.defaultCharset())))
            .build();

    PersistedEvent newEvent1 = store.persist(ctx1, event1);
    PersistedEvent newEvent2 = store.persist(ctx2, event2);

    assertThat(store.loadBatch(ctx1)).containsExactly(newEvent1);
    assertThat(store.loadBatch(ctx2)).containsExactly(newEvent2);
  }

  @Test
  public void persist_withEventsOfSameExtras_shouldEndBeStoredUnderSameContexts() {
    TransportContext ctx1 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();

    PersistedEvent newEvent1 = store.persist(ctx1, EVENT);
    PersistedEvent newEvent2 = store.persist(ctx2, EVENT);

    assertThat(store.loadBatch(ctx2)).containsExactly(newEvent1, newEvent2);
  }

  @Test
  public void persist_sameBackendswithDifferentExtras_shouldEndBeStoredUnderDifferentContexts() {
    TransportContext ctx1 =
        TransportContext.builder().setBackendName("backend1").setExtras(null).build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();

    PersistedEvent newEvent1 = store.persist(ctx1, EVENT);
    PersistedEvent newEvent2 = store.persist(ctx2, EVENT);

    assertThat(store.loadBatch(ctx1)).containsExactly(newEvent1);
    assertThat(store.loadBatch(ctx2)).containsExactly(newEvent2);
  }

  @Test
  public void persist_withEventCode_correctlyRoundTrips() {
    EventInternal eventWithCode = EVENT.toBuilder().setCode(5).build();
    PersistedEvent newEvent = store.persist(TRANSPORT_CONTEXT, eventWithCode);
    Iterable<PersistedEvent> events = store.loadBatch(TRANSPORT_CONTEXT);

    assertThat(newEvent.getEvent()).isEqualTo(eventWithCode);
    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void recordSuccess_deletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    store.recordSuccess(Collections.singleton(newEvent1));
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).containsExactly(newEvent2);
    store.recordSuccess(Collections.singleton(newEvent2));
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void recordSuccess_withMultipleEvents_deletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    store.recordSuccess(Arrays.asList(newEvent1, newEvent2));
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void recordFailure_eventuallyDeletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    for (int i = 0; i < SQLiteEventStore.MAX_RETRIES; i++) {
      Iterable<PersistedEvent> events = store.loadBatch(TRANSPORT_CONTEXT);
      assertThat(events).containsExactly(newEvent1, newEvent2);
      store.recordFailure(Collections.singleton(newEvent1));
    }
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).containsExactly(newEvent2);
  }

  @Test
  public void recordFailure_withMultipleEvents_eventuallyDeletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    for (int i = 0; i < SQLiteEventStore.MAX_RETRIES; i++) {
      Iterable<PersistedEvent> events = store.loadBatch(TRANSPORT_CONTEXT);
      assertThat(events).containsExactly(newEvent1, newEvent2);
      store.recordFailure(Arrays.asList(newEvent1, newEvent2));
    }
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void getNextCallTime_doesNotReturnUnknownBackends() {
    assertThat(store.getNextCallTime(TRANSPORT_CONTEXT)).isEqualTo(0);
  }

  @Test
  public void recordNextCallTime_correctlyRecordsTimestamp() {
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1);
    store.recordNextCallTime(ANOTHER_TRANSPORT_CONTEXT, 2);

    assertThat(store.getNextCallTime(TRANSPORT_CONTEXT)).isEqualTo(1);
    assertThat(store.getNextCallTime(ANOTHER_TRANSPORT_CONTEXT)).isEqualTo(2);
  }

  @Test
  public void recordNextCallTime_correctlyUpdatesTimestamp() {
    long timestamp1 = 1;
    long timestamp2 = 2;
    store.recordNextCallTime(TRANSPORT_CONTEXT, timestamp1);

    assertThat(store.getNextCallTime(TRANSPORT_CONTEXT)).isEqualTo(timestamp1);

    store.recordNextCallTime(TRANSPORT_CONTEXT, timestamp2);

    assertThat(store.getNextCallTime(TRANSPORT_CONTEXT)).isEqualTo(timestamp2);
  }

  @Test
  public void hasPendingEventsFor_whenEventsExist_shouldReturnTrue() {
    assertThat(store.hasPendingEventsFor(TRANSPORT_CONTEXT)).isFalse();

    store.persist(TRANSPORT_CONTEXT, EVENT);

    assertThat(store.hasPendingEventsFor(TRANSPORT_CONTEXT)).isTrue();
    assertThat(store.hasPendingEventsFor(ANOTHER_TRANSPORT_CONTEXT)).isFalse();
  }

  @Test
  public void persist_whenDbSizeOnDiskIsAtLimit_shouldNotPersistNewEvents() {
    SQLiteEventStore storeUnderTest =
        newStoreWithConfig(
            clock, CONFIG.toBuilder().setMaxStorageSizeInBytes(store.getByteSize()).build());
    assertThat(storeUnderTest.persist(TRANSPORT_CONTEXT, EVENT)).isNull();

    storeUnderTest =
        newStoreWithConfig(
            clock, CONFIG.toBuilder().setMaxStorageSizeInBytes(store.getByteSize() + 1).build());
    assertThat(storeUnderTest.persist(TRANSPORT_CONTEXT, EVENT)).isNotNull();
  }

  @Test
  public void loadBatch_shouldLoadNoMoreThanBatchSizeItems() {
    for (int i = 0; i <= CONFIG.getLoadBatchSize(); i++) {
      store.persist(TRANSPORT_CONTEXT, EVENT);
    }
    Iterable<PersistedEvent> persistedEvents = store.loadBatch(TRANSPORT_CONTEXT);
    assertThat(persistedEvents).hasSize(CONFIG.getLoadBatchSize());

    store.recordSuccess(persistedEvents);
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).hasSize(1);
  }

  @Test
  public void cleanUp_whenEventIsNotOld_shouldNotDeleteIt() {
    PersistedEvent persistedEvent = store.persist(TRANSPORT_CONTEXT, EVENT);
    assertThat(store.cleanUp()).isEqualTo(0);
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).containsExactly(persistedEvent);
  }

  @Test
  public void cleanUp_whenEventIsOld_shouldDeleteIt() {
    store.persist(TRANSPORT_CONTEXT, EVENT);
    clock.advance(HOUR + 1);
    assertThat(store.cleanUp()).isEqualTo(1);
    assertThat(store.loadBatch(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void loadActiveContexts_whenNoContextsAvailable_shouldReturnEmptyList() {
    assertThat(store.loadActiveContexts()).isEmpty();
  }

  @Test
  public void loadActiveContexts_whenTwoContextsAvailable_shouldReturnThem() {
    TransportContext ctx1 =
        TransportContext.builder().setBackendName("backend1").setExtras(null).build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();

    store.persist(ctx1, EVENT);
    store.persist(ctx1, EVENT);
    store.persist(ctx2, EVENT);
    store.persist(ctx2, EVENT);

    assertThat(store.loadActiveContexts()).containsExactly(ctx1, ctx2);
  }

  @Test
  public void loadActiveContexts_whenTwoContextsWithOneAvailable_shouldReturnIt() {
    TransportContext ctx1 =
        TransportContext.builder().setBackendName("backend1").setExtras(null).build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras("e1".getBytes(Charset.defaultCharset()))
            .build();

    store.persist(ctx1, EVENT);
    PersistedEvent persistedEvent2 = store.persist(ctx2, EVENT);
    store.recordSuccess(Collections.singleton(persistedEvent2));

    assertThat(store.loadActiveContexts()).containsExactly(ctx1);
  }
}
