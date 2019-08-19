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

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.proto.MaybeDocument;
import com.google.firebase.firestore.util.Logger;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Timestamp;

/**
 * Migration logic to backfill data after a schema migration. Backfill logic in this class runs
 * unconditionally at client startup and is idempotent once the backfill completes.
 *
 * <p>Backfills are not guaranteed to process all entries at once. Instead, only a small subset is
 * migrated at client startup to decrease the overall impact on performance.
 */
class SQLiteDataBackfill {
  private static final String LOG_TAG = "SQLiteDataBackfill";

  @VisibleForTesting static final int BACKFILL_MIGRATION_SIZE = 100;

  private SQLiteDatabase db;

  SQLiteDataBackfill(SQLiteDatabase db) {
    this.db = db;
  }

  /** Synchronously run all data backfills. */
  void start() {
    db.beginTransaction();
    try {
      populateReadTime();
      db.setTransactionSuccessful();
    } finally {
      db.endTransaction();
    }
  }

  /** Populates the read time used during Index-Free query processing. */
  void populateReadTime() {
    SQLitePersistence.Query watermarkQuery =
        new SQLitePersistence.Query(
            db, "SELECT first_document_without_read_time FROM target_globals");
    watermarkQuery.first(
        value -> {
          // The read time migration sets the watermark to NULL when the migration
          // completes.
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
                          + "WHERE read_time_seconds IS NULL AND path >= ? ORDER BY path LIMIT ?")
                  .binding(encodedFirstDocumentWithoutReadTime, BACKFILL_MIGRATION_SIZE);

          query.forEach(
              cursor -> {
                ++rowCount[0];

                String encodedCurrentPath = cursor.getString(0);
                try {
                  Timestamp readTime = extractReadTime(cursor.getBlob(1));
                  if (readTime == null) {
                    Logger.warn(
                        LOG_TAG,
                        "Failed to detect document for key %s during read time backfill",
                        EncodedPath.decodeFieldPath(encodedCurrentPath));
                    return;
                  }

                  SQLiteStatement updateStatement =
                      db.compileStatement(
                          "UPDATE remote_documents SET read_time_seconds = ? and read_time_nanos = ? WHERE path = ?");
                  updateStatement.bindLong(1, readTime.getSeconds());
                  updateStatement.bindLong(2, readTime.getNanos());
                  updateStatement.bindString(3, encodedCurrentPath);
                  updateStatement.executeUpdateDelete();
                  lastMigratedKey[0] = encodedCurrentPath;
                } catch (InvalidProtocolBufferException e) {
                  Logger.warn(
                      LOG_TAG,
                      "Failed to decode document for key %s during read time backfill",
                      EncodedPath.decodeFieldPath(encodedCurrentPath));
                }
              });

          SQLiteStatement updateStatement =
              db.compileStatement("UPDATE target_globals SET first_document_without_read_time = ?");
          if (rowCount[0] == BACKFILL_MIGRATION_SIZE && lastMigratedKey[0] != null) {
            String nextKey = EncodedPath.prefixSuccessor(lastMigratedKey[0]);
            updateStatement.bindString(1, nextKey);
            Logger.debug(
                LOG_TAG, "Backfilled the read time for all documents up until %s", nextKey);
          } else {
            updateStatement.bindNull(1);
            Logger.debug(LOG_TAG, "Backfill for read time complete");
          }
          updateStatement.executeUpdateDelete();
        });
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
