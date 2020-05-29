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

import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.DB_NAME;
import static com.google.android.datatransport.runtime.scheduling.persistence.SchemaManager.SCHEMA_VERSION;
import static com.google.common.truth.Truth.assertThat;

import android.content.ContentValues;
import android.database.sqlite.SQLiteDatabase;
import androidx.test.core.app.ApplicationProvider;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.time.TestClock;
import com.google.android.datatransport.runtime.time.UptimeClock;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.charset.Charset;
import java.util.Map;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SchemaManagerTest {
  private static final TransportContext CONTEXT1 =
      TransportContext.builder().setBackendName("b1").build();

  private static final TransportContext CONTEXT2 =
      TransportContext.builder()
          .setBackendName("b2")
          .setExtras("e2".getBytes(Charset.defaultCharset()))
          .build()
          .withPriority(Priority.VERY_LOW);

  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");
  private static final EventInternal EVENT1 =
      EventInternal.builder()
          .setTransportName("42")
          .setEventMillis(1)
          .setUptimeMillis(2)
          .setEncodedPayload(
              new EncodedPayload(PROTOBUF_ENCODING, "Hello".getBytes(Charset.defaultCharset())))
          .addMetadata("key1", "value1")
          .addMetadata("key2", "value2")
          .build();

  private static final EventInternal EVENT2 =
      EVENT1
          .toBuilder()
          .setEncodedPayload(
              new EncodedPayload(PROTOBUF_ENCODING, "World".getBytes(Charset.defaultCharset())))
          .build();

  private static final long HOUR = 60 * 60 * 1000;
  private static final EventStoreConfig CONFIG =
      EventStoreConfig.DEFAULT.toBuilder().setLoadBatchSize(5).setEventCleanUpAge(HOUR).build();

  private final TestClock clock = new TestClock(1);

  @Test
  public void persist_correctlyRoundTrips() {
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, SCHEMA_VERSION);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);

    PersistedEvent newEvent = store.persist(CONTEXT1, EVENT1);
    Iterable<PersistedEvent> events = store.loadBatch(CONTEXT1);

    assertThat(newEvent.getEvent()).isEqualTo(EVENT1);
    assertThat(events).containsExactly(newEvent);
  }

  @Test
  public void upgradingV1ToLatest_emptyDatabase_allowsPersistsAfterUpgrade() {
    int oldVersion = 1;
    int newVersion = SCHEMA_VERSION;
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, oldVersion);

    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);

    schemaManager.onUpgrade(schemaManager.getWritableDatabase(), oldVersion, newVersion);
    PersistedEvent newEvent1 = store.persist(CONTEXT1, EVENT1);

    assertThat(store.loadBatch(CONTEXT1)).containsExactly(newEvent1);
  }

  @Test
  public void upgradingV1ToLatest_nonEmptyDB_isLossless() {
    int oldVersion = 1;
    int newVersion = SCHEMA_VERSION;
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, oldVersion);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);
    // We simulate operations as done by an older SQLLiteEventStore at V1
    // We cannot simulate older operations with a newer client
    PersistedEvent event1 = simulatedPersistOnV1Database(schemaManager, CONTEXT1, EVENT1);

    // Upgrade to V2
    schemaManager.onUpgrade(schemaManager.getWritableDatabase(), oldVersion, newVersion);

    assertThat(store.loadBatch(CONTEXT1)).containsExactly(event1);
  }

  @Test
  public void upgradingV3ToV4_nonEmptyDB_isLossless() {
    int oldVersion = 3;
    int newVersion = 4;
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, oldVersion);
    SQLiteEventStore store = new SQLiteEventStore(clock, new UptimeClock(), CONFIG, schemaManager);
    // We simulate operations as done by an older SQLLiteEventStore at V1
    // We cannot simulate older operations with a newer client
    PersistedEvent event1 = simulatedPersistOnV1Database(schemaManager, CONTEXT1, EVENT1);

    // Upgrade to V4
    schemaManager.onUpgrade(schemaManager.getWritableDatabase(), oldVersion, newVersion);
    assertThat(store.loadBatch(CONTEXT1)).containsExactly(event1);

    long inlineRows =
        store
            .getDb()
            .compileStatement("SELECT COUNT(*) from events where inline = 1")
            .simpleQueryForLong();
    assertThat(inlineRows).isEqualTo(1);
  }

  @Test
  public void upgrade_toANonExistentVersion_fails() {
    int oldVersion = 1;
    int nonExistentVersion = 1000;
    SchemaManager schemaManager =
        new SchemaManager(ApplicationProvider.getApplicationContext(), DB_NAME, oldVersion);

    Assert.assertThrows(
        IllegalArgumentException.class,
        () ->
            schemaManager.onUpgrade(
                schemaManager.getWritableDatabase(), oldVersion, nonExistentVersion));
  }

  private PersistedEvent simulatedPersistOnV1Database(
      SchemaManager schemaManager, TransportContext transportContext, EventInternal eventInternal) {
    SQLiteDatabase db = schemaManager.getWritableDatabase();

    ContentValues record = new ContentValues();
    record.put("backend_name", transportContext.getBackendName());
    record.put("priority", PriorityMapping.toInt(transportContext.getPriority()));
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
