// Copyright 2022 Google LLC
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

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.isRunningAgainstEmulator;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.model.DatabaseId;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.Collections;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CountTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testCountQueryEquals() {
    CollectionReference coll1 = testCollection("foo");
    CollectionReference coll1_same = coll1.firestore.collection(coll1.getPath());
    AggregateQuery query1 = coll1.count();
    AggregateQuery query1_same = coll1_same.count();
    AggregateQuery query2 =
        coll1.document("bar").collection("baz").whereEqualTo("a", 1).limit(100).count();
    AggregateQuery query2_same =
        coll1.document("bar").collection("baz").whereEqualTo("a", 1).limit(100).count();
    AggregateQuery query3 =
        coll1.document("bar").collection("baz").whereEqualTo("b", 1).orderBy("c").count();
    AggregateQuery query3_same =
        coll1.document("bar").collection("baz").whereEqualTo("b", 1).orderBy("c").count();

    assertTrue(query1.equals(query1_same));
    assertTrue(query2.equals(query2_same));
    assertTrue(query3.equals(query3_same));

    assertEquals(query1.hashCode(), query1_same.hashCode());
    assertEquals(query2.hashCode(), query2_same.hashCode());
    assertEquals(query3.hashCode(), query3_same.hashCode());

    assertFalse(query1.equals(null));
    assertFalse(query1.equals("string"));
    assertFalse(query1.equals(query2));
    assertFalse(query2.equals(query3));
    assertNotEquals(query1.hashCode(), query2.hashCode());
    assertNotEquals(query2.hashCode(), query3.hashCode());
  }

  @Test
  public void testCanRunCount() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    AggregateQuerySnapshot snapshot = waitFor(collection.count().get(AggregateSource.SERVER));
    assertEquals(3L, snapshot.getCount());
  }

  @Test
  public void testCanRunCountWithFilters() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    AggregateQuerySnapshot snapshot =
        waitFor(collection.whereEqualTo("k", "b").count().get(AggregateSource.SERVER));
    assertEquals(1L, snapshot.getCount());
  }

  @Test
  public void testCanRunCountWithOrderBy() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c"),
                "d", map("absent", "d")));

    AggregateQuerySnapshot snapshot =
        waitFor(collection.orderBy("k").count().get(AggregateSource.SERVER));
    // "d" is filtered out because it is ordered by "k".
    assertEquals(3L, snapshot.getCount());
  }

  @Test
  public void testTerminateDoesNotCrashWithFlyingCountQuery() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    collection.orderBy("k").count().get(AggregateSource.SERVER);
    waitFor(collection.firestore.terminate());
  }

  @Test
  public void testCountSnapshotEquals() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    AggregateQuerySnapshot snapshot1 =
        waitFor(collection.whereEqualTo("k", "b").count().get(AggregateSource.SERVER));
    AggregateQuerySnapshot snapshot1_same =
        waitFor(collection.whereEqualTo("k", "b").count().get(AggregateSource.SERVER));

    AggregateQuerySnapshot snapshot2 =
        waitFor(collection.whereEqualTo("k", "a").count().get(AggregateSource.SERVER));
    waitFor(collection.document("d").set(map("k", "a")));
    AggregateQuerySnapshot snapshot2_different =
        waitFor(collection.whereEqualTo("k", "a").count().get(AggregateSource.SERVER));

    assertTrue(snapshot1.equals(snapshot1_same));
    assertEquals(snapshot1.hashCode(), snapshot1_same.hashCode());
    assertTrue(snapshot1.getQuery().equals(collection.whereEqualTo("k", "b").count()));

    assertFalse(snapshot1.equals(null));
    assertFalse(snapshot1.equals("string"));
    assertFalse(snapshot1.equals(snapshot2));
    assertNotEquals(snapshot1.hashCode(), snapshot2.hashCode());
    assertFalse(snapshot2.equals(snapshot2_different));
    assertNotEquals(snapshot2.hashCode(), snapshot2_different.hashCode());
  }

  @Test
  public void testCanRunCountCollectionGroupQuery() {
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
      batch.set(db.document(path.replace("${collectionGroup}", collectionGroup)), map("x", 1));
    }
    waitFor(batch.commit());

    AggregateQuerySnapshot snapshot =
        waitFor(db.collectionGroup(collectionGroup).count().get(AggregateSource.SERVER));
    assertEquals(
        5L, // "cg-doc1", "cg-doc2", "cg-doc3", "cg-doc4", "cg-doc5",
        snapshot.getCount());
  }

  @Test
  public void testCanRunCountAggregateWithFiltersAndLimits() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "a"),
                "c", map("k", "a"),
                "d", map("k", "d")));

    AggregateQuerySnapshot snapshot =
        waitFor(collection.whereEqualTo("k", "a").limit(2).count().get(AggregateSource.SERVER));
    assertEquals(2L, snapshot.getCount());

    snapshot =
        waitFor(
            collection.whereEqualTo("k", "a").limitToLast(2).count().get(AggregateSource.SERVER));
    assertEquals(2L, snapshot.getCount());

    snapshot =
        waitFor(
            collection
                .whereEqualTo("k", "d")
                .limitToLast(1000)
                .count()
                .get(AggregateSource.SERVER));
    assertEquals(1L, snapshot.getCount());
  }

  @Test
  public void testCanRunCountOnNonExistentCollection() {
    CollectionReference collection = testFirestore().collection("random-coll");

    AggregateQuerySnapshot snapshot = waitFor(collection.count().get(AggregateSource.SERVER));
    assertEquals(0L, snapshot.getCount());

    snapshot = waitFor(collection.whereEqualTo("k", 100).count().get(AggregateSource.SERVER));
    assertEquals(0L, snapshot.getCount());
  }

  @Test
  public void testCountFailWithoutNetwork() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));
    waitFor(collection.getFirestore().disableNetwork());

    Exception e = waitForException(collection.count().get(AggregateSource.SERVER));
    assertThat(e, instanceOf(FirebaseFirestoreException.class));
    assertEquals(
        FirebaseFirestoreException.Code.UNAVAILABLE, ((FirebaseFirestoreException) e).getCode());

    waitFor(collection.getFirestore().enableNetwork());
    AggregateQuerySnapshot snapshot = waitFor(collection.count().get(AggregateSource.SERVER));
    assertEquals(3L, snapshot.getCount());
  }

  @Test
  public void testCountErrorMessageShouldContainConsoleLinkIfMissingIndex() {
    assumeFalse(
        "Skip this test when running against the Firestore emulator because the Firestore emulator "
            + "does not use indexes and never fails with a 'missing index' error",
        isRunningAgainstEmulator());

    CollectionReference collection = testCollectionWithDocs(Collections.emptyMap());
    Query compositeIndexQuery = collection.whereEqualTo("field1", 42).whereLessThan("field2", 99);
    AggregateQuery compositeIndexCountQuery = compositeIndexQuery.count();
    Task<AggregateQuerySnapshot> task = compositeIndexCountQuery.get(AggregateSource.SERVER);

    Throwable throwable = assertThrows(Throwable.class, () -> waitFor(task));

    Throwable cause = throwable.getCause();
    assertThat(cause).hasMessageThat().ignoringCase().contains("index");
    // TODO(b/316359394) Remove this check for the default databases once cl/582465034 is rolled
    // out to production.
    if (collection
        .firestore
        .getDatabaseId()
        .getDatabaseId()
        .equals(DatabaseId.DEFAULT_DATABASE_ID)) {
      assertThat(cause).hasMessageThat().contains("https://console.firebase.google.com");
    }
  }
}
