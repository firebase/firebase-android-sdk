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
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.querySnapshotToValues;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class FieldsTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  private static Map<String, Object> nestedObject(int number) {
    return map(
        "name", String.format("room %d", number),
        "metadata",
            map(
                "createdAt",
                (double) number,
                "deep",
                map("field", String.format("deep-field-%d", number))));
  }

  @Test
  public void testNestedFieldsCanBeWrittenWithSet() {
    Map<String, Object> data = nestedObject(1);
    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(data));
    DocumentSnapshot result = waitFor(docRef.get());
    assertEquals(data, result.getData());
  }

  @Test
  @SuppressWarnings("unchecked")
  public void testNestedFieldsCanReadDirectly() {
    Map<String, Object> data = nestedObject(1);
    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(data));
    DocumentSnapshot result = waitFor(docRef.get());
    assertEquals(data.get("name"), result.get("name"));
    assertEquals(data.get("metadata"), result.get("metadata"));
    Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
    Map<String, Object> deepObject = (Map<String, Object>) metadata.get("deep");
    assertEquals(deepObject.get("field"), result.get("metadata.deep.field"));
    assertNull(result.get("metadata.nofield"));
    assertNull(result.get("nonmetadata.nofield"));
  }

  @Test
  public void testNestedFieldCanBeUpdated() {
    Map<String, Object> data = nestedObject(1);
    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(data));
    waitFor(docRef.update("metadata.deep.field", 100.0, "metadata.added", 200.0));
    DocumentSnapshot result = waitFor(docRef.get());
    Map<String, Object> expectedData =
        map(
            "name",
            "room 1",
            "metadata",
            map("createdAt", 1.0, "deep", map("field", 100.0), "added", 200.0));
    assertEquals(expectedData, result.getData());
  }

  @Test
  public void testNestedFieldsCanBeUsedInQueryFilters() {
    Map<String, Object> docs =
        map("1", nestedObject(300), "2", nestedObject(100), "3", nestedObject(200));
    // inequality adds implicit sort on field
    List<Map<String, Object>> expected = Arrays.asList(nestedObject(200), nestedObject(300));
    CollectionReference collection = testCollection();
    List<Task<Void>> tasks = new ArrayList<>();
    for (Map.Entry<String, Object> entry : docs.entrySet()) {
      tasks.add(collection.document(entry.getKey()).set(entry.getValue()));
    }
    waitFor(Tasks.whenAll(tasks));
    Query query = collection.whereGreaterThanOrEqualTo("metadata.createdAt", 200);
    QuerySnapshot res = waitFor(query.get());
    assertEquals(expected, querySnapshotToValues(res));
  }

  @Test
  public void testNestedFieldsCanBeUsedInOrderBy() {
    Map<String, Object> docs =
        map("1", nestedObject(300), "2", nestedObject(100), "3", nestedObject(200));
    List<Map<String, Object>> expected =
        Arrays.asList(nestedObject(100), nestedObject(200), nestedObject(300));
    CollectionReference collection = testCollection();
    List<Task<Void>> tasks = new ArrayList<>();
    for (Map.Entry<String, Object> entry : docs.entrySet()) {
      tasks.add(collection.document(entry.getKey()).set(entry.getValue()));
    }
    waitFor(Tasks.whenAll(tasks));
    Query query = collection.orderBy("metadata.createdAt");
    QuerySnapshot res = waitFor(query.get());
    assertEquals(expected, querySnapshotToValues(res));
  }

  /**
   * Creates test data with special characters in field names. Datastore currently prohibits mixing
   * nested data with special characters so tests that use this data must be separate.
   */
  private static Map<String, Object> dottedObject(int number) {
    return map(
        "field", String.format("field %d", number),
        "field.dot", (double) number,
        "field\\slash", (double) number);
  }

  @Test
  public void testFieldsWithSpecialCharsCanBeWrittenWithSet() {
    Map<String, Object> data = dottedObject(1);
    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(data));
    DocumentSnapshot doc = waitFor(docRef.get());
    assertEquals(data, doc.getData());
  }

  @Test
  public void testFieldsWithSpecialCharsCanBeReadDirectly() {
    Map<String, Object> data = dottedObject(1);
    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(data));
    DocumentSnapshot doc = waitFor(docRef.get());
    assertEquals(data.get("field"), doc.get("field"));
    assertEquals(data.get("field.dot"), doc.get(FieldPath.of("field.dot")));
    assertEquals(data.get("field\\slash"), doc.get("field\\slash"));
  }

  @Test
  public void testFieldsWithSpecialCharsCanBeUpdated() {
    Map<String, Object> data = dottedObject(1);
    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(data));
    waitFor(docRef.update(FieldPath.of("field.dot"), 100.0, "field\\slash", 200.0));
    DocumentSnapshot doc = waitFor(docRef.get());
    assertEquals(map("field", "field 1", "field.dot", 100.0, "field\\slash", 200.0), doc.getData());
  }

  @Test
  public void testFieldsWithSpecialCharsCanBeUsedInQueryFilters() {
    Map<String, Object> docs =
        map("1", dottedObject(300), "2", dottedObject(100), "3", dottedObject(200));
    // inequality adds implicit sort on field
    List<Map<String, Object>> expected = Arrays.asList(dottedObject(200), dottedObject(300));
    CollectionReference collection = testCollection();
    List<Task<Void>> tasks = new ArrayList<>();
    for (Map.Entry<String, Object> entry : docs.entrySet()) {
      tasks.add(collection.document(entry.getKey()).set(entry.getValue()));
    }
    waitFor(Tasks.whenAll(tasks));
    Query query = collection.whereGreaterThanOrEqualTo(FieldPath.of("field.dot"), 200);
    QuerySnapshot res = waitFor(query.get());
    assertEquals(expected, querySnapshotToValues(res));
  }

  @Test
  public void testFieldsWithSpecialCharsCanBeUsedInOrderBy() {
    Map<String, Object> docs =
        map("1", dottedObject(300), "2", dottedObject(100), "3", dottedObject(200));
    List<Map<String, Object>> expected =
        Arrays.asList(dottedObject(100), dottedObject(200), dottedObject(300));
    CollectionReference collection = testCollection();
    List<Task<Void>> tasks = new ArrayList<>();
    for (Map.Entry<String, Object> entry : docs.entrySet()) {
      tasks.add(collection.document(entry.getKey()).set(entry.getValue()));
    }
    waitFor(Tasks.whenAll(tasks));
    Query query = collection.orderBy(FieldPath.of("field.dot"));
    QuerySnapshot res = waitFor(query.get());
    assertEquals(expected, querySnapshotToValues(res));

    query = collection.orderBy("field\\slash");
    res = waitFor(query.get());
    assertEquals(expected, querySnapshotToValues(res));
  }

  private static Map<String, Object> objectWithTimestamp(Timestamp timestamp) {
    return map("timestamp", timestamp, "nested", map("timestamp2", timestamp));
  }

  @Test
  public void testTimestampsAreTruncated() {
    Timestamp originalTimestamp = new Timestamp(100, 123456789);
    // Timestamps are currently truncated to microseconds after being written to the database.
    Timestamp truncatedTimestamp =
        new Timestamp(
            originalTimestamp.getSeconds(), originalTimestamp.getNanoseconds() / 1000 * 1000);

    DocumentReference docRef = testCollection().document();
    waitFor(docRef.set(objectWithTimestamp(originalTimestamp)));
    DocumentSnapshot snapshot = waitFor(docRef.get());
    Map<String, Object> data = snapshot.getData();

    Timestamp readTimestamp = (Timestamp) snapshot.get("timestamp");
    assertThat(readTimestamp).isEqualTo(truncatedTimestamp);
    assertThat(readTimestamp).isEqualTo(data.get("timestamp"));

    Timestamp readNestedTimestamp = (Timestamp) snapshot.get("nested.timestamp2");
    assertThat(readNestedTimestamp).isEqualTo(truncatedTimestamp);
    @SuppressWarnings("unchecked")
    Map<String, Object> nestedObject = (Map<String, Object>) data.get("nested");
    assertThat(nestedObject.get("timestamp2")).isEqualTo(readNestedTimestamp);
  }
}
