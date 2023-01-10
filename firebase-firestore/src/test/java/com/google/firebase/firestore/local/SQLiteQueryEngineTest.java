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

import static com.google.firebase.firestore.local.IndexManager.*;
import static com.google.firebase.firestore.model.FieldIndex.*;
import static com.google.firebase.firestore.model.FieldIndex.Segment.*;
import static com.google.firebase.firestore.testutil.TestUtil.andFilters;
import static com.google.firebase.firestore.testutil.TestUtil.doc;
import static com.google.firebase.firestore.testutil.TestUtil.docMap;
import static com.google.firebase.firestore.testutil.TestUtil.docSet;
import static com.google.firebase.firestore.testutil.TestUtil.fieldIndex;
import static com.google.firebase.firestore.testutil.TestUtil.filter;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static com.google.firebase.firestore.testutil.TestUtil.orFilters;
import static com.google.firebase.firestore.testutil.TestUtil.orderBy;
import static com.google.firebase.firestore.testutil.TestUtil.patchMutation;
import static com.google.firebase.firestore.testutil.TestUtil.query;
import static com.google.firebase.firestore.testutil.TestUtil.setMutation;
import static org.junit.Assert.assertEquals;

import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentSet;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SQLiteQueryEngineTest extends QueryEngineTestCase {

  @Override
  Persistence getPersistence() {
    return PersistenceTestHelpers.createSQLitePersistence();
  }

  @Test
  public void testCombinesIndexedWithNonIndexedResults() throws Exception {
    MutableDocument doc1 = doc("coll/a", 1, map("foo", true));
    MutableDocument doc2 = doc("coll/b", 2, map("foo", true));
    MutableDocument doc3 = doc("coll/c", 3, map("foo", true));
    MutableDocument doc4 = doc("coll/d", 3, map("foo", true)).setHasLocalMutations();

    indexManager.addFieldIndex(fieldIndex("coll", "foo", Kind.ASCENDING));

    addDocument(doc1);
    addDocument(doc2);
    indexManager.updateIndexEntries(docMap(doc1, doc2));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc2));

    addDocument(doc3);
    addMutation(setMutation("coll/d", map("foo", true)));

    Query queryWithFilter = query("coll").filter(filter("foo", "==", true));
    DocumentSet results =
        expectOptimizedCollectionScan(() -> runQuery(queryWithFilter, SnapshotVersion.NONE));

    assertEquals(docSet(queryWithFilter.comparator(), doc1, doc2, doc3, doc4), results);
  }

  @Test
  public void testUsesPartialIndexForLimitQueries() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("coll/2", 1, map("a", 1, "b", 1));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 1, "b", 2));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 2, "b", 3));
    addDocument(doc1, doc2, doc3, doc4, doc5);

    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc5));

    Query query =
        query("coll").filter(filter("a", "==", 1)).filter(filter("b", "==", 1)).limitToFirst(3);
    DocumentSet result = expectOptimizedCollectionScan(() -> runQuery(query, SnapshotVersion.NONE));
    assertEquals(docSet(query.comparator(), doc2), result);
  }

  @Test
  public void testRefillsIndexedLimitQueries() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1));
    MutableDocument doc2 = doc("coll/2", 1, map("a", 2));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 4));
    addDocument(doc1, doc2, doc3, doc4);

    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc4));

    addMutation(patchMutation("coll/3", map("a", 5)));

    Query query = query("coll").orderBy(orderBy("a")).limitToFirst(3);
    DocumentSet result = expectOptimizedCollectionScan(() -> runQuery(query, SnapshotVersion.NONE));
    assertEquals(docSet(query.comparator(), doc1, doc2, doc4), result);
  }

  @Test
  public void canPerformOrQueriesUsingIndexes() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("coll/2", 1, map("a", 2, "b", 1));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", 2));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1, "b", 1));
    addDocument(doc1, doc2, doc3, doc4, doc5);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.DESCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc5));

    // Two equalities: a==1 || b==1.
    Query query1 = query("coll").filter(orFilters(filter("a", "==", 1), filter("b", "==", 1)));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc1, doc2, doc4, doc5), result1);

    // with one inequality: a>2 || b==1.
    Query query2 = query("coll").filter(orFilters(filter("a", ">", 2), filter("b", "==", 1)));
    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc2, doc3, doc5), result2);

    // (a==1 && b==0) || (a==3 && b==2)
    Query query3 =
        query("coll")
            .filter(
                orFilters(
                    andFilters(filter("a", "==", 1), filter("b", "==", 0)),
                    andFilters(filter("a", "==", 3), filter("b", "==", 2))));
    DocumentSet result3 =
        expectOptimizedCollectionScan(() -> runQuery(query3, SnapshotVersion.NONE));
    assertEquals(docSet(query3.comparator(), doc1, doc3), result3);

    // a==1 && (b==0 || b==3).
    Query query4 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "==", 1), orFilters(filter("b", "==", 0), filter("b", "==", 3))));
    DocumentSet result4 =
        expectOptimizedCollectionScan(() -> runQuery(query4, SnapshotVersion.NONE));
    assertEquals(docSet(query4.comparator(), doc1, doc4), result4);

    // (a==2 || b==2) && (a==3 || b==3)
    Query query5 =
        query("coll")
            .filter(
                andFilters(
                    orFilters(filter("a", "==", 2), filter("b", "==", 2)),
                    orFilters(filter("a", "==", 3), filter("b", "==", 3))));
    DocumentSet result5 =
        expectOptimizedCollectionScan(() -> runQuery(query5, SnapshotVersion.NONE));
    assertEquals(docSet(query5.comparator(), doc3), result5);

    // Test with limits (implicit order by ASC): (a==1) || (b > 0) LIMIT 2
    Query query6 =
        query("coll").filter(orFilters(filter("a", "==", 1), filter("b", ">", 0))).limitToFirst(2);
    DocumentSet result6 =
        expectOptimizedCollectionScan(() -> runQuery(query6, SnapshotVersion.NONE));
    assertEquals(docSet(query6.comparator(), doc1, doc2), result6);

    // Test with limits (implicit order by DESC): (a==1) || (b > 0) LIMIT_TO_LAST 2
    Query query7 =
        query("coll").filter(orFilters(filter("a", "==", 1), filter("b", ">", 0))).limitToLast(2);
    DocumentSet result7 =
        expectOptimizedCollectionScan(() -> runQuery(query7, SnapshotVersion.NONE));
    assertEquals(docSet(query7.comparator(), doc3, doc4), result7);

    // Test with limits (explicit order by ASC): (a==2) || (b == 1) ORDER BY a LIMIT 1
    Query query8 =
        query("coll")
            .filter(orFilters(filter("a", "==", 2), filter("b", "==", 1)))
            .limitToFirst(1)
            .orderBy(orderBy("a", "asc"));
    DocumentSet result8 =
        expectOptimizedCollectionScan(() -> runQuery(query8, SnapshotVersion.NONE));
    assertEquals(docSet(query8.comparator(), doc5), result8);

    // Test with limits (explicit order by DESC): (a==2) || (b == 1) ORDER BY a LIMIT_TO_LAST 1
    Query query9 =
        query("coll")
            .filter(orFilters(filter("a", "==", 2), filter("b", "==", 1)))
            .limitToLast(1)
            .orderBy(orderBy("a", "asc"));
    DocumentSet result9 =
        expectOptimizedCollectionScan(() -> runQuery(query9, SnapshotVersion.NONE));
    assertEquals(docSet(query9.comparator(), doc2), result9);

    // Test with limits without orderBy (the __name__ ordering is the tie breaker).
    Query query10 =
        query("coll").filter(orFilters(filter("a", "==", 2), filter("b", "==", 1))).limitToFirst(1);
    DocumentSet result10 =
        expectOptimizedCollectionScan(() -> runQuery(query10, SnapshotVersion.NONE));
    assertEquals(docSet(query10.comparator(), doc2), result10);
  }

  @Test
  public void orQueryWithInAndNotInUsingIndexes() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("coll/2", 1, map("b", 1));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", 2));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.DESCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    // Two equalities: a==1 || b==1.
    Query query1 =
        query("coll")
            .filter(orFilters(filter("a", "==", 2), filter("b", "in", Arrays.asList(2, 3))));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc3, doc4, doc6), result1);

    // a==2 || (b != 2 && b != 3)
    // Has implicit "orderBy b"
    Query query2 =
        query("coll")
            .filter(orFilters(filter("a", "==", 2), filter("b", "not-in", Arrays.asList(2, 3))));
    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc1, doc2), result2);
  }

  @Test
  public void orQueryWithArrayMembershipUsingIndexes() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", Arrays.asList(0)));
    MutableDocument doc2 = doc("coll/2", 1, map("b", Arrays.asList(1)));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", Arrays.asList(2, 7)));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", Arrays.asList(3, 7)));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.CONTAINS));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    Query query1 =
        query("coll").filter(orFilters(filter("a", "==", 2), filter("b", "array-contains", 7)));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc3, doc4, doc6), result1);

    Query query2 =
        query("coll")
            .filter(
                orFilters(
                    filter("a", "==", 2), filter("b", "array-contains-any", Arrays.asList(0, 3))));
    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc1, doc4, doc6), result2);
  }

  @Test
  public void queryWithMultipleInsOnTheSameField() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("coll/2", 1, map("b", 1));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", 2));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.DESCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    // a IN [1,2,3] && a IN [0,1,4] should result in "a==1".
    Query query1 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(1, 2, 3)),
                    filter("a", "in", Arrays.asList(0, 1, 4))));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc1, doc4, doc5), result1);

    // a IN [2,3] && a IN [0,1,4] is never true and so the result should be an empty set.
    Query query2 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    filter("a", "in", Arrays.asList(0, 1, 4))));

    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator()), result2);

    // a IN [0,3] || a IN [0,2] should union them (similar to: a IN [0,2,3]).
    Query query3 =
        query("coll")
            .filter(
                orFilters(
                    filter("a", "in", Arrays.asList(0, 3)),
                    filter("a", "in", Arrays.asList(0, 2))));

    DocumentSet result3 =
        expectOptimizedCollectionScan(() -> runQuery(query3, SnapshotVersion.NONE));
    assertEquals(docSet(query3.comparator(), doc3, doc6), result3);

    // Nested composite filter: (a IN [0,1,2,3] && (a IN [0,2] || (b>1 && a IN [1,3]))
    Query query4 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(0, 1, 2, 3)),
                    orFilters(
                        filter("a", "in", Arrays.asList(0, 2)),
                        andFilters(filter("b", ">=", 1), filter("a", "in", Arrays.asList(1, 3))))));

    DocumentSet result4 =
        expectOptimizedCollectionScan(() -> runQuery(query4, SnapshotVersion.NONE));
    assertEquals(docSet(query4.comparator(), doc3, doc4), result4);
  }

  @Test
  public void queryWithMultipleInsOnDifferentFields() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", 0));
    MutableDocument doc2 = doc("coll/2", 1, map("b", 1));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", 2));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", 3));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.DESCENDING));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    Query query1 =
        query("coll")
            .filter(
                orFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    filter("b", "in", Arrays.asList(0, 2))));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc1, doc3, doc6), result1);

    Query query2 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    filter("b", "in", Arrays.asList(0, 2))));

    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc3), result2);

    // Nested composite filter: (b in [0,3] && (b IN [1] || (b in [2,3] && a IN [1,3]))
    Query query3 =
        query("coll")
            .filter(
                andFilters(
                    filter("b", "in", Arrays.asList(0, 3)),
                    orFilters(
                        filter("b", "in", Arrays.asList(1)),
                        andFilters(
                            filter("b", "in", Arrays.asList(2, 3)),
                            filter("a", "in", Arrays.asList(1, 3))))));

    DocumentSet result3 =
        expectOptimizedCollectionScan(() -> runQuery(query3, SnapshotVersion.NONE));
    assertEquals(docSet(query3.comparator(), doc4), result3);
  }

  @Test
  public void queryInWithArrayContainsAny() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", Arrays.asList(0)));
    MutableDocument doc2 = doc("coll/2", 1, map("b", Arrays.asList(1)));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", Arrays.asList(2, 7), "c", 10));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", Arrays.asList(3, 7)));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2, "c", 20));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.CONTAINS));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    Query query1 =
        query("coll")
            .filter(
                orFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    filter("b", "array-contains-any", Arrays.asList(0, 7))));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc1, doc3, doc4, doc6), result1);

    Query query2 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    filter("b", "array-contains-any", Arrays.asList(0, 7))));

    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc3), result2);

    Query query3 =
        query("coll")
            .filter(
                orFilters(
                    andFilters(filter("a", "in", Arrays.asList(2, 3)), filter("c", "==", 10)),
                    filter("b", "array-contains-any", Arrays.asList(0, 7))));
    DocumentSet result3 =
        expectOptimizedCollectionScan(() -> runQuery(query3, SnapshotVersion.NONE));
    assertEquals(docSet(query3.comparator(), doc1, doc3, doc4), result3);

    Query query4 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    orFilters(
                        filter("b", "array-contains-any", Arrays.asList(0, 7)),
                        filter("c", "==", 20))));
    DocumentSet result4 =
        expectOptimizedCollectionScan(() -> runQuery(query4, SnapshotVersion.NONE));
    assertEquals(docSet(query4.comparator(), doc3, doc6), result4);
  }

  @Test
  public void queryInWithArrayContains() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", Arrays.asList(0)));
    MutableDocument doc2 = doc("coll/2", 1, map("b", Arrays.asList(1)));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", Arrays.asList(2, 7), "c", 10));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", Arrays.asList(3, 7)));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2, "c", 20));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.CONTAINS));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    Query query1 =
        query("coll")
            .filter(
                orFilters(
                    filter("a", "in", Arrays.asList(2, 3)), filter("b", "array-contains", 3)));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc3, doc4, doc6), result1);

    Query query2 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(2, 3)), filter("b", "array-contains", 7)));

    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc3), result2);

    Query query3 =
        query("coll")
            .filter(
                orFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    andFilters(filter("b", "array-contains", 3), filter("a", "==", 1))));
    DocumentSet result3 =
        expectOptimizedCollectionScan(() -> runQuery(query3, SnapshotVersion.NONE));
    assertEquals(docSet(query3.comparator(), doc3, doc4, doc6), result3);

    Query query4 =
        query("coll")
            .filter(
                andFilters(
                    filter("a", "in", Arrays.asList(2, 3)),
                    orFilters(filter("b", "array-contains", 7), filter("a", "==", 1))));
    DocumentSet result4 =
        expectOptimizedCollectionScan(() -> runQuery(query4, SnapshotVersion.NONE));
    assertEquals(docSet(query4.comparator(), doc3), result4);
  }

  @Test
  public void orderByEquality() throws Exception {
    MutableDocument doc1 = doc("coll/1", 1, map("a", 1, "b", Arrays.asList(0)));
    MutableDocument doc2 = doc("coll/2", 1, map("b", Arrays.asList(1)));
    MutableDocument doc3 = doc("coll/3", 1, map("a", 3, "b", Arrays.asList(2, 7), "c", 10));
    MutableDocument doc4 = doc("coll/4", 1, map("a", 1, "b", Arrays.asList(3, 7)));
    MutableDocument doc5 = doc("coll/5", 1, map("a", 1));
    MutableDocument doc6 = doc("coll/6", 1, map("a", 2, "c", 20));
    addDocument(doc1, doc2, doc3, doc4, doc5, doc6);
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.ASCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "a", Kind.DESCENDING));
    indexManager.addFieldIndex(fieldIndex("coll", "b", Kind.CONTAINS));
    indexManager.updateIndexEntries(docMap(doc1, doc2, doc3, doc4, doc5, doc6));
    indexManager.updateCollectionGroup("coll", IndexOffset.fromDocument(doc6));

    Query query1 = query("coll").filter(filter("a", "==", 1)).orderBy(orderBy("a"));
    DocumentSet result1 =
        expectOptimizedCollectionScan(() -> runQuery(query1, SnapshotVersion.NONE));
    assertEquals(docSet(query1.comparator(), doc1, doc4, doc5), result1);

    Query query2 =
        query("coll").filter(filter("a", "in", Arrays.asList(2, 3))).orderBy(orderBy("a"));
    DocumentSet result2 =
        expectOptimizedCollectionScan(() -> runQuery(query2, SnapshotVersion.NONE));
    assertEquals(docSet(query2.comparator(), doc6, doc3), result2);
  }
}
