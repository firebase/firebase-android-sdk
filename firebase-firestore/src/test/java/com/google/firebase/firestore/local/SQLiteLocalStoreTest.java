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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.TestUtil.addedRemoteEvent;
import static com.google.firebase.firestore.testutil.TestUtil.deleteMutation;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.keyMap;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.updateRemoteEvent;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.FieldIndex;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteLocalStoreTest extends LocalStoreTestCase {
  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Override
  boolean garbageCollectorIsEager() {
    return false;
  }

  @Test
  public void testAddsIndexes() {
    FieldIndex indexA =
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexB =
        fieldIndex("coll", 1, FieldIndex.INITIAL_STATE, "b", FieldIndex.Segment.Kind.DESCENDING);
    FieldIndex indexC =
        fieldIndex(
            "coll",
            2,
            FieldIndex.INITIAL_STATE,
            "c1",
            FieldIndex.Segment.Kind.ASCENDING,
            "c2",
            FieldIndex.Segment.Kind.CONTAINS);

    configureFieldIndexes(Arrays.asList(indexA, indexB));
    Collection<FieldIndex> fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexB);

    configureFieldIndexes(Arrays.asList(indexA, indexC));
    fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexC);
  }

  @Test
  public void testRemovesIndexes() {
    FieldIndex indexA =
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "a", FieldIndex.Segment.Kind.ASCENDING);
    FieldIndex indexB =
        fieldIndex("coll", 1, FieldIndex.INITIAL_STATE, "b", FieldIndex.Segment.Kind.DESCENDING);

    configureFieldIndexes(Arrays.asList(indexA, indexB));
    Collection<FieldIndex> fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA, indexB);

    configureFieldIndexes(singletonList(indexA));
    fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA);
  }

  @Test
  public void testDoesNotResetIndexWhenSameIndexIsAdded() {
    FieldIndex indexA =
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "a", FieldIndex.Segment.Kind.ASCENDING);

    configureFieldIndexes(singletonList(indexA));
    Collection<FieldIndex> fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(indexA);

    Query query = query("coll").filter(filter("a", "==", 1));
    int targetId = allocateQuery(query);
    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("a", 1)), targetId));

    backfillIndexes();
    FieldIndex updatedIndexA =
        fieldIndex(
            "coll",
            0,
            FieldIndex.IndexState.create(1, version(10), key("coll/a"), -1),
            "a",
            FieldIndex.Segment.Kind.ASCENDING);

    fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(updatedIndexA);

    // Re-add the same index. We do not reset the index to its initial state.
    configureFieldIndexes(singletonList(indexA));
    fieldIndexes = getFieldIndexes();
    assertThat(fieldIndexes).containsExactly(updatedIndexA);
  }

  @Test
  public void testDeletedDocumentRemovesIndex() {
    FieldIndex index =
        fieldIndex(
            "coll", 0, FieldIndex.INITIAL_STATE, "matches", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    Query query = query("coll").filter(filter("matches", "==", true));
    int targetId = allocateQuery(query);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("matches", true)), targetId));

    // Add the document to the index
    backfillIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 1, /* byCollection= */ 0);
    assertQueryReturned("coll/a");

    applyRemoteEvent(
        updateRemoteEvent(deletedDoc("coll/a", 0), singletonList(targetId), emptyList()));

    // No backfill needed for deleted document.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 0);
    assertQueryReturned();
  }

  @Test
  public void testUsesIndexes() {
    FieldIndex index =
        fieldIndex(
            "coll", 0, FieldIndex.INITIAL_STATE, "matches", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    Query query = query("coll").filter(filter("matches", "==", true));
    int targetId = allocateQuery(query);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("matches", true)), targetId));

    backfillIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 1, /* byCollection= */ 0);
    assertQueryReturned("coll/a");
  }

  @Test
  public void testUsesPartiallyIndexedRemoteDocumentsWhenAvailable() {
    FieldIndex index =
        fieldIndex(
            "coll", 0, FieldIndex.INITIAL_STATE, "matches", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    Query query = query("coll").filter(filter("matches", "==", true));
    int targetId = allocateQuery(query);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("matches", true)), targetId));
    backfillIndexes();

    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 20, map("matches", true)), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 1, /* byCollection= */ 1);
    assertQueryReturned("coll/a", "coll/b");
  }

  @Test
  public void testUsesPartiallyIndexedOverlaysWhenAvailable() {
    FieldIndex index =
        fieldIndex(
            "coll", 0, FieldIndex.INITIAL_STATE, "matches", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    writeMutation(setMutation("coll/a", map("matches", true)));
    backfillIndexes();

    writeMutation(setMutation("coll/b", map("matches", true)));

    Query query = query("coll").filter(filter("matches", "==", true));
    executeQuery(query);
    assertOverlaysRead(/* byKey= */ 1, /* byCollection= */ 1);
    assertOverlayTypes(
        keyMap(
            "coll/a",
            CountingQueryEngine.OverlayType.Set,
            "coll/b",
            CountingQueryEngine.OverlayType.Set));
    assertQueryReturned("coll/a", "coll/b");
  }

  @Test
  public void testDoesNotUseLimitWhenIndexIsOutdated() {
    FieldIndex index =
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "count", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    Query query = query("coll").orderBy(orderBy("count")).limitToFirst(2);
    int targetId = allocateQuery(query);

    applyRemoteEvent(
        addedRemoteEvent(
            Arrays.asList(
                doc("coll/a", 10, map("count", 1)),
                doc("coll/b", 10, map("count", 2)),
                doc("coll/c", 10, map("count", 3))),
            Collections.singletonList(targetId),
            Collections.emptyList()));
    backfillIndexes();

    writeMutation(deleteMutation("coll/b"));

    executeQuery(query);

    // The query engine first reads the documents by key and then re-runs the query without limit.
    assertRemoteDocumentsRead(/* byKey= */ 5, /* byCollection= */ 0);
    assertOverlaysRead(/* byKey= */ 5, /* byCollection= */ 1);
    assertOverlayTypes(keyMap("coll/b", CountingQueryEngine.OverlayType.Delete));
    assertQueryReturned("coll/a", "coll/c");
  }

  @Test
  public void testUsesIndexForLimitQueryWhenIndexIsUpdated() {
    FieldIndex index =
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "count", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    Query query = query("coll").orderBy(orderBy("count")).limitToFirst(2);
    int targetId = allocateQuery(query);

    applyRemoteEvent(
        addedRemoteEvent(
            Arrays.asList(
                doc("coll/a", 10, map("count", 1)),
                doc("coll/b", 10, map("count", 2)),
                doc("coll/c", 10, map("count", 3))),
            Collections.singletonList(targetId),
            Collections.emptyList()));
    writeMutation(deleteMutation("coll/b"));
    backfillIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 0);
    assertOverlaysRead(/* byKey= */ 2, /* byCollection= */ 0);
    assertQueryReturned("coll/a", "coll/c");
  }

  @Test
  public void testIndexesServerTimestamps() {
    FieldIndex index =
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "time", FieldIndex.Segment.Kind.ASCENDING);
    configureFieldIndexes(singletonList(index));

    writeMutation(setMutation("coll/a", map("time", FieldValue.serverTimestamp())));
    backfillIndexes();

    Query query = query("coll").orderBy(orderBy("time", "asc"));
    executeQuery(query);
    assertOverlaysRead(/* byKey= */ 1, /* byCollection= */ 0);
    assertOverlayTypes(keyMap("coll/a", CountingQueryEngine.OverlayType.Set));
    assertQueryReturned("coll/a");
  }
}
