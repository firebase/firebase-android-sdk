// Copyright 2023 Google LLC
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
package com.google.firebase.firestore;

import static com.google.firebase.firestore.AggregateField.average;
import static com.google.firebase.firestore.AggregateField.sum;
import static com.google.firebase.firestore.Filter.and;
import static com.google.firebase.firestore.Filter.equalTo;
import static com.google.firebase.firestore.Filter.greaterThan;
import static com.google.firebase.firestore.Filter.greaterThanOrEqualTo;
import static com.google.firebase.firestore.Filter.lessThan;
import static com.google.firebase.firestore.Filter.lessThanOrEqualTo;
import static com.google.firebase.firestore.Filter.notEqualTo;
import static com.google.firebase.firestore.Filter.notInArray;
import static com.google.firebase.firestore.Filter.or;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.nullList;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static java.util.Collections.emptyList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.testutil.CompositeIndexTestHelper;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

/*
 * Guidance for Creating Tests:
 * ----------------------------
 * When creating tests that require composite indexes, it is recommended to utilize the
 * "CompositeIndexTestHelper" class. This utility class provides methods for creating and setting
 * test documents and running queries with ease, ensuring proper data isolation and query
 * construction.
 *
 * To get started, please refer to the instructions provided in the README file. This will guide
 * you through setting up your local testing environment and updating the Terraform configuration
 * with any new composite indexes required for your testing scenarios.
 *
 * Note: Whenever feasible, make use of the current document fields (such as 'a,' 'b,' 'author,'
 * 'title') to avoid introducing new composite indexes and surpassing the limit. Refer to the
 * guidelines at https://firebase.google.com/docs/firestore/quotas#indexes for further information.
 */
@RunWith(AndroidJUnit4.class)
public class CompositeIndexQueryTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  private static Map<String, Map<String, Object>> testDocs =
      map(
          "a",
          map("author", "authorA", "title", "titleA", "pages", 100, "year", 1980, "rating", 5.0),
          "b",
          map("author", "authorB", "title", "titleB", "pages", 50, "year", 2020, "rating", 4.0));

  @Test
  public void testOrQueriesWithCompositeIndexes() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();
    Map<String, Map<String, Object>> testDocs =
        map(
            "doc1", map("a", 1, "b", 0),
            "doc2", map("a", 2, "b", 1),
            "doc3", map("a", 3, "b", 2),
            "doc4", map("a", 1, "b", 3),
            "doc5", map("a", 1, "b", 1));
    CollectionReference collection = testHelper.withTestDocs(testDocs);

    Query query = collection.where(or(greaterThan("a", 2), equalTo("b", 1)));
    // with one inequality: a>2 || b==1.
    testHelper.assertOnlineAndOfflineResultsMatch(testHelper.query(query), "doc5", "doc2", "doc3");

    // Test with limits (implicit order by ASC): (a==1) || (b > 0) LIMIT 2
    query = collection.where(or(equalTo("a", 1), greaterThan("b", 0))).limit(2);
    testHelper.assertOnlineAndOfflineResultsMatch(testHelper.query(query), "doc1", "doc2");

    // Test with limits (explicit order by): (a==1) || (b > 0) LIMIT_TO_LAST 2
    // Note: The public query API does not allow implicit ordering when limitToLast is used.
    query = collection.where(or(equalTo("a", 1), greaterThan("b", 0))).limitToLast(2).orderBy("b");
    testHelper.assertOnlineAndOfflineResultsMatch(testHelper.query(query), "doc3", "doc4");

    // Test with limits (explicit order by ASC): (a==2) || (b == 1) ORDER BY a LIMIT 1
    query = collection.where(or(equalTo("a", 2), equalTo("b", 1))).limit(1).orderBy("a");
    testHelper.assertOnlineAndOfflineResultsMatch(testHelper.query(query), "doc5");

    // Test with limits (explicit order by DESC): (a==2) || (b == 1) ORDER BY a LIMIT_TO_LAST 1
    query = collection.where(or(equalTo("a", 2), equalTo("b", 1))).limitToLast(1).orderBy("a");
    testHelper.assertOnlineAndOfflineResultsMatch(testHelper.query(query), "doc2");
  }

  @Test
  public void testCanRunAggregateCollectionGroupQuery() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();
    String collectionGroup = testHelper.withTestCollection().getId();

    FirebaseFirestore db = testFirestore();

    String[] docPaths =
        new String[] {
          "abc/123/${collectionGroup}/cg-doc1",
          "abc/123/${collectionGroup}/cg-doc2",
          "${collectionGroup}/cg-doc3",
          "${collectionGroup}/cg-doc4",
          "def/456/${collectionGroup}/cg-doc5",
          "${collectionGroup}/virtual-doc/nested-coll/not-cg-doc",
          "x${collectionGroup}/not-cg-doc",
          "${collectionGroup}x/not-cg-doc",
          "abc/123/${collectionGroup}x/not-cg-doc",
          "abc/123/x${collectionGroup}/not-cg-doc",
          "abc/${collectionGroup}"
        };
    WriteBatch batch = db.batch();
    for (String path : docPaths) {
      batch.set(
          db.document(path.replace("${collectionGroup}", collectionGroup)),
          testHelper.addTestSpecificFieldsToDoc(map("a", 2)));
    }
    waitFor(batch.commit());

    AggregateQuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(db.collectionGroup(collectionGroup))
                .aggregate(AggregateField.count(), sum("a"), average("a"))
                .get(AggregateSource.SERVER));
    assertEquals(
        5L, // "cg-doc1", "cg-doc2", "cg-doc3", "cg-doc4", "cg-doc5",
        snapshot.get(AggregateField.count()));
    assertEquals(10L, snapshot.get(sum("a")));
    assertEquals((Double) 2.0, snapshot.get(average("a")));
  }

  @Test
  public void testCanPerformMaxAggregations() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();
    CollectionReference collection = testHelper.withTestDocs(testDocs);

    AggregateField f1 = sum("pages");
    AggregateField f2 = average("pages");
    AggregateField f3 = AggregateField.count();
    AggregateField f4 = sum("year");
    AggregateField f5 = average("rating");

    AggregateQuerySnapshot snapshot =
        waitFor(
            testHelper.query(collection).aggregate(f1, f2, f3, f4, f5).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(f1), 150L);
    assertEquals(snapshot.get(f2), 75.0);
    assertEquals(snapshot.get(f3), 2L);
    assertEquals(snapshot.get(f4), 4000L);
    assertEquals(snapshot.get(f5), 4.5);
  }

  @Test
  public void testCanGetCorrectTypeForSum() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();
    CollectionReference collection = testHelper.withTestDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .aggregate(sum("pages"), sum("year"), sum("rating"))
                .get(AggregateSource.SERVER));

    Object sumPages = snapshot.get(sum("pages"));
    Object sumYear = snapshot.get(sum("year"));
    Object sumRating = snapshot.get(sum("rating"));
    assertTrue(sumPages instanceof Long);
    assertTrue(sumYear instanceof Long);
    assertTrue(sumRating instanceof Double);
  }

  @Test
  public void testPerformsAggregationWhenUsingArrayContainsAnyOperator() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    Map<String, Map<String, Object>> testDocs =
        map(
            "a",
            map(
                "author",
                "authorA",
                "title",
                "titleA",
                "pages",
                100,
                "year",
                1980,
                "rating",
                asList(5, 1000)),
            "b",
            map(
                "author", "authorB", "title", "titleB", "pages", 50, "year", 2020, "rating",
                asList(4)),
            "c",
            map(
                "author",
                "authorC",
                "title",
                "titleC",
                "pages",
                100,
                "year",
                1980,
                "rating",
                asList(2222, 3)),
            "d",
            map(
                "author", "authorD", "title", "titleD", "pages", 50, "year", 2020, "rating",
                asList(0)));
    CollectionReference collection = testHelper.withTestDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection.whereArrayContainsAny("rating", asList(5, 3)))
                .aggregate(
                    sum("rating"),
                    average("rating"),
                    sum("pages"),
                    average("pages"),
                    AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), 0L);
    assertNull(snapshot.get(average("rating")));
    assertEquals(snapshot.get(sum("pages")), 200L);
    assertEquals(snapshot.get(average("pages")), (Double) 100.0);
    assertEquals(snapshot.get(AggregateField.count()), 2L);
  }

  /** Multiple Inequality */
  @Test
  public void testMultipleInequalityOnDifferentFields() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 0, "v", 0),
                "doc2", map("key", "b", "sort", 3, "v", 1),
                "doc3", map("key", "c", "sort", 1, "v", 3),
                "doc4", map("key", "d", "sort", 2, "v", 2)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .whereGreaterThan("v", 2)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc3");

    // Duplicate inequality fields
    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .whereGreaterThan("sort", 1)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc4");

    // With multiple IN
    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThanOrEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .whereIn("v", asList(2, 3, 4))
                .whereIn("sort", asList(2, 3))
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc4");

    // With NOT-IN
    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThanOrEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .whereNotIn("v", asList(2, 4, 5))
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc1", "doc3");

    // With orderby
    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThanOrEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .orderBy("v", Direction.DESCENDING)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc3", "doc4", "doc1");

    // With limit
    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThanOrEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .orderBy("v", Direction.DESCENDING)
                .limit(2)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc3", "doc4");

    // With limitToLast
    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThanOrEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .orderBy("v", Direction.DESCENDING)
                .limitToLast(2)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc4", "doc1");
  }

  @Test
  public void testMultipleInequalityOnSpecialValues() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 0, "v", 0),
                "doc2", map("key", "b", "sort", Double.NaN, "v", 1),
                "doc3", map("key", "c", "sort", null, "v", 3),
                "doc4", map("key", "d", "v", 2),
                "doc5", map("key", "e", "sort", 0),
                "doc6", map("key", "f", "sort", 1, "v", 1)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc5", "doc6");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereLessThanOrEqualTo("sort", 2)
                .whereLessThanOrEqualTo("v", 1)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc6");
  }

  @Test
  public void testMultipleInequalityWithArrayMembership() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 0, "v", asList(0)),
                "doc2", map("key", "b", "sort", 1, "v", asList(0, 1, 3)),
                "doc3", map("key", "c", "sort", 1, "v", emptyList()),
                "doc4", map("key", "d", "sort", 2, "v", asList(1)),
                "doc5", map("key", "e", "sort", 3, "v", asList(2, 4)),
                "doc6", map("key", "f", "sort", 4, "v", asList(Double.NaN)),
                "doc7", map("key", "g", "sort", 4, "v", nullList())));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .whereArrayContains("v", 0)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .whereArrayContainsAny("v", asList(0, 1))
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc4");
  }

  private static Map<String, Object> nestedObject(int number) {
    return map(
        "name",
        String.format("room %d", number),
        "metadata",
        map("createdAt", number),
        "field",
        String.format("field %d", number),
        "field.dot",
        number,
        "field\\slash",
        number);
  }

  @Test
  public void testMultipleInequalityWithNestedField() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", nestedObject(400),
                "doc2", nestedObject(200),
                "doc3", nestedObject(100),
                "doc4", nestedObject(300)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereLessThanOrEqualTo("metadata.createdAt", 500)
                .whereGreaterThan("metadata.createdAt", 100)
                .whereNotEqualTo("name", "room 200")
                .orderBy("name")
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc4", "doc1");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThanOrEqualTo("field", "field 100")
                .whereNotEqualTo(FieldPath.of("field.dot"), 300)
                .whereLessThan("field\\slash", 400)
                .orderBy("name", Direction.DESCENDING)
                .get());
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc3");
  }

  @Test
  public void testMultipleInequalityWithCompositeFilters() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1",
                map("key", "a", "sort", 0, "v", 5),
                "doc2",
                map("key", "aa", "sort", 4, "v", 4),
                "doc3",
                map("key", "c", "sort", 3, "v", 3),
                "doc4",
                map("key", "b", "sort", 2, "v", 2),
                "doc5",
                map("key", "b", "sort", 2, "v", 1),
                "doc6",
                map("key", "b", "sort", 0, "v", 0)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .where(
                    or(
                        and(equalTo("key", "b"), lessThanOrEqualTo("sort", 2)),
                        and(notEqualTo("key", "b"), greaterThan("v", 4))))
                .get());
    // Implicitly ordered by: 'key' asc, 'sort' asc, 'v' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc1", "doc6", "doc5", "doc4");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .where(
                    or(
                        and(equalTo("key", "b"), lessThanOrEqualTo("sort", 2)),
                        and(notEqualTo("key", "b"), greaterThan("v", 4))))
                .orderBy("sort", Direction.DESCENDING)
                .orderBy("key")
                .get());
    // Ordered by: 'sort' desc, 'key' asc, 'v' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc5", "doc4", "doc1", "doc6");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .where(
                    and(
                        or(
                            and(equalTo("key", "b"), lessThanOrEqualTo("sort", 4)),
                            and(notEqualTo("key", "b"), greaterThanOrEqualTo("v", 4))),
                        or(
                            and(greaterThan("key", "b"), greaterThanOrEqualTo("sort", 1)),
                            and(lessThan("key", "b"), greaterThan("v", 0)))))
                .get());
    // Implicitly ordered by: 'key' asc, 'sort' asc, 'v' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc1", "doc2");
  }

  @Test
  public void testMultipleInequalityFieldsWillBeImplicitlyOrderedLexicographically() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 0, "v", 5),
                "doc2", map("key", "aa", "sort", 4, "v", 4),
                "doc3", map("key", "b", "sort", 3, "v", 3),
                "doc4", map("key", "b", "sort", 2, "v", 2),
                "doc5", map("key", "b", "sort", 2, "v", 1),
                "doc6", map("key", "b", "sort", 0, "v", 0)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereNotEqualTo("key", "a")
                .whereGreaterThan("sort", 1)
                .whereIn("v", asList(1, 2, 3, 4))
                .get());
    // Implicitly ordered by: 'key' asc, 'sort' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc4", "doc5", "doc3");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("sort", 1)
                .whereNotEqualTo("key", "a")
                .whereIn("v", asList(1, 2, 3, 4))
                .get());
    // Implicitly ordered by: 'key' asc, 'sort' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc4", "doc5", "doc3");
  }

  @Test
  public void testMultipleInequalityWithMultipleExplicitOrderBy() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1",
                map("key", "a", "sort", 5, "v", 0),
                "doc2",
                map("key", "aa", "sort", 4, "v", 0),
                "doc3",
                map("key", "b", "sort", 3, "v", 1),
                "doc4",
                map("key", "b", "sort", 2, "v", 1),
                "doc5",
                map("key", "bb", "sort", 1, "v", 1),
                "doc6",
                map("key", "c", "sort", 0, "v", 2)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .orderBy("v")
                .get());
    // Ordered by: 'v' asc, 'key' asc, 'sort' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc4", "doc3", "doc5");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .orderBy("v")
                .orderBy("sort")
                .get());
    // Ordered by: 'v asc, 'sort' asc, 'key' asc,  __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc5", "doc4", "doc3");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .orderBy("v", Direction.DESCENDING)
                .get());
    // Implicit order by matches the direction of last explicit order by.
    // Ordered by: 'v' desc, 'key' desc, 'sort' desc, __name__ desc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc5", "doc3", "doc4", "doc2");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .orderBy("v", Direction.DESCENDING)
                .orderBy("sort")
                .get());
    // Ordered by: 'v desc, 'sort' asc, 'key' asc,  __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc5", "doc4", "doc3", "doc2");
  }

  @Test
  public void testMultipleInequalityInAggregateQuery() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 5, "v", 0),
                "doc2", map("key", "aa", "sort", 4, "v", 0),
                "doc3", map("key", "b", "sort", 3, "v", 1),
                "doc4", map("key", "b", "sort", 2, "v", 1),
                "doc5", map("key", "bb", "sort", 1, "v", 1)));

    AggregateQuerySnapshot snapshot1 =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .orderBy("v")
                .count()
                .get(AggregateSource.SERVER));
    assertEquals(4L, snapshot1.getCount());

    AggregateQuerySnapshot snapshot2 =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("key", "a")
                .whereGreaterThanOrEqualTo("sort", 1)
                .whereNotEqualTo("v", 0)
                .aggregate(
                    AggregateField.count(), AggregateField.sum("sort"), AggregateField.average("v"))
                .get(AggregateSource.SERVER));
    assertEquals(3L, snapshot2.get(AggregateField.count()));
    assertEquals(6L, snapshot2.get(AggregateField.sum("sort")));
    assertEquals((Double) 1.0, snapshot2.get(AggregateField.average("v")));
  }

  @Test
  public void testMultipleInequalityFieldsWithDocumentKey() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 5),
                "doc2", map("key", "aa", "sort", 4),
                "doc3", map("key", "b", "sort", 3),
                "doc4", map("key", "b", "sort", 2),
                "doc5", map("key", "bb", "sort", 1)));

    QuerySnapshot snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereGreaterThan("sort", 1)
                .whereNotEqualTo("key", "a")
                .whereLessThan(FieldPath.documentId(), "doc5")
                .get());
    // Document Key in inequality field will implicitly ordered to the last.
    // Implicitly ordered by: 'key' asc, 'sort' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc4", "doc3");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereLessThan(FieldPath.documentId(), "doc5")
                .whereGreaterThan("sort", 1)
                .whereNotEqualTo("key", "a")
                .get());
    // Changing filters order will not effect implicit order.
    // Implicitly ordered by: 'key' asc, 'sort' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc4", "doc3");

    snapshot =
        waitFor(
            testHelper
                .query(collection)
                .whereLessThan(FieldPath.documentId(), "doc5")
                .whereGreaterThan("sort", 1)
                .whereNotEqualTo("key", "a")
                .orderBy("sort", Direction.DESCENDING)
                .get());
    // Ordered by: 'sort' desc,'key' desc,  __name__ desc
    testHelper.assertSnapshotResultIdsMatch(snapshot, "doc2", "doc3", "doc4");
  }

  @Test
  public void testMultipleInequalityReadFromCacheWhenOffline() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1", map("key", "a", "sort", 1),
                "doc2", map("key", "aa", "sort", 4),
                "doc3", map("key", "b", "sort", 3),
                "doc4", map("key", "b", "sort", 2)));

    Query query =
        testHelper.query(collection).whereNotEqualTo("key", "a").whereLessThanOrEqualTo("sort", 3);

    // populate the cache.
    QuerySnapshot snapshot1 = waitFor(query.get());
    assertEquals(2L, snapshot1.size());
    assertFalse(snapshot1.getMetadata().isFromCache());

    waitFor(collection.firestore.getClient().disableNetwork());

    QuerySnapshot snapshot2 = waitFor(query.get());
    assertEquals(2L, snapshot2.size());
    assertTrue(snapshot2.getMetadata().isFromCache());
    // Implicitly ordered by: 'key' asc, 'sort' asc, __name__ asc
    testHelper.assertSnapshotResultIdsMatch(snapshot2, "doc4", "doc3");
  }

  @Test
  public void testMultipleInequalityFromCacheAndFromServer() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection =
        testHelper.withTestDocs(
            map(
                "doc1",
                map("a", 1, "b", 0),
                "doc2",
                map("a", 2, "b", 1),
                "doc3",
                map("a", 3, "b", 2),
                "doc4",
                map("a", 1, "b", 3),
                "doc5",
                map("a", 1, "b", 1)));

    // implicit AND: a != 1 && b < 2
    Query query1 = testHelper.query(collection).whereNotEqualTo("a", 1).whereLessThan("b", 2);
    testHelper.assertOnlineAndOfflineResultsMatch(query1, "doc2");

    // explicit AND: a != 1 && b < 2
    Query query2 = testHelper.query(collection).where(and(notEqualTo("a", 1), lessThan("b", 2)));
    testHelper.assertOnlineAndOfflineResultsMatch(query2, "doc2");

    // explicit AND: a < 3 && b not-in [2, 3]
    // Implicitly ordered by: a asc, b asc, __name__ asc
    Query query3 =
        testHelper.query(collection).where(and(lessThan("a", 3), notInArray("b", asList(2, 3))));
    testHelper.assertOnlineAndOfflineResultsMatch(query3, "doc1", "doc5", "doc2");

    // a <3 && b != 0, ordered by: b desc, a desc, __name__ desc
    Query query4 =
        testHelper
            .query(collection)
            .whereLessThan("a", 3)
            .whereNotEqualTo("b", 0)
            .orderBy("b", Direction.DESCENDING)
            .limit(2);
    testHelper.assertOnlineAndOfflineResultsMatch(query4, "doc4", "doc2");

    // explicit OR: a>2 || b<1.
    Query query5 = testHelper.query(collection).where(or(greaterThan("a", 2), lessThan("b", 1)));
    testHelper.assertOnlineAndOfflineResultsMatch(query5, "doc1", "doc3");
  }

  @Test
  public void testMultipleInequalityRejectsIfDocumentKeyIsNotTheLastOrderByField() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection = testHelper.withTestCollection();

    // Implicitly ordered by:  __name__ asc, 'key' asc,
    Query query =
        testHelper.query(collection).whereNotEqualTo("key", 42).orderBy(FieldPath.documentId());
    Exception e = waitForException(query.get());
    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
    assertTrue(
        firestoreException
            .getMessage()
            .toLowerCase()
            .contains("order by clause cannot contain more fields after the key"));
  }

  @Test
  public void testMultipleInequalityRejectsIfDocumentKeyAppearsOnlyInEqualityFilter() {
    CompositeIndexTestHelper testHelper = new CompositeIndexTestHelper();

    CollectionReference collection = testHelper.withTestCollection();

    Query query =
        testHelper
            .query(collection)
            .whereNotEqualTo("key", 42)
            .whereEqualTo(FieldPath.documentId(), "doc1");
    Exception e = waitForException(query.get());
    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
    assertTrue(
        firestoreException
            .getMessage()
            .contains(
                "Equality on key is not allowed if there are other inequality fields and key does not appear in inequalities."));
  }
}
