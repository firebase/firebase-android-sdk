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
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.Values;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
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
    indexManager.addFieldIndex(fieldIndex("coll", "count", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("count", 1));
    addDoc("coll/doc2", map("count", 2));
    addDoc("coll/doc3", map("count", 3));
  }

  private void setUpArrayValueFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "values", FieldIndex.Segment.Kind.CONTAINS));
    addDoc("coll/doc1", map("values", Arrays.asList(1, 2, 3)));
    addDoc("coll/doc2", map("values", Arrays.asList(4, 5, 6)));
    addDoc("coll/doc3", map("values", Arrays.asList(7, 8, 9)));
  }

  @Override
  Persistence getPersistence() {
    if (persistence == null) {
      persistence = PersistenceTestHelpers.createSQLitePersistence();
    }
    return persistence;
  }

  @Test
  public void addsDocuments() {
    indexManager.addFieldIndex(fieldIndex("coll", "exists", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("exists", 1));
    addDoc("coll/doc2", map());
  }

  @Test
  public void testEqualityFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "==", 2));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testNestedFieldEqualityFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "a.b", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("a", map("b", 1)));
    addDoc("coll/doc2", map("a", map("b", 2)));
    Query query = query("coll").filter(filter("a.b", "==", 2));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testNotEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "!=", 2));
    verifyResults(query, "coll/doc1", "coll/doc3");
  }

  @Test
  public void testLessThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<", 2));
    verifyResults(query, "coll/doc1");
  }

  @Test
  public void testLessThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<=", 2));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testGreaterThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">=", 2));
    verifyResults(query, "coll/doc2", "coll/doc3");
  }

  @Test
  public void testGreaterThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 2));
    verifyResults(query, "coll/doc3");
  }

  @Test
  public void testRangeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 1)).filter(filter("count", "<", 3));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testStartAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "coll/doc2", "coll/doc3");
  }

  @Test
  public void testStartAfterFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(/* inclusive= */ false, 2));
    verifyResults(query, "coll/doc3");
  }

  @Test
  public void testEndAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testEndBeforeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(/* inclusive= */ false, 2));
    verifyResults(query, "coll/doc1");
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
    verifyResults(startAt, "coll/doc2");
  }

  @Test
  public void testInFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "in", Arrays.asList(1, 3)));
    verifyResults(query, "coll/doc1", "coll/doc3");
  }

  @Test
  public void testNotInFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "not-in", Arrays.asList(1, 2)));
    verifyResults(query, "coll/doc3");
  }

  @Test
  public void testArrayContainsFilter() {
    setUpArrayValueFilter();
    Query query = query("coll").filter(filter("values", "array-contains", 1));
    verifyResults(query, "coll/doc1");
  }

  @Test
  public void testArrayContainsAnyFilter() {
    setUpArrayValueFilter();
    Query query =
        query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1, 2, 4)));
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testArrayContainsDoesNotMatchNonArray() {
    // Set up two field indices. This causes two index entries to be written, but our query should
    // only use one index.
    setUpArrayValueFilter();
    setUpSingleValueFilter();
    addDoc("coll/nonmatching", map("values", 1));
    Query query = query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1)));
    verifyResults(query, "coll/doc1");
  }

  @Test
  public void testNoMatchingFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("unknown", "==", true));
    assertNull(indexManager.getFieldIndex(query.toTarget()));
  }

  @Test
  public void testNoMatchingDocs() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "==", -1));
    verifyResults(query);
  }

  @Test
  public void testEqualityFilterWithNonMatchingType() {
    indexManager.addFieldIndex(fieldIndex("coll", "value", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/boolean", map("value", true));
    addDoc("coll/string", map("value", "true"));
    addDoc("coll/number", map("value", 1));
    Query query = query("coll").filter(filter("value", "==", true));
    verifyResults(query, "coll/boolean");
  }

  @Test
  public void testCollectionGroup() {
    indexManager.addFieldIndex(fieldIndex("coll1", "value", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll1/doc1", map("value", true));
    addDoc("coll2/doc2/coll1/doc1", map("value", true));
    addDoc("coll2/doc2", map("value", true));
    Query query = new Query(path(""), "coll1").filter(filter("value", "==", true));
    verifyResults(query, "coll1/doc1", "coll2/doc2/coll1/doc1");
  }

  @Test
  public void testLimitFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "value", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("value", 1));
    addDoc("coll/doc2", map("value", 1));
    addDoc("coll/doc3", map("value", 1));
    Query query = query("coll").filter(filter("value", "==", 1)).limitToFirst(2);
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testLimitAppliesOrdering() {
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "value",
            FieldIndex.Segment.Kind.CONTAINS,
            "value",
            FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("value", Arrays.asList(1, "foo")));
    addDoc("coll/doc2", map("value", Arrays.asList(3, "foo")));
    addDoc("coll/doc3", map("value", Arrays.asList(2, "foo")));
    Query query =
        query("coll")
            .filter(filter("value", "array-contains", "foo"))
            .orderBy(orderBy("value"))
            .limitToFirst(2);
    verifyResults(query, "coll/doc1", "coll/doc3");
  }

  @Test
  public void testAdvancedQueries() {
    // This test compares local query results with those received from the Java Server SDK.

    indexManager.addFieldIndex(fieldIndex("coll", "null", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "int", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "float", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "string", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "multi", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "array", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "array", FieldIndex.Segment.Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "array", FieldIndex.Segment.Kind.CONTAINS));
    indexManager.addFieldIndex(fieldIndex("coll", "map", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "map.field", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "prefix", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "prefix",
            FieldIndex.Segment.Kind.ASCENDING,
            "suffix",
            FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "a",
            FieldIndex.Segment.Kind.ASCENDING,
            "b",
            FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "a",
            FieldIndex.Segment.Kind.DESCENDING,
            "b",
            FieldIndex.Segment.Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "a",
            FieldIndex.Segment.Kind.ASCENDING,
            "b",
            FieldIndex.Segment.Kind.DESCENDING));
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "a",
            FieldIndex.Segment.Kind.DESCENDING,
            "b",
            FieldIndex.Segment.Kind.DESCENDING));
    indexManager.addFieldIndex(
        fieldIndex(
            "coll",
            "b",
            FieldIndex.Segment.Kind.ASCENDING,
            "a",
            FieldIndex.Segment.Kind.ASCENDING));

    List<Map<String, Object>> data =
        new ArrayList<Map<String, Object>>() {
          {
            add(map());
            add(map("int", 1, "array", Arrays.asList(1, "foo")));
            add(map("array", Arrays.asList(2, "foo")));
            add(map("int", 3, "array", Arrays.asList(3, "foo")));
            add(map("array", "foo"));
            add(map("array", Collections.singletonList(1)));
            add(map("float", -0.0, "string", "a"));
            add(map("float", 0, "string", "ab"));
            add(map("float", 0.0, "string", "b"));
            add(map("float", Double.NaN));
            add(map("multi", true));
            add(map("multi", 1));
            add(map("multi", "string"));
            add(map("multi", Collections.emptyList()));
            add(map("null", null));
            add(map("prefix", Arrays.asList(1, 2), "suffix", null));
            add(map("prefix", Collections.singletonList(1), "suffix", 2));
            add(map("map", map()));
            add(map("map", map("field", true)));
            add(map("map", map("field", false)));
            add(map("a", 0, "b", 0));
            add(map("a", 0, "b", 1));
            add(map("a", 1, "b", 0));
            add(map("a", 1, "b", 1));
            add(map("a", 2, "b", 0));
            add(map("a", 2, "b", 1));
          }
        };

    for (int i = 0; i < data.size(); ++i) {
      addDoc("coll/" + Values.canonicalId(wrap(data.get(i))), data.get(i));
    }

    Query q = query("coll");

    verifyResults(
        q.orderBy(orderBy("int")), "coll/{array:[1,foo],int:1}", "coll/{array:[3,foo],int:3}");
    verifyResults(q.filter(filter("float", "==", Double.NaN)), "coll/{float:NaN}");
    verifyResults(
        q.filter(filter("float", "==", -0.0)),
        "coll/{float:-0.0,string:a}",
        "coll/{float:0,string:ab}",
        "coll/{float:0.0,string:b}");
    verifyResults(
        q.filter(filter("float", "==", 0)),
        "coll/{float:-0.0,string:a}",
        "coll/{float:0,string:ab}",
        "coll/{float:0.0,string:b}");
    verifyResults(
        q.filter(filter("float", "==", 0.0)),
        "coll/{float:-0.0,string:a}",
        "coll/{float:0,string:ab}",
        "coll/{float:0.0,string:b}");
    verifyResults(q.filter(filter("string", "==", "a")), "coll/{float:-0.0,string:a}");
    verifyResults(
        q.filter(filter("string", ">", "a")),
        "coll/{float:0,string:ab}",
        "coll/{float:0.0,string:b}");
    verifyResults(
        q.filter(filter("string", ">=", "a")),
        "coll/{float:-0.0,string:a}",
        "coll/{float:0,string:ab}",
        "coll/{float:0.0,string:b}");
    verifyResults(
        q.filter(filter("string", "<", "b")),
        "coll/{float:-0.0,string:a}",
        "coll/{float:0,string:ab}");
    verifyResults(
        q.filter(filter("string", "<", "coll")),
        "coll/{float:-0.0,string:a}",
        "coll/{float:0,string:ab}",
        "coll/{float:0.0,string:b}");
    verifyResults(
        q.filter(filter("string", ">", "a")).filter(filter("string", "<", "b")),
        "coll/{float:0,string:ab}");
    verifyResults(
        q.filter(filter("array", "array-contains", "foo")),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}");
    verifyResults(
        q.filter(filter("array", "array-contains-any", Arrays.asList(1, "foo"))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}",
        "coll/{array:[1]}");
    verifyResults(q.filter(filter("multi", ">=", true)), "coll/{multi:true}");
    verifyResults(q.filter(filter("multi", ">=", 0)), "coll/{multi:1}");
    verifyResults(q.filter(filter("multi", ">=", "")), "coll/{multi:string}");
    verifyResults(q.filter(filter("multi", ">=", Collections.emptyList())), "coll/{multi:[]}");
    verifyResults(
        q.filter(filter("multi", "!=", true)),
        "coll/{multi:1}",
        "coll/{multi:string}",
        "coll/{multi:[]}");
    verifyResults(
        q.filter(filter("multi", "in", Arrays.asList(true, 1))),
        "coll/{multi:true}",
        "coll/{multi:1}");
    verifyResults(
        q.filter(filter("multi", "not-in", Arrays.asList(true, 1))),
        "coll/{multi:string}",
        "coll/{multi:[]}");
    verifyResults(
        q.orderBy(orderBy("array")).startAt(bound(true, Collections.singletonList(2))),
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).startAt(bound(true, Collections.singletonList(2))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(true, Collections.singletonList(2)))
            .limitToFirst(2),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array")).startAt(bound(false, Collections.singletonList(2))),
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).startAt(bound(false, Collections.singletonList(2))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(false, Collections.singletonList(2)))
            .limitToFirst(2),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array")).startAt(bound(false, Arrays.asList(2, "foo"))),
        "coll/{array:[3,foo],int:3}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).startAt(bound(false, Arrays.asList(2, "foo"))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(false, Arrays.asList(2, "foo")))
            .limitToFirst(2),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(true, Collections.singletonList(2))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(true, Collections.singletonList(2))),
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Collections.singletonList(2))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array"))
            .endAt(bound(false, Collections.singletonList(2)))
            .limitToFirst(2),
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(false, Collections.singletonList(2))),
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Arrays.asList(2, "foo"))),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Arrays.asList(2, "foo"))).limitToFirst(2),
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(false, Arrays.asList(2, "foo"))),
        "coll/{array:[3,foo],int:3}");
    verifyResults(q.orderBy(orderBy("a")).orderBy(orderBy("b")).limitToFirst(1), "coll/{a:0,b:0}");
    verifyResults(
        q.orderBy(orderBy("a", "desc")).orderBy(orderBy("b")).limitToFirst(1), "coll/{a:2,b:0}");
    verifyResults(
        q.orderBy(orderBy("a")).orderBy(orderBy("b", "desc")).limitToFirst(1), "coll/{a:0,b:1}");
    verifyResults(
        q.orderBy(orderBy("a", "desc")).orderBy(orderBy("b", "desc")).limitToFirst(1),
        "coll/{a:2,b:1}");
    verifyResults(
        q.filter(filter("a", ">", 0)).filter(filter("b", "==", 1)),
        "coll/{a:1,b:1}",
        "coll/{a:2,b:1}");
    verifyResults(q.filter(filter("a", "==", 1)).filter(filter("b", "==", 1)), "coll/{a:1,b:1}");
    verifyResults(
        q.filter(filter("a", "!=", 0)).filter(filter("b", "==", 1)),
        "coll/{a:1,b:1}",
        "coll/{a:2,b:1}");
    verifyResults(
        q.filter(filter("b", "==", 1)).filter(filter("a", "!=", 0)),
        "coll/{a:1,b:1}",
        "coll/{a:2,b:1}");
    verifyResults(
        q.filter(filter("a", "not-in", Arrays.asList(0, 1))), "coll/{a:2,b:0}", "coll/{a:2,b:1}");
    verifyResults(
        q.filter(filter("a", "not-in", Arrays.asList(0, 1))).filter(filter("b", "==", 1)),
        "coll/{a:2,b:1}");
    verifyResults(
        q.filter(filter("b", "==", 1)).filter(filter("a", "not-in", Arrays.asList(0, 1))),
        "coll/{a:2,b:1}");
    verifyResults(q.filter(filter("null", "==", null)), "coll/{null:null}");
    verifyResults(q.orderBy(orderBy("null")), "coll/{null:null}");
    verifyResults(
        q.filter(filter("prefix", "==", Arrays.asList(1, 2))), "coll/{prefix:[1,2],suffix:null}");
    verifyResults(
        q.filter(filter("prefix", "==", Collections.singletonList(1)))
            .filter(filter("suffix", "==", 2)),
        "coll/{prefix:[1],suffix:2}");
    verifyResults(q.filter(filter("map", "==", map())), "coll/{map:{}}");
    verifyResults(q.filter(filter("map", "==", map("field", true))), "coll/{map:{field:true}}");
    verifyResults(q.filter(filter("map.field", "==", true)), "coll/{map:{field:true}}");
    verifyResults(
        q.orderBy(orderBy("map")),
        "coll/{map:{}}",
        "coll/{map:{field:true}}",
        "coll/{map:{field:false}}");
    verifyResults(
        q.orderBy(orderBy("map.field")), "coll/{map:{field:true}}", "coll/{map:{field:false}}");
  }

  @Test
  public void testUpdateTime() {
    indexManager.addFieldIndex(
        fieldIndex("coll1", -1, version(20), "value", FieldIndex.Segment.Kind.ASCENDING));

    Collection<FieldIndex> indexes = indexManager.getFieldIndexes("coll1");
    assertEquals(indexes.size(), 1);
    FieldIndex index = indexes.iterator().next();
    assertEquals(index.getUpdateTime(), version(20));
  }

  @Test
  public void testCollectionGroupSequenceNumbersCanBeUpdated() {
    SQLiteIndexManager sqLiteIndexManager = (SQLiteIndexManager) indexManager;
    sqLiteIndexManager.setCollectionGroup("coll1", 1);
    sqLiteIndexManager.setCollectionGroup("coll2", 2);
    List<String> orderedCollectionGroups =
        getCollectionGroupsOrderBySequenceNumber((SQLitePersistence) getPersistence());
    List<String> expected = Arrays.asList("coll1", "coll2");
    assertEquals(expected, orderedCollectionGroups);

    sqLiteIndexManager.setCollectionGroup("coll1", 3);
    orderedCollectionGroups =
        getCollectionGroupsOrderBySequenceNumber((SQLitePersistence) getPersistence());
    expected = Arrays.asList("coll2", "coll1");
    assertEquals(expected, orderedCollectionGroups);
  }

  @Test
  public void testGetFieldIndexes() {
    SQLiteIndexManager sqLiteIndexManager = (SQLiteIndexManager) indexManager;
    sqLiteIndexManager.addFieldIndex(
        fieldIndex("coll1", 1, version(30), "value", FieldIndex.Segment.Kind.ASCENDING));
    sqLiteIndexManager.addFieldIndex(
        fieldIndex("coll2", 2, SnapshotVersion.NONE, "value", FieldIndex.Segment.Kind.CONTAINS));

    Collection<FieldIndex> indexes = sqLiteIndexManager.getFieldIndexes("coll1");
    assertEquals(indexes.size(), 1);
    Iterator<FieldIndex> it = indexes.iterator();
    assertEquals(it.next().getCollectionGroup(), "coll1");
    sqLiteIndexManager.addFieldIndex(
        fieldIndex("coll1", 3, version(20), "newValue", FieldIndex.Segment.Kind.CONTAINS));

    indexes = sqLiteIndexManager.getFieldIndexes("coll1");
    assertEquals(indexes.size(), 2);
    it = indexes.iterator();
    assertEquals(it.next().getCollectionGroup(), "coll1");
    assertEquals(it.next().getCollectionGroup(), "coll1");
  }

  @Test
  public void testAddFieldIndexWritesToCollectionGroup() {
    SQLiteIndexManager sqLiteIndexManager = (SQLiteIndexManager) indexManager;
    sqLiteIndexManager.addFieldIndex(
        fieldIndex("coll1", 1, version(30), "value", FieldIndex.Segment.Kind.ASCENDING));
    sqLiteIndexManager.addFieldIndex(
        fieldIndex("coll2", 2, SnapshotVersion.NONE, "value", FieldIndex.Segment.Kind.CONTAINS));
    List<String> collectionGroups =
        getCollectionGroupsOrderBySequenceNumber((SQLitePersistence) getPersistence());
    assertEquals(2, collectionGroups.size());
    assertEquals("coll1", collectionGroups.get(0));
    assertEquals("coll2", collectionGroups.get(1));
  }

  @Test
  public void markCollectionGroupIndexedSetsCollectionToNextMax() {
    SQLiteIndexManager sqLiteIndexManager = (SQLiteIndexManager) indexManager;
    sqLiteIndexManager.addFieldIndex(
        new FieldIndex("coll1").withAddedField(field("value"), FieldIndex.Segment.Kind.ASCENDING));
    sqLiteIndexManager.addFieldIndex(
        new FieldIndex("coll2").withAddedField(field("value"), FieldIndex.Segment.Kind.ASCENDING));

    // Set `coll1` to max value.
    sqLiteIndexManager.markCollectionGroupIndexed("coll1");
    sqLiteIndexManager.markCollectionGroupIndexed("coll1");
    List<String> collectionGroups =
        getCollectionGroupsOrderBySequenceNumber((SQLitePersistence) getPersistence());
    assertEquals("coll2", collectionGroups.get(0));
    assertEquals("coll1", collectionGroups.get(1));

    // Mark `coll2` as indexed and verify that it has a higher sequence number than `coll1`.
    sqLiteIndexManager.markCollectionGroupIndexed("coll2");
    collectionGroups =
        getCollectionGroupsOrderBySequenceNumber((SQLitePersistence) getPersistence());
    assertEquals("coll1", collectionGroups.get(0));
    assertEquals("coll2", collectionGroups.get(1));
  }

  private void addDoc(String key, Map<String, Object> data) {
    MutableDocument doc = doc(key, 1, data);
    indexManager.handleDocumentChange(null, doc);
  }

  private void verifyResults(Query query, String... documents) {
    Target target = query.toTarget();
    FieldIndex fieldIndex = indexManager.getFieldIndex(target);
    Iterable<DocumentKey> results = indexManager.getDocumentsMatchingTarget(fieldIndex, target);
    List<DocumentKey> keys = Arrays.stream(documents).map(s -> key(s)).collect(Collectors.toList());
    assertWithMessage("Result for %s", query).that(results).containsExactlyElementsIn(keys);
  }

  public static List<String> getCollectionGroupsOrderBySequenceNumber(
      SQLitePersistence persistence) {
    List<String> orderedCollectionGroups = new ArrayList<>();
    persistence
        .query(
            "SELECT collection_group "
                + "FROM collection_group_update_times "
                + "ORDER BY sequence_number")
        .forEach(
            row -> {
              orderedCollectionGroups.add(row.getString(0));
            });
    return orderedCollectionGroups;
  }
}
