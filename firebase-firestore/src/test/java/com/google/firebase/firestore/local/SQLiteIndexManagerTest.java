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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.firebase.firestore.testutil.TestUtil.bound;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.field;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteIndexManagerTest extends IndexManagerTestCase {
  /** Current state of indexing support. Used for restoring after test run. */
  private static final boolean supportsIndexing = Persistence.INDEXING_SUPPORT_ENABLED;

  @BeforeClass
  public static void beforeClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = true;
  }

  @BeforeClass
  public static void afterClass() {
    Persistence.INDEXING_SUPPORT_ENABLED = supportsIndexing;
  }

  private void setUpSingleValueFilter() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("count"), FieldIndex.Segment.Kind.ASC));
    addDoc("c/d1", map("count", 1));
    addDoc("c/d2", map("count", 2));
    addDoc("c/d3", map("count", 3));
  }

  private void setUpArrayValueFilter() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("values"), FieldIndex.Segment.Kind.CONTAINS));
    addDoc("c/d1", map("values", Arrays.asList(1, 2, 3)));
    addDoc("c/d2", map("values", Arrays.asList(4, 5, 6)));
    addDoc("c/d3", map("values", Arrays.asList(7, 8, 9)));
  }

  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Test
  public void addsDocuments() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("exists"), FieldIndex.Segment.Kind.ASC));
    addDoc("c/d1", map("exists", 1));
    addDoc("c/d2", map());
  }

  @Test
  public void testEqualityFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "==", 2));
    verifyResults(query, "c/d2");
  }

  @Test
  public void testNestedFieldEqualityFilter() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("a.b"), FieldIndex.Segment.Kind.ASC));
    addDoc("c/d1", map("a", map("b", 1)));
    addDoc("c/d2", map("a", map("b", 2)));
    Query query = query("coll").filter(filter("a.b", "==", 2));
    verifyResults(query, "c/d2");
  }

  @Test
  public void testNotEqualityFilter() {
    // TODO(indexing): Optimize != filters. We currently return all documents and do not exclude
    // the documents with the provided value.
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "!=", 2));
    verifyResults(query, "c/d1", "c/d2", "c/d3");
  }

  @Test
  public void testLessThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<", 2));
    verifyResults(query, "c/d1");
  }

  @Test
  public void testLessThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<=", 2));
    verifyResults(query, "c/d1", "c/d2");
  }

  @Test
  public void testGreaterThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">=", 2));
    verifyResults(query, "c/d2", "c/d3");
  }

  @Test
  public void testGreaterThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 2));
    verifyResults(query, "c/d3");
  }

  @Test
  public void testRangeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 1)).filter(filter("count", "<", 3));
    verifyResults(query, "c/d2");
  }

  @Test
  public void testStartAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "c/d2", "c/d3");
  }

  @Test
  public void testStartAfterFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(/* inclusive= */ false, 2));
    verifyResults(query, "c/d3");
  }

  @Test
  public void testEndAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "c/d1", "c/d2");
  }

  @Test
  public void testEndBeforeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(/* inclusive= */ false, 2));
    verifyResults(query, "c/d1");
  }

  @Test
  public void testRangeWithBoundFilter() {
    setUpSingleValueFilter();
    Query startAt =
        query("coll")
            .filter(filter("count", ">=", 1))
            .filter(filter("count", "<=", 3))
            .orderBy(orderBy("count"))
            .startAt(bound(/* inclusive= */ false, 1))
            .endAt(bound(/* inclusive= */ true, 2));
    verifyResults(startAt, "c/d2");
  }

  @Test
  public void testInFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "in", Arrays.asList(1, 3)));
    verifyResults(query, "c/d1", "c/d3");
  }

  @Test
  public void testNotInFilter() {
    // TODO(indexing): Optimize not-in filters. We currently return all documents and do not exclude
    // the documents with the provided values.
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "not-in", Arrays.asList(1, 2)));
    verifyResults(query, "c/d1", "c/d2", "c/d3");
  }

  @Test
  public void testArrayContainsFilter() {
    setUpArrayValueFilter();
    Query query = query("coll").filter(filter("values", "array-contains", 1));
    verifyResults(query, "c/d1");
  }

  @Test
  public void testArrayContainsAnyFilter() {
    setUpArrayValueFilter();
    Query query = query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1, 2, 4)));
    verifyResults(query, "c/d1", "c/d2");
  }

  @Test
  public void testArrayContainsDoesNotMatchNonArray() {
    // Set up two field indices. This causes two index entries to be written, but our query should
    // only use one index.
    setUpArrayValueFilter();
    setUpSingleValueFilter();
    addDoc("coll/nonmatching", map("values", 1));
    Query query = query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1)));
    verifyResults(query, "c/d1");
  }

  @Test
  public void testNoMatchingFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("unknown", "==", true));
    assertNull(indexManager.getDocumentsMatchingTarget(query.toTarget()));
  }

  @Test
  public void testNoMatchingDocs() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "==", -1));
    verifyResults(query);
  }

  @Test
  public void testEqualityFilterWithNonMatchingType() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("value"), FieldIndex.Segment.Kind.ASC));
    addDoc("coll/boolean", map("value", true));
    addDoc("coll/string", map("value", "true"));
    addDoc("coll/number", map("value", 1));
    Query query = query("coll").filter(filter("value", "==", true));
    verifyResults(query, "coll/boolean");
  }

  @Test
  public void testCollectionGroup() {
    indexManager.addFieldIndex(
        new FieldIndex("coll1").withAddedField(field("value"), FieldIndex.Segment.Kind.ASC));
    addDoc("coll1/doc1", map("value", true));
    addDoc("coll2/doc2/coll1/doc1", map("value", true));
    addDoc("coll2/doc2", map("value", true));
    Query query = new Query(path(""), "coll1").filter(filter("value", "==", true));
    verifyResults(query, "coll1/doc1", "coll2/doc2/coll1/doc1");
  }

  @Test
  public void testLimitFilter() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("value"), FieldIndex.Segment.Kind.ASC));
    addDoc("c/d1", map("value", 1));
    addDoc("c/d2", map("value", 1));
    addDoc("c/d3", map("value", 1));
    Query query = query("coll").filter(filter("value", "==", 1)).limitToFirst(2);
    verifyResults(query, "c/d1", "c/d2");
  }

  @Test
  public void testLimitAppliesOrdering() {
    indexManager.addFieldIndex(
        new FieldIndex("coll")
            .withAddedField(field("value"), FieldIndex.Segment.Kind.ASC)
            .withAddedField(field("value"), FieldIndex.Segment.Kind.CONTAINS));
    addDoc("c/d1", map("value", Arrays.asList(1, "foo")));
    addDoc("c/d2", map("value", Arrays.asList(3, "foo")));
    addDoc("c/d3", map("value", Arrays.asList(2, "foo")));
    Query query =
        query("coll")
            .filter(filter("value", "array-contains", "foo"))
            .orderBy(orderBy("value"))
            .limitToFirst(2);
    verifyResults(query, "c/d1", "c/d3");
  }

  @Test
  public void testAdvancedQueries() {
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("null"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("int"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("float"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("string"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("multi"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("array"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("array"), FieldIndex.Segment.Kind.DESC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("array"), FieldIndex.Segment.Kind.CONTAINS));
    indexManager.addFieldIndex(
            new FieldIndex("coll").withAddedField(field("map"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
            new FieldIndex("coll").withAddedField(field("map.field"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll").withAddedField(field("prefix"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll")
            .withAddedField(field("prefix"), FieldIndex.Segment.Kind.ASC)
            .withAddedField(field("suffix"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.ASC)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.DESC)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.ASC));
    indexManager.addFieldIndex(
        new FieldIndex("coll")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.ASC)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.DESC));
    indexManager.addFieldIndex(
        new FieldIndex("coll")
            .withAddedField(field("a"), FieldIndex.Segment.Kind.DESC)
            .withAddedField(field("b"), FieldIndex.Segment.Kind.DESC));

    List<Map<String, Object>> data =
        new ArrayList<Map<String, Object>>() {
          {
            add(map("int", 1, "array", Arrays.asList(1, "foo"))); // d0
            add(map("array", Arrays.asList(2, "foo"))); // d1
            add(map("int", 3, "array", Arrays.asList(3, "foo"))); // d2
            add(map("float", -0.0, "string", "a")); // d3
            add(map("float", 0, "string", "ab")); // d4
            add(map("float", 0.0, "string", "b")); // d5
            add(map("float", Double.NaN)); // d6
            add(map("multi", true)); // d7
            add(map("array", "foo")); // d8
            add(map("array", Collections.singletonList(1))); // d9
            add(map("multi", 1)); // d10
            add(map("multi", "string")); // d11
            add(map("multi", Collections.emptyList())); // d12
            add(map("a", 0, "b", 0)); // d13
            add(map("a", 0, "b", 1)); // d14
            add(map("a", 1, "b", 0)); // d15
            add(map("a", 1, "b", 1)); // d16
            add(map()); // d17
            add(map("null", null)); // d18
            add(map("prefix", Arrays.asList(1, 2), "suffix", null)); // d19
            add(map("prefix", Collections.singletonList(1), "suffix", 2)); // d20
            add(map("map", map())); // d20
            add(map("map", map("field", true))); // d21
            add(map("map", map("field", false))); // d22
          }
        };

    for (int i = 0; i < data.size(); ++i) {
      addDoc("coll/doc" + i, data.get(i));
    }

    Query q = query("coll");

    verifyResults(q.orderBy(orderBy("int")), "c/d0", "c/d2");
    verifyResults(q.filter(filter("float", "==", Double.NaN)), "c/d6");
    verifyResults(q.filter(filter("float", "==", -0.0)), "c/d3", "c/d4", "c/d5");
    verifyResults(q.filter(filter("float", "==", 0)), "c/d3", "c/d4", "c/d5");
    verifyResults(q.filter(filter("float", "==", 0.0)), "c/d3", "c/d4", "c/d5");
    verifyResults(q.filter(filter("string", "==", "a")), "c/d3");
    verifyResults(q.filter(filter("string", ">", "a")), "c/d4", "c/d5");
    verifyResults(q.filter(filter("string", ">=", "a")), "c/d3", "c/d4", "c/d5");
    verifyResults(q.filter(filter("string", "<", "b")), "c/d3", "c/d4");
    verifyResults(q.filter(filter("string", "<", "coll")), "c/d3", "c/d4", "c/d5");
    verifyResults(q.filter(filter("string", ">", "a")).filter(filter("string", "<", "b")), "c/d4");
    verifyResults(q.filter(filter("array", "array-contains", "foo")), "c/d0", "c/d1", "c/d2");
    verifyResults(
        q.filter(filter("array", "array-contains-any", Arrays.asList(1, "foo"))),
        "c/d0",
        "c/d1",
        "c/d2",
        "c/d9");
    verifyResults(q.filter(filter("multi", ">=", true)), "c/d7");
    verifyResults(q.filter(filter("multi", ">=", 0)), "c/d10");
    verifyResults(q.filter(filter("multi", ">=", "")), "c/d11");
    verifyResults(q.filter(filter("multi", ">=", Collections.emptyList())), "c/d12");
    verifyResults(
        q.orderBy(orderBy("array")).startAt(bound(true, Collections.singletonList(2))),
        "c/d1",
        "c/d2");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).startAt(bound(true, Collections.singletonList(2))),
        "c/d0",
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(true, Collections.singletonList(2)))
            .limitToFirst(2),
        "c/d0",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array")).startAt(bound(false, Collections.singletonList(2))),
        "c/d1",
        "c/d2");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).startAt(bound(false, Collections.singletonList(2))),
        "c/d0",
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(false, Collections.singletonList(2)))
            .limitToFirst(2),
        "c/d0",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array")).startAt(bound(false, Arrays.asList(2, "foo"))), "c/d2");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).startAt(bound(false, Arrays.asList(2, "foo"))),
        "c/d0",
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(false, Arrays.asList(2, "foo")))
            .limitToFirst(2),
        "c/d0",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(true, Collections.singletonList(2))),
        "c/d0",
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(true, Collections.singletonList(2))),
        "c/d1",
        "c/d2");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Collections.singletonList(2))),
        "c/d0",
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array"))
            .endAt(bound(false, Collections.singletonList(2)))
            .limitToFirst(2),
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(false, Collections.singletonList(2))),
        "c/d1",
        "c/d2");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Arrays.asList(2, "foo"))),
        "c/d0",
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Arrays.asList(2, "foo"))).limitToFirst(2),
        "c/d8",
        "c/d9");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(false, Arrays.asList(2, "foo"))), "c/d2");
    verifyResults(q.orderBy(orderBy("a")).orderBy(orderBy("b")).limitToFirst(1), "c/d13");
    verifyResults(q.orderBy(orderBy("a", "desc")).orderBy(orderBy("b")).limitToFirst(1), "c/d15");
    verifyResults(q.orderBy(orderBy("a")).orderBy(orderBy("b", "desc")).limitToFirst(1), "c/d14");
    verifyResults(
        q.orderBy(orderBy("a", "desc")).orderBy(orderBy("b", "desc")).limitToFirst(1), "c/d16");
    verifyResults(q.filter(filter("null", "==", null)), "c/d18");
    verifyResults(q.orderBy(orderBy("null")), "c/d18");
    verifyResults(q.filter(filter("prefix", "==", Arrays.asList(1, 2))), "c/d19");
    verifyResults(
        q.filter(filter("prefix", "==", Collections.singletonList(1)))
            .filter(filter("suffix", "==", 2)),
        "c/d20");
    verifyResults(q.filter(filter("map","==", map())),"c/d21");
    verifyResults(q.filter(filter("map", "==",map("field", true))),"c/d22");
    verifyResults(q.filter(filter("map.field", "==",true)),"c/d22");
    verifyResults(q.orderBy(orderBy("map")),"c/d21", "c/d22", "c/d23");
    verifyResults(q.orderBy(orderBy("map.field")),"c/d22", "c/d23");
  }

  @Test
  public void testUpdateTime() {
    indexManager.addFieldIndex(
        new FieldIndex("coll1")
            .withAddedField(field("value"), FieldIndex.Segment.Kind.ASC)
            .withVersion(new SnapshotVersion(new Timestamp(10, 20))));

    List<FieldIndex> indexes = ((SQLiteIndexManager) indexManager).getFieldIndexes();
    assertEquals(indexes.size(), 1);
    FieldIndex index = indexes.get(0);
    assertEquals(index.getVersion(), new SnapshotVersion(new Timestamp(10, 20)));
  }

  private void addDoc(String key, Map<String, Object> data) {
    MutableDocument doc = doc(key, 1, data);
    indexManager.addIndexEntries(doc);
  }

  private void verifyResults(Query query, String... documents) {
    Iterable<DocumentKey> results = indexManager.getDocumentsMatchingTarget(query.toTarget());
    List<DocumentKey> keys = Arrays.stream(documents).map(s -> key(s)).collect(Collectors.toList());
    assertWithMessage("Result for %s", query).that(results).containsExactlyElementsIn(keys);
  }
}
