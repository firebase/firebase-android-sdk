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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.proto.MaybeDocument;
import com.google.firebase.firestore.util.AsyncQueue;
import com.google.firebase.firestore.util.Logger;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;
import java.util.concurrent.TimeUnit;

/**
 * Migration logic to backfill data after a schema migration. Backfills run periodically and migrate
 * a subset of data at a time until all data has been converted.
 */
class SQLiteDataBackfill {
  private static final String LOG_TAG = "SQLiteDataBackfill";

  private static final long MIGRATION_DELAY_MS = TimeUnit.SECONDS.toMillis(2);

  @VisibleForTesting static final int BACKFILL_BATCH_SIZE = 20;

  private final SQLiteDatabase db;
  private final AsyncQueue asyncQueue;
  private AsyncQueue.DelayedTask backfillTask;

  SQLiteDataBackfill(SQLiteDatabase db, AsyncQueue queue) {
    this.db = db;
    this.asyncQueue = queue;
  }

  /**
   * Schedules data backfill on the AsyncQueue. The backfill runs periodically until all data has
   * been processed. If all data has already been processed, the backfill exits early.
   */
  void start() {
    hardAssert(backfillTask == null, "start() called multiple times");
    enqueue();
  }

  private void enqueue() {
    backfillTask =
        asyncQueue.enqueueAfterDelay(
            AsyncQueue.TimerId.DATA_BACKFILL,
            MIGRATION_DELAY_MS,
            () -> {
              if (db.isOpen()) {
                boolean done;
                db.beginTransaction();
                try {
                  done = populateReadTime();
                  db.setTransactionSuccessful();
                } finally {
                  db.endTransaction();
                }

                if (!done) {
                  start();
                }
              }
            });
  }

  /**
   * Populates the read time used during Index-Free query processing.
   *
   * @return Whether the backfill finished.
   */
  boolean populateReadTime() {
    boolean[] done = new boolean[] {false};

    SQLitePersistence.Query watermarkQuery =
        new SQLitePersistence.Query(db, "SELECT read_time_backfill_watermark FROM target_globals");
    watermarkQuery.first(
        value -> {
          // The read time backfill sets the watermark to NULL when the migration completes.
          if (value.isNull(0)) {
            Logger.debug(LOG_TAG, "No read times to backfill");
            return;
          }

          int[] rowCount = new int[] {0};
          String[] lastMigratedKey = new String[] {null};

          String encodedFirstDocumentWithoutReadTime = value.getString(0);
          SQLitePersistence.Query query =
              new SQLitePersistence.Query(
                      db,
                      "SELECT path, contents FROM remote_documents "
                          + "WHERE read_time_seconds IS NULL AND path > ? ORDER BY path LIMIT ?")
                  .binding(encodedFirstDocumentWithoutReadTime, BACKFILL_BATCH_SIZE);

          query.forEach(
              cursor -> {
                ++rowCount[0];

                String encodedCurrentPath = cursor.getString(0);
                try {
                  Timestamp readTime = extractReadTime(cursor.getBlob(1));
                  hardAssert(
                      readTime != null,
                      "Failed to detect read time for document %s",
                      EncodedPath.decodeFieldPath(encodedCurrentPath));

                  SQLiteStatement updateStatement =
                      db.compileStatement(
                          "UPDATE remote_documents SET read_time_seconds = ? and read_time_nanos = ? WHERE path = ?");
                  updateStatement.bindLong(1, readTime.getSeconds());
                  updateStatement.bindLong(2, readTime.getNanos());
                  updateStatement.bindString(3, encodedCurrentPath);
                  updateStatement.executeUpdateDelete();
                  lastMigratedKey[0] = encodedCurrentPath;
                } catch (InvalidProtocolBufferException e) {
                  throw fail(
                      "Failed to decode document %s during read time backfill",
                      EncodedPath.decodeFieldPath(encodedCurrentPath));
                }
              });

          SQLiteStatement updateStatement =
              db.compileStatement("UPDATE target_globals SET read_time_backfill_watermark = ?");
          if (rowCount[0] == BACKFILL_BATCH_SIZE && lastMigratedKey[0] != null) {
            updateStatement.bindString(1, lastMigratedKey[0]);
            Logger.debug(
                LOG_TAG,
                "Backfilled the read time for all documents up until %s",
                lastMigratedKey[0]);
            done[0] = false;
          } else {
            updateStatement.bindNull(1);
            Logger.debug(LOG_TAG, "Backfill for read time complete");
            done[0] = true;
          }
          updateStatement.executeUpdateDelete();
        });

    return done[0];
  }

  @Nullable
  private Timestamp extractReadTime(byte[] documentData) throws InvalidProtocolBufferException {
    MaybeDocument maybeDocument = MaybeDocument.parseFrom(documentData);

    if (maybeDocument.getDocument() != null) {
      // Use the update time as the lower bound.
      return maybeDocument.getDocument().getUpdateTime();
    } else if (maybeDocument.getNoDocument() != null) {
      return maybeDocument.getNoDocument().getReadTime();
    } else if (maybeDocument.getUnknownDocument() != null) {
      return maybeDocument.getUnknownDocument().getVersion();
    } else {
      return null;
    }
  }
}
