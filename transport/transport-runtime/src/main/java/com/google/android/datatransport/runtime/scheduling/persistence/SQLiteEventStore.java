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
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabaseLockedException;
import android.os.SystemClock;
import android.util.Base64;
import androidx.annotation.Nullable;
import androidx.annotation.RestrictTo;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.logging.Logging;
import com.google.android.datatransport.runtime.synchronization.SynchronizationException;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.util.ArrayList;
import java.util.Arrays;
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

  private static final String LOG_TAG = "SQLiteEventStore";

  static final int MAX_RETRIES = 16;

  private static final int LOCK_RETRY_BACK_OFF_MILLIS = 50;
  private static final Encoding PROTOBUF_ENCODING = Encoding.of("proto");

  private final SchemaManager schemaManager;
  private final Clock wallClock;
  private final Clock monotonicClock;
  private final EventStoreConfig config;

  @Inject
  SQLiteEventStore(
      @WallTime Clock wallClock,
      @Monotonic Clock clock,
      EventStoreConfig config,
      SchemaManager schemaManager) {

    this.schemaManager = schemaManager;
    this.wallClock = wallClock;
    this.monotonicClock = clock;
    this.config = config;
  }

  @VisibleForTesting
  SQLiteDatabase getDb() {
    return retryIfDbLocked(
        schemaManager::getWritableDatabase,
        ex -> {
          throw new SynchronizationException("Timed out while trying to open db.", ex);
        });
  }

  @Override
  @Nullable
  public PersistedEvent persist(TransportContext transportContext, EventInternal event) {
    Logging.d(
        LOG_TAG,
        "Storing event with priority=%s, name=%s for destination %s",
        transportContext.getPriority(),
        event.getTransportName(),
        transportContext.getBackendName());
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
              int maxBlobSizePerRow = config.getMaxBlobByteSizePerRow();

              byte[] payloadBytes = event.getEncodedPayload().getBytes();
              boolean inline = payloadBytes.length <= maxBlobSizePerRow;
              ContentValues values = new ContentValues();
              values.put("context_id", contextId);
              values.put("transport_name", event.getTransportName());
              values.put("timestamp_ms", event.getEventMillis());
              values.put("uptime_ms", event.getUptimeMillis());
              values.put("payload_encoding", event.getEncodedPayload().getEncoding().getName());
              values.put("code", event.getCode());
              values.put("num_attempts", 0);
              values.put("inline", inline);
              values.put("payload", inline ? payloadBytes : new byte[0]);
              long newEventId = db.insert("events", null, values);
              if (!inline) {
                int numChunks = (int) Math.ceil((double) payloadBytes.length / maxBlobSizePerRow);

                for (int chunk = 1; chunk <= numChunks; chunk++) {
                  byte[] chunkBytes =
                      Arrays.copyOfRange(
                          payloadBytes,
                          (chunk - 1) * maxBlobSizePerRow,
                          Math.min((chunk) * maxBlobSizePerRow, payloadBytes.length));
                  ContentValues payloadValues = new ContentValues();
                  payloadValues.put("event_id", newEventId);
                  payloadValues.put("sequence_num", chunk);
                  payloadValues.put("bytes", chunkBytes);
                  db.insert("event_payloads", null, payloadValues);
                }
              }

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
    record.put("priority", PriorityMapping.toInt(transportContext.getPriority()));
    record.put("next_request_ms", 0);
    if (transportContext.getExtras() != null) {
      record.put("extras", Base64.encodeToString(transportContext.getExtras(), Base64.DEFAULT));
    }

    return db.insert("transport_contexts", null, record);
  }

  @Nullable
  private Long getTransportContextId(SQLiteDatabase db, TransportContext transportContext) {
    final StringBuilder selection = new StringBuilder("backend_name = ? and priority = ?");
    ArrayList<String> selectionArgs =
        new ArrayList<>(
            Arrays.asList(
                transportContext.getBackendName(),
                String.valueOf(PriorityMapping.toInt(transportContext.getPriority()))));

    if (transportContext.getExtras() != null) {
      selection.append(" and extras = ?");
      selectionArgs.add(Base64.encodeToString(transportContext.getExtras(), Base64.DEFAULT));
    }

    return tryWithCursor(
        db.query(
            "transport_contexts",
            new String[] {"_id"},
            selection.toString(),
            selectionArgs.toArray(new String[0]),
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
                  String.valueOf(PriorityMapping.toInt(transportContext.getPriority()))
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
                    String.valueOf(PriorityMapping.toInt(transportContext.getPriority()))
                  });

          if (rowsUpdated < 1) {
            values.put("backend_name", transportContext.getBackendName());
            values.put("priority", PriorityMapping.toInt(transportContext.getPriority()));
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
  public Iterable<TransportContext> loadActiveContexts() {
    return inTransaction(
        db ->
            tryWithCursor(
                db.rawQuery(
                    "SELECT distinct t._id, t.backend_name, t.priority, t.extras "
                        + "FROM transport_contexts AS t, events AS e WHERE e.context_id = t._id",
                    new String[] {}),
                cursor -> {
                  List<TransportContext> results = new ArrayList<>();
                  while (cursor.moveToNext()) {
                    results.add(
                        TransportContext.builder()
                            .setBackendName(cursor.getString(1))
                            .setPriority(PriorityMapping.valueOf(cursor.getInt(2)))
                            .setExtras(maybeBase64Decode(cursor.getString(3)))
                            .build());
                  }
                  return results;
                }));
  }

  @Override
  public int cleanUp() {
    long oneWeekAgo = wallClock.getTime() - config.getEventCleanUpAge();
    return inTransaction(
        db -> db.delete("events", "timestamp_ms < ?", new String[] {String.valueOf(oneWeekAgo)}));
  }

  @Override
  public void close() {
    schemaManager.close();
  }

  @RestrictTo(RestrictTo.Scope.TESTS)
  public void clearDb() {
    inTransaction(
        db -> {
          db.delete("events", null, new String[] {});
          db.delete("transport_contexts", null, new String[] {});
          return null;
        });
  }

  private static byte[] maybeBase64Decode(@Nullable String value) {
    if (value == null) {
      return null;
    }
    return Base64.decode(value, Base64.DEFAULT);
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
            new String[] {
              "_id",
              "transport_name",
              "timestamp_ms",
              "uptime_ms",
              "payload_encoding",
              "payload",
              "code",
              "inline",
            },
            "context_id = ?",
            new String[] {contextId.toString()},
            null,
            null,
            null,
            String.valueOf(config.getLoadBatchSize())),
        cursor -> {
          while (cursor.moveToNext()) {
            long id = cursor.getLong(0);
            boolean inline = cursor.getInt(7) != 0;
            EventInternal.Builder event =
                EventInternal.builder()
                    .setTransportName(cursor.getString(1))
                    .setEventMillis(cursor.getLong(2))
                    .setUptimeMillis(cursor.getLong(3));
            if (inline) {
              event.setEncodedPayload(
                  new EncodedPayload(toEncoding(cursor.getString(4)), cursor.getBlob(5)));
            } else {
              event.setEncodedPayload(
                  new EncodedPayload(toEncoding(cursor.getString(4)), readPayload(id)));
            }
            if (!cursor.isNull(6)) {
              event.setCode(cursor.getInt(6));
            }
            events.add(PersistedEvent.create(id, transportContext, event.build()));
          }
          return null;
        });
    return events;
  }

  private byte[] readPayload(long eventId) {
    return tryWithCursor(
        getDb()
            .query(
                "event_payloads",
                new String[] {"bytes"},
                "event_id = ?",
                new String[] {String.valueOf(eventId)},
                null,
                null,
                "sequence_num"),
        cursor -> {
          List<byte[]> chunks = new ArrayList<>();
          int totalLength = 0;
          while (cursor.moveToNext()) {
            byte[] chunk = cursor.getBlob(0);
            chunks.add(chunk);
            totalLength += chunk.length;
          }

          byte[] payloadBytes = new byte[totalLength];
          int offset = 0;
          for (int i = 0; i < chunks.size(); i++) {
            byte[] chunk = chunks.get(i);
            System.arraycopy(chunk, 0, payloadBytes, offset, chunk.length);
            offset += chunk.length;
          }
          return payloadBytes;
        });
  }

  private static Encoding toEncoding(@Nullable String value) {
    if (value == null) {
      return PROTOBUF_ENCODING;
    }
    return Encoding.of(value);
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
}
