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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.proto.WriteBatch;
import com.google.firestore.v1.Document;
import com.google.firestore.v1.Write;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** Tests migrations in SQLiteSchema. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteSchemaTest {

  private static final String[] NO_ARGS = new String[0];

  private SQLiteDatabase db;
  private SQLiteSchema schema;

  @Before
  public void setUp() {
    SQLiteOpenHelper opener =
        new SQLiteOpenHelper(RuntimeEnvironment.application, "foo", null, 1) {
          @Override
          public void onCreate(SQLiteDatabase db) {}

          @Override
          public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
        };
    db = opener.getWritableDatabase();
    schema = new SQLiteSchema(db);
  }

  @After
  public void tearDown() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  public void canRerunMigrations() {
    schema.runMigrations();
    // Run the whole thing again
    schema.runMigrations();
    // Run just a piece. Adds a column, make sure it doesn't throw
    schema.runMigrations(4, 6);
  }

  private Map<String, Set<String>> getCurrentSchema() {
    Map<String, Set<String>> tables = new HashMap<>();
    new SQLitePersistence.Query(db, "SELECT tbl_name FROM sqlite_master WHERE type = \"table\"")
        .forEach(
            c -> {
              String table = c.getString(0);
              Set<String> columns = new HashSet<>(schema.getTableColumns(table));
              tables.put(table, columns);
            });
    return tables;
  }

  private void assertNoRemovals(
      Map<String, Set<String>> oldSchema, Map<String, Set<String>> newSchema, int newVersion) {
    for (Map.Entry<String, Set<String>> entry : oldSchema.entrySet()) {
      String table = entry.getKey();
      Set<String> newColumns = newSchema.get(table);
      assertNotNull("Table " + table + " was deleted at version " + newVersion, newColumns);
      Set<String> oldColumns = entry.getValue();
      // We could use `Set.containsAll()`, but if we iterate we can point out the column that was
      // deleted.
      for (String column : oldColumns) {
        assertTrue(
            "Column " + column + " was deleted from table " + table + " at version " + newVersion,
            newColumns.contains(column));
      }
    }
  }

  @Test
  public void migrationsDontDeleteTablesOrColumns() {
    // In order to support users downgrading the SDK we need to make sure that every prior-released
    // version of the SDK can gracefully handle running against an upgraded schema. We can't
    // guarantee this in the general case, but this test at least ensures that no schema upgrade
    // deletes an existing table or column, which would be very likely to break old versions of the
    // SDK relying on that table or column.
    Map<String, Set<String>> tables = new HashMap<>();
    for (int toVersion = 1; toVersion <= SQLiteSchema.VERSION; toVersion++) {
      schema.runMigrations(toVersion - 1, toVersion);
      Map<String, Set<String>> newTables = getCurrentSchema();
      assertNoRemovals(tables, newTables, toVersion);
      tables = newTables;
    }
  }

  @Test
  public void canRecoverFromDowngrades() {
    for (int downgradeVersion = 0; downgradeVersion < SQLiteSchema.VERSION; downgradeVersion++) {
      // Upgrade schema to current, then upgrade from `downgradeVersion` to current
      schema.runMigrations();
      schema.runMigrations(downgradeVersion, SQLiteSchema.VERSION);
    }
  }

  @Test
  public void createsMutationsTable() {
    schema.runMigrations();

    assertNoResultsForQuery("SELECT uid, batch_id FROM mutations", NO_ARGS);

    db.execSQL("INSERT INTO mutations (uid, batch_id) VALUES ('foo', 1)");

    Cursor cursor = db.rawQuery("SELECT uid, batch_id FROM mutations", NO_ARGS);
    assertTrue(cursor.moveToFirst());
    assertEquals("foo", cursor.getString(cursor.getColumnIndex("uid")));
    assertEquals(1, cursor.getInt(cursor.getColumnIndex("batch_id")));

    assertFalse(cursor.moveToNext());
    cursor.close();
  }

  @Test
  public void deletesAllTargets() {
    schema.runMigrations(0, 2);

    db.execSQL("INSERT INTO targets (canonical_id, target_id) VALUES ('foo1', 1)");
    db.execSQL("INSERT INTO targets (canonical_id, target_id) VALUES ('foo2', 2)");
    db.execSQL("INSERT INTO target_globals (highest_target_id) VALUES (2)");

    db.execSQL("INSERT INTO target_documents (target_id, path) VALUES (1, 'foo/bar')");
    db.execSQL("INSERT INTO target_documents (target_id, path) VALUES (2, 'foo/baz')");

    schema.runMigrations(2, 3);

    assertNoResultsForQuery("SELECT * FROM targets", NO_ARGS);
    assertNoResultsForQuery("SELECT * FROM target_globals", NO_ARGS);
    assertNoResultsForQuery("SELECT * FROM target_documents", NO_ARGS);
  }

  @Test
  public void countsTargets() {
    schema.runMigrations(0, 3);
    long expected = 50;
    for (int i = 0; i < expected; i++) {
      db.execSQL(
          "INSERT INTO targets (canonical_id, target_id) VALUES (?, ?)",
          new String[] {"foo" + i, "" + i});
    }
    schema.runMigrations(3, 5);
    Cursor c = db.rawQuery("SELECT target_count FROM target_globals LIMIT 1", NO_ARGS);
    assertTrue(c.moveToFirst());
    long targetCount = c.getInt(0);
    assertEquals(expected, targetCount);
  }

  @Test
  public void testDatabaseName() {
    assertEquals(
        "firestore.%5BDEFAULT%5D.my-project.%28default%29",
        SQLitePersistence.databaseName("[DEFAULT]", DatabaseId.forProject("my-project")));
    assertEquals(
        "firestore.%5BDEFAULT%5D.my-project.my-database",
        SQLitePersistence.databaseName(
            "[DEFAULT]", DatabaseId.forDatabase("my-project", "my-database")));
  }

  @Test
  public void dropsHeldWriteAcks() {
    // This test creates a database with schema version 5 that has two users, both of which have
    // acknowledged mutations that haven't yet been removed from IndexedDb ("heldWriteAcks").
    // Schema version 6 removes heldWriteAcks, and as such these mutations are deleted.
    schema.runMigrations(0, 5);

    // User 'userA' has two acknowledged mutations and one that is pending.
    // User 'userB' has one acknowledged mutation and one that is pending.
    addMutationBatch(db, 1, "userA", "docs/foo");
    addMutationBatch(db, 2, "userA", "docs/foo");
    addMutationBatch(db, 3, "userB", "docs/bar", "doc/baz");
    addMutationBatch(db, 4, "userB", "docs/pending");
    addMutationBatch(db, 5, "userA", "docs/pending");

    // Populate the mutation queues' metadata
    db.execSQL(
        "INSERT INTO mutation_queues (uid, last_acknowledged_batch_id) VALUES (?, ?)",
        new Object[] {"userA", 2});
    db.execSQL(
        "INSERT INTO mutation_queues (uid, last_acknowledged_batch_id) VALUES (?, ?)",
        new Object[] {"userB", 3});
    db.execSQL(
        "INSERT INTO mutation_queues (uid, last_acknowledged_batch_id) VALUES (?, ?)",
        new Object[] {"userC", -1});

    schema.runMigrations(5, 6);

    // Verify that all but the two pending mutations have been cleared by the migration.
    new SQLitePersistence.Query(db, "SELECT COUNT(*) FROM mutations")
        .first(value -> assertEquals(2, value.getInt(0)));

    // Verify that we still have two index entries for the pending documents
    new SQLitePersistence.Query(db, "SELECT COUNT(*) FROM document_mutations")
        .first(value -> assertEquals(2, value.getInt(0)));

    // Verify that we still have one metadata entry for each existing queue
    new SQLitePersistence.Query(db, "SELECT COUNT(*) FROM mutation_queues")
        .first(value -> assertEquals(3, value.getInt(0)));
  }

  private void addMutationBatch(SQLiteDatabase db, int batchId, String uid, String... docs) {
    WriteBatch.Builder write = WriteBatch.newBuilder();
    write.setBatchId(batchId);

    for (String doc : docs) {
      db.execSQL(
          "INSERT INTO document_mutations (uid, path, batch_id) VALUES (?, ?, ?)",
          new Object[] {uid, EncodedPath.encode(ResourcePath.fromString(doc)), batchId});

      write.addWrites(
          Write.newBuilder()
              .setUpdate(
                  Document.newBuilder()
                      .setName("projects/projectId/databases/(default)/documents/" + doc)));
    }

    db.execSQL(
        "INSERT INTO mutations (uid, batch_id, mutations) VALUES (?,?,?)",
        new Object[] {uid, batchId, write.build().toByteArray()});
  }

  @Test
  public void addsSentinelRows() {
    schema.runMigrations(0, 6);

    long oldSequenceNumber = 1;
    // Set the highest sequence number to this value so that untagged documents
    // will pick up this value.
    long newSequenceNumber = 2;
    db.execSQL(
        "UPDATE target_globals SET highest_listen_sequence_number = ?",
        new Object[] {newSequenceNumber});

    // Set up some documents (we only need the keys)
    // For the odd ones, add sentinel rows.
    for (int i = 0; i < 10; i++) {
      String path = "docs/doc_" + i;
      db.execSQL("INSERT INTO remote_documents (path) VALUES (?)", new String[] {path});
      if (i % 2 == 1) {
        db.execSQL(
            "INSERT INTO target_documents (target_id, path, sequence_number) VALUES (0, ?, ?)",
            new Object[] {path, oldSequenceNumber});
      }
    }

    schema.runMigrations(6, 7);

    // Verify.
    new SQLitePersistence.Query(
            db, "SELECT path, sequence_number FROM target_documents WHERE target_id = 0")
        .forEach(
            row -> {
              String path = row.getString(0);
              long sequenceNumber = row.getLong(1);

              int docNum = Integer.parseInt(path.split("_", -1)[1]);
              // The even documents were missing sequence numbers, they should now be filled in
              // to have the new sequence number. The odd documents should have their
              // sequence number unchanged, and so be the old value.
              long expected = docNum % 2 == 1 ? oldSequenceNumber : newSequenceNumber;
              assertEquals(expected, sequenceNumber);
            });
  }

  private void assertNoResultsForQuery(String query, String[] args) {
    Cursor cursor = null;
    try {
      cursor = db.rawQuery(query, args);
      assertFalse(cursor.moveToFirst());
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }
  }
}
