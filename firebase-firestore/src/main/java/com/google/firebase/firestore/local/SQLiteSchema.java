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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.content.ContentValues;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import com.google.common.base.Preconditions;

/**
 * Migrates schemas from version 0 (empty) to whatever the current version is.
 *
 * <p>Migrations are numbered for the version of the database they apply to. The VERSION constant in
 * this class should be one more than the highest numbered migration.
 *
 * <p>NOTE: Once we ship code with a migration in it the code for that migration should never be
 * changed again. Further changes can be made to the schema by adding a new migration method,
 * bumping the VERSION, and adding a call to the migration method from runMigrations.
 */
class SQLiteSchema {

  /**
   * The version of the schema. Increase this by one for each migration added to runMigrations
   * below.
   */
  static final int VERSION = 7;
  // Remove this constant and increment VERSION to enable indexing support
  static final int INDEXING_SUPPORT_VERSION = VERSION + 1;

  private final SQLiteDatabase db;

  // PORTING NOTE: The Android client doesn't need to use a serializer to remove held write acks.
  SQLiteSchema(SQLiteDatabase db) {
    this.db = db;
  }

  void runMigrations() {
    runMigrations(0, VERSION);
  }

  void runMigrations(int fromVersion) {
    runMigrations(fromVersion, VERSION);
  }

  /**
   * Runs the migration methods defined in this class, starting at the given version.
   *
   * @param fromVersion The version the database is starting at. When first created it will be zero.
   * @param toVersion The version the database is migrating to. Usually VERSION, but can be
   *     otherwise for testing.
   */
  void runMigrations(int fromVersion, int toVersion) {
    // Each case in this switch statement intentionally falls through to the one below it, making
    // it possible to start at the version that's installed and then run through any that haven't
    // been applied yet.

    if (fromVersion < 1 && toVersion >= 1) {
      createMutationQueue();
      createQueryCache();
      createRemoteDocumentCache();
    }

    // Migration 2 to populate the target_globals table no longer needed since migration 3
    // unconditionally clears it.

    if (fromVersion < 3 && toVersion >= 3) {
      // Brand new clients don't need to drop and recreate--only clients that have potentially
      // corrupt data.
      if (fromVersion != 0) {
        dropQueryCache();
        createQueryCache();
      }
    }

    if (fromVersion < 4 && toVersion >= 4) {
      ensureTargetGlobal();
      addTargetCount();
    }

    if (fromVersion < 5 && toVersion >= 5) {
      addSequenceNumber();
    }

    if (fromVersion < 6 && toVersion >= 6) {
      removeAcknowledgedMutations();
    }

    if (fromVersion < 7 && toVersion >= 7) {
      ensureSequenceNumbers();
    }

    if (fromVersion < INDEXING_SUPPORT_VERSION && toVersion >= INDEXING_SUPPORT_VERSION) {
      Preconditions.checkState(Persistence.INDEXING_SUPPORT_ENABLED);
      createLocalDocumentsCollectionIndex();
    }
  }

  private void createMutationQueue() {
    // A table naming all the mutation queues in the system.
    db.execSQL(
        "CREATE TABLE mutation_queues ("
            + "uid TEXT PRIMARY KEY, "
            + "last_acknowledged_batch_id INTEGER, "
            + "last_stream_token BLOB)");

    // All the mutation batches in the system, partitioned by user.
    db.execSQL(
        "CREATE TABLE mutations ("
            + "uid TEXT, "
            + "batch_id INTEGER, "
            + "mutations BLOB, "
            + "PRIMARY KEY (uid, batch_id))");

    // A manually maintained index of all the mutation batches that affect a given document key.
    // the rows in this table are references based on the contents of mutations.mutations.
    db.execSQL(
        "CREATE TABLE document_mutations ("
            + "uid TEXT, "
            + "path TEXT, "
            + "batch_id INTEGER, "
            + "PRIMARY KEY (uid, path, batch_id))");
  }

  private void removeAcknowledgedMutations() {
    SQLitePersistence.Query mutationQueuesQuery =
        new SQLitePersistence.Query(
            db, "SELECT uid, last_acknowledged_batch_id FROM mutation_queues");

    mutationQueuesQuery.forEach(
        mutationQueueEntry -> {
          String uid = mutationQueueEntry.getString(0);
          long lastAcknowledgedBatchId = mutationQueueEntry.getLong(1);

          SQLitePersistence.Query mutationsQuery =
              new SQLitePersistence.Query(
                      db, "SELECT batch_id FROM mutations WHERE uid = ? AND batch_id <= ?")
                  .binding(uid, lastAcknowledgedBatchId);
          mutationsQuery.forEach(value -> removeMutationBatch(uid, value.getInt(0)));
        });
  }

  private void removeMutationBatch(String uid, int batchId) {
    SQLiteStatement mutationDeleter =
        db.compileStatement("DELETE FROM mutations WHERE uid = ? AND batch_id = ?");
    mutationDeleter.bindString(1, uid);
    mutationDeleter.bindLong(2, batchId);
    int deleted = mutationDeleter.executeUpdateDelete();
    hardAssert(deleted != 0, "Mutatiohn batch (%s, %d) did not exist", uid, batchId);

    // Delete all index entries for this batch
    db.execSQL(
        "DELETE FROM document_mutations WHERE uid = ? AND batch_id = ?",
        new Object[] {uid, batchId});
  }

  private void createQueryCache() {
    // A cache of targets and associated metadata
    db.execSQL(
        "CREATE TABLE targets ("
            + "target_id INTEGER PRIMARY KEY, "
            + "canonical_id TEXT, "
            + "snapshot_version_seconds INTEGER, "
            + "snapshot_version_nanos INTEGER, "
            + "resume_token BLOB, "
            + "last_listen_sequence_number INTEGER,"
            + "target_proto BLOB)");

    db.execSQL("CREATE INDEX query_targets ON targets (canonical_id, target_id)");

    // Global state tracked across all queries, tracked separately
    db.execSQL(
        "CREATE TABLE target_globals ("
            + "highest_target_id INTEGER, "
            + "highest_listen_sequence_number INTEGER, "
            + "last_remote_snapshot_version_seconds INTEGER, "
            + "last_remote_snapshot_version_nanos INTEGER)");

    // A Mapping table between targets and document paths
    db.execSQL(
        "CREATE TABLE target_documents ("
            + "target_id INTEGER, "
            + "path TEXT, "
            + "PRIMARY KEY (target_id, path))");

    // The document_targets reverse mapping table is just an index on target_documents.
    db.execSQL("CREATE INDEX document_targets ON target_documents (path, target_id)");
  }

  private void dropQueryCache() {
    db.execSQL("DROP TABLE targets");
    db.execSQL("DROP TABLE target_globals");
    db.execSQL("DROP TABLE target_documents");
  }

  private void createRemoteDocumentCache() {
    // A cache of documents obtained from the server.
    db.execSQL("CREATE TABLE remote_documents (path TEXT PRIMARY KEY, contents BLOB)");
  }

  private void createLocalDocumentsCollectionIndex() {
    // A per-user, per-collection index for cached documents indexed by a single field's name and
    // value.
    db.execSQL(
        "CREATE TABLE collection_index ("
            + "uid TEXT, "
            + "collection_path TEXT, "
            + "field_path TEXT, "
            + "field_value_type INTEGER, " // determines type of field_value fields.
            + "field_value_1, " // first component
            + "field_value_2, " // second component; required for timestamps, GeoPoints
            + "document_id TEXT, "
            + "PRIMARY KEY (uid, collection_path, field_path, field_value_type, field_value_1, "
            + "field_value_2, document_id))");
  }

  // Note that this runs before we add the target count column, so we don't populate it yet.
  private void ensureTargetGlobal() {
    boolean targetGlobalExists = DatabaseUtils.queryNumEntries(db, "target_globals") == 1;
    if (!targetGlobalExists) {
      db.execSQL(
          "INSERT INTO target_globals (highest_target_id, highest_listen_sequence_number, "
              + "last_remote_snapshot_version_seconds, last_remote_snapshot_version_nanos) "
              + "VALUES (?, ?, ?, ?)",
          new String[] {"0", "0", "0", "0"});
    }
  }

  private void addTargetCount() {
    long count = DatabaseUtils.queryNumEntries(db, "targets");
    db.execSQL("ALTER TABLE target_globals ADD COLUMN target_count INTEGER");
    ContentValues cv = new ContentValues();
    cv.put("target_count", count);
    db.update("target_globals", cv, null, null);
  }

  private void addSequenceNumber() {
    db.execSQL("ALTER TABLE target_documents ADD COLUMN sequence_number INTEGER");
  }

  /**
   * Ensures that each entry in the remote document cache has a corresponding sentinel row. Any
   * entries that lack a sentinel row are given one with the sequence number set to the highest
   * recorded sequence number from the target metadata.
   */
  private void ensureSequenceNumbers() {
    // Get the current highest sequence number
    SQLitePersistence.Query sequenceNumberQuery =
        new SQLitePersistence.Query(
            db, "SELECT highest_listen_sequence_number FROM target_globals LIMIT 1");
    Long boxedSequenceNumber = sequenceNumberQuery.firstValue(c -> c.getLong(0));
    hardAssert(boxedSequenceNumber != null, "Missing highest sequence number");

    long sequenceNumber = boxedSequenceNumber;
    SQLiteStatement tagDocument =
        db.compileStatement(
            "INSERT INTO target_documents (target_id, path, sequence_number) VALUES (0, ?, ?)");
    SQLitePersistence.Query untaggedDocumentsQuery =
        new SQLitePersistence.Query(
            db,
            "SELECT RD.path FROM remote_documents AS RD WHERE NOT EXISTS (SELECT TD.path FROM target_documents AS TD WHERE RD.path = TD.path AND TD.target_id = 0)");
    untaggedDocumentsQuery.forEach(
        row -> {
          tagDocument.clearBindings();
          tagDocument.bindString(1, row.getString(0));
          tagDocument.bindLong(2, sequenceNumber);
          hardAssert(tagDocument.executeInsert() != -1, "Failed to insert a sentinel row");
        });
  }
}
