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
import static com.google.firebase.firestore.testutil.TestUtil.orFilters;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static com.google.firebase.firestore.testutil.TestUtil.updateRemoteEvent;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ObjectValue;
import com.google.firebase.firestore.model.ServerTimestamps;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.FieldTransform;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.Precondition;
import com.google.firebase.firestore.model.mutation.ServerTimestampOperation;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
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

  @Test
  public void testDeeplyNestedServerTimestamps() {
    Timestamp timestamp = Timestamp.now();
    Value initialServerTimestamp = ServerTimestamps.valueOf(timestamp, null);
    Map<String, Value> fields =
        new HashMap<String, Value>() {
          {
            put("timestamp", ServerTimestamps.valueOf(timestamp, initialServerTimestamp));
          }
        };
    FieldPath path = FieldPath.fromSingleSegment("timestamp");
    FieldMask mask =
        FieldMask.fromSet(
            new HashSet<FieldPath>() {
              {
                add(path);
              }
            });
    FieldTransform fieldTransform =
        new FieldTransform(path, ServerTimestampOperation.getInstance());
    List<FieldTransform> fieldTransforms =
        new ArrayList<FieldTransform>() {
          {
            add(fieldTransform);
          }
        };
    // The purpose of this test is to ensure that deeply nested server timestamps do not result in
    // a stack overflow error. Below we use a `Thread` object to create a large number of mutations
    // because the `Thread` class allows us to specify the maximum stack size.
    AtomicReference<Throwable> error = new AtomicReference<>();
    Thread thread =
        new Thread(
            Thread.currentThread().getThreadGroup(),
            () -> {
              try {
                for (int i = 0; i < 1000; ++i) {
                  writeMutation(
                      new PatchMutation(
                          DocumentKey.fromPathString("some/object/for/test"),
                          ObjectValue.fromMap(fields),
                          mask,
                          Precondition.NONE,
                          fieldTransforms));
                }
              } catch (Throwable e) {
                error.set(e);
              }
            },
            /* name */ "test",
            /* stackSize */ 1024 * 1024);
    try {
      thread.start();
      thread.join();
    } catch (InterruptedException e) {
      throw new AssertionError(e);
    }
    assertThat(error.get()).isNull();
  }

  @Test
  public void testCanAutoCreateIndexes() {
    Query query = query("coll").filter(filter("matches", "==", true));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("matches", true)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("matches", false)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("matches", false)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("matches", false)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("matches", true)), targetId));

    // First time query runs without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index should be created.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    backfillIndexes();

    applyRemoteEvent(addedRemoteEvent(doc("coll/f", 20, map("matches", true)), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 1);
    assertQueryReturned("coll/a", "coll/e", "coll/f");
  }

  @Test
  public void testCanAutoCreateIndexesWorksWithOrQuery() {
    Query query = query("coll").filter(orFilters(filter("a", "==", 3), filter("b", "==", true)));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("b", true)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("b", false)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("a", 5, "b", false)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("a", true)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("a", 3, "b", true)), targetId));

    // First time query runs without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index should be created.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/e", "coll/a");

    backfillIndexes();

    applyRemoteEvent(addedRemoteEvent(doc("coll/f", 20, map("a", 3, "b", false)), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 1);
    assertQueryReturned("coll/f", "coll/e", "coll/a");
  }

  @Test
  public void testDoesNotAutoCreateIndexesForSmallCollections() {
    Query query = query("coll").filter(filter("foo", "==", 9)).filter(filter("count", ">=", 3));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("foo", 9, "count", 5)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("foo", 8, "count", 6)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("foo", 9, "count", 0)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("count", 4)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("foo", 9, "count", 3)), targetId));

    // SDK will not create indexes since collection size is too small.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    backfillIndexes();

    applyRemoteEvent(addedRemoteEvent(doc("coll/f", 20, map("foo", 9, "count", 4)), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 3);
    assertQueryReturned("coll/a", "coll/e", "coll/f");
  }

  @Test
  public void testDoesNotAutoCreateIndexesWhenIndexLookUpIsExpensive() {
    Query query = query("coll").filter(filter("array", "array-contains-any", Arrays.asList(0, 7)));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(5);

    applyRemoteEvent(
        addedRemoteEvent(doc("coll/a", 10, map("array", Arrays.asList(2, 7))), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("array", emptyList())), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("array", singletonList(3))), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/d", 10, map("array", Arrays.asList(2, 10, 20))), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/e", 10, map("array", Arrays.asList(2, 0, 8))), targetId));

    // SDK will not create indexes since relative read cost is too large.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    backfillIndexes();

    applyRemoteEvent(addedRemoteEvent(doc("coll/f", 20, map("array", singletonList(0))), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 3);
    assertQueryReturned("coll/a", "coll/e", "coll/f");
  }

  @Test
  public void testIndexAutoCreationWorksWhenBackfillerRunsHalfway() {
    Query query =
        query("coll").filter(filter("matches", "==", "foo")).filter(filter("count", ">", 10));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(
        addedRemoteEvent(doc("coll/a", 10, map("matches", "foo", "count", 11)), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/b", 10, map("matches", "foo", "count", 9)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("matches", "foo")), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("matches", 7, "count", 11)), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/e", 10, map("matches", "foo", "count", 21)), targetId));

    // First time query is running without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index should be created.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    setBackfillerMaxDocumentsToProcess(2);
    backfillIndexes();

    applyRemoteEvent(
        addedRemoteEvent(doc("coll/f", 20, map("matches", "foo", "count", 15)), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 1, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e", "coll/f");
  }

  @Test
  public void testIndexCreatedByIndexAutoCreationExistsAfterTurnOffAutoCreation() {
    Query query = query("coll").filter(filter("value", "not-in", Collections.singletonList(3)));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("value", 5)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("value", 3)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("value", 3)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("value", 3)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("value", 2)), targetId));

    // First time query runs without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index should be created.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    setIndexAutoCreationEnabled(false);

    backfillIndexes();

    applyRemoteEvent(addedRemoteEvent(doc("coll/f", 20, map("value", 7)), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 1);
    assertQueryReturned("coll/a", "coll/e", "coll/f");
  }

  @Test
  public void testDisableIndexAutoCreationWorks() {
    Query query1 = query("coll").filter(filter("value", "in", Arrays.asList(0, 1)));
    int targetId1 = allocateQuery(query1);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("value", 1)), targetId1));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("value", 8)), targetId1));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("value", "string")), targetId1));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("value", false)), targetId1));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("value", 0)), targetId1));

    // First time query is running without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index should be created.
    executeQuery(query1);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    setIndexAutoCreationEnabled(false);

    backfillIndexes();

    executeQuery(query1);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 0);
    assertQueryReturned("coll/a", "coll/e");

    Query query2 = query("foo").filter(filter("value", "!=", Double.NaN));
    int targetId2 = allocateQuery(query2);

    applyRemoteEvent(addedRemoteEvent(doc("foo/a", 10, map("value", 5)), targetId2));
    applyRemoteEvent(addedRemoteEvent(doc("foo/b", 10, map("value", Double.NaN)), targetId2));
    applyRemoteEvent(addedRemoteEvent(doc("foo/c", 10, map("value", Double.NaN)), targetId2));
    applyRemoteEvent(addedRemoteEvent(doc("foo/d", 10, map("value", Double.NaN)), targetId2));
    applyRemoteEvent(addedRemoteEvent(doc("foo/e", 10, map("value", "string")), targetId2));

    executeQuery(query2);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("foo/a", "foo/e");

    backfillIndexes();

    // Run the query in second time, test index won't be created
    executeQuery(query2);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("foo/a", "foo/e");
  }

  @Test
  public void testDeleteAllIndexesWorksWithIndexAutoCreation() {
    Query query = query("coll").filter(filter("value", "==", "match"));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("value", "match")), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("value", Double.NaN)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("value", null)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("value", "mismatch")), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("value", "match")), targetId));

    // First time query is running without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index should be created.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    backfillIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 0);
    assertQueryReturned("coll/a", "coll/e");

    deleteAllIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    // Field index is created again.
    backfillIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 2, /* byCollection= */ 0);
    assertQueryReturned("coll/a", "coll/e");
  }

  @Test
  public void testDeleteAllIndexesWorksWithManualAddedIndexes() {
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

    deleteAllIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 1);
    assertQueryReturned("coll/a");
  }

  @Test
  public void testIndexAutoCreationWorksWithMutation() {
    Query query =
        query("coll").filter(filter("value", "array-contains-any", Arrays.asList(8, 1, "string")));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(
        addedRemoteEvent(doc("coll/a", 10, map("value", Arrays.asList(8, 1, "string"))), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("value", emptyList())), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/c", 10, map("value", singletonList(3))), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/d", 10, map("value", Arrays.asList(0, 5))), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/e", 10, map("value", singletonList("string"))), targetId));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    writeMutation(deleteMutation("coll/e"));

    backfillIndexes();

    writeMutation(setMutation("coll/f", map("value", singletonList(1))));

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 1, /* byCollection= */ 0);
    assertOverlaysRead(/* byKey= */ 1, /* byCollection= */ 1);
    assertQueryReturned("coll/a", "coll/f");
  }

  @Test
  public void testIndexAutoCreationDoesnotWorkWithMultipleInequality() {
    Query query = query("coll").filter(filter("field1", "<", 5)).filter(filter("field2", "<", 5));
    int targetId = allocateQuery(query);

    setIndexAutoCreationEnabled(true);
    setMinCollectionSizeToAutoCreateIndex(0);
    setRelativeIndexReadCostPerDocument(2);

    applyRemoteEvent(addedRemoteEvent(doc("coll/a", 10, map("field1", 1, "field2", 2)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/b", 10, map("field1", 8, "field2", 2)), targetId));
    applyRemoteEvent(
        addedRemoteEvent(doc("coll/c", 10, map("field1", "string", "field2", 2)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/d", 10, map("field1", 2)), targetId));
    applyRemoteEvent(addedRemoteEvent(doc("coll/e", 10, map("field1", 4, "field2", 4)), targetId));

    // First time query is running without indexes.
    // Based on current heuristic, collection document counts (5) > 2 * resultSize (2).
    // Full matched index will not be created since FieldIndex does not support multiple inequality.
    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");

    backfillIndexes();

    executeQuery(query);
    assertRemoteDocumentsRead(/* byKey= */ 0, /* byCollection= */ 2);
    assertQueryReturned("coll/a", "coll/e");
  }
}
