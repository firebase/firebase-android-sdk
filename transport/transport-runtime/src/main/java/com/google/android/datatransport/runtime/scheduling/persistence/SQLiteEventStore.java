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

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Build;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import android.support.annotation.WorkerThread;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.synchronization.SynchronizationException;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import javax.inject.Singleton;

/** {@link EventStore} implementation backed by a SQLite database. */
@Singleton
@WorkerThread
public class SQLiteEventStore implements EventStore, SynchronizationGuard {

  static final int MAX_RETRIES = 10;

  private static final Priority[] ALL_PRIORITIES = Priority.values();

  @VisibleForTesting
  @RestrictTo(RestrictTo.Scope.TESTS)
  public static final int LOCK_RETRY_BACK_OFF_MILLIS = 50;

  private final OpenHelper openHelper;
  private final Clock monotonicClock;
  private SQLiteDatabase db;

  @Inject
  SQLiteEventStore(Context applicationContext, @Monotonic Clock clock) {
    this.openHelper = new OpenHelper(applicationContext);
    this.monotonicClock = clock;
  }

  private SQLiteDatabase getDb() {
    if (db == null) {
      db =
          retryIfDbLocked(
              1000,
              openHelper::getWritableDatabase,
              ex -> {
                throw new SynchronizationException("Timed out while trying to open db.", ex);
              });
    }
    return db;
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
    if (!events.iterator().hasNext()) {
      return;
    }
    String query =
        "UPDATE events SET num_attempts = num_attempts + 1 WHERE _id in " + toIdList(events);

    inTransaction(
        db -> {
          db.compileStatement(query).execute();
          db.compileStatement("DELETE FROM events WHERE num_attempts >= " + MAX_RETRIES).execute();
          return null;
        });
  }

  @Override
  public void recordSuccess(Iterable<PersistedEvent> events) {
    if (!events.iterator().hasNext()) {
      return;
    }

    String query = "DELETE FROM events WHERE _id in " + toIdList(events);
    getDb().compileStatement(query).execute();
  }

  private static String toIdList(Iterable<PersistedEvent> events) {
    StringBuilder idList = new StringBuilder("(");
    Iterator<PersistedEvent> iterator = events.iterator();
    while (iterator.hasNext()) {
      idList.append(iterator.next().getId());
      if (iterator.hasNext()) {
        idList.append(',');
      }
    }
    idList.append(')');
    return idList.toString();
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
          List<PersistedEvent> events = loadEvents(db, backendName);
          return join(events, loadMetadata(db, events));
        });
  }

  /** Loads all events for a backend. */
  private List<PersistedEvent> loadEvents(SQLiteDatabase db, String backendName) {
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
    return events;
  }

  /** Loads metadata pairs for given events. */
  private Map<Long, Set<Metadata>> loadMetadata(SQLiteDatabase db, List<PersistedEvent> events) {
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
    return metadataIndex;
  }

  /** Populate metadata into the events. */
  private List<PersistedEvent> join(
      List<PersistedEvent> events, Map<Long, Set<Metadata>> metadataIndex) {
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
  }

  private <T> T retryIfDbLocked(
      long lockTimeoutMs, Producer<T> retriable, Function<Throwable, T> failureHandler) {
    long startTime = monotonicClock.getTime();
    do {
      try {
        return retriable.produce();
      } catch (SQLiteDatabaseLockedException ex) {
        if (monotonicClock.getTime() >= startTime + lockTimeoutMs) {
          return failureHandler.apply(ex);
        }
        SystemClock.sleep(LOCK_RETRY_BACK_OFF_MILLIS);
      }
    } while (true);
  }

  interface Producer<T> {
    T produce();
  }

  interface Function<T, U> {
    U apply(T input);
  }

  /** Tries to start a transaction until it succeeds or times out. */
  private void ensureBeginTransaction(SQLiteDatabase db, long lockTimeoutMs) {
    retryIfDbLocked(
        lockTimeoutMs,
        () -> {
          db.beginTransaction();
          return null;
        },
        ex -> {
          throw new SynchronizationException("Timed out while trying to acquire the lock.", ex);
        });
  }

  @Override
  public <T> T runCriticalSection(long lockTimeoutMs, CriticalSection<T> criticalSection) {
    SQLiteDatabase db = getDb();
    ensureBeginTransaction(db, lockTimeoutMs);
    try {
      T result = criticalSection.execute();
      db.setTransactionSuccessful();
      return result;
    } finally {
      db.endTransaction();
    }
  }

  private <T> T inTransaction(Function<SQLiteDatabase, T> function) {
    SQLiteDatabase db = getDb();
    db.beginTransaction();
    try {
      T result = function.apply(db);
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
    // TODO: when we do schema upgrades in the future we need to make sure both downgrades and
    // upgrades work as expected, e.g. `up+down+up` is equivalent to `up`.
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

      db.rawQuery("PRAGMA busy_timeout=0;", new String[0]).close();

      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
        db.setForeignKeyConstraintsEnabled(true);
      }
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
