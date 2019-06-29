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

import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXTS_SQL_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXT_BACKEND_PRIORITY_EXTRAS_INDEX_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENTS_SQL_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_BACKEND_INDEX_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_METADATA_SQL_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.DROP_CONTEXTS_SQL;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.DROP_EVENTS_SQL;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.DROP_EVENT_METADATA_SQL;
import static com.google.android.datatransport.runtime.scheduling.persistence.LegacySQL.CREATE_CONTEXTS_SQL_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.LegacySQL.CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.LegacySQL.CREATE_EVENTS_SQL_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.LegacySQL.CREATE_EVENT_BACKEND_INDEX_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.LegacySQL.CREATE_EVENT_METADATA_SQL_V1;
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import edu.emory.mathcs.backport.java.util.Collections;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SchemaManagerTest {
  private static final TransportContext CONTEXT1 =
      TransportContext.builder().setBackendName("b1").build();

  private static final TransportContext CONTEXT2 =
      TransportContext.builder()
          .setBackendName("b2")
          .setExtras("e2".getBytes())
          .build()
          .withPriority(Priority.VERY_LOW);

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
          CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1,
          DROP_EVENTS_SQL,
          DROP_EVENT_METADATA_SQL,
          DROP_CONTEXTS_SQL);

  private final DatabaseBootstrapClient V2_BOOTSTRAP_CLIENT =
      new DatabaseBootstrapClient(
          CREATE_EVENTS_SQL_V2,
          CREATE_EVENT_METADATA_SQL_V2,
          CREATE_CONTEXTS_SQL_V2,
          CREATE_EVENT_BACKEND_INDEX_V2,
          CREATE_CONTEXT_BACKEND_PRIORITY_EXTRAS_INDEX_V2,
          DROP_EVENTS_SQL,
          DROP_EVENT_METADATA_SQL,
          DROP_CONTEXTS_SQL);

  private final DatabaseMigrationClient V2_MIGRATION =
      new DatabaseMigrationClient(Collections.singletonList(EventStoreModule.MIGRATE_TO_V2));

  @Test
  public void persist_correctlyRoundTrips() {
    SchemaManager schemaManager =
        new SchemaManager(RuntimeEnvironment.application, V1_BOOTSTRAP_CLIENT, V2_MIGRATION);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);

    PersistedEvent newEvent = store.persist(CONTEXT1, EVENT1);
    Iterable<PersistedEvent> events = store.loadBatch(CONTEXT1);

    assertThat(newEvent.getEvent()).isEqualTo(EVENT1);
    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void upgrading_emptyDatabase_allowsPersistsAfterUpgrade() {
    SchemaManager schemaManager =
        new SchemaManager(RuntimeEnvironment.application, V1_BOOTSTRAP_CLIENT, V2_MIGRATION);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);

    schemaManager.onUpgrade(schemaManager.getWritableDatabase(), 1, 2);
    PersistedEvent newEvent1 = store.persist(CONTEXT1, EVENT1);

    assertThat(store.loadBatch(CONTEXT1)).containsExactly(newEvent1);
  }

  @Test
  public void upgradeding_nonEmptyDB_preservesValues() {
    SchemaManager schemaManager =
        new SchemaManager(RuntimeEnvironment.application, V1_BOOTSTRAP_CLIENT, V2_MIGRATION);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);
    // We simulate operations as done by an older SQLLiteEventStore at V1
    // We cannot simulate older operations with a newer client
    PersistedEvent event1 = simulatedPersistOnV1Database(schemaManager, CONTEXT1, EVENT1);

    // Upgrade to V2
    schemaManager.onUpgrade(schemaManager.getWritableDatabase(), 1, 2);

    assertThat(store.loadBatch(CONTEXT1)).containsExactly(event1);
  }

  @Test
  public void downgradeFromAFutureVersion_withEmptyDB_allowsPersistanceAfterMigration() {
    SchemaManager schemaManager =
        new SchemaManager(RuntimeEnvironment.application, V2_BOOTSTRAP_CLIENT, V2_MIGRATION);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);
    // We simulate operations as done by an older SQLLiteEventStore at V1
    // We cannot simulate older operations with a newer client
    simulatedPersistOnV1Database(schemaManager, CONTEXT1, EVENT1);

    schemaManager.onDowngrade(schemaManager.getWritableDatabase(), 3, 2);
    PersistedEvent event2 = store.persist(CONTEXT2, EVENT2);

    assertThat(store.loadBatch(CONTEXT2)).containsExactly(event2);
  }

  @Test
  public void downgradeFromAFutureVersion_withNonEmptyDB_isLossy() {
    SchemaManager schemaManager =
        new SchemaManager(RuntimeEnvironment.application, V2_BOOTSTRAP_CLIENT, V2_MIGRATION);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);
    PersistedEvent event1 = store.persist(CONTEXT1, EVENT1);

    schemaManager.onDowngrade(schemaManager.getWritableDatabase(), 3, 2);

    assertThat(store.loadBatch(CONTEXT1)).doesNotContain(event1);
  }

  private PersistedEvent simulatedPersistOnV1Database(
      SchemaManager schemaManager, TransportContext transportContext, EventInternal eventInternal) {
    SQLiteDatabase db = schemaManager.getWritableDatabase();

    ContentValues record = new ContentValues();
    record.put("backend_name", transportContext.getBackendName());
    record.put("priority", transportContext.getPriority().ordinal());
    record.put("next_request_ms", 0);
    long contextId = db.insert("transport_contexts", null, record);

    ContentValues values = new ContentValues();
    values.put("context_id", contextId);
    values.put("transport_name", eventInternal.getTransportName());
    values.put("timestamp_ms", eventInternal.getEventMillis());
    values.put("uptime_ms", eventInternal.getUptimeMillis());
    values.put("payload", eventInternal.getPayload());
    values.put("code", eventInternal.getCode());
    values.put("num_attempts", 0);
    long newEventId = db.insert("events", null, values);

    for (Map.Entry<String, String> entry : eventInternal.getMetadata().entrySet()) {
      ContentValues metadata = new ContentValues();
      metadata.put("event_id", newEventId);
      metadata.put("name", entry.getKey());
      metadata.put("value", entry.getValue());
      db.insert("event_metadata", null, metadata);
    }

    return PersistedEvent.create(newEventId, transportContext, eventInternal);
  }
}
