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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.common.truth.Truth;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Collections;
import java.util.Map;
import org.junit.After;
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
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(AggregateField.count()).get(AggregateSource.SERVER));

    assertEquals(2L, snapshot.get(AggregateField.count()));
  }

  @Test
  public void testCanRunSumUsingAggregationMethod() {
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
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    collection
        .aggregate(AggregateField.count(), sum("pages"), average("pages"))
        .get(AggregateSource.SERVER);

    waitFor(collection.firestore.terminate());
  }

  @Test
  public void testCanGetCorrectDoubleTypeForSum() {
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("weight")).get(AggregateSource.SERVER));

    Object sumWeight = snapshot.get(sum("weight"));
    assertTrue(sumWeight instanceof Double);
  }

  @Test
  public void testCanGetCorrectDoubleTypeForSumWhenFieldsAddUpToInteger() {
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("height")).get(AggregateSource.SERVER));

    Object sumWeight = snapshot.get(sum("height"));
    assertTrue(sumWeight instanceof Double);
  }

  @Test
  public void testCanGetCorrectLongTypeForSum() {
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("pages")).get(AggregateSource.SERVER));

    Object sumPages = snapshot.get(sum("pages"));
    assertTrue(sumPages instanceof Long);
  }

  @Test
  public void testCanGetCorrectTypeForAvg() {
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(average("pages")).get(AggregateSource.SERVER));

    Object avg = snapshot.get(average("pages"));
    assertTrue(avg instanceof Double);
  }

  @Test
  public void testCannotPerformMoreThanMaxAggregations() {
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
  public void testPerformsAggregationsWhenNaNExistsForSomeFieldValues() {
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
        waitFor(collection.aggregate(sum("rating"), average("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), Double.NaN);
    assertEquals(snapshot.get(average("rating")), (Double) Double.NaN);
  }

  @Test
  public void testThrowsAnErrorWhenGettingTheResultOfAnUnrequestedAggregation() {
    CollectionReference collection = testCollectionWithDocs(testDocs1);

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection
                .whereGreaterThan("pages", 200)
                .aggregate(sum("pages"))
                .get(AggregateSource.SERVER));

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
                .aggregate(sum("rating"), average("rating"), AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), 8L);
    assertEquals(snapshot.get(average("rating")), (Double) 4.0);
    assertEquals(snapshot.get(AggregateField.count()), 2L);
  }

  @Test
  public void testPerformsAggregationsOnNestedMapValues() {
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
                .aggregate(sum("metadata.pages"), average("metadata.pages"), AggregateField.count())
                .get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("metadata.pages")), 150L);
    assertEquals(snapshot.get(average("metadata.pages")), (Double) 75.0);
    assertEquals(snapshot.get(AggregateField.count()), 2L);
  }

  @Test
  public void testPerformsSumThatOverflowsMaxLong() {
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
    // Sum of rating would be 0, but if the accumulation overflow, we expect infinity
    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("author", "authorA", "title", "titleA", "rating", Double.MAX_VALUE),
            "b", map("author", "authorB", "title", "titleB", "rating", Double.MAX_VALUE),
            "c", map("author", "authorC", "title", "titleC", "rating", -Double.MAX_VALUE),
            "d", map("author", "authorD", "title", "titleD", "rating", -Double.MAX_VALUE));
    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    Object sum = snapshot.get(sum("rating"));
    assertTrue(sum instanceof Double);
    assertTrue(
        sum.equals(0.0)
            || sum.equals(Double.POSITIVE_INFINITY)
            || sum.equals(Double.NEGATIVE_INFINITY));
  }

  @Test
  public void testPerformsSumOverResultSetOfZeroDocuments() {
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
    Map<String, Map<String, Object>> testDocs =
        map("a", map("author", "authorA", "title", "titleA", "rating", Double.MIN_VALUE));

    CollectionReference collection = testCollectionWithDocs(testDocs);

    AggregateQuerySnapshot snapshot =
        waitFor(collection.aggregate(sum("rating")).get(AggregateSource.SERVER));

    assertEquals(snapshot.get(sum("rating")), Double.MIN_VALUE);
  }

  @Test
  public void testPerformsAverageOfIntsThatResultsInAnInt() {
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
  public void testAggregateErrorMessageShouldContainConsoleLinkIfMissingIndex() {
    assumeFalse(
        "Skip this test when running against the Firestore emulator because the "
            + "Firestore emulator does not use indexes and never fails with a 'missing index'"
            + " error",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(Collections.emptyMap());
    Query compositeIndexQuery = collection.whereEqualTo("field1", 42).whereLessThan("field2", 99);
    AggregateQuery compositeIndexAggregateQuery =
        compositeIndexQuery.aggregate(AggregateField.count(), sum("pages"), average("pages"));
    Task<AggregateQuerySnapshot> task = compositeIndexAggregateQuery.get(AggregateSource.SERVER);

    Throwable throwable = assertThrows(Throwable.class, () -> waitFor(task));

    Throwable cause = throwable.getCause();
    Truth.assertThat(cause).hasMessageThat().ignoringCase().contains("index");
    // TODO(b/316359394) Remove this check for the default databases once cl/582465034 is rolled
    // out to production.
    if (collection
        .firestore
        .getDatabaseId()
        .getDatabaseId()
        .equals(DatabaseId.DEFAULT_DATABASE_ID)) {
      Truth.assertThat(cause).hasMessageThat().contains("https://console.firebase.google.com");
    }
  }

  @Test
  public void allowsAliasesLongerThan1500Bytes() {
    // The longest field name allowed is 1500 bytes, and string sizes are calculated as the number
    // of UTF-8 encoded bytes + 1 in server. The alias chosen by the client is <op>_<fieldName>.
    // If the field name is 1500 bytes, the alias will be longer than 1500, which is the limit for
    // aliases. This is to make sure the client can handle this corner case correctly.
    StringBuilder builder = new StringBuilder(1499);
    for (int i = 0; i < 1499; i++) {
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
