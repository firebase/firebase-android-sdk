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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
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
    Query query1 = coll1.document("bar").collection("baz").whereEqualTo("a", 1).limit(100);
    Query query1_same = coll1.document("bar").collection("baz").whereEqualTo("a", 1).limit(100);
    Query query2 = coll1.document("bar").collection("baz").whereEqualTo("b", 1).orderBy("c");
    Query query2_same = coll1.document("bar").collection("baz").whereEqualTo("b", 1).orderBy("c");

    assertEquals(coll1, coll1_same);
    assertEquals(query1, query1_same);
    assertEquals(query2, query2_same);

    assertEquals(coll1.hashCode(), coll1_same.hashCode());
    assertEquals(query1.hashCode(), query1_same.hashCode());
    assertEquals(query2.hashCode(), query2_same.hashCode());

    assertNotEquals(coll1, query1);
    assertNotEquals(query1, query2);
    assertNotEquals(coll1.hashCode(), query1.hashCode());
    assertNotEquals(query1.hashCode(), query2.hashCode());
  }

  @Test
  public void testCanRunCount() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));

    AggregateQuerySnapshot snapshot =
        waitFor(collection.count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(3), snapshot.getCount());
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
        waitFor(collection.whereEqualTo("k", "b").count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(1), snapshot.getCount());
  }

  @Test
  public void testCanRunCollectionGroupQuery() {
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
        waitFor(db.collectionGroup(collectionGroup).count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(
        Long.valueOf(5), // "cg-doc1", "cg-doc2", "cg-doc3", "cg-doc4", "cg-doc5",
        snapshot.getCount());
  }

  @Test
  public void testCanRunCountWithFiltersAndLimits() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "a"),
                "c", map("k", "a"),
                "d", map("k", "d")));

    AggregateQuerySnapshot snapshot =
        waitFor(
            collection.whereEqualTo("k", "a").limit(2).count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(2), snapshot.getCount());

    snapshot =
        waitFor(
            collection
                .whereEqualTo("k", "a")
                .limitToLast(2)
                .count()
                .get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(2), snapshot.getCount());

    snapshot =
        waitFor(
            collection
                .whereEqualTo("k", "d")
                .limitToLast(1000)
                .count()
                .get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(1), snapshot.getCount());
  }

  @Test
  public void testCanRunCountOnNonExistentCollection() {
    CollectionReference collection = testFirestore().collection("random-coll");

    AggregateQuerySnapshot snapshot =
        waitFor(collection.count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(0), snapshot.getCount());

    snapshot =
        waitFor(collection.whereEqualTo("k", 100).count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(0), snapshot.getCount());
  }

  @Test
  public void testFailWithoutNetwork() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));
    waitFor(collection.getFirestore().disableNetwork());

    Exception e = waitForException(collection.count().get(AggregateSource.SERVER_DIRECT));
    assertThat(e, instanceOf(FirebaseFirestoreException.class));
    assertEquals(
        FirebaseFirestoreException.Code.UNAVAILABLE, ((FirebaseFirestoreException) e).getCode());

    waitFor(collection.getFirestore().enableNetwork());
    AggregateQuerySnapshot snapshot =
        waitFor(collection.count().get(AggregateSource.SERVER_DIRECT));
    assertEquals(Long.valueOf(3), snapshot.getCount());
  }

  @Test
  public void testExponentialBackoffWorks() {
    CollectionReference collection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a"),
                "b", map("k", "b"),
                "c", map("k", "c")));
    waitFor(collection.getFirestore().disableNetwork());

    Exception e =
        waitForException(
            collection.count().get(AggregateSource.SERVER_DIRECT, /* maxAttempts */ 2));
    assertThat(e, instanceOf(FirebaseFirestoreException.class));
    assertEquals(
        FirebaseFirestoreException.Code.UNAVAILABLE, ((FirebaseFirestoreException) e).getCode());
  }
}
