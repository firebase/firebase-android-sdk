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
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.synchronization.SynchronizationException;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
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

  private static final int LOCK_RETRY_BACK_OFF_MILLIS = 50;

  private final OpenHelper openHelper;
  private final Clock wallClock;
  private final Clock monotonicClock;
  private final EventStoreConfig config;

  @Inject
  SQLiteEventStore(
      Context applicationContext,
      @WallTime Clock wallClock,
      @Monotonic Clock clock,
      EventStoreConfig config) {

    this.openHelper = new OpenHelper(applicationContext);
    this.wallClock = wallClock;
    this.monotonicClock = clock;
    this.config = config;
  }

  private SQLiteDatabase getDb() {
    return retryIfDbLocked(
        openHelper::getWritableDatabase,
        ex -> {
          throw new SynchronizationException("Timed out while trying to open db.", ex);
        });
  }

  @Override
  @Nullable
  public PersistedEvent persist(TransportContext transportContext, EventInternal event) {

    long newRowId =
        inTransaction(
            db -> {
              // drop new events until old ones are uploaded and removed.
              // TODO(vkryachko): come up with a more sophisticated algorithm for limiting disk
              // space.
              if (isStorageAtLimit()) {
                return -1L;
              }

              long contextId = ensureTransportContext(db, transportContext);
              ContentValues values = new ContentValues();
              values.put("context_id", contextId);
              values.put("transport_name", event.getTransportName());
              values.put("timestamp_ms", event.getEventMillis());
              values.put("uptime_ms", event.getUptimeMillis());
              values.put("payload", event.getPayload());
              values.put("code", event.getCode());
              values.put("num_attempts", 0);
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

    if (newRowId < 1) {
      return null;
    }
    return PersistedEvent.create(newRowId, transportContext, event);
  }

  private long ensureTransportContext(SQLiteDatabase db, TransportContext transportContext) {
    Long existingId = getTransportContextId(db, transportContext);
    if (existingId != null) {
      return existingId;
    }

    ContentValues record = new ContentValues();
    record.put("backend_name", transportContext.getBackendName());
    record.put("priority", transportContext.getPriority().ordinal());
    record.put("next_request_ms", 0);
    return db.insert("transport_contexts", null, record);
  }

  @Nullable
  private Long getTransportContextId(SQLiteDatabase db, TransportContext transportContext) {
    return tryWithCursor(
        db.query(
            "transport_contexts",
            new String[] {"_id"},
            "backend_name = ? and priority = ?",
            new String[] {
              transportContext.getBackendName(),
              String.valueOf(transportContext.getPriority().ordinal())
            },
            null,
            null,
            null),
        cursor -> {
          if (!cursor.moveToNext()) {
            return null;
          }
          return cursor.getLong(0);
        });
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
  public long getNextCallTime(TransportContext transportContext) {
    return tryWithCursor(
        getDb()
            .rawQuery(
                "SELECT next_request_ms FROM transport_contexts WHERE backend_name = ? and priority = ?",
                new String[] {
                  transportContext.getBackendName(),
                  String.valueOf(transportContext.getPriority().ordinal())
                }),
        cursor -> {
          if (cursor.moveToNext()) {
            return cursor.getLong(0);
          }
          return 0L;
        });
  }

  @Override
  public boolean hasPendingEventsFor(TransportContext transportContext) {
    return inTransaction(
        db -> {
          Long contextId = getTransportContextId(db, transportContext);
          if (contextId == null) {
            return false;
          }
          return tryWithCursor(
              getDb()
                  .rawQuery(
                      "SELECT 1 FROM events WHERE context_id = ? LIMIT 1",
                      new String[] {contextId.toString()}),
              Cursor::moveToNext);
        });
  }

  @Override
  public void recordNextCallTime(TransportContext transportContext, long timestampMs) {
    inTransaction(
        db -> {
          ContentValues values = new ContentValues();
          values.put("next_request_ms", timestampMs);
          int rowsUpdated =
              db.update(
                  "transport_contexts",
                  values,
                  "backend_name = ? and priority = ?",
                  new String[] {
                    transportContext.getBackendName(),
                    String.valueOf(transportContext.getPriority().ordinal())
                  });

          if (rowsUpdated < 1) {
            values.put("backend_name", transportContext.getBackendName());
            values.put("priority", transportContext.getPriority().ordinal());
            db.insert("transport_contexts", null, values);
          }
          return null;
        });
  }

  @Override
  public Iterable<PersistedEvent> loadBatch(TransportContext transportContext) {
    return inTransaction(
        db -> {
          List<PersistedEvent> events = loadEvents(db, transportContext);
          return join(events, loadMetadata(db, events));
        });
  }

  @Override
  public int cleanUp() {
    long oneWeekAgo = wallClock.getTime() - config.getEventCleanUpAge();
    return inTransaction(
        db -> db.delete("events", "timestamp_ms < ?", new String[] {String.valueOf(oneWeekAgo)}));
  }

  @Override
  public void close() {
    openHelper.close();
  }

  /** Loads all events for a backend. */
  private List<PersistedEvent> loadEvents(SQLiteDatabase db, TransportContext transportContext) {
    List<PersistedEvent> events = new ArrayList<>();
    Long contextId = getTransportContextId(db, transportContext);
    if (contextId == null) {
      return events;
    }

    tryWithCursor(
        db.query(
            "events",
            new String[] {"_id", "transport_name", "timestamp_ms", "uptime_ms", "payload", "code"},
            "context_id = ?",
            new String[] {contextId.toString()},
            null,
            null,
            null,
            String.valueOf(config.getLoadBatchSize())),
        cursor -> {
          while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            EventInternal.Builder event =
                EventInternal.builder()
                    .setTransportName(cursor.getString(1))
                    .setEventMillis(cursor.getLong(2))
                    .setUptimeMillis(cursor.getLong(3))
                    .setPayload(cursor.getBlob(4));
            if (!cursor.isNull(5)) {
              event.setCode(cursor.getInt(5));
            }
            events.add(PersistedEvent.create(id, transportContext, event.build()));
          }
          return null;
        });
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

    tryWithCursor(
        db.query(
            "event_metadata",
            new String[] {"event_id", "name", "value"},
            whereClause.toString(),
            null,
            null,
            null,
            null),
        cursor -> {
          while (cursor.moveToNext()) {
            long eventId = cursor.getLong(0);
            Set<Metadata> currentSet = metadataIndex.get(eventId);
            if (currentSet == null) {
              currentSet = new HashSet<>();
              metadataIndex.put(eventId, currentSet);
            }
            currentSet.add(new Metadata(cursor.getString(1), cursor.getString(2)));
          }
          return null;
        });
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
          PersistedEvent.create(current.getId(), current.getTransportContext(), newEvent.build()));
    }
    return events;
  }

  private <T> T retryIfDbLocked(Producer<T> retriable, Function<Throwable, T> failureHandler) {
    long startTime = monotonicClock.getTime();
    do {
      try {
        return retriable.produce();
      } catch (SQLiteDatabaseLockedException ex) {
        if (monotonicClock.getTime() >= startTime + config.getCriticalSectionEnterTimeoutMs()) {
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
  private void ensureBeginTransaction(SQLiteDatabase db) {
    retryIfDbLocked(
        () -> {
          db.beginTransaction();
          return null;
        },
        ex -> {
          throw new SynchronizationException("Timed out while trying to acquire the lock.", ex);
        });
  }

  @Override
  public <T> T runCriticalSection(CriticalSection<T> criticalSection) {
    SQLiteDatabase db = getDb();
    ensureBeginTransaction(db);
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

  private boolean isStorageAtLimit() {
    long byteSize = getPageCount() * getPageSize();

    return byteSize >= config.getMaxStorageSizeInBytes();
  }

  @VisibleForTesting
  long getByteSize() {
    return getPageCount() * getPageSize();
  }

  /** Gets the page size of the database. Typically 4096. */
  private long getPageSize() {
    return getDb().compileStatement("PRAGMA page_size").simpleQueryForLong();
  }

  /**
   * Gets the number of pages in the database file. Multiplying this with the page size yields the
   * approximate size of the database on disk (including the WAL, if relevant).
   */
  private long getPageCount() {
    return getDb().compileStatement("PRAGMA page_count").simpleQueryForLong();
  }

  private static <T> T tryWithCursor(Cursor c, Function<Cursor, T> function) {
    try {
      return function.apply(c);
    } finally {
      c.close();
    }
  }

  private static class OpenHelper extends SQLiteOpenHelper {
    // TODO: when we do schema upgrades in the future we need to make sure both downgrades and
    // upgrades work as expected, e.g. `up+down+up` is equivalent to `up`.
    private static int SCHEMA_VERSION = 1;
    private static String DB_NAME = "com.google.android.datatransport.events";
    private static String CREATE_EVENTS_SQL =
        "CREATE TABLE events "
            + "(_id INTEGER PRIMARY KEY,"
            + " context_id INTEGER NOT NULL,"
            + " transport_name TEXT NOT NULL,"
            + " timestamp_ms INTEGER NOT NULL,"
            + " uptime_ms INTEGER NOT NULL,"
            + " payload BLOB NOT NULL,"
            + " code INTEGER,"
            + " num_attempts INTEGER NOT NULL,"
            + "FOREIGN KEY (context_id) REFERENCES transport_contexts(_id) ON DELETE CASCADE)";

    private static String CREATE_EVENT_METADATA_SQL =
        "CREATE TABLE event_metadata "
            + "(_id INTEGER PRIMARY KEY,"
            + " event_id INTEGER NOT NULL,"
            + " name TEXT NOT NULL,"
            + " value TEXT NOT NULL,"
            + "FOREIGN KEY (event_id) REFERENCES events(_id) ON DELETE CASCADE)";

    private static String CREATE_CONTEXTS_SQL =
        "CREATE TABLE transport_contexts "
            + "(_id INTEGER PRIMARY KEY,"
            + " backend_name TEXT NOT NULL,"
            + " priority INTEGER NOT NULL,"
            + " next_request_ms INTEGER NOT NULL)";

    private static String CREATE_EVENT_BACKEND_INDEX =
        "CREATE INDEX events_backend_id on events(context_id)";

    private static String CREATE_CONTEXT_BACKEND_PRIORITY_INDEX =
        "CREATE UNIQUE INDEX contexts_backend_priority on transport_contexts(backend_name, priority)";

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
      db.execSQL(CREATE_CONTEXTS_SQL);
      db.execSQL(CREATE_EVENT_BACKEND_INDEX);
      db.execSQL(CREATE_CONTEXT_BACKEND_PRIORITY_INDEX);
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
