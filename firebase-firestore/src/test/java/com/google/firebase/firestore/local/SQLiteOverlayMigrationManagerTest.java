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

import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.mergeMutation;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.util.AsyncQueue;
import java.util.Collections;
import java.util.List;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteOverlayMigrationManagerTest {
  private Persistence persistence;
  private LocalStore localStore;

  @Before
  public void setUp() {
    // Setup persistence to version 12, which is before Overlay.
    persistence =
        PersistenceTestHelpers.createSQLitePersistenceForVersion("test-data-migration", 12);
    IndexBackfiller indexBackfiller = new IndexBackfiller(persistence, new AsyncQueue());
    localStore =
        new LocalStore(persistence, indexBackfiller, new QueryEngine(), User.UNAUTHENTICATED);
    localStore.start();
  }

  @After
  public void tearDown() {
    persistence.shutdown();
  }

  private void writeRemoteDocument(MutableDocument document) {
    // Set read time to update time.
    document = document.withReadTime(document.getVersion());
    persistence.getRemoteDocumentCache().add(document, document.getReadTime());
  }

  private void writeMutation(Mutation mutation) {
    writeMutations(asList(mutation));
  }

  private void writeMutations(List<Mutation> mutations) {
    LocalWriteResult result = localStore.writeLocally(mutations);
  }

  /** Asserts that the given local store contains the given document. */
  private void assertContains(MutableDocument expected) {
    Document actual = localStore.readDocument(expected.getKey());
    assertEquals(expected, actual);
  }

  @Test
  public void testCreateOverlayFromSet() {
    writeRemoteDocument(doc("foo/bar", 2, map("it", "original")));
    writeMutation(setMutation("foo/bar", map("foo", "bar")));

    // Switch to new persistence and run migrations
    this.persistence.shutdown();
    persistence = PersistenceTestHelpers.createSQLitePersistence("test-data-migration");
    IndexBackfiller indexBackfiller = new IndexBackfiller(persistence, new AsyncQueue());
    localStore =
        new LocalStore(persistence, indexBackfiller, new QueryEngine(), User.UNAUTHENTICATED);
    localStore.start();

    assertEquals(
        setMutation("foo/bar", map("foo", "bar")),
        persistence.getDocumentOverlay(User.UNAUTHENTICATED).getOverlay(key("foo/bar")));
    assertContains(doc("foo/bar", 2, map("foo", "bar")).setHasLocalMutations());

    SQLiteOverlayMigrationManager migrationManager =
        (SQLiteOverlayMigrationManager) persistence.getOverlayMigrationManager();
    assertFalse(migrationManager.hasPendingOverlayMigration());
  }

  @Test
  public void testCreateOverlayFromDelete() {
    writeRemoteDocument(doc("foo/bar", 2, map("it", "original")));
    writeMutation(deleteMutation("foo/bar"));

    // Switch to new persistence and run migrations
    this.persistence.shutdown();
    persistence = PersistenceTestHelpers.createSQLitePersistence("test-data-migration");
    IndexBackfiller indexBackfiller = new IndexBackfiller(persistence, new AsyncQueue());
    localStore =
        new LocalStore(persistence, indexBackfiller, new QueryEngine(), User.UNAUTHENTICATED);
    localStore.start();

    assertEquals(
        deleteMutation("foo/bar"),
        persistence.getDocumentOverlay(User.UNAUTHENTICATED).getOverlay(key("foo/bar")));
    assertContains(deletedDoc("foo/bar", 2).setHasLocalMutations());

    SQLiteOverlayMigrationManager migrationManager =
        (SQLiteOverlayMigrationManager) persistence.getOverlayMigrationManager();
    assertFalse(migrationManager.hasPendingOverlayMigration());
  }

  @Test
  public void testCreateOverlayFromPatches() {
    writeRemoteDocument(doc("foo/bar", 2, map("it", "original")));
    writeMutation(patchMutation("foo/bar", map("it", FieldValue.increment(1))));
    writeMutations(
        asList(
            patchMutation("foo/bar", map("it", FieldValue.increment(1))),
            mergeMutation(
                "foo/newBar", map("it", FieldValue.arrayUnion(1)), Collections.emptyList())));

    // Switch to new persistence and run migrations
    this.persistence.shutdown();
    persistence = PersistenceTestHelpers.createSQLitePersistence("test-data-migration");
    IndexBackfiller indexBackfiller = new IndexBackfiller(persistence, new AsyncQueue());
    localStore =
        new LocalStore(persistence, indexBackfiller, new QueryEngine(), User.UNAUTHENTICATED);
    localStore.start();

    DocumentOverlayCache overlay = persistence.getDocumentOverlay(User.UNAUTHENTICATED);
    assertEquals(
        mergeMutation("foo/bar", map("it", 2), Collections.emptyList()),
        overlay.getOverlay(key("foo/bar")));
    assertEquals(
        mergeMutation("foo/newBar", map("it", asList(1)), Collections.emptyList()),
        overlay.getOverlay(key("foo/newBar")));

    assertContains(doc("foo/bar", 2, map("it", 2)).setHasLocalMutations());
    assertContains(doc("foo/newBar", 0, map("it", asList(1))).setHasLocalMutations());

    SQLiteOverlayMigrationManager migrationManager =
        (SQLiteOverlayMigrationManager) persistence.getOverlayMigrationManager();
    assertFalse(migrationManager.hasPendingOverlayMigration());
  }

  @Test
  public void testCreateOverlayForDifferentUsers() {
    writeRemoteDocument(doc("foo/bar", 2, map("it", "original")));
    writeMutation(setMutation("foo/bar", map("foo", "set-by-unauthenticated")));

    // Switch to a different user
    IndexBackfiller indexBackfiller = new IndexBackfiller(persistence, new AsyncQueue());
    localStore =
        new LocalStore(persistence, indexBackfiller, new QueryEngine(), new User("another_user"));
    localStore.start();
    writeMutation(setMutation("foo/bar", map("foo", "set-by-another_user")));

    // Switch to new persistence and run migrations
    this.persistence.shutdown();
    persistence = PersistenceTestHelpers.createSQLitePersistence("test-data-migration");
    indexBackfiller = new IndexBackfiller(persistence, new AsyncQueue());
    localStore =
        new LocalStore(persistence, indexBackfiller, new QueryEngine(), User.UNAUTHENTICATED);
    localStore.start();

    assertEquals(
        setMutation("foo/bar", map("foo", "set-by-unauthenticated")),
        persistence.getDocumentOverlay(User.UNAUTHENTICATED).getOverlay(key("foo/bar")));
    assertEquals(
        setMutation("foo/bar", map("foo", "set-by-another_user")),
        persistence.getDocumentOverlay(new User("another_user")).getOverlay(key("foo/bar")));

    SQLiteOverlayMigrationManager migrationManager =
        (SQLiteOverlayMigrationManager) persistence.getOverlayMigrationManager();
    assertFalse(migrationManager.hasPendingOverlayMigration());
  }
}
