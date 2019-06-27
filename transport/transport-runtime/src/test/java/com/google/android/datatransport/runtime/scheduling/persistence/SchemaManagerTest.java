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
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import java.util.Arrays;
import java.util.Collection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(ParameterizedRobolectricTestRunner.class)
public class SchemaManagerTest {

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

  private static final long HOUR = 60 * 60 * 1000;
  private static final EventStoreConfig CONFIG =
      EventStoreConfig.DEFAULT.toBuilder().setLoadBatchSize(5).setEventCleanUpAge(HOUR).build();

  private final TestClock clock = new TestClock(1);
  private SQLiteEventStore store;
  private final SchemaManager schemaManager;

  public SchemaManagerTest(
      String createEventsSql, String createEventMetadataSql, String createContextsSql) {
    schemaManager =
        new SchemaManager(
            RuntimeEnvironment.application,
            createEventsSql,
            createEventMetadataSql,
            createContextsSql);
  }

  @Before
  public void initialize() {
    store = newStoreWithConfig(clock, CONFIG, schemaManager);
  }

  @ParameterizedRobolectricTestRunner.Parameters
  public static Collection primeNumbers() {
    return Arrays.asList(
        new Object[][] {
          {
            EventStoreModule.CREATE_EVENTS_SQL_V1,
            EventStoreModule.CREATE_EVENT_METADATA_SQL_V1,
            EventStoreModule.CREATE_CONTEXTS_SQL_V1
          }
        });
  }

  @Test
  public void persist_withEventsOfDifferentPriority_shouldEndBeStoredUnderDifferentContexts() {
    TransportContext ctx1 = TRANSPORT_CONTEXT;
    TransportContext ctx2 = TRANSPORT_CONTEXT.withPriority(Priority.VERY_LOW);

    EventInternal event1 = EVENT;
    EventInternal event2 = EVENT.toBuilder().setPayload("World".getBytes()).build();

    PersistedEvent newEvent1 = store.persist(ctx1, event1);
    PersistedEvent newEvent2 = store.persist(ctx2, event2);

    assertThat(store.loadBatch(ctx1)).containsExactly(newEvent1);
    assertThat(store.loadBatch(ctx2)).containsExactly(newEvent2);
  }

  private SQLiteEventStore newStoreWithConfig(
      Clock clock, EventStoreConfig config, SchemaManager schemaManager) {
    return new SQLiteEventStore(clock, new UptimeClock(), config, schemaManager);
  }
}
