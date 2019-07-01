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

import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXTS_SQL_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENTS_SQL_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_BACKEND_INDEX_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_METADATA_SQL_V1;
import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SchemaManagerTest {
  private static final TransportContext CONTEXT1 =
      TransportContext.builder().setBackendName("b1").build();
  private static final EventInternal EVENT1 =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setPayload("Hello".getBytes())
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();
  private static final EventInternal EVENT2 =
      EVENT1.toBuilder().setPayload("World".getBytes()).build();

  private static final long HOUR = 60 * 60 * 1000;
  private static final EventStoreConfig CONFIG =
      EventStoreConfig.DEFAULT.toBuilder().setLoadBatchSize(5).setEventCleanUpAge(HOUR).build();

  private final TestClock clock = new TestClock(1);

  private final DatabaseBootstrapClient V1_BOOTSTRAP_CLIENT =
      new DatabaseBootstrapClient(
          CREATE_EVENTS_SQL_V1,
          CREATE_EVENT_METADATA_SQL_V1,
          CREATE_CONTEXTS_SQL_V1,
          CREATE_EVENT_BACKEND_INDEX_V1,
          CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1);

  @Test
  public void persist_correctlyRoundTrips() {
    SchemaManager schemaManager =
        new SchemaManager(RuntimeEnvironment.application, V1_BOOTSTRAP_CLIENT);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);

    PersistedEvent newEvent = store.persist(CONTEXT1, EVENT1);
    Iterable<PersistedEvent> events = store.loadBatch(CONTEXT1);

    assertThat(newEvent.getEvent()).isEqualTo(EVENT1);
    assertThat(events).containsExactly(newEvent);
  }
}
