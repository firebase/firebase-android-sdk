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
import static com.google.firebase.firestore.model.FieldIndex.IndexState;
import static com.google.firebase.firestore.model.FieldIndex.Segment.Kind;
import static com.google.firebase.firestore.testutil.TestUtil.andFilters;
import static com.google.firebase.firestore.testutil.TestUtil.blob;
import static com.google.firebase.firestore.testutil.TestUtil.bound;
import static com.google.firebase.firestore.testutil.TestUtil.deletedDoc;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.key;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orFilters;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.path;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.ref;
import static com.google.firebase.firestore.testutil.TestUtil.version;
import static com.google.firebase.firestore.testutil.TestUtil.wrap;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.BsonBinaryData;
import com.google.firebase.firestore.BsonObjectId;
import com.google.firebase.firestore.BsonTimestamp;
import com.google.firebase.firestore.Decimal128Value;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.GeoPoint;
import com.google.firebase.firestore.Int32Value;
import com.google.firebase.firestore.MaxKey;
import com.google.firebase.firestore.MinKey;
import com.google.firebase.firestore.RegexValue;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.testutil.TestUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteIndexManagerTest extends IndexManagerTestCase {

  private void setUpSingleValueFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "count", Kind.ASCENDING));
    addDoc("coll/val1", map("count", 1));
    addDoc("coll/val2", map("count", 2));
    addDoc("coll/val3", map("count", 3));
  }

  private void setUpArrayValueFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "values", Kind.CONTAINS));
    addDoc("coll/arr1", map("values", Arrays.asList(1, 2, 3)));
    addDoc("coll/arr2", map("values", Arrays.asList(4, 5, 6)));
    addDoc("coll/arr3", map("values", Arrays.asList(7, 8, 9)));
  }

  private void setUpMultipleOrderBys() {
    indexManager.addFieldIndex(
        fieldIndex("coll", "a", Kind.ASCENDING, "b", Kind.DESCENDING, "c", Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex("coll", "a", Kind.DESCENDING, "b", Kind.ASCENDING, "c", Kind.DESCENDING));
    addDoc("coll/val1", map("a", 1, "b", 1, "c", 3));
    addDoc("coll/val2", map("a", 2, "b", 2, "c", 2));
    addDoc("coll/val3", map("a", 2, "b", 2, "c", 3));
    addDoc("coll/val4", map("a", 2, "b", 2, "c", 4));
    addDoc("coll/val5", map("a", 2, "b", 2, "c", 5));
    addDoc("coll/val6", map("a", 3, "b", 3, "c", 6));
  }

  @Override
  Persistence getPersistence() {
    if (persistence == null) {
      persistence = PersistenceTestHelpers.createSQLitePersistence();
    }
    return persistence;
  }

  @Test
  public void testAddsDocuments() {
    indexManager.addFieldIndex(fieldIndex("coll", "exists", Kind.ASCENDING));
    addDoc("coll/doc1", map("exists", 1));
    addDoc("coll/doc2", map());
  }

  @Test
  public void testOrderByFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "count", Kind.ASCENDING));
    addDoc("coll/val1", map("count", 1));
    addDoc("coll/val2", map("not-count", 2));
    addDoc("coll/val3", map("count", 3));
    Query query = query("coll").orderBy(orderBy("count"));
    verifyResults(query, "coll/val1", "coll/val3");
  }

  @Test
  public void testOrderByKeyFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "count", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "count", Kind.DESCENDING));
    addDoc("coll/val1", map("count", 1));
    addDoc("coll/val2", map("count", 1));
    addDoc("coll/val3", map("count", 3));

    Query query = query("coll").orderBy(orderBy("count"));
    verifyResults(query, "coll/val1", "coll/val2", "coll/val3");

    query = query("coll").orderBy(orderBy("count", "desc"));
    verifyResults(query, "coll/val3", "coll/val2", "coll/val1");
  }

  @Test
  public void testAscendingOrderWithLessThanFilter() {
    setUpMultipleOrderBys();

    Query originalQuery =
        query("coll")
            .filter(filter("a", "==", 2))
            .filter(filter("b", "==", 2))
            .filter(filter("c", "<", 5))
            .orderBy(orderBy("c", "asc"));
    Query queryWithNonRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 1))
            .endAt(bound(/* inclusive= */ false, 6));
    Query queryWithRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 2))
            .endAt(bound(/* inclusive= */ false, 4));

    verifyResults(originalQuery, "coll/val2", "coll/val3", "coll/val4");
    verifyResults(queryWithNonRestrictedBound, "coll/val2", "coll/val3", "coll/val4");
    verifyResults(queryWithRestrictedBound, "coll/val3");
  }

  @Test
  public void testDescendingOrderWithLessThanFilter() {
    setUpMultipleOrderBys();

    Query originalQuery =
        query("coll")
            .filter(filter("a", "==", 2))
            .filter(filter("b", "==", 2))
            .filter(filter("c", "<", 5))
            .orderBy(orderBy("c", "desc"));
    Query queryWithNonRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 6))
            .endAt(bound(/* inclusive= */ false, 1));
    Query queryWithRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 4))
            .endAt(bound(/* inclusive= */ false, 2));

    verifyResults(originalQuery, "coll/val4", "coll/val3", "coll/val2");
    verifyResults(queryWithNonRestrictedBound, "coll/val4", "coll/val3", "coll/val2");
    verifyResults(queryWithRestrictedBound, "coll/val3");
  }

  @Test
  public void testAscendingOrderWithGreaterThanFilter() {
    setUpMultipleOrderBys();

    Query originalQuery =
        query("coll")
            .filter(filter("a", "==", 2))
            .filter(filter("b", "==", 2))
            .filter(filter("c", ">", 2))
            .orderBy(orderBy("c", "asc"));
    Query queryWithNonRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 2))
            .endAt(bound(/* inclusive= */ false, 6));
    Query queryWithRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 3))
            .endAt(bound(/* inclusive= */ false, 5));

    verifyResults(originalQuery, "coll/val3", "coll/val4", "coll/val5");
    verifyResults(queryWithNonRestrictedBound, "coll/val3", "coll/val4", "coll/val5");
    verifyResults(queryWithRestrictedBound, "coll/val4");
  }

  @Test
  public void testDescendingOrderWithGreaterThanFilter() {
    setUpMultipleOrderBys();

    Query originalQuery =
        query("coll")
            .filter(filter("a", "==", 2))
            .filter(filter("b", "==", 2))
            .filter(filter("c", ">", 2))
            .orderBy(orderBy("c", "desc"));
    Query queryWithNonRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 6))
            .endAt(bound(/* inclusive= */ false, 2));
    Query queryWithRestrictedBound =
        originalQuery
            .startAt(bound(/* inclusive= */ false, 5))
            .endAt(bound(/* inclusive= */ false, 3));

    verifyResults(originalQuery, "coll/val5", "coll/val4", "coll/val3");
    verifyResults(queryWithNonRestrictedBound, "coll/val5", "coll/val4", "coll/val3");
    verifyResults(queryWithRestrictedBound, "coll/val4");
  }

  @Test
  public void testEqualityFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "==", 2));
    verifyResults(query, "coll/val2");
  }

  @Test
  public void testOrderByWithNotEqualsFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "count", Kind.ASCENDING));
    addDoc("coll/val1", map("count", 1));
    addDoc("coll/val2", map("count", 2));

    Query query = query("coll").filter(filter("count", "!=", 2)).orderBy(orderBy("count"));
    verifyResults(query, "coll/val1");
  }

  @Test
  public void testNestedFieldEqualityFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "a.b", Kind.ASCENDING));
    addDoc("coll/doc1", map("a", map("b", 1)));
    addDoc("coll/doc2", map("a", map("b", 2)));
    Query query = query("coll").filter(filter("a.b", "==", 2));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testNotEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "!=", 2));
    verifyResults(query, "coll/val1", "coll/val3");
  }

  @Test
  public void testEqualsWithNotEqualsFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING, "b", Kind.ASCENDING));
    addDoc("coll/val1", map("a", 1, "b", 1));
    addDoc("coll/val2", map("a", 1, "b", 2));
    addDoc("coll/val3", map("a", 2, "b", 1));
    addDoc("coll/val4", map("a", 2, "b", 2));

    // Verifies that we apply the filter in the order of the field index
    Query query = query("coll").filter(filter("a", "==", 1)).filter(filter("b", "!=", 1));
    verifyResults(query, "coll/val2");

    query = query("coll").filter(filter("b", "!=", 1)).filter(filter("a", "==", 1));
    verifyResults(query, "coll/val2");
  }

  @Test
  public void testEqualsWithNotEqualsFilterSameField() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 1)).filter(filter("count", "!=", 2));
    verifyResults(query, "coll/val3");

    query = query("coll").filter(filter("count", "==", 1)).filter(filter("count", "!=", 2));
    verifyResults(query, "coll/val1");

    query = query("coll").filter(filter("count", "==", 1)).filter(filter("count", "!=", 1));
    verifyResults(query);
  }

  @Test
  public void testLessThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<", 2));
    verifyResults(query, "coll/val1");
  }

  @Test
  public void testLessThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "<=", 2));
    verifyResults(query, "coll/val1", "coll/val2");
  }

  @Test
  public void testGreaterThanOrEqualsFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">=", 2));
    verifyResults(query, "coll/val2", "coll/val3");
  }

  @Test
  public void testGreaterThanFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 2));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testRangeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", ">", 1)).filter(filter("count", "<", 3));
    verifyResults(query, "coll/val2");
  }

  @Test
  public void testStartAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "coll/val2", "coll/val3");
  }

  @Test
  public void testAppliesStartAtFilterWithNotIn() {
    setUpSingleValueFilter();
    Query query =
        query("coll")
            .filter(filter("count", "!=", 2))
            .orderBy(orderBy("count"))
            .startAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testStartAfterFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).startAt(bound(/* inclusive= */ false, 2));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testEndAtFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(/* inclusive= */ true, 2));
    verifyResults(query, "coll/val1", "coll/val2");
  }

  @Test
  public void testEndBeforeFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").orderBy(orderBy("count")).endAt(bound(/* inclusive= */ false, 2));
    verifyResults(query, "coll/val1");
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
    verifyResults(startAt, "coll/val2");
  }

  @Test
  public void testInFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "in", Arrays.asList(1, 3)));
    verifyResults(query, "coll/val1", "coll/val3");
  }

  @Test
  public void testNotInFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("count", "not-in", Arrays.asList(1, 2)));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testNotInWithGreaterThanFilter() {
    setUpSingleValueFilter();
    Query query =
        query("coll")
            .filter(filter("count", ">", 1))
            .filter(filter("count", "not-in", Collections.singletonList(2)));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testOutOfBoundsNotInWithGreaterThanFilter() {
    setUpSingleValueFilter();
    Query query =
        query("coll")
            .filter(filter("count", ">", 2))
            .filter(filter("count", "not-in", Collections.singletonList(1)));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testArrayContainsFilter() {
    setUpArrayValueFilter();
    Query query = query("coll").filter(filter("values", "array-contains", 1));
    verifyResults(query, "coll/arr1");
  }

  @Test
  public void testArrayContainsWithNotEqualsFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.CONTAINS, "b", Kind.ASCENDING));
    addDoc("coll/val1", map("a", Collections.singletonList(1), "b", 1));
    addDoc("coll/val2", map("a", Collections.singletonList(1), "b", 2));
    addDoc("coll/val3", map("a", Collections.singletonList(2), "b", 1));
    addDoc("coll/val4", map("a", Collections.singletonList(2), "b", 2));

    Query query =
        query("coll").filter(filter("a", "array-contains", 1)).filter(filter("b", "!=", 1));
    verifyResults(query, "coll/val2");
  }

  @Test
  public void testArrayContainsWithNotEqualsFilterOnSameField() {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.CONTAINS, "a", Kind.ASCENDING));
    addDoc("coll/val1", map("a", Arrays.asList(1, 1)));
    addDoc("coll/val2", map("a", Arrays.asList(1, 2)));
    addDoc("coll/val3", map("a", Arrays.asList(2, 1)));
    addDoc("coll/val4", map("a", Arrays.asList(2, 2)));

    Query query =
        query("coll")
            .filter(filter("a", "array-contains", 1))
            .filter(filter("a", "!=", Arrays.asList(1, 2)));
    verifyResults(query, "coll/val1", "coll/val3");
  }

  @Test
  public void testEqualsWithNotEqualsOnSameField() {
    setUpSingleValueFilter();

    Object[][] filtersAndResults =
        new Object[][] {
          new Filter[] {filter("count", ">", 1), filter("count", "!=", 2)},
          new String[] {"coll/val3"},
          new Filter[] {filter("count", "==", 1), filter("count", "!=", 2)},
          new String[] {"coll/val1"},
          new Filter[] {filter("count", "==", 1), filter("count", "!=", 1)},
          new String[] {},
          new Filter[] {filter("count", ">", 2), filter("count", "!=", 2)},
          new String[] {"coll/val3"},
          new Filter[] {filter("count", ">=", 2), filter("count", "!=", 2)},
          new String[] {"coll/val3"},
          new Filter[] {filter("count", "<=", 2), filter("count", "!=", 2)},
          new String[] {"coll/val1"},
          new Filter[] {filter("count", "<=", 2), filter("count", "!=", 1)},
          new String[] {"coll/val2"},
          new Filter[] {filter("count", "<", 2), filter("count", "!=", 2)},
          new String[] {"coll/val1"},
          new Filter[] {filter("count", "<", 2), filter("count", "!=", 1)},
          new String[] {},
          new Filter[] {
            filter("count", ">", 2), filter("count", "not-in", Collections.singletonList(3))
          },
          new String[] {},
          new Filter[] {
            filter("count", ">=", 2), filter("count", "not-in", Collections.singletonList(3))
          },
          new String[] {"coll/val2"},
          new Filter[] {filter("count", ">=", 2), filter("count", "not-in", Arrays.asList(3, 3))},
          new String[] {"coll/val2"},
          new Filter[] {filter("count", ">", 1), filter("count", "<", 3), filter("count", "!=", 2)},
          new String[] {},
          new Filter[] {
            filter("count", ">=", 1), filter("count", "<", 3), filter("count", "!=", 2)
          },
          new String[] {"coll/val1"},
          new Filter[] {
            filter("count", ">=", 1), filter("count", "<=", 3), filter("count", "!=", 2)
          },
          new String[] {"coll/val1", "coll/val3"},
          new Filter[] {
            filter("count", ">", 1), filter("count", "<=", 3), filter("count", "!=", 2)
          },
          new String[] {"coll/val3"}
        };

    for (int i = 0; i < filtersAndResults.length; i += 2) {
      Query query = query("coll");
      for (Filter filter : (Filter[]) filtersAndResults[i]) {
        query = query.filter(filter);
      }
      verifyResults(query, (String[]) filtersAndResults[i + 1]);
    }
  }

  @Test
  public void testArrayContainsAnyFilter() {
    setUpArrayValueFilter();
    Query query =
        query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1, 2, 4)));
    verifyResults(query, "coll/arr1", "coll/arr2");
  }

  @Test
  public void testArrayContainsDoesNotMatchNonArray() {
    // Set up two field indices. This causes two index entries to be written, but our query should
    // only use one index.
    setUpArrayValueFilter();
    setUpSingleValueFilter();
    addDoc("coll/nonmatching", map("values", 1));
    Query query = query("coll").filter(filter("values", "array-contains-any", Arrays.asList(1)));
    verifyResults(query, "coll/arr1");
  }

  @Test
  public void testNoMatchingFilter() {
    setUpSingleValueFilter();
    Query query = query("coll").filter(filter("unknown", "==", true));
    assertEquals(indexManager.getIndexType(query.toTarget()), IndexManager.IndexType.NONE);
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
    indexManager.addFieldIndex(fieldIndex("coll", "value", Kind.ASCENDING));
    addDoc("coll/boolean", map("value", true));
    addDoc("coll/string", map("value", "true"));
    addDoc("coll/number", map("value", 1));
    Query query = query("coll").filter(filter("value", "==", true));
    verifyResults(query, "coll/boolean");
  }

  @Test
  public void testCollectionGroup() {
    indexManager.addFieldIndex(fieldIndex("coll1", "value", Kind.ASCENDING));
    addDoc("coll1/doc1", map("value", true));
    addDoc("coll2/doc2/coll1/doc1", map("value", true));
    addDoc("coll2/doc2", map("value", true));
    Query query = new Query(path(""), "coll1").filter(filter("value", "==", true));
    verifyResults(query, "coll1/doc1", "coll2/doc2/coll1/doc1");
  }

  @Test
  public void testLimitFilter() {
    indexManager.addFieldIndex(fieldIndex("coll", "value", Kind.ASCENDING));
    addDoc("coll/doc1", map("value", 1));
    addDoc("coll/doc2", map("value", 1));
    addDoc("coll/doc3", map("value", 1));
    Query query = query("coll").filter(filter("value", "==", 1)).limitToFirst(2);
    verifyResults(query, "coll/doc1", "coll/doc2");
  }

  @Test
  public void testLimitAppliesOrdering() {
    indexManager.addFieldIndex(fieldIndex("coll", "value", Kind.CONTAINS, "value", Kind.ASCENDING));
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
  public void testIndexEntriesAreUpdated() {
    indexManager.addFieldIndex(fieldIndex("coll", "value", Kind.ASCENDING));
    Query query = query("coll").orderBy(orderBy("value"));

    addDoc("coll/doc1", map("value", true));
    verifyResults(query, "coll/doc1");

    addDocs(doc("coll/doc1", 1, map()), doc("coll/doc2", 1, map("value", true)));
    verifyResults(query, "coll/doc2");
  }

  @Test
  public void testIndexEntriesAreUpdatedWithDeletedDoc() {
    indexManager.addFieldIndex(fieldIndex("coll", "value", Kind.ASCENDING));
    Query query = query("coll").orderBy(orderBy("value"));

    addDoc("coll/doc1", map("value", true));
    verifyResults(query, "coll/doc1");

    addDocs(deletedDoc("coll/doc1", 1));
    verifyResults(query);
  }

  @Test
  public void testCursorsDoNoExpandResultSet() {
    indexManager.addFieldIndex(fieldIndex("coll", "c", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "c", Kind.DESCENDING));

    addDoc("coll/val1", map("a", 1, "b", 1, "c", 3));
    addDoc("coll/val2", map("a", 2, "b", 2, "c", 2));

    Query query =
        query("coll").filter(filter("c", ">", 2)).orderBy(orderBy("c")).startAt(bound(true, 2));
    verifyResults(query, "coll/val1");

    query =
        query("coll")
            .filter(filter("c", "<", 3))
            .orderBy(orderBy("c", "desc"))
            .startAt(bound(true, 3));
    verifyResults(query, "coll/val2");
  }

  @Test
  public void testFiltersOnTheSameField() {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING, "b", Kind.ASCENDING));

    addDoc("coll/val1", map("a", 1, "b", 1));
    addDoc("coll/val2", map("a", 2, "b", 2));
    addDoc("coll/val3", map("a", 3, "b", 3));
    addDoc("coll/val4", map("a", 4, "b", 4));

    Query query = query("coll").filter(filter("a", ">", 1)).filter(filter("a", "==", 2));
    verifyResults(query, "coll/val2");

    query = query("coll").filter(filter("a", "<=", 1)).filter(filter("a", "==", 2));
    verifyResults(query);

    query =
        query("coll")
            .filter(filter("a", ">", 1))
            .filter(filter("a", "==", 2))
            .orderBy(orderBy("a"))
            .orderBy(orderBy(DocumentKey.KEY_FIELD_NAME));
    verifyResults(query, "coll/val2");

    query =
        query("coll")
            .filter(filter("a", ">", 1))
            .filter(filter("a", "==", 2))
            .orderBy(orderBy("a"))
            .orderBy(orderBy(DocumentKey.KEY_FIELD_NAME, "desc"));
    verifyResults(query, "coll/val2");

    query =
        query("coll")
            .filter(filter("a", ">", 1))
            .filter(filter("a", "==", 3))
            .orderBy(orderBy("a"))
            .orderBy(orderBy("b"));
    verifyResults(query, "coll/val3");
  }

  @Test
  public void testAdvancedQueries() {
    // This test compares local query results with those received from the Java Server SDK.

    indexManager.addFieldIndex(fieldIndex("coll", "null", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "int", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "float", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "string", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "multi", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "array", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "array", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "array", Kind.CONTAINS));
    indexManager.addFieldIndex(fieldIndex("coll", "map", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "map.field", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "prefix", Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex("coll", "prefix", Kind.ASCENDING, "suffix", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING, "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING, "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING, "b", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING, "b", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING, "a", Kind.ASCENDING));

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
        "coll/{array:[1]}",
        "coll/{array:[2,foo]}",
        "coll/{array:[3,foo],int:3}");
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
        "coll/{array:[1]}",
        "coll/{array:foo}");
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
        "coll/{array:[1]}",
        "coll/{array:foo}");
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
        "coll/{array:[1]}",
        "coll/{array:foo}");
    verifyResults(
        q.orderBy(orderBy("array", "desc"))
            .startAt(bound(false, Arrays.asList(2, "foo")))
            .limitToFirst(2),
        "coll/{array:[1,foo],int:1}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(true, Collections.singletonList(2))),
        "coll/{array:foo}",
        "coll/{array:[1]}",
        "coll/{array:[1,foo],int:1}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(true, Collections.singletonList(2))),
        "coll/{array:[3,foo],int:3}",
        "coll/{array:[2,foo]}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Collections.singletonList(2))),
        "coll/{array:foo}",
        "coll/{array:[1]}",
        "coll/{array:[1,foo],int:1}");
    verifyResults(
        q.orderBy(orderBy("array"))
            .endAt(bound(false, Collections.singletonList(2)))
            .limitToFirst(2),
        "coll/{array:foo}",
        "coll/{array:[1]}");
    verifyResults(
        q.orderBy(orderBy("array", "desc")).endAt(bound(false, Collections.singletonList(2))),
        "coll/{array:[3,foo],int:3}",
        "coll/{array:[2,foo]}");
    verifyResults(
        q.orderBy(orderBy("array")).endAt(bound(false, Arrays.asList(2, "foo"))),
        "coll/{array:foo}",
        "coll/{array:[1]}",
        "coll/{array:[1,foo],int:1}");
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
        "coll/{map:{field:false}}",
        "coll/{map:{field:true}}");
    verifyResults(
        q.orderBy(orderBy("map.field")), "coll/{map:{field:false}}", "coll/{map:{field:true}}");
  }

  @Test
  public void testPersistsIndexOffset() {
    indexManager.addFieldIndex(fieldIndex("coll1", "value", Kind.ASCENDING));
    IndexOffset offset = IndexOffset.create(version(20), key("coll/doc"), 42);
    indexManager.updateCollectionGroup("coll1", offset);

    indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    indexManager.start();

    Collection<FieldIndex> indexes = indexManager.getFieldIndexes("coll1");
    assertEquals(indexes.size(), 1);
    FieldIndex index = indexes.iterator().next();
    assertEquals(offset, index.getIndexState().getOffset());
  }

  @Test
  public void testNextCollectionGroupAdvancesWhenCollectionIsUpdated() {
    indexManager.addFieldIndex(fieldIndex("coll1"));
    indexManager.addFieldIndex(fieldIndex("coll2"));

    String collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll1", collectionGroup);

    indexManager.updateCollectionGroup("coll1", IndexOffset.NONE);
    collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll2", collectionGroup);

    indexManager.updateCollectionGroup("coll2", IndexOffset.NONE);
    collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll1", collectionGroup);
  }

  @Test
  public void testGetFieldIndexes() {
    indexManager.addFieldIndex(
        fieldIndex("coll1", 1, FieldIndex.INITIAL_STATE, "value", Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex("coll2", 2, FieldIndex.INITIAL_STATE, "value", Kind.CONTAINS));

    Collection<FieldIndex> indexes = indexManager.getFieldIndexes("coll1");
    assertEquals(indexes.size(), 1);
    Iterator<FieldIndex> it = indexes.iterator();
    assertEquals(it.next().getCollectionGroup(), "coll1");
    indexManager.addFieldIndex(
        fieldIndex("coll1", 3, FieldIndex.INITIAL_STATE, "newValue", Kind.CONTAINS));

    indexes = indexManager.getFieldIndexes("coll1");
    assertEquals(indexes.size(), 2);
    it = indexes.iterator();
    assertEquals(it.next().getCollectionGroup(), "coll1");
    assertEquals(it.next().getCollectionGroup(), "coll1");
  }

  @Test
  public void testDeleteFieldIndexRemovesEntryFromCollectionGroup() {
    indexManager.addFieldIndex(
        fieldIndex("coll1", 1, IndexState.create(1, IndexOffset.NONE), "value", Kind.ASCENDING));
    indexManager.addFieldIndex(
        fieldIndex("coll2", 2, IndexState.create(2, IndexOffset.NONE), "value", Kind.CONTAINS));
    String collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll1", collectionGroup);

    indexManager.deleteFieldIndex(indexManager.getFieldIndexes("coll1").iterator().next());
    collectionGroup = indexManager.getNextCollectionGroupToUpdate();
    assertEquals("coll2", collectionGroup);
  }

  @Test
  public void testDeleteFieldIndexRemovesAllMetadata() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 1, IndexState.create(1, IndexOffset.NONE), "value", Kind.ASCENDING));
    addDoc("coll/doc", map("value", 1));
    indexManager.updateCollectionGroup("coll", IndexOffset.NONE);

    validateRowCount(1);

    FieldIndex existingIndex = indexManager.getFieldIndexes("coll").iterator().next();
    indexManager.deleteFieldIndex(existingIndex);

    validateRowCount(0);
  }

  @Test
  public void testChangeUser() {
    IndexManager indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    indexManager.start();

    // Add two indexes and mark one as updated.
    indexManager.addFieldIndex(fieldIndex("coll1", 1, FieldIndex.INITIAL_STATE));
    indexManager.addFieldIndex(fieldIndex("coll2", 2, FieldIndex.INITIAL_STATE));

    indexManager.updateCollectionGroup("coll2", IndexOffset.NONE);

    verifySequenceNumber(indexManager, "coll1", 0);
    verifySequenceNumber(indexManager, "coll2", 1);

    // New user signs it. The user should see all existing field indices.
    // Sequence numbers are set to 0.
    indexManager = persistence.getIndexManager(new User("authenticated"));
    indexManager.start();

    // Add a new index and mark it as updated.
    indexManager.addFieldIndex(fieldIndex("coll3", 2, FieldIndex.INITIAL_STATE));
    indexManager.updateCollectionGroup("coll3", IndexOffset.NONE);

    verifySequenceNumber(indexManager, "coll1", 0);
    verifySequenceNumber(indexManager, "coll2", 0);
    verifySequenceNumber(indexManager, "coll3", 1);

    // Original user signs it. The user should also see the new index with a zero sequence number.
    indexManager = persistence.getIndexManager(User.UNAUTHENTICATED);
    indexManager.start();

    verifySequenceNumber(indexManager, "coll1", 0);
    verifySequenceNumber(indexManager, "coll2", 1);
    verifySequenceNumber(indexManager, "coll3", 0);
  }

  @Test
  public void testPartialIndexAndFullIndex() throws Exception {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "c", Kind.ASCENDING, "d", Kind.ASCENDING));

    Query query1 = query("coll").filter(filter("a", "==", 1));
    validateIndexType(query1, IndexManager.IndexType.FULL);

    Query query2 = query("coll").filter(filter("b", "==", 1));
    validateIndexType(query2, IndexManager.IndexType.FULL);

    Query query3 = query("coll").filter(filter("a", "==", 1)).orderBy(orderBy("a"));
    validateIndexType(query3, IndexManager.IndexType.FULL);

    Query query4 = query("coll").filter(filter("b", "==", 1)).orderBy(orderBy("b"));
    validateIndexType(query4, IndexManager.IndexType.FULL);

    Query query5 = query("coll").filter(filter("a", "==", 1)).filter(filter("b", "==", 1));
    validateIndexType(query5, IndexManager.IndexType.PARTIAL);

    Query query6 = query("coll").filter(filter("a", "==", 1)).orderBy(orderBy("b"));
    validateIndexType(query6, IndexManager.IndexType.PARTIAL);

    Query query7 = query("coll").filter(filter("b", "==", 1)).orderBy(orderBy("a"));
    validateIndexType(query7, IndexManager.IndexType.PARTIAL);

    Query query8 = query("coll").filter(filter("c", "==", 1)).filter(filter("d", "==", 1));
    validateIndexType(query8, IndexManager.IndexType.FULL);

    Query query9 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("c"));
    validateIndexType(query9, IndexManager.IndexType.FULL);

    Query query10 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("d"));
    validateIndexType(query10, IndexManager.IndexType.FULL);

    Query query11 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("c"))
            .orderBy(orderBy("d"));
    validateIndexType(query11, IndexManager.IndexType.FULL);

    Query query12 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("d"))
            .orderBy(orderBy("c"));
    validateIndexType(query12, IndexManager.IndexType.FULL);

    Query query13 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", "==", 1))
            .orderBy(orderBy("e"));
    validateIndexType(query13, IndexManager.IndexType.PARTIAL);

    Query query14 = query("coll").filter(filter("c", "==", 1)).filter(filter("d", "<=", 1));
    validateIndexType(query14, IndexManager.IndexType.FULL);

    Query query15 =
        query("coll")
            .filter(filter("c", "==", 1))
            .filter(filter("d", ">", 1))
            .orderBy(orderBy("d"));
    validateIndexType(query15, IndexManager.IndexType.FULL);
  }

  @Test
  public void testIndexTypeForOrQueries() throws Exception {
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING, "a", Kind.ASCENDING));

    // OR query without orderBy without limit which has missing sub-target indexes.
    Query query1 = query("coll").filter(orFilters(filter("a", "==", 1), filter("c", "==", 1)));
    validateIndexType(query1, IndexManager.IndexType.NONE);

    // OR query with explicit orderBy without limit which has missing sub-target indexes.
    Query query2 =
        query("coll")
            .filter(orFilters(filter("a", "==", 1), filter("c", "==", 1)))
            .orderBy(orderBy("c"));
    validateIndexType(query2, IndexManager.IndexType.NONE);

    // OR query with implicit orderBy without limit which has missing sub-target indexes.
    Query query3 = query("coll").filter(orFilters(filter("a", "==", 1), filter("c", ">", 1)));
    validateIndexType(query3, IndexManager.IndexType.NONE);

    // OR query with explicit orderBy with limit which has missing sub-target indexes.
    Query query4 =
        query("coll")
            .filter(orFilters(filter("a", "==", 1), filter("c", "==", 1)))
            .orderBy(orderBy("c"))
            .limitToFirst(2);
    validateIndexType(query4, IndexManager.IndexType.NONE);

    // OR query with implicit orderBy with limit which has missing sub-target indexes.
    Query query5 =
        query("coll").filter(orFilters(filter("a", "==", 1), filter("c", ">", 1))).limitToLast(2);
    validateIndexType(query5, IndexManager.IndexType.NONE);

    // OR query without orderBy without limit which has all sub-target indexes.
    Query query6 = query("coll").filter(orFilters(filter("a", "==", 1), filter("b", "==", 1)));
    validateIndexType(query6, IndexManager.IndexType.FULL);

    // OR query with explicit orderBy without limit which has all sub-target indexes.
    Query query7 =
        query("coll")
            .filter(orFilters(filter("a", "==", 1), filter("b", "==", 1)))
            .orderBy(orderBy("a"));
    validateIndexType(query7, IndexManager.IndexType.FULL);

    // OR query with implicit orderBy without limit which has all sub-target indexes.
    Query query8 = query("coll").filter(orFilters(filter("a", ">", 1), filter("b", "==", 1)));
    validateIndexType(query8, IndexManager.IndexType.FULL);

    // OR query without orderBy with limit which has all sub-target indexes.
    Query query9 =
        query("coll").filter(orFilters(filter("a", "==", 1), filter("b", "==", 1))).limitToFirst(2);
    validateIndexType(query9, IndexManager.IndexType.PARTIAL);

    // OR query with explicit orderBy with limit which has all sub-target indexes.
    Query query10 =
        query("coll")
            .filter(orFilters(filter("a", "==", 1), filter("b", "==", 1)))
            .orderBy(orderBy("a"))
            .limitToFirst(2);
    validateIndexType(query10, IndexManager.IndexType.PARTIAL);

    // OR query with implicit orderBy with limit which has all sub-target indexes.
    Query query11 =
        query("coll").filter(orFilters(filter("a", ">", 1), filter("b", "==", 1))).limitToLast(2);
    validateIndexType(query11, IndexManager.IndexType.PARTIAL);
  }

  @Test
  public void TestCreateTargetIndexesCreatesFullIndexesForEachSubTarget() {
    Query query =
        query("coll")
            .filter(orFilters(filter("a", "==", 1), filter("b", "==", 2), filter("c", "==", 3)));

    Query subQuery1 = query("coll").filter(filter("a", "==", 1));
    Query subQuery2 = query("coll").filter(filter("b", "==", 2));
    Query subQuery3 = query("coll").filter(filter("c", "==", 3));

    validateIndexType(query, IndexManager.IndexType.NONE);
    validateIndexType(subQuery1, IndexManager.IndexType.NONE);
    validateIndexType(subQuery2, IndexManager.IndexType.NONE);
    validateIndexType(subQuery3, IndexManager.IndexType.NONE);

    indexManager.createTargetIndexes(query.toTarget());

    validateIndexType(query, IndexManager.IndexType.FULL);
    validateIndexType(subQuery1, IndexManager.IndexType.FULL);
    validateIndexType(subQuery2, IndexManager.IndexType.FULL);
    validateIndexType(subQuery3, IndexManager.IndexType.FULL);
  }

  @Test
  public void TestCreateTargetIndexesUpgradesPartialIndexToFullIndex() {
    Query query = query("coll").filter(andFilters(filter("a", "==", 1), filter("b", "==", 2)));

    Query subQuery1 = query("coll").filter(filter("a", "==", 1));
    Query subQuery2 = query("coll").filter(filter("b", "==", 2));

    indexManager.createTargetIndexes(subQuery1.toTarget());

    validateIndexType(query, IndexManager.IndexType.PARTIAL);
    validateIndexType(subQuery1, IndexManager.IndexType.FULL);
    validateIndexType(subQuery2, IndexManager.IndexType.NONE);

    indexManager.createTargetIndexes(query.toTarget());

    validateIndexType(query, IndexManager.IndexType.FULL);
    validateIndexType(subQuery1, IndexManager.IndexType.FULL);
    validateIndexType(subQuery2, IndexManager.IndexType.NONE);
  }

  @Test
  public void testIndexesBsonObjectId() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc("coll/doc1", map("key", new BsonObjectId("507f191e810c19729de860ea")));
    addDoc("coll/doc2", map("key", new BsonObjectId("507f191e810c19729de860eb")));
    addDoc("coll/doc3", map("key", new BsonObjectId("507f191e810c19729de860ec")));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "==", new BsonObjectId("507f191e810c19729de860ea")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", "!=", new BsonObjectId("507f191e810c19729de860ea")));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", ">=", new BsonObjectId("507f191e810c19729de860eb")));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "<=", new BsonObjectId("507f191e810c19729de860eb")));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">", new BsonObjectId("507f191e810c19729de860eb")));
    verifyResults(query, "coll/doc3");

    query = query("coll").filter(filter("key", "<", new BsonObjectId("507f191e810c19729de860eb")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", ">", new BsonObjectId("507f191e810c19729de860ec")));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", new BsonObjectId("507f191e810c19729de860ea")));
    verifyResults(query);
  }

  @Test
  public void testIndexesBsonBinary() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc("coll/doc1", map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    addDoc("coll/doc2", map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})));
    addDoc("coll/doc3", map("key", BsonBinaryData.fromBytes(1, new byte[] {2, 1, 2})));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query =
        query("coll")
            .filter(filter("key", "==", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    verifyResults(query, "coll/doc1");

    query =
        query("coll")
            .filter(filter("key", "!=", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query =
        query("coll")
            .filter(filter("key", ">=", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query =
        query("coll")
            .filter(filter("key", "<=", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query =
        query("coll").filter(filter("key", ">", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})));
    verifyResults(query, "coll/doc3");

    query =
        query("coll").filter(filter("key", "<", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})));
    verifyResults(query, "coll/doc1");

    query =
        query("coll").filter(filter("key", ">", BsonBinaryData.fromBytes(1, new byte[] {2, 1, 2})));
    verifyResults(query);

    query =
        query("coll").filter(filter("key", "<", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    verifyResults(query);
  }

  @Test
  public void testIndexesBsonTimestamp() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc("coll/doc1", map("key", new BsonTimestamp(1, 1)));
    addDoc("coll/doc2", map("key", new BsonTimestamp(1, 2)));
    addDoc("coll/doc3", map("key", new BsonTimestamp(2, 1)));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "==", new BsonTimestamp(1, 1)));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", "!=", new BsonTimestamp(1, 1)));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", ">=", new BsonTimestamp(1, 2)));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "<=", new BsonTimestamp(1, 2)));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">", new BsonTimestamp(1, 2)));
    verifyResults(query, "coll/doc3");

    query = query("coll").filter(filter("key", "<", new BsonTimestamp(1, 2)));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", ">", new BsonTimestamp(2, 1)));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", new BsonTimestamp(1, 1)));
    verifyResults(query);
  }

  @Test
  public void testIndexesRegex() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc("coll/doc1", map("key", new RegexValue("a", "i")));
    addDoc("coll/doc2", map("key", new RegexValue("a", "m")));
    addDoc("coll/doc3", map("key", new RegexValue("b", "i")));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "==", new RegexValue("a", "i")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", "!=", new RegexValue("a", "i")));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", ">=", new RegexValue("a", "m")));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "<=", new RegexValue("a", "m")));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">", new RegexValue("a", "m")));
    verifyResults(query, "coll/doc3");

    query = query("coll").filter(filter("key", "<", new RegexValue("a", "m")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", ">", new RegexValue("b", "i")));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", new RegexValue("a", "i")));
    verifyResults(query);
  }

  @Test
  public void testIndexesInt32() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc("coll/doc1", map("key", new Int32Value(1)));
    addDoc("coll/doc2", map("key", new Int32Value(2)));
    addDoc("coll/doc3", map("key", new Int32Value(3)));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "==", new Int32Value(1)));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", "!=", new Int32Value(1)));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", ">=", new Int32Value(2)));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "<=", new Int32Value(2)));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">", new Int32Value(2)));
    verifyResults(query, "coll/doc3");

    query = query("coll").filter(filter("key", "<", new Int32Value(2)));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", ">", new Int32Value(3)));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", new Int32Value(1)));
    verifyResults(query);
  }

  @Test
  public void testIndexesDecimal128Value() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc("coll/doc1", map("key", new Decimal128Value("-1.2e3")));
    addDoc("coll/doc2", map("key", new Decimal128Value("0.0")));
    addDoc("coll/doc3", map("key", new Decimal128Value("1.2e3")));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "==", new Decimal128Value("-1200")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", "!=", new Decimal128Value("0")));
    verifyResults(query, "coll/doc1", "coll/doc3");

    query = query("coll").filter(filter("key", ">=", new Decimal128Value("-0")));
    verifyResults(query, "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "<=", new Decimal128Value("-0.0")));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">", new Decimal128Value("1.2e-3")));
    verifyResults(query, "coll/doc3");

    query = query("coll").filter(filter("key", "<", new Decimal128Value("-1.2e-3")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", ">", new Decimal128Value("1.2e3")));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", new Decimal128Value("-1.2e3")));
    verifyResults(query);
  }

  @Test
  public void testIndexesDecimal128ValueWithPrecisionLoss() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));

    addDoc(
        "coll/doc1",
        map(
            "key",
            new Decimal128Value(
                "-0.1234567890123456789"))); // will be rounded to -0.12345678901234568
    addDoc("coll/doc2", map("key", new Decimal128Value("0")));
    addDoc(
        "coll/doc3",
        map(
            "key",
            new Decimal128Value(
                "0.1234567890123456789"))); // will be rounded to 0.12345678901234568

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc1", "coll/doc2", "coll/doc3");

    query = query("coll").filter(filter("key", "==", new Decimal128Value("0.1234567890123456789")));
    verifyResults(query, "coll/doc3");

    // Mismatch behaviour caused by rounding error. Firestore fetches the doc3 from SQLite DB as
    // doc3 rounds to the same number, even though the actual number in doc3 is different
    // Unlike SQLiteLocalStoreTest, this returns the doc3 as result
    query = query("coll").filter(filter("key", "==", new Decimal128Value("0.12345678901234568")));
    verifyResults(query, "coll/doc3");

    // Operations that doesn't go up to 17 decimal digits of precision wouldn't be affected by
    // this rounding errors.
    query = query("coll").filter(filter("key", "!=", new Decimal128Value("0.0")));
    verifyResults(query, "coll/doc1", "coll/doc3");

    query = query("coll").filter(filter("key", ">=", new Decimal128Value("1.23e-1")));
    verifyResults(query, "coll/doc3");

    query = query("coll").filter(filter("key", "<=", new Decimal128Value("-1.23e-1")));
    verifyResults(query, "coll/doc1");

    query = query("coll").filter(filter("key", ">", new Decimal128Value("1.2e3")));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", new Decimal128Value("-1.2e3")));
    verifyResults(query);
  }

  @Test
  public void testIndexesMinKey() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("key", MinKey.instance()));
    addDoc("coll/doc2", map("key", MinKey.instance()));
    addDoc("coll/doc3", map("key", null));
    addDoc("coll/doc4", map("key", 1));
    addDoc("coll/doc5", map("key", MaxKey.instance()));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc3", "coll/doc1", "coll/doc2", "coll/doc4", "coll/doc5");

    query = query("coll").filter(filter("key", "==", MinKey.instance()));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", "!=", MinKey.instance()));
    verifyResults(query, "coll/doc4", "coll/doc5");

    query = query("coll").filter(filter("key", ">=", MinKey.instance()));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", "<=", MinKey.instance()));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">", MinKey.instance()));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", MinKey.instance()));
    verifyResults(query);
  }

  @Test
  public void testIndexesMaxKey() {
    indexManager.addFieldIndex(
        fieldIndex("coll", 0, FieldIndex.INITIAL_STATE, "key", FieldIndex.Segment.Kind.ASCENDING));
    addDoc("coll/doc1", map("key", MinKey.instance()));
    addDoc("coll/doc2", map("key", 1));
    addDoc("coll/doc3", map("key", MaxKey.instance()));
    addDoc("coll/doc4", map("key", MaxKey.instance()));
    addDoc("coll/doc5", map("key", null));

    Query query = query("coll").orderBy(orderBy("key", "asc"));
    verifyResults(query, "coll/doc5", "coll/doc1", "coll/doc2", "coll/doc3", "coll/doc4");

    query = query("coll").filter(filter("key", "==", MaxKey.instance()));
    verifyResults(query, "coll/doc3", "coll/doc4");

    query = query("coll").filter(filter("key", "!=", MaxKey.instance()));
    verifyResults(query, "coll/doc1", "coll/doc2");

    query = query("coll").filter(filter("key", ">=", MaxKey.instance()));
    verifyResults(query, "coll/doc3", "coll/doc4");

    query = query("coll").filter(filter("key", "<=", MaxKey.instance()));
    verifyResults(query, "coll/doc3", "coll/doc4");

    query = query("coll").filter(filter("key", ">", MaxKey.instance()));
    verifyResults(query);

    query = query("coll").filter(filter("key", "<", MaxKey.instance()));
    verifyResults(query);
  }

  @Test
  public void testIndexFieldsOfBsonTypesTogether() {
    indexManager.addFieldIndex(fieldIndex("coll", "key", Kind.DESCENDING));

    addDoc("coll/doc1", map("key", MinKey.instance()));
    addDoc("coll/doc2", map("key", new Int32Value(2)));
    addDoc("coll/doc3", map("key", new Int32Value(-1)));
    addDoc("coll/doc4", map("key", new Decimal128Value("1.2e3")));
    addDoc("coll/doc5", map("key", new Decimal128Value("-0.0")));
    addDoc("coll/doc6", map("key", new BsonTimestamp(1, 2)));
    addDoc("coll/doc7", map("key", new BsonTimestamp(1, 1)));
    addDoc("coll/doc8", map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 4})));
    addDoc("coll/doc9", map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    addDoc("coll/doc10", map("key", new BsonObjectId("507f191e810c19729de860eb")));
    addDoc("coll/doc11", map("key", new BsonObjectId("507f191e810c19729de860ea")));
    addDoc("coll/doc12", map("key", new RegexValue("a", "m")));
    addDoc("coll/doc13", map("key", new RegexValue("a", "i")));
    addDoc("coll/doc14", map("key", MaxKey.instance()));

    Query query = query("coll").orderBy(orderBy("key", "desc"));
    verifyResults(
        query,
        "coll/doc14", // maxKey
        "coll/doc12", // regex m
        "coll/doc13", // regex i
        "coll/doc10", // objectId eb
        "coll/doc11", // objectId ea
        "coll/doc8", // binary [1,2,4]
        "coll/doc9", // binary [1,2,3]
        "coll/doc6", // timestamp 1,2
        "coll/doc7", // timestamp 1,1
        "coll/doc4", // decimal128 1200
        "coll/doc2", // int32 2
        "coll/doc5", // decimal128 -0.0
        "coll/doc3", // int32 -1
        "coll/doc1" // minKey
        );
  }

  @Test
  public void testIndexFieldsOfAllTypesTogether() {
    indexManager.addFieldIndex(fieldIndex("coll", "key", Kind.DESCENDING));

    addDoc("coll/a", map("key", null));
    addDoc("coll/b", map("key", MinKey.instance()));
    addDoc("coll/c", map("key", true));
    addDoc("coll/d", map("key", Double.NaN));
    addDoc("coll/e", map("key", new Int32Value(1)));
    addDoc("coll/f", map("key", 2.0));
    addDoc("coll/g", map("key", 3L));
    addDoc("coll/h", map("key", new Decimal128Value("1.2e3")));
    addDoc("coll/i", map("key", new Timestamp(100, 123456000)));
    addDoc("coll/j", map("key", new BsonTimestamp(1, 2)));
    addDoc("coll/k", map("key", "string"));
    addDoc("coll/l", map("key", blob(1, 2, 3)));
    addDoc("coll/m", map("key", BsonBinaryData.fromBytes(1, new byte[] {1, 2, 3})));
    addDoc("coll/n", map("key", ref("foo/bar")));
    addDoc("coll/o", map("key", new BsonObjectId("507f191e810c19729de860ea")));
    addDoc("coll/p", map("key", new GeoPoint(0, 1)));
    addDoc("coll/q", map("key", new RegexValue("^foo", "i")));
    addDoc("coll/r", map("key", Arrays.asList(1, 2)));
    addDoc("coll/s", map("key", FieldValue.vector(new double[] {1, 2, 3})));
    addDoc("coll/t", map("key", map("a", 1)));
    addDoc("coll/u", map("key", MaxKey.instance()));

    Query query = query("coll").orderBy(orderBy("key", "desc"));
    verifyResults(
        query,
        "coll/u", // maxKey
        "coll/t", // map
        "coll/s", // vector
        "coll/r", // array
        "coll/q", // regex
        "coll/p", // geopoint
        "coll/o", // objectId
        "coll/n", // reference
        "coll/m", // bsonBinary
        "coll/l", // bytes
        "coll/k", // string
        "coll/j", // bsonTimestamp
        "coll/i", // timestamp
        "coll/h", // Number decimal128
        "coll/g", // Number long
        "coll/f", // Number double
        "coll/e", // Number int32
        "coll/d", // Number NaN
        "coll/c", // boolean
        "coll/b", // minKey
        "coll/a" // null
        );
  }

  private void validateIndexType(Query query, IndexManager.IndexType expected) {
    IndexManager.IndexType indexType = indexManager.getIndexType(query.toTarget());
    assertEquals(indexType, expected);
  }

  private void verifySequenceNumber(
      IndexManager indexManager, String collectionGroup, int expectedSequnceNumber) {
    assertEquals(
        expectedSequnceNumber,
        indexManager
            .getFieldIndexes(collectionGroup)
            .iterator()
            .next()
            .getIndexState()
            .getSequenceNumber());
  }

  private void addDocs(Document... docs) {
    indexManager.updateIndexEntries(docMap(docs));
  }

  private void addDoc(String key, Map<String, Object> data) {
    addDocs(doc(key, 1, data));
  }

  private void verifyResults(Query query, String... documents) {
    Target target = query.toTarget();
    List<DocumentKey> results = indexManager.getDocumentsMatchingTarget(target);
    assertNotNull("Target cannot be served from index.", results);
    List<DocumentKey> keys =
        Arrays.stream(documents).map(TestUtil::key).collect(Collectors.toList());
    assertWithMessage("Result for %s", query)
        .that(results)
        .containsExactlyElementsIn(keys)
        .inOrder();
  }

  /** Validates the row count in the SQLite tables that are used for indexing. */
  private void validateRowCount(int expectedRows) {
    SQLitePersistence persistence = (SQLitePersistence) this.persistence;
    persistence
        .query(
            "SELECT "
                + "(SELECT COUNT(*) FROM index_state) AS index_state_count, "
                + "(SELECT COUNT(*) FROM index_entries) AS index_entries_count, "
                + "(SELECT COUNT(*) FROM index_configuration) AS index_configuration_count")
        .first(
            value -> {
              assertEquals(value.getInt(0), expectedRows);
              assertEquals(value.getInt(1), expectedRows);
              assertEquals(value.getInt(2), expectedRows);
            });
  }
}
