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

import static com.google.firebase.firestore.local.EncodedPath.encode;
import static com.google.firebase.firestore.local.PersistenceTestHelpers.createDummyDocument;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteDataBackfillTest {

  private SQLiteDatabase db;
  private SQLiteSchema schema;
  private SQLiteDataBackfill backfill;

  @Before
  public void setUp() {
    SQLiteOpenHelper opener =
        new SQLiteOpenHelper(ApplicationProvider.getApplicationContext(), "foo", null, 1) {
          @Override
          public void onCreate(SQLiteDatabase db) {}

          @Override
          public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {}
        };
    db = opener.getWritableDatabase();
    backfill = new SQLiteDataBackfill(db);
    schema = new SQLiteSchema(db);
  }

  @After
  public void tearDown() {
    if (db != null) {
      db.close();
    }
  }

  @Test
  public void populatesReadTimeDuringBackfill() {
    // Initialize the schema to the state prior to the index-free migration.
    schema.runMigrations(0, 8);

    // Use one more document than fits in a single batch.
    int documentCount = SQLiteDataBackfill.BACKFILL_MIGRATION_SIZE + 1;
    for (int i = 0; i < documentCount; ++i) {
      String paddedPath = String.format("coll/doc_%03d", i);
      db.execSQL(
          "INSERT INTO remote_documents (path, contents) VALUES (?, ?)",
          new Object[] {encode(path(paddedPath)), createDummyDocument(paddedPath)});
    }

    // Run the index-free migration.
    schema.runMigrations(8, 9);

    verifyRemainingBackfillCount(documentCount);
    verifyReadTimeWatermark(encode(path("coll/doc_000")));

    backfill.populateReadTime();
    verifyRemainingBackfillCount(1);
    verifyReadTimeWatermark(EncodedPath.prefixSuccessor(encode(path("coll/doc_099"))));

    backfill.populateReadTime();
    verifyRemainingBackfillCount(0);
    verifyReadTimeWatermark(null);

    // Verify that we can call `populateReadTime()` even when there is nothing left to migrate
    backfill.populateReadTime();
    verifyRemainingBackfillCount(0);
    verifyReadTimeWatermark(null);
  }

  @Test
  public void runsOnEmptyDatabase() {
    // Initialize the schema to the state prior to the index-free migration.
    schema.runMigrations(0, 9);
    verifyRemainingBackfillCount(0);
    verifyReadTimeWatermark(null);

    backfill.populateReadTime();
    verifyRemainingBackfillCount(0);
    verifyReadTimeWatermark(null);
  }

  private void verifyReadTimeWatermark(@Nullable String expectedWatermark) {
    new SQLitePersistence.Query(db, "SELECT first_document_without_read_time FROM target_globals")
        .first(
            value -> {
              if (expectedWatermark != null) {
                assertEquals(expectedWatermark, value.getString(0));
              } else {
                assertTrue(value.isNull(0));
              }
            });
  }

  private void verifyRemainingBackfillCount(int expectedDocumentCount) {
    new SQLitePersistence.Query(
            db, "SELECT COUNT(*) FROM remote_documents WHERE read_time_seconds IS NULL")
        .first(value -> assertEquals(expectedDocumentCount, value.getInt(0)));
  }
}
