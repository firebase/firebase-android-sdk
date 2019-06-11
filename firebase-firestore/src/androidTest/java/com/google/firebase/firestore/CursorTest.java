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

package com.google.firebase.firestore;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToIds;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.writeAllDocs;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class CursorTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void canPageThroughItems() {
    CollectionReference testCollection =
        testCollectionWithDocs(
            map(
                "a", map("v", "a"),
                "b", map("v", "b"),
                "c", map("v", "c"),
                "d", map("v", "d"),
                "e", map("v", "e"),
                "f", map("v", "f")));

    QuerySnapshot snapshot = waitFor(testCollection.limit(2).get());
    assertEquals(asList(map("v", "a"), map("v", "b")), querySnapshotToValues(snapshot));

    DocumentSnapshot lastDoc = snapshot.getDocuments().get(1);
    snapshot = waitFor(testCollection.limit(3).startAfter(lastDoc).get());

    assertEquals(
        asList(map("v", "c"), map("v", "d"), map("v", "e")), querySnapshotToValues(snapshot));

    lastDoc = snapshot.getDocuments().get(2);
    snapshot = waitFor(testCollection.limit(1).startAfter(lastDoc).get());
    assertEquals(asList(map("v", "f")), querySnapshotToValues(snapshot));

    lastDoc = snapshot.getDocuments().get(0);
    snapshot = waitFor(testCollection.limit(3).startAfter(lastDoc).get());
    assertEquals(asList(), querySnapshotToValues(snapshot));
  }

  @Test
  public void canBeCreatedFromDocuments() {
    CollectionReference testCollection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 1.0),
                "b", map("k", "b", "sort", 2.0),
                "c", map("k", "c", "sort", 2.0),
                "d", map("k", "d", "sort", 2.0),
                "e", map("k", "e", "sort", 0.0),
                "f", map("k", "f", "nosort", 1.0) // should not show up
                ));

    Query query = testCollection.orderBy("sort");
    DocumentSnapshot snapshot = waitFor(testCollection.document("c").get());

    assertTrue(snapshot.exists());
    QuerySnapshot querySnapshot = waitFor(query.startAt(snapshot).get());
    assertEquals(
        asList(map("k", "c", "sort", 2.0), map("k", "d", "sort", 2.0)),
        querySnapshotToValues(querySnapshot));

    querySnapshot = waitFor(query.endBefore(snapshot).get());
    assertEquals(
        asList(map("k", "e", "sort", 0.0), map("k", "a", "sort", 1.0), map("k", "b", "sort", 2.0)),
        querySnapshotToValues(querySnapshot));
  }

  @Test
  public void canBeCreatedFromValues() {
    CollectionReference testCollection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 1.0),
                "b", map("k", "b", "sort", 2.0),
                "c", map("k", "c", "sort", 2.0),
                "d", map("k", "d", "sort", 2.0),
                "e", map("k", "e", "sort", 0.0),
                "f", map("k", "f", "nosort", 1.0) // should not show up
                ));

    Query query = testCollection.orderBy("sort");
    QuerySnapshot snapshot = waitFor(query.startAt(2.0).get());

    assertEquals(
        asList(map("k", "b", "sort", 2.0), map("k", "c", "sort", 2.0), map("k", "d", "sort", 2.0)),
        querySnapshotToValues(snapshot));

    snapshot = waitFor(query.endBefore(2.0).get());
    assertEquals(
        asList(map("k", "e", "sort", 0.0), map("k", "a", "sort", 1.0)),
        querySnapshotToValues(snapshot));
  }

  @Test
  public void canBeCreatedUsingDocumentId() {
    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("k", "a"),
            "b", map("k", "b"),
            "c", map("k", "c"),
            "d", map("k", "d"),
            "e", map("k", "e"));

    CollectionReference writer =
        testFirestore().collection("parent-collection").document().collection("sub-collection");
    writeAllDocs(writer, testDocs);

    CollectionReference reader = testFirestore().collection(writer.getPath());
    QuerySnapshot snapshot =
        waitFor(reader.orderBy(FieldPath.documentId()).startAt("b").endBefore("d").get());

    assertEquals(asList(map("k", "b"), map("k", "c")), querySnapshotToValues(snapshot));
  }

  @Test
  public void canBeUsedWithReferenceValues() {
    FirebaseFirestore db = testFirestore();
    Map<String, Map<String, Object>> testDocs =
        map(
            "a", map("k", "1a", "ref", db.collection("1").document("a")),
            "b", map("k", "1b", "ref", db.collection("1").document("b")),
            "c", map("k", "2a", "ref", db.collection("2").document("a")),
            "d", map("k", "2b", "ref", db.collection("2").document("b")),
            "e", map("k", "3a", "ref", db.collection("3").document("a")));

    CollectionReference testCollection = testCollectionWithDocs(testDocs);

    QuerySnapshot snapshot =
        waitFor(
            testCollection
                .orderBy("ref")
                .startAfter(db.collection("1").document("a"))
                .endAt(db.collection("2").document("b"))
                .get());

    List<String> results = new ArrayList<>();
    for (DocumentSnapshot doc : snapshot) {
      results.add(doc.getString("k"));
    }
    assertEquals(asList("1b", "2a", "2b"), results);
  }

  @Test
  public void canBeUsedInDescendingQueries() {
    CollectionReference testCollection =
        testCollectionWithDocs(
            map(
                "a", map("k", "a", "sort", 1.0),
                "b", map("k", "b", "sort", 2.0),
                "c", map("k", "c", "sort", 2.0),
                "d", map("k", "d", "sort", 3.0),
                "e", map("k", "e", "sort", 0.0),
                "f", map("k", "f", "nosort", 1.0) // should not show up
                ));

    Query query =
        testCollection
            .orderBy("sort", Direction.DESCENDING)
            .orderBy(FieldPath.documentId(), Direction.DESCENDING);

    QuerySnapshot snapshot = waitFor(query.startAt(2.0).get());

    assertEquals(
        asList(
            map("k", "c", "sort", 2.0),
            map("k", "b", "sort", 2.0),
            map("k", "a", "sort", 1.0),
            map("k", "e", "sort", 0.0)),
        querySnapshotToValues(snapshot));

    snapshot = waitFor(query.endBefore(2.0).get());
    assertEquals(asList(map("k", "d", "sort", 3.0)), querySnapshotToValues(snapshot));
  }

  private static Timestamp timestamp(long seconds, int micros) {
    // Firestore only supports microsecond resolution, so use a microsecond as a minimum value for
    // nanoseconds.
    return new Timestamp(seconds, micros * 1000);
  }

  @Test
  public void timestampsCanBePassedToQueriesAsLimits() {
    CollectionReference testCollection =
        testCollectionWithDocs(
            map(
                "a", map("timestamp", timestamp(100, 2)),
                "b", map("timestamp", timestamp(100, 5)),
                "c", map("timestamp", timestamp(100, 3)),
                "d", map("timestamp", timestamp(100, 1)),
                // Number of microseconds deliberately repeated.
                "e", map("timestamp", timestamp(100, 5)),
                "f", map("timestamp", timestamp(100, 4))));

    Query query = testCollection.orderBy("timestamp");
    QuerySnapshot snapshot =
        waitFor(query.startAfter(timestamp(100, 2)).endAt(timestamp(100, 5)).get());
    assertEquals(asList("c", "f", "b", "e"), querySnapshotToIds(snapshot));
  }

  @Test
  public void timestampsCanBePassedToQueriesInWhereClause() {
    CollectionReference testCollection =
        testCollectionWithDocs(
            map(
                "a", map("timestamp", timestamp(100, 7)),
                "b", map("timestamp", timestamp(100, 4)),
                "c", map("timestamp", timestamp(100, 8)),
                "d", map("timestamp", timestamp(100, 5)),
                "e", map("timestamp", timestamp(100, 6))));

    QuerySnapshot snapshot =
        waitFor(
            testCollection
                .whereGreaterThanOrEqualTo("timestamp", timestamp(100, 5))
                .whereLessThan("timestamp", timestamp(100, 8))
                .get());
    assertEquals(asList("d", "e", "a"), querySnapshotToIds(snapshot));
  }

  @Test
  public void timestampsAreTruncatedToMicroseconds() {
    Timestamp nanos = new Timestamp(0, 123456789);
    Timestamp micros = new Timestamp(0, 123456000);
    Timestamp millis = new Timestamp(0, 123000000);
    CollectionReference testCollection = testCollectionWithDocs(map("a", map("timestamp", nanos)));

    QuerySnapshot snapshot = waitFor(testCollection.whereEqualTo("timestamp", nanos).get());
    assertThat(querySnapshotToValues(snapshot)).hasSize(1);
    // Because Timestamp should have been truncated to microseconds, the microsecond timestamp
    // should be considered equal to the nanosecond one.
    snapshot = waitFor(testCollection.whereEqualTo("timestamp", micros).get());
    assertThat(querySnapshotToValues(snapshot)).hasSize(1);
    // The truncation is just to the microseconds, however, so the millisecond timestamp should be
    // treated as different and thus the query should return no results.
    snapshot = waitFor(testCollection.whereEqualTo("timestamp", millis).get());
    assertThat(querySnapshotToValues(snapshot)).isEmpty();
  }
}
