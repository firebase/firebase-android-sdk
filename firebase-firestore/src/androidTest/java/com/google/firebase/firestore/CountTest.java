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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;

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
}
