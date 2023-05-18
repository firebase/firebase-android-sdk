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
import static com.google.firebase.firestore.AggregateField.count;
import static com.google.firebase.firestore.AggregateField.sum;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.isRunningAgainstEmulator;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.truth.Truth;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Collections;
import java.util.Map;
import org.junit.After;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AggregationTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  private static Map<String, Map<String, Object>> testDocs1 =
      map(
          "a",
          map(
              "author", "authorA", "title", "titleA", "pages", 100, "height", 24.5, "weight", 24.1,
              "foo", 1, "bar", 2, "baz", 3),
          "b",
          map(
              "author", "authorB", "title", "titleB", "pages", 50, "height", 25.5, "weight", 75.5,
              "foo", 1, "bar", 2, "baz", 3));

  @Test
  public void testAggregateCountQueryEquals() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference coll1 = testCollection("foo");
    CollectionReference coll1_same = coll1.firestore.collection(coll1.getPath());
    AggregateQuery query1 = coll1.aggregate(AggregateField.count());
    AggregateQuery query1_same = coll1_same.aggregate(AggregateField.count());
    AggregateQuery query2 =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(AggregateField.count());
    AggregateQuery query2_same =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(AggregateField.count());
    AggregateQuery query3 =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("b", 1)
            .orderBy("c")
            .aggregate(AggregateField.count());
    AggregateQuery query3_same =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("b", 1)
            .orderBy("c")
            .aggregate(AggregateField.count());

    assertEquals(query1, query1_same);
    assertEquals(query2, query2_same);
    assertEquals(query3, query3_same);

    assertEquals(query1.hashCode(), query1_same.hashCode());
    assertEquals(query2.hashCode(), query2_same.hashCode());
    assertEquals(query3.hashCode(), query3_same.hashCode());

    assertNotNull(query1);
    assertNotEquals("string", query1);
    assertNotEquals(query1, query2);
    assertNotEquals(query2, query3);
    assertNotEquals(query1.hashCode(), query2.hashCode());
    assertNotEquals(query2.hashCode(), query3.hashCode());
  }

  @Test
  public void testAggregateSumQueryEquals() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference coll1 = testCollection("foo");
    CollectionReference coll1_same = coll1.firestore.collection(coll1.getPath());
    AggregateQuery query1 = coll1.aggregate(sum("baz"));
    AggregateQuery query1_same = coll1_same.aggregate(sum("baz"));
    AggregateQuery query2 =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(sum("baz"));
    AggregateQuery query2_same =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(sum("baz"));
    AggregateQuery query3 =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("b", 1)
            .orderBy("c")
            .aggregate(sum("baz"));
    AggregateQuery query3_same =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("b", 1)
            .orderBy("c")
            .aggregate(sum("baz"));

    assertEquals(query1, query1_same);
    assertEquals(query2, query2_same);
    assertEquals(query3, query3_same);

    assertEquals(query1.hashCode(), query1_same.hashCode());
    assertEquals(query2.hashCode(), query2_same.hashCode());
    assertEquals(query3.hashCode(), query3_same.hashCode());

    assertNotNull(query1);
    assertNotEquals("string", query1);
    assertNotEquals(query1, query2);
    assertNotEquals(query2, query3);
    assertNotEquals(query1.hashCode(), query2.hashCode());
    assertNotEquals(query2.hashCode(), query3.hashCode());
  }

  @Test
  public void testAggregateAvgQueryEquals() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference coll1 = testCollection("foo");
    CollectionReference coll1_same = coll1.firestore.collection(coll1.getPath());
    AggregateQuery query1 = coll1.aggregate(average("baz"));
    AggregateQuery query1_same = coll1_same.aggregate(average("baz"));
    AggregateQuery query2 =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(average("baz"));
    AggregateQuery query2_same =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(average("baz"));
    AggregateQuery query3 =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("b", 1)
            .orderBy("c")
            .aggregate(average("baz"));
    AggregateQuery query3_same =
        coll1
            .document("bar")
            .collection("baz")
            .whereEqualTo("b", 1)
            .orderBy("c")
            .aggregate(average("baz"));

    assertEquals(query1, query1_same);
    assertEquals(query2, query2_same);
    assertEquals(query3, query3_same);

    assertEquals(query1.hashCode(), query1_same.hashCode());
    assertEquals(query2.hashCode(), query2_same.hashCode());
    assertEquals(query3.hashCode(), query3_same.hashCode());

    assertNotNull(query1);
    assertNotEquals("string", query1);
    assertNotEquals(query1, query2);
    assertNotEquals(query2, query3);
    assertNotEquals(query1.hashCode(), query2.hashCode());
    assertNotEquals(query2.hashCode(), query3.hashCode());
  }

  @Test
  public void testAggregateQueryNotEquals() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference coll = testCollection("foo");

    AggregateQuery query1 = coll.aggregate(AggregateField.count());
    AggregateQuery query2 = coll.aggregate(sum("baz"));
    AggregateQuery query3 = coll.aggregate(average("baz"));

    assertNotEquals(query1, query2);
    assertNotEquals(query2, query3);
    assertNotEquals(query3, query1);
    assertNotEquals(query1.hashCode(), query2.hashCode());
    assertNotEquals(query2.hashCode(), query3.hashCode());
    assertNotEquals(query3.hashCode(), query1.hashCode());

    AggregateQuery query4 =
        coll.document("bar").collection("baz").whereEqualTo("a", 1).limit(100).aggregate(count());
    AggregateQuery query5 =
        coll.document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(sum("baz"));
    AggregateQuery query6 =
        coll.document("bar")
            .collection("baz")
            .whereEqualTo("a", 1)
            .limit(100)
            .aggregate(average("baz"));

    assertNotEquals(query4, query5);
    assertNotEquals(query5, query6);
    assertNotEquals(query6, query4);
    assertNotEquals(query4.hashCode(), query5.hashCode());
    assertNotEquals(query5.hashCode(), query6.hashCode());
    assertNotEquals(query6.hashCode(), query4.hashCode());
  }

  @Test
  public void testCanRunCountUsingAggregationMethod() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(AggregateField.count()).get(AggregateSource.SERVER));

    assertEquals(2L, snapshot.get(AggregateField.count()));
  }

  @Test
  public void testCanRunSumUsingAggregationMethod() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshotPages =
        waitFor(collection.aggregate(sum("pages")).get(AggregateSource.SERVER));

    assertEquals(150L, snapshotPages.get(sum("pages")));
    assertEquals((Long) 150L, snapshotPages.getLong(sum("pages")));
    assertEquals((Double) 150.0, snapshotPages.getDouble(sum("pages")));

    AggregateQuerySnapshot snapshotWeight =
        waitFor(collection.aggregate(sum("weight")).get(AggregateSource.SERVER));

    assertEquals(99.6, snapshotWeight.get(sum("weight")));
    assertEquals((Long) 99L, snapshotWeight.getLong(sum("weight")));
    assertEquals((Double) 99.6, snapshotWeight.getDouble(sum("weight")));
  }

  @Test
  public void testCanRunAvgUsingAggregationMethod() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("pages")).get(AggregateSource.SERVER));

    assertEquals((Double) 75.0, snapshot.getDouble(average("pages")));

    AggregateQuerySnapshot snapshotWeight =
        waitFor(collection.aggregate(average("weight")).get(AggregateSource.SERVER));

    assertEquals((Long) 49L, snapshotWeight.getLong(average("weight")));
    assertEquals((Double) 49.8, snapshotWeight.getDouble(average("weight")));
  }

  @Test
  public void testCanGetDuplicateAggregations() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(
                    AggregateField.count(), AggregateField.count(), sum("pages"), sum("pages"))
                .get(AggregateSource.SERVER));

    assertEquals(2L, snapshot.get(AggregateField.count()));
    assertEquals(150L, snapshot.get(sum("pages")));
  }

  @Test
  public void testCanGetMultipleAggregationsInTheSameQuery() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(AggregateField.count(), sum("pages"), average("pages"))
                .get(AggregateSource.SERVER));

    assertEquals(2L, snapshot.get(AggregateField.count()));
    assertEquals(150L, snapshot.get(sum("pages")));
    assertEquals((Double) 75.0, snapshot.getDouble(average("pages")));
  }

  @Test
  public void testTerminateDoesNotCrashWithFlyingAggregateQuery() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    collection
        .aggregate(AggregateField.count(), sum("pages"), average("pages"))
        .get(AggregateSource.SERVER);

    waitFor(collection.firestore.terminate());
  }

  @Test
  public void testCanGetCorrectTypeForSum() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(sum("pages"), sum("height"), sum("weight"))
                .get(AggregateSource.SERVER));

    Object sumPages = snapshot.get(sum("pages"));
    Object sumHeight = snapshot.get(sum("height"));
    Object sumWeight = snapshot.get(sum("weight"));
    assertTrue(sumPages instanceof Long);
    assertTrue(sumHeight instanceof Long);
    assertTrue(sumWeight instanceof Double);
  }

  @Test
  public void testCanGetCorrectTypeForAvg() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("pages")).get(AggregateSource.SERVER));

    Object avg = snapshot.get(average("pages"));
    assertTrue(avg instanceof Double);
  }

  @Test
  public void testCanPerformMaxAggregations() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);
    AggregateField f1 = sum("pages");
    AggregateField f2 = average("pages");
    AggregateField f3 = AggregateField.count();
    AggregateField f4 = sum("foo");
    AggregateField f5 = sum("bar");

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(f1, f2, f3, f4, f5).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(f1), 150L);
    assertEquals(snapshot.get(f2), 75.0);
    assertEquals(snapshot.get(f3), 2L);
    assertEquals(snapshot.get(f4), 2L);
    assertEquals(snapshot.get(f5), 4L);
  }

  @Test
  public void testCannotPerformMoreThanMaxAggregations() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);
    AggregateField f1 = sum("pages");
    AggregateField f2 = average("pages");
    AggregateField f3 = AggregateField.count();
    AggregateField f4 = sum("foo");
    AggregateField f5 = sum("bar");
    AggregateField f6 = sum("baz");

    Exception e =
        waitForException(collection.aggregate(f1, f2, f3, f4, f5, f6).get(AggregateSource.SERVER));

    assertThat(e, instanceOf(FirebaseFirestoreException.class));
    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
    assertEquals(FirebaseFirestoreException.Code.INVALID_ARGUMENT, firestoreException.getCode());
    assertTrue(firestoreException.getMessage().contains("maximum number of aggregations"));
  }

  @Test
  public void testCanRunAggregateCollectionGroupQuery() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    FirebaseFirestore db = testFirestore();
    // Use .document() to get a random collection group name to use but ensure it starts with 'b'
    // for predictable ordering.
    String collectionGroup = "b" + db.collection("foo").document().getId();

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
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 2));
    }
    waitFor(batch.commit());

    AggregateQuerySnapshot snapshot =
        waitFor(
            db.collectionGroup(collectionGroup)
                .aggregate(AggregateField.count(), sum("x"), average("x"))
                .get(AggregateSource.SERVER));
    assertEquals(
        5L, // "cg-doc1", "cg-doc2", "cg-doc3", "cg-doc4", "cg-doc5",
        snapshot.get(AggregateField.count()));
    assertEquals(10L, snapshot.get(sum("x")));
    assertEquals((Double) 2.0, snapshot.get(average("x")));
  }

  @Test
  public void testPerformsAggregationsWhenNaNExistsForSomeFieldValues() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a",
            map("author", "authorA", "title", "titleA", "pages", 100, "year", 1980, "rating", 5),
            "b",
            map("author", "authorB", "title", "titleB", "pages", 50, "year", 2020, "rating", 4),
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
                Double.NaN),
            "d",
            map("author", "authorD", "title", "titleD", "pages", 50, "year", 2020, "rating", 0));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(sum("rating"), sum("pages"), average("year"), average("rating"))
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), Double.NaN);
    assertEquals(snapshot.get(sum("pages")), 300L);
    assertEquals(snapshot.get(average("rating")), (Double) Double.NaN);
    assertEquals(snapshot.get(average("year")), (Double) 2000.0);
  }

  @Test
  public void testThrowsAnErrorWhenGettingTheResultOfAnUnrequestedAggregation() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("pages")).get(AggregateSource.SERVER));

    Exception exception = null;
    try {
      snapshot.get(average("pages"));
    } catch (RuntimeException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(
        exception.getMessage(), "'average(pages)' was not requested in the aggregation query.");

    exception = null;
    try {
      snapshot.get(sum("foo"));
    } catch (RuntimeException e) {
      exception = e;
    }
    assertNotNull(exception);
    assertEquals(exception.getMessage(), "'sum(foo)' was not requested in the aggregation query.");
  }

  @Test
  public void testPerformsAggregationWhenUsingInOperator() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a",
            map("author", "authorA", "title", "titleA", "pages", 100, "year", 1980, "rating", 5),
            "b",
            map("author", "authorB", "title", "titleB", "pages", 50, "year", 2020, "rating", 4),
            "c",
            map("author", "authorC", "title", "titleC", "pages", 100, "year", 1980, "rating", 3),
            "d",
            map("author", "authorD", "title", "titleD", "pages", 50, "year", 2020, "rating", 0));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .whereIn("rating", asList(5, 3))
                .aggregate(
                    sum("rating"),
                    average("rating"),
                    sum("pages"),
                    average("pages"),
                    AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), 8L);
    assertEquals(snapshot.get(average("rating")), (Double) 4.0);
    assertEquals(snapshot.get(sum("pages")), 200L);
    assertEquals(snapshot.get(average("pages")), (Double) 100.0);
    assertEquals(snapshot.get(AggregateField.count()), 2L);
  }

  @Test
  public void testPerformsAggregationWhenUsingArrayContainsAnyOperator() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

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
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .whereArrayContainsAny("rating", asList(5, 3))
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

  @Test
  public void testPerformsAggregationsOnNestedMapValues() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a",
            map(
                "author",
                "authorA",
                "title",
                "titleA",
                "metadata",
                map("pages", 100, "rating", map("critic", 2, "user", 5))),
            "b",
            map(
                "author",
                "authorB",
                "title",
                "titleB",
                "metadata",
                map("pages", 50, "rating", map("critic", 4, "user", 4))));

    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(
                    sum("metadata.pages"),
                    average("metadata.pages"),
                    average("metadata.rating.critic"),
                    sum("metadata.rating.user"),
                    AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("metadata.pages")), 150L);
    assertEquals(snapshot.get(average("metadata.pages")), (Double) 75.0);
    assertEquals(snapshot.get(average("metadata.rating.critic")), (Double) 3.0);
    assertEquals(snapshot.get(sum("metadata.rating.user")), 9L);
    assertEquals(snapshot.get(AggregateField.count()), 2L);
  }

  @Test
  public void testPerformsSumThatOverflowsMaxLong() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Long.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", Long.MAX_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    Object sum = snapshot.get(sum("rating"));
    assertTrue(sum instanceof Double);
    assertEquals(sum, (double) Long.MAX_VALUE + (double) Long.MAX_VALUE);
  }

  @Test
  public void testPerformsSumThatCanOverflowIntegerValuesDuringAccumulation() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Long.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", 1),
            "c", map("author", "authorC", "title", "titleC", "rating", -101));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    Object sum = snapshot.get(sum("rating"));
    assertTrue(sum instanceof Long);
    assertEquals(sum, Long.MAX_VALUE - 100);
  }

  @Test
  public void testPerformsSumThatIsNegative() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Long.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", -Long.MAX_VALUE),
            "c", map("author", "authorC", "title", "titleC", "rating", -101),
            "d", map("author", "authorD", "title", "titleD", "rating", -10000));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), -10101L);
  }

  @Test
  public void testPerformsSumThatIsPositiveInfinity() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Double.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", Double.MAX_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    Object sum = snapshot.get(sum("rating"));
    assertTrue(sum instanceof Double);
    assertEquals(sum, Double.POSITIVE_INFINITY);
    assertEquals(snapshot.getDouble(sum("rating")), (Double) Double.POSITIVE_INFINITY);
    assertEquals(snapshot.getLong(sum("rating")), (Long) Long.MAX_VALUE);
  }

  @Test
  public void testPerformsSumThatIsNegativeInfinity() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", -Double.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", -Double.MAX_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    Object sum = snapshot.get(sum("rating"));

    assertTrue(sum instanceof Double);
    assertEquals(sum, Double.NEGATIVE_INFINITY);
    assertEquals(snapshot.getDouble(sum("rating")), (Double) Double.NEGATIVE_INFINITY);
    assertEquals(snapshot.getLong(sum("rating")), (Long) Long.MIN_VALUE);
  }

  @Test
  public void testPerformsSumThatIsValidButCouldOverflowDuringAggregation() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Double.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", Double.MAX_VALUE),
            "c", map("author", "authorC", "title", "titleC", "rating", -Double.MAX_VALUE),
            "d", map("author", "authorD", "title", "titleD", "rating", -Double.MAX_VALUE),
            "e", map("author", "authorE", "title", "titleE", "rating", Double.MAX_VALUE),
            "f", map("author", "authorF", "title", "titleF", "rating", -Double.MAX_VALUE),
            "g", map("author", "authorG", "title", "titleG", "rating", -Double.MAX_VALUE),
            "h", map("author", "authorH", "title", "titleH", "rating", Double.MAX_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    Object sum = snapshot.get(sum("rating"));
    assertTrue(sum instanceof Long);
    assertEquals(sum, 0L);
  }

  @Test
  public void testPerformsSumOverResultSetOfZeroDocuments() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .whereGreaterThan("pages", 200)
                .aggregate(sum("pages"))
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("pages")), 0L);
  }

  @Test
  public void testPerformsSumOnlyOnNumericFields() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 5),
            "b", map("author", "authorB", "title", "titleB", "rating", 4),
            "c", map("author", "authorC", "title", "titleC", "rating", "3"),
            "d", map("author", "authorD", "title", "titleD", "rating", 1));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(sum("rating"), AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), 10L);
    assertEquals(snapshot.get(AggregateField.count()), 4L);
  }

  @Test
  public void testPerformsSumOfMinIEEE754() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map("a", map("author", "authorA", "title", "titleA", "rating", Double.MIN_VALUE));

    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), Double.MIN_VALUE);
  }

  @Test
  public void testPerformsAverageOfIntsThatResultsInAnInt() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 10),
            "b", map("author", "authorB", "title", "titleB", "rating", 5),
            "c", map("author", "authorC", "title", "titleC", "rating", 0));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 5.0);
    assertEquals(snapshot.getLong(average("rating")), (Long) 5L);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 5.0);
  }

  @Test
  public void testPerformsAverageOfFloatsThatResultsInAnInt() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 10.5),
            "b", map("author", "authorB", "title", "titleB", "rating", 9.5));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 10.0);
    assertEquals(snapshot.getLong(average("rating")), (Long) 10L);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 10.0);
  }

  @Test
  public void testPerformsAverageOfFloatsAndIntsThatResultsInAnInt() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 10),
            "b", map("author", "authorB", "title", "titleB", "rating", 9.5),
            "c", map("author", "authorC", "title", "titleC", "rating", 10.5));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 10.0);
    assertEquals(snapshot.getLong(average("rating")), (Long) 10L);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 10.0);
  }

  @Test
  public void testPerformsAverageOfFloatsThatResultsInAFloat() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 5.5),
            "b", map("author", "authorB", "title", "titleB", "rating", 4.5),
            "c", map("author", "authorC", "title", "titleC", "rating", 3.5));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 4.5);
    assertEquals(snapshot.getLong(average("rating")), (Long) 4L);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 4.5);
  }

  @Test
  public void testPerformsAverageOfFloatsAndIntsThatResultsInAFloat() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 8.6),
            "b", map("author", "authorB", "title", "titleB", "rating", 9),
            "c", map("author", "authorC", "title", "titleC", "rating", 10));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));
    assertEquals(snapshot.get(average("rating")), (Double) 9.2, 0.00000000000001);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 9.2, 0.00000000000001);
    assertEquals(snapshot.getLong(average("rating")), (Long) 9L);
  }

  @Test
  public void testPerformsAverageOfIntsThatResultsInAFloat() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 10),
            "b", map("author", "authorB", "title", "titleB", "rating", 9));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 9.5);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 9.5);
    assertEquals(snapshot.getLong(average("rating")), (Long) 9L);
  }

  @Test
  public void testPerformsAverageCausingUnderflow() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Double.MIN_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", 0));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 0.0);
    assertEquals(snapshot.getDouble(average("rating")), (Double) 0.0);
    assertEquals(snapshot.getLong(average("rating")), (Long) 0L);
  }

  @Test
  public void testPerformsAverageOfMinIEEE754() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map("a", map("author", "authorA", "title", "titleA", "rating", Double.MIN_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) Double.MIN_VALUE);
    assertEquals(snapshot.getDouble(average("rating")), (Double) Double.MIN_VALUE);
    assertEquals(snapshot.getLong(average("rating")), (Long) 0L);
  }

  @Test
  public void testPerformsAverageOverflowIEEE754DuringAccumulation() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a",
            map("author", "authorA", "title", "titleA", "rating", Double.MAX_VALUE),
            "b",
            map("author", "authorB", "title", "titleB", "rating", Double.MAX_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) Double.POSITIVE_INFINITY);
    assertEquals(snapshot.getDouble(average("rating")), (Double) Double.POSITIVE_INFINITY);
    assertEquals(snapshot.getLong(average("rating")), (Long) Long.MAX_VALUE);
  }

  @Test
  public void testPerformsAverageThatIncludesNaN() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a",
            map("author", "authorA", "title", "titleA", "rating", 5),
            "b",
            map("author", "authorB", "title", "titleB", "rating", 4),
            "c",
            map("author", "authorC", "title", "titleC", "rating", Double.NaN),
            "d",
            map("author", "authorD", "title", "titleD", "rating", 0));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) Double.NaN);
    assertEquals(snapshot.getDouble(average("rating")), (Double) Double.NaN);
    assertEquals(snapshot.getLong(average("rating")), (Long) 0L);
  }

  @Test
  public void testPerformsAverageOverResultSetOfZeroDocuments() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .whereGreaterThan("pages", 200)
                .aggregate(average("pages"))
                .get(AggregateSource.SERVER));

    assertNull(snapshot.get(average("pages")));
    assertNull(snapshot.getDouble(average("pages")));
    assertNull(snapshot.getLong(average("pages")));
  }

  @Test
  public void testPerformsAverageOnlyOnNumericFields() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());

    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", 5),
            "b", map("author", "authorB", "title", "titleB", "rating", 4),
            "c", map("author", "authorC", "title", "titleC", "rating", "3"),
            "d", map("author", "authorD", "title", "titleD", "rating", 6));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .aggregate(average("rating"), AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(average("rating")), (Double) 5.0);
    assertEquals(snapshot.get(AggregateField.count()), 4L);
  }

  @Test
  @Ignore("TODO: Enable once we have production support for sum/avg.")
  public void testAggregateFailWithGoodMessageIfMissingIndex() {
    assumeFalse(
        "Skip this test when running against the Firestore emulator because the Firestore emulator "
            + "does not use indexes and never fails with a 'missing index' error",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(Collections.emptyMap());
    Query compositeIndexQuery = collection.whereEqualTo("field1", 42).whereLessThan("field2", 99);
    AggregateQuery compositeIndexCountQuery =
        compositeIndexQuery.aggregate(AggregateField.count(), sum("pages"), average("pages"));
    Task<AggregateQuerySnapshot> task = compositeIndexCountQuery.get(AggregateSource.SERVER);

    Throwable throwable = assertThrows(Throwable.class, () -> waitFor(task));

    Throwable cause = throwable.getCause();
    Truth.assertThat(cause).hasMessageThat().ignoringCase().contains("index");
    Truth.assertThat(cause).hasMessageThat().contains("https://console.firebase.google.com");
  }

  @Test
  public void allowsAliasesLongerThan1500Bytes() {
    assumeTrue(
        "Skip this test if running against production because sum/avg is only support "
            + "in emulator currently.",
        isRunningAgainstEmulator());
    // The longest field name allowed is 1500. The alias chosen by the client is <op>_<fieldName>.
    // If the field name is
    // 1500 bytes, the alias will be longer than 1500, which is the limit for aliases. This is to
    // make sure the client
    // can handle this corner case correctly.

    StringBuilder builder = new StringBuilder(1500);
    for (int i = 0; i < 1500; i++) {
      builder.append("a");
    }
    String longField = builder.toString();
    Map<String, Map<String, Object>> testDocs = map("a", map(longField, 1), "b", map(longField, 2));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum(longField)).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum(longField)), 3L);
  }
}
