// Copyright 2021 Google LLC
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

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertNotNull;
import static junit.framework.TestCase.assertNull;

import com.google.firebase.firestore.index.IndexEntry;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteIndexBackfillerTest {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @AfterClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  @Rule public TestName name = new TestName();

  private SQLitePersistence persistence;
  private IndexBackfiller backfiller;

  @Before
  public void setUp() {
    persistence = PersistenceTestHelpers.createSQLitePersistence();
    backfiller = persistence.getIndexBackfiller();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  @Test
  public void addAndRemoveIndexEntry() {
    IndexEntry testEntry =
        new IndexEntry(1, "TEST_BLOB".getBytes(), "sample-uid", "sample-documentId");
    persistence.runTransaction(
        "testAddAndRemoveIndexEntry",
        () -> {
          backfiller.addIndexEntry(testEntry);
          IndexEntry entry = backfiller.getIndexEntry(1);
          assertNotNull(entry);
          assertEquals("TEST_BLOB", new String(entry.getIndexValue()));
          assertEquals("sample-documentId", entry.getDocumentId());
          assertEquals("sample-uid", entry.getUid());

          backfiller.removeIndexEntry(1, "sample-uid", "sample-documentId");
          entry = backfiller.getIndexEntry(1);
          assertNull(entry);
        });
  }
}
