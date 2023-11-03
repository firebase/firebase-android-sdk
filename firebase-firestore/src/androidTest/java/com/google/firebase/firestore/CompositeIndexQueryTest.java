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
import static com.google.firebase.firestore.Filter.equalTo;
import static com.google.firebase.firestore.Filter.greaterThan;
import static com.google.firebase.firestore.Filter.or;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
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
}
