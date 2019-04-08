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

package com.google.android.datatransport.runtime.scheduling.persistence;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.UptimeClock;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SQLiteEventStoreTest {
  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").build();
  private static final TransportContext ANOTHER_TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend2").build();
  private static final EventInternal EVENT =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setPayload("Hello".getBytes())
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();

  private final SQLiteEventStore store = newStoreWithCapacity(10 * 1024 * 1024);

  private static SQLiteEventStore newStoreWithCapacity(long capacity) {
    return new SQLiteEventStore(RuntimeEnvironment.application, new UptimeClock(), capacity);
  }

  @Test
  public void persist_correctlyRoundTrips() {
    PersistedEvent newEvent = store.persist(TRANSPORT_CONTEXT, EVENT);
    Iterable<PersistedEvent> events = store.loadAll(TRANSPORT_CONTEXT);

    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void recordSuccess_deletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    store.recordSuccess(Collections.singleton(newEvent1));
    assertThat(store.loadAll(TRANSPORT_CONTEXT)).containsExactly(newEvent2);
    store.recordSuccess(Collections.singleton(newEvent2));
    assertThat(store.loadAll(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void recordSuccess_withMultipleEvents_deletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    store.recordSuccess(Arrays.asList(newEvent1, newEvent2));
    assertThat(store.loadAll(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void recordFailure_eventuallyDeletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    for (int i = 0; i < SQLiteEventStore.MAX_RETRIES; i++) {
      Iterable<PersistedEvent> events = store.loadAll(TRANSPORT_CONTEXT);
      assertThat(events).containsExactly(newEvent1, newEvent2);
      store.recordFailure(Collections.singleton(newEvent1));
    }
    assertThat(store.loadAll(TRANSPORT_CONTEXT)).containsExactly(newEvent2);
  }

  @Test
  public void recordFailure_withMultipleEvents_eventuallyDeletesEvents() {
    PersistedEvent newEvent1 = store.persist(TRANSPORT_CONTEXT, EVENT);
    PersistedEvent newEvent2 = store.persist(TRANSPORT_CONTEXT, EVENT);

    for (int i = 0; i < SQLiteEventStore.MAX_RETRIES; i++) {
      Iterable<PersistedEvent> events = store.loadAll(TRANSPORT_CONTEXT);
      assertThat(events).containsExactly(newEvent1, newEvent2);
      store.recordFailure(Arrays.asList(newEvent1, newEvent2));
    }
    assertThat(store.loadAll(TRANSPORT_CONTEXT)).isEmpty();
  }

  @Test
  public void getNextCallTime_doesNotReturnUnknownBackends() {
    assertThat(store.getNextCallTime(TRANSPORT_CONTEXT)).isNull();
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
    SQLiteEventStore storeUnderTest = newStoreWithCapacity(store.getByteSize());
    assertThat(storeUnderTest.persist(TRANSPORT_CONTEXT, EVENT)).isNull();

    storeUnderTest = newStoreWithCapacity(store.getByteSize() + 1);
    assertThat(storeUnderTest.persist(TRANSPORT_CONTEXT, EVENT)).isNotNull();
  }
}
