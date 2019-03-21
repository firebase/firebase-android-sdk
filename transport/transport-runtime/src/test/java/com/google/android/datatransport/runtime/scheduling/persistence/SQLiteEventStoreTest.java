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

import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.time.UptimeClock;
import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SQLiteEventStoreTest {

  private static final String BACKEND_NAME = "backend1";
  private static final String ANOTHER_BACKEND_NAME = "backend2";
  private static final EventInternal EVENT =
      EventInternal.builder()
          .setTransportName("42")
          .setPriority(Priority.DEFAULT)
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setPayload("Hello".getBytes())
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();

  private final SQLiteEventStore store =
      new SQLiteEventStore(RuntimeEnvironment.application, new UptimeClock());

  @Test
  public void persist_correctlyRoundTrips() {
    PersistedEvent newEvent = store.persist(BACKEND_NAME, EVENT);
    Iterable<PersistedEvent> events = store.loadAll(BACKEND_NAME);

    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void recordSuccess_deletesEvents() {
    PersistedEvent newEvent1 = store.persist(BACKEND_NAME, EVENT);
    PersistedEvent newEvent2 = store.persist(BACKEND_NAME, EVENT);

    store.recordSuccess(Collections.singleton(newEvent1));
    assertThat(store.loadAll(BACKEND_NAME)).containsExactly(newEvent2);
    store.recordSuccess(Collections.singleton(newEvent2));
    assertThat(store.loadAll(BACKEND_NAME)).isEmpty();
  }

  @Test
  public void recordSuccess_withMultipleEvents_deletesEvents() {
    PersistedEvent newEvent1 = store.persist(BACKEND_NAME, EVENT);
    PersistedEvent newEvent2 = store.persist(BACKEND_NAME, EVENT);

    store.recordSuccess(Arrays.asList(newEvent1, newEvent2));
    assertThat(store.loadAll(BACKEND_NAME)).isEmpty();
  }

  @Test
  public void recordFailure_eventuallyDeletesEvents() {
    PersistedEvent newEvent1 = store.persist(BACKEND_NAME, EVENT);
    PersistedEvent newEvent2 = store.persist(BACKEND_NAME, EVENT);

    for (int i = 0; i < SQLiteEventStore.MAX_RETRIES; i++) {
      Iterable<PersistedEvent> events = store.loadAll(BACKEND_NAME);
      assertThat(events).containsExactly(newEvent1, newEvent2);
      store.recordFailure(Collections.singleton(newEvent1));
    }
    assertThat(store.loadAll(BACKEND_NAME)).containsExactly(newEvent2);
  }

  @Test
  public void recordFailure_withMultipleEvents_eventuallyDeletesEvents() {
    PersistedEvent newEvent1 = store.persist(BACKEND_NAME, EVENT);
    PersistedEvent newEvent2 = store.persist(BACKEND_NAME, EVENT);

    for (int i = 0; i < SQLiteEventStore.MAX_RETRIES; i++) {
      Iterable<PersistedEvent> events = store.loadAll(BACKEND_NAME);
      assertThat(events).containsExactly(newEvent1, newEvent2);
      store.recordFailure(Arrays.asList(newEvent1, newEvent2));
    }
    assertThat(store.loadAll(BACKEND_NAME)).isEmpty();
  }

  @Test
  public void getNextCallTime_doesNotReturnUnknownBackends() {
    assertThat(store.getNextCallTime(BACKEND_NAME)).isNull();
  }

  @Test
  public void recordNextCallTime_correctlyRecordsTimestamp() {
    store.recordNextCallTime(BACKEND_NAME, 1);
    store.recordNextCallTime(ANOTHER_BACKEND_NAME, 2);

    assertThat(store.getNextCallTime(BACKEND_NAME)).isEqualTo(1);
    assertThat(store.getNextCallTime(ANOTHER_BACKEND_NAME)).isEqualTo(2);
  }

  @Test
  public void recordNextCallTime_correctlyUpdatesTimestamp() {
    long timestamp1 = 1;
    long timestamp2 = 2;
    store.recordNextCallTime(BACKEND_NAME, timestamp1);

    assertThat(store.getNextCallTime(BACKEND_NAME)).isEqualTo(timestamp1);

    store.recordNextCallTime(BACKEND_NAME, timestamp2);

    assertThat(store.getNextCallTime(BACKEND_NAME)).isEqualTo(timestamp2);
  }

  @Test
  public void hasPendingEventsFor_whenEventsExist_shouldReturnTrue() {
    assertThat(store.hasPendingEventsFor(BACKEND_NAME)).isFalse();

    store.persist(BACKEND_NAME, EVENT);

    assertThat(store.hasPendingEventsFor(BACKEND_NAME)).isTrue();
    assertThat(store.hasPendingEventsFor(ANOTHER_BACKEND_NAME)).isFalse();
  }
}
