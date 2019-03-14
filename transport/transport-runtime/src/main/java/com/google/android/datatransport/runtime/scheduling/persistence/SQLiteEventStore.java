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

package com.google.android.datatransport.runtime.scheduling.persistence;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.support.annotation.Nullable;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EventInternal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;

/** {@link EventStore} implementation backed by a SQLite database. */
public class SQLiteEventStore implements EventStore {

  static final int MAX_RETRIES = 10;

  private static final Priority[] ALL_PRIORITIES = Priority.values();

  private final OpenHelper openHelper;
  private SQLiteDatabase db;

  @Inject
  SQLiteEventStore(Context applicationContext) {
    this.openHelper = new OpenHelper(applicationContext);
  }

  private SQLiteDatabase getDb() {
    if (db == null) {
      db = openHelper.getWritableDatabase();
    }
    return db;
  }

  @Override
  public <T> T atomically(AtomicFunction<T> function) {
    return inTransaction(db -> function.execute());
  }

  @Override
  public PersistedEvent persist(String backendName, EventInternal event) {
    ContentValues values = new ContentValues();
    values.put("backend_id", backendName);
    values.put("transport_name", event.getTransportName());
    values.put("priority", event.getPriority().ordinal());
    values.put("timestamp_ms", event.getEventMillis());
    values.put("uptime_ms", event.getUptimeMillis());
    values.put("payload", event.getPayload());
    values.put("num_attempts", 0);

    long newRowId =
        inTransaction(
            db -> {
              long newEventId = db.insert("events", null, values);

              // TODO: insert all with one sql query.
              for (Map.Entry<String, String> entry : event.getMetadata().entrySet()) {
                ContentValues metadata = new ContentValues();
                metadata.put("event_id", newEventId);
                metadata.put("name", entry.getKey());
                metadata.put("value", entry.getValue());
                db.insert("event_metadata", null, metadata);
              }
              return newEventId;
            });

    return PersistedEvent.create(newRowId, backendName, event);
  }

  @Override
  public void recordFailure(Iterable<PersistedEvent> events) {
    StringBuilder query =
        new StringBuilder("UPDATE events SET num_attempts = num_attempts + 1 WHERE _id in (");
    Iterator<PersistedEvent> iterator = events.iterator();
    while (iterator.hasNext()) {
      query.append(iterator.next().getId());
      if (iterator.hasNext()) {
        query.append(',');
      }
    }
    query.append(')');
    inTransaction(
        db -> {
          db.compileStatement(query.toString()).execute();
          db.compileStatement("DELETE FROM events WHERE num_attempts >= " + MAX_RETRIES).execute();
          return null;
        });
  }

  @Override
  public void recordSuccess(Iterable<PersistedEvent> events) {
    StringBuilder query = new StringBuilder("DELETE FROM events WHERE _id in (");
    Iterator<PersistedEvent> iterator = events.iterator();
    while (iterator.hasNext()) {
      query.append(iterator.next().getId());
      if (iterator.hasNext()) {
        query.append(',');
      }
    }
    query.append(')');
    getDb().compileStatement(query.toString()).execute();
  }

  @Override
  @Nullable
  public Long getNextCallTime(String backendName) {
    try (Cursor cursor =
        getDb()
            .rawQuery(
                "SELECT next_request_ms FROM backends WHERE name = ?",
                new String[] {backendName})) {
      if (cursor.moveToNext()) {
        return cursor.getLong(0);
      }
    }
    return null;
  }

  @Override
  public boolean hasPendingEventsFor(String backendName) {
    try (Cursor cursor =
        getDb()
            .rawQuery(
                "SELECT 1 FROM events WHERE backend_id = ? LIMIT 1", new String[] {backendName})) {
      return cursor.moveToNext();
    }
  }

  @Override
  public void recordNextCallTime(String backendName, long timestampMs) {
    inTransaction(
        db -> {
          ContentValues values = new ContentValues();
          values.put("next_request_ms", timestampMs);
          int rowsUpdated = db.update("backends", values, "name = ?", new String[] {backendName});

          if (rowsUpdated < 1) {
            values.put("name", backendName);
            db.insert("backends", null, values);
          }
          return null;
        });
  }

  @Override
  public Iterable<PersistedEvent> loadAll(String backendName) {
    return inTransaction(
        db -> {
          List<PersistedEvent> events = new ArrayList<>();
          try (Cursor cursor =
              db.query(
                  "events",
                  new String[] {
                    "_id", "transport_name", "priority", "timestamp_ms", "uptime_ms", "payload"
                  },
                  "backend_id = ?",
                  new String[] {backendName},
                  null,
                  null,
                  null)) {
            while (cursor.moveToNext()) {
              long id = cursor.getLong(0);
              EventInternal event =
                  EventInternal.builder()
                      .setTransportName(cursor.getString(1))
                      .setPriority(toPriority(cursor.getInt(2)))
                      .setEventMillis(cursor.getLong(3))
                      .setUptimeMillis(cursor.getLong(4))
                      .setPayload(cursor.getBlob(5))
                      .build();
              events.add(PersistedEvent.create(id, backendName, event));
            }
          }

          Map<Long, Set<Metadata>> metadataIndex = new HashMap<>();
          StringBuilder whereClause = new StringBuilder("event_id IN (");
          for (int i = 0; i < events.size(); i++) {
            whereClause.append(events.get(i).getId());
            if (i < events.size() - 1) {
              whereClause.append(',');
            }
          }
          whereClause.append(')');

          try (Cursor cursor =
              db.query(
                  "event_metadata",
                  new String[] {"event_id", "name", "value"},
                  whereClause.toString(),
                  null,
                  null,
                  null,
                  null)) {
            while (cursor.moveToNext()) {
              long eventId = cursor.getLong(0);
              Set<Metadata> currentSet = metadataIndex.get(eventId);
              if (currentSet == null) {
                currentSet = new HashSet<>();
                metadataIndex.put(eventId, currentSet);
              }
              currentSet.add(new Metadata(cursor.getString(1), cursor.getString(2)));
            }
          }
          ListIterator<PersistedEvent> iterator = events.listIterator();
          while (iterator.hasNext()) {
            PersistedEvent current = iterator.next();
            if (!metadataIndex.containsKey(current.getId())) {
              continue;
            }
            EventInternal.Builder newEvent = current.getEvent().toBuilder();

            for (Metadata metadata : metadataIndex.get(current.getId())) {
              newEvent.addMetadata(metadata.key, metadata.value);
            }
            iterator.set(
                PersistedEvent.create(current.getId(), current.getBackendName(), newEvent.build()));
          }
          return events;
        });
  }

  interface TransactionFn<T> {
    T execute(SQLiteDatabase db);
  }

  private <T> T inTransaction(TransactionFn<T> function) {
    SQLiteDatabase db = getDb();
    db.beginTransaction();
    try {
      T result = function.execute(db);
      db.setTransactionSuccessful();
      return result;
    } finally {
      db.endTransaction();
    }
  }

  private static class Metadata {
    final String key;
    final String value;

    private Metadata(String key, String value) {
      this.key = key;
      this.value = value;
    }
  }

  private static Priority toPriority(int value) {
    if (value < 0 || value >= ALL_PRIORITIES.length) {
      return Priority.DEFAULT;
    }
    return ALL_PRIORITIES[value];
  }

  private static class OpenHelper extends SQLiteOpenHelper {
    private static int SCHEMA_VERSION = 1;
    private static String DB_NAME = "com.google.android.datatransport.events";
    private static String CREATE_EVENTS_SQL =
        "CREATE TABLE events "
            + "(_id INTEGER PRIMARY KEY,"
            + " backend_id TEXT NOT NULL,"
            + " transport_name TEXT NOT NULL,"
            + " priority INTEGER NOT NULL,"
            + " timestamp_ms INTEGER NOT NULL,"
            + " uptime_ms INTEGER NOT NULL,"
            + " payload BLOB NOT NULL,"
            + " num_attempts INTEGER NOT NULL)";

    private static String CREATE_EVENT_METADATA_SQL =
        "CREATE TABLE event_metadata "
            + "(_id INTEGER PRIMARY KEY,"
            + " event_id INTEGER NOT NULL,"
            + " name TEXT NOT NULL,"
            + " value TEXT NOT NULL,"
            + "FOREIGN KEY (event_id) REFERENCES events(_id) ON DELETE CASCADE)";

    private static String CREATE_BACKENDS_SQL =
        "CREATE TABLE backends "
            + "(name TEXT PRIMARY KEY NOT NULL,"
            + " next_request_ms INTEGER NOT NULL)";

    private static String CREATE_EVENT_BACKEND_INDEX =
        "CREATE INDEX events_backend_id on events(backend_id)";

    private boolean configured = false;

    private OpenHelper(Context context) {
      super(context, DB_NAME, null, SCHEMA_VERSION);
    }

    @Override
    public void onConfigure(SQLiteDatabase db) {
      // Note that this is only called automatically by the SQLiteOpenHelper base class on Jelly
      // Bean and above.
      configured = true;
      // Threads/processes that access the database concurrently will not fail immediately, but
      // instead block with timeout. Since data access will only happen from one thread, this will
      // only happen for multi-process apps.
      Cursor cursor = db.rawQuery("PRAGMA busy_timeout = 5000", new String[0]);
      cursor.close();
    }

    private void ensureConfigured(SQLiteDatabase db) {
      if (!configured) {
        onConfigure(db);
      }
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
      ensureConfigured(db);
      db.execSQL(CREATE_EVENTS_SQL);
      db.execSQL(CREATE_EVENT_METADATA_SQL);
      db.execSQL(CREATE_BACKENDS_SQL);
      db.execSQL(CREATE_EVENT_BACKEND_INDEX);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      ensureConfigured(db);
    }

    @Override
    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
      ensureConfigured(db);
    }

    @Override
    public void onOpen(SQLiteDatabase db) {
      ensureConfigured(db);
    }
  }
}
