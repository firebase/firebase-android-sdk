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

import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import android.text.TextUtils;
import android.util.Log;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.proto.Target;
import com.google.firebase.firestore.util.Consumer;
import com.google.firebase.firestore.util.Preconditions;
import com.google.protobuf.InvalidProtocolBufferException;
import java.util.ArrayList;
import java.util.List;

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
  static final int VERSION = 11;

  // Remove this constant and increment VERSION to enable indexing support
  static final int INDEXING_SUPPORT_VERSION = VERSION + 1;

  /**
   * The batch size for the sequence number migration in `ensureSequenceNumbers()`.
   *
   * <p>This addresses https://github.com/firebase/firebase-android-sdk/issues/370, where a customer
   * reported that schema migrations failed for clients with thousands of documents. The number has
   * been chosen based on manual experiments.
   */
  private static final int SEQUENCE_NUMBER_BATCH_SIZE = 100;

  private final SQLiteDatabase db;

  private final LocalSerializer serializer;

  SQLiteSchema(SQLiteDatabase db, LocalSerializer serializer) {
    this.db = db;
    this.serializer = serializer;
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
    /*
     * New migrations should be added at the end of the series of `if` statements and should follow
     * the pattern. Make sure to increment `VERSION` and to read the comment below about
     * requirements for new migrations.
     */

    if (fromVersion < 1 && toVersion >= 1) {
      createV1MutationQueue();
      createV1TargetCache();
      createV1RemoteDocumentCache();
    }

    // Migration 2 to populate the target_globals table no longer needed since migration 3
    // unconditionally clears it.

    if (fromVersion < 3 && toVersion >= 3) {
      // Brand new clients don't need to drop and recreate--only clients that have potentially
      // corrupt data.
      if (fromVersion != 0) {
        dropV1TargetCache();
        createV1TargetCache();
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

    if (fromVersion < 8 && toVersion >= 8) {
      createV8CollectionParentsIndex();
    }

    if (fromVersion < 9 && toVersion >= 9) {
      if (!hasReadTime()) {
        addReadTime();
      } else {
        // Index-free queries rely on the fact that documents updated after a query's last limbo
        // free snapshot version are persisted with their read-time. If a customer upgrades to
        // schema version 9, downgrades and then upgrades again, some queries may have a last limbo
        // free snapshot version despite the fact that not all updated document have an associated
        // read time.
        dropLastLimboFreeSnapshotVersion();
      }
    }

    if (fromVersion == 9 && toVersion >= 10) {
      // Firestore v21.10 contained a regression that led us to disable an assert that is required
      // to ensure data integrity. While the schema did not change between version 9 and 10, we use
      // the schema bump to version 10 to clear any affected data.
      dropLastLimboFreeSnapshotVersion();
    }

    if (fromVersion < 11 && toVersion >= 11) {
      // Schema version 11 changed the format of canonical IDs in the target cache.
      rewriteCanonicalIds();
    }

    /*
     * Adding a new migration? READ THIS FIRST!
     *
     * Be aware that the SDK version may be downgraded then re-upgraded. This means that running
     * your new migration must not prevent older versions of the SDK from functioning. Additionally,
     * your migration must be able to run multiple times. In practice, this means a few things:
     *  * Do not delete tables or columns. Older versions may be reading and writing them.
     *  * Guard schema additions. Check if tables or columns exist before adding them.
     *  * Data migrations should *probably* always run. Older versions of the SDK will not have
     *    maintained invariants from later versions, so migrations that update values cannot assume
     *    that existing values have been properly maintained. Calculate them again, if applicable.
     */

    if (fromVersion < INDEXING_SUPPORT_VERSION && toVersion >= INDEXING_SUPPORT_VERSION) {
      Preconditions.checkState(Persistence.INDEXING_SUPPORT_ENABLED);
      createLocalDocumentsCollectionIndex();
    }
  }

  /**
   * Used to assert that a set of tables either all exist or not. The supplied function is run if
   * none of the tables exist. Use this method to create a set of tables at once.
   *
   * <p>If some but not all of the tables exist, an exception will be thrown.
   */
  private void ifTablesDontExist(String[] tables, Runnable fn) {
    boolean tablesFound = false;
    String allTables = "[" + TextUtils.join(", ", tables) + "]";
    for (int i = 0; i < tables.length; i++) {
      String table = tables[i];
      boolean tableFound = tableExists(table);
      if (i == 0) {
        tablesFound = tableFound;
      } else if (tableFound != tablesFound) {
        String msg = "Expected all of " + allTables + " to either exist or not, but ";
        if (tablesFound) {
          msg += tables[0] + " exists and " + table + " does not";
        } else {
          msg += tables[0] + " does not exist and " + table + " does";
        }
        throw new IllegalStateException(msg);
      }
    }
    if (!tablesFound) {
      fn.run();
    } else {
      Log.d("SQLiteSchema", "Skipping migration because all of " + allTables + " already exist");
    }
  }

  private void createV1MutationQueue() {
    ifTablesDontExist(
        new String[] {"mutation_queues", "mutations", "document_mutations"},
        () -> {
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

          // A manually maintained index of all the mutation batches that affect a given document
          // key.
          // the rows in this table are references based on the contents of mutations.mutations.
          db.execSQL(
              "CREATE TABLE document_mutations ("
                  + "uid TEXT, "
                  + "path TEXT, "
                  + "batch_id INTEGER, "
                  + "PRIMARY KEY (uid, path, batch_id))");
        });
  }

  /** Note: as of this migration, `last_acknowledged_batch_id` is no longer used by the code. */
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

  private void createV1TargetCache() {
    ifTablesDontExist(
        new String[] {"targets", "target_globals", "target_documents"},
        () -> {
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
        });
  }

  private void dropV1TargetCache() {
    // This might be overkill, but if any future migration drops these, it's possible we could try
    // dropping tables that don't exist.
    if (tableExists("targets")) {
      db.execSQL("DROP TABLE targets");
    }
    if (tableExists("target_globals")) {
      db.execSQL("DROP TABLE target_globals");
    }
    if (tableExists("target_documents")) {
      db.execSQL("DROP TABLE target_documents");
    }
  }

  private void createV1RemoteDocumentCache() {
    ifTablesDontExist(
        new String[] {"remote_documents"},
        () -> {
          // A cache of documents obtained from the server.
          db.execSQL("CREATE TABLE remote_documents (path TEXT PRIMARY KEY, contents BLOB)");
        });
  }

  // TODO(indexing): Put the schema version in this method name.
  private void createLocalDocumentsCollectionIndex() {
    ifTablesDontExist(
        new String[] {"collection_index"},
        () -> {
          // A per-user, per-collection index for cached documents indexed by a single field's name
          // and value.
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
        });
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
    if (!tableContainsColumn("target_globals", "target_count")) {
      db.execSQL("ALTER TABLE target_globals ADD COLUMN target_count INTEGER");
    }
    // Even if the column already existed, rerun the data migration to make sure it's correct.
    long count = DatabaseUtils.queryNumEntries(db, "targets");
    ContentValues cv = new ContentValues();
    cv.put("target_count", count);
    db.update("target_globals", cv, null, null);
  }

  private void addSequenceNumber() {
    if (!tableContainsColumn("target_documents", "sequence_number")) {
      db.execSQL("ALTER TABLE target_documents ADD COLUMN sequence_number INTEGER");
    }
  }

  private boolean hasReadTime() {
    boolean hasReadTimeSeconds = tableContainsColumn("remote_documents", "read_time_seconds");
    boolean hasReadTimeNanos = tableContainsColumn("remote_documents", "read_time_nanos");

    hardAssert(
        hasReadTimeSeconds == hasReadTimeNanos,
        "Table contained just one of read_time_seconds or read_time_nanos");

    return hasReadTimeSeconds && hasReadTimeNanos;
  }

  private void addReadTime() {
    db.execSQL("ALTER TABLE remote_documents ADD COLUMN read_time_seconds INTEGER");
    db.execSQL("ALTER TABLE remote_documents ADD COLUMN read_time_nanos INTEGER");
  }

  private void dropLastLimboFreeSnapshotVersion() {
    new SQLitePersistence.Query(db, "SELECT target_id, target_proto FROM targets")
        .forEach(
            cursor -> {
              int targetId = cursor.getInt(0);
              byte[] targetProtoBytes = cursor.getBlob(1);

              try {
                Target targetProto = Target.parseFrom(targetProtoBytes);
                targetProto = targetProto.toBuilder().clearLastLimboFreeSnapshotVersion().build();
                db.execSQL(
                    "UPDATE targets SET target_proto = ? WHERE target_id = ?",
                    new Object[] {targetProto.toByteArray(), targetId});
              } catch (InvalidProtocolBufferException e) {
                throw fail("Failed to decode Query data for target %s", targetId);
              }
            });
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
                "SELECT RD.path FROM remote_documents AS RD WHERE NOT EXISTS ("
                    + "SELECT TD.path FROM target_documents AS TD "
                    + "WHERE RD.path = TD.path AND TD.target_id = 0"
                    + ") LIMIT ?")
            .binding(SEQUENCE_NUMBER_BATCH_SIZE);

    boolean[] resultsRemaining = new boolean[1];

    do {
      resultsRemaining[0] = false;

      untaggedDocumentsQuery.forEach(
          row -> {
            resultsRemaining[0] = true;
            tagDocument.clearBindings();
            tagDocument.bindString(1, row.getString(0));
            tagDocument.bindLong(2, sequenceNumber);
            hardAssert(tagDocument.executeInsert() != -1, "Failed to insert a sentinel row");
          });
    } while (resultsRemaining[0]);
  }

  private void createV8CollectionParentsIndex() {
    ifTablesDontExist(
        new String[] {"collection_parents"},
        () -> {
          // A table storing associations between a Collection ID (e.g. 'messages') to a parent path
          // (e.g. '/chats/123') that contains it as a (sub)collection. This is used to efficiently
          // find all collections to query when performing a Collection Group query. Note that the
          // parent path will be an empty path in the case of root-level collections.
          db.execSQL(
              "CREATE TABLE collection_parents ("
                  + "collection_id TEXT, "
                  + "parent TEXT, "
                  + "PRIMARY KEY(collection_id, parent))");
        });

    // Helper to add an index entry iff we haven't already written it.
    MemoryIndexManager.MemoryCollectionParentIndex cache =
        new MemoryIndexManager.MemoryCollectionParentIndex();
    SQLiteStatement addIndexEntry =
        db.compileStatement(
            "INSERT OR REPLACE INTO collection_parents (collection_id, parent) VALUES (?, ?)");
    Consumer<ResourcePath> addEntry =
        collectionPath -> {
          if (cache.add(collectionPath)) {
            String collectionId = collectionPath.getLastSegment();
            ResourcePath parentPath = collectionPath.popLast();
            addIndexEntry.clearBindings();
            addIndexEntry.bindString(1, collectionId);
            addIndexEntry.bindString(2, EncodedPath.encode(parentPath));
            addIndexEntry.execute();
          }
        };

    // Index existing remote documents.
    SQLitePersistence.Query remoteDocumentsQuery =
        new SQLitePersistence.Query(db, "SELECT path FROM remote_documents");
    remoteDocumentsQuery.forEach(
        row -> {
          ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
          addEntry.accept(path.popLast());
        });

    // Index existing mutations.
    SQLitePersistence.Query documentMutationsQuery =
        new SQLitePersistence.Query(db, "SELECT path FROM document_mutations");
    documentMutationsQuery.forEach(
        row -> {
          ResourcePath path = EncodedPath.decodeResourcePath(row.getString(0));
          addEntry.accept(path.popLast());
        });
  }

  private boolean tableContainsColumn(String table, String column) {
    List<String> columns = getTableColumns(table);
    return columns.indexOf(column) != -1;
  }

  @VisibleForTesting
  List<String> getTableColumns(String table) {
    // NOTE: SQLitePersistence.Query helper binding doesn't work with PRAGMA queries. So, just use
    // `rawQuery`.
    Cursor c = null;
    List<String> columns = new ArrayList<>();
    try {
      c = db.rawQuery("PRAGMA table_info(" + table + ")", null);
      int nameIndex = c.getColumnIndex("name");
      while (c.moveToNext()) {
        columns.add(c.getString(nameIndex));
      }
    } finally {
      if (c != null) {
        c.close();
      }
    }
    return columns;
  }

  private void rewriteCanonicalIds() {
    new SQLitePersistence.Query(db, "SELECT target_id, target_proto FROM targets")
        .forEach(
            cursor -> {
              int targetId = cursor.getInt(0);
              byte[] targetProtoBytes = cursor.getBlob(1);

              try {
                Target targetProto = Target.parseFrom(targetProtoBytes);
                TargetData targetData = serializer.decodeTargetData(targetProto);
                String updatedCanonicalId = targetData.getTarget().getCanonicalId();
                db.execSQL(
                    "UPDATE targets SET canonical_id  = ? WHERE target_id = ?",
                    new Object[] {updatedCanonicalId, targetId});
              } catch (InvalidProtocolBufferException e) {
                throw fail("Failed to decode Query data for target %s", targetId);
              }
            });
  };

  private boolean tableExists(String table) {
    return !new SQLitePersistence.Query(db, "SELECT 1=1 FROM sqlite_master WHERE tbl_name = ?")
        .binding(table)
        .isEmpty();
  }
}
