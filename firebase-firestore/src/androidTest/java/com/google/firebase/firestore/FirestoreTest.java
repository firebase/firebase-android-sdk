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

import static com.google.firebase.firestore.AccessHelper.getAsyncQueue;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.newTestSettings;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.provider;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testChangeUserTo;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollection;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testCollectionWithDocs;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testDocument;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirebaseApp;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForOnlineSnapshot;
import static com.google.firebase.firestore.testutil.TestUtil.expectError;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.FirebaseApp;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.Query.Direction;
import com.google.firebase.firestore.auth.User;
import com.google.firebase.firestore.testutil.EventAccumulator;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import com.google.firebase.firestore.util.Logger.Level;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Semaphore;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

// TODO: Add the skipped tests from typescript.
@RunWith(AndroidJUnit4.class)
public class FirestoreTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testCanUpdateAnExistingDocument() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData =
        map("desc", "NewDescription", "owner", map("name", "Jonny", "email", "new@xyz.com"));
    waitFor(documentReference.set(initialValue));
    waitFor(documentReference.update("desc", "NewDescription", "owner.email", "new@xyz.com"));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testCanUpdateAnUnknownDocument() {
    DocumentReference writerRef = testFirestore().collection("collection").document();
    DocumentReference readerRef =
        testFirestore().collection("collection").document(writerRef.getId());
    waitFor(writerRef.set(map("a", "a")));
    waitFor(readerRef.update(map("b", "b")));
    DocumentSnapshot writerSnap = waitFor(writerRef.get(Source.CACHE));
    assertTrue(writerSnap.exists());
    try {
      waitFor(readerRef.get(Source.CACHE));
      fail("Should have thrown exception");
    } catch (RuntimeException e) {
      assertEquals(
          Code.UNAVAILABLE, ((FirebaseFirestoreException) e.getCause().getCause()).getCode());
    }
    writerSnap = waitFor(writerRef.get());
    assertEquals(map("a", "a", "b", "b"), writerSnap.getData());
    DocumentSnapshot readerSnap = waitFor(readerRef.get());
    assertEquals(map("a", "a", "b", "b"), readerSnap.getData());
  }

  @Test
  public void testCanMergeDataWithAnExistingDocumentUsingSet() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner.data", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> mergeData = map("updated", true, "owner.data", map("name", "Sebastian"));
    Map<String, Object> finalData =
        map(
            "desc",
            "Description",
            "updated",
            true,
            "owner.data",
            map("name", "Sebastian", "email", "abc@xyz.com"));
    waitFor(documentReference.set(initialValue));
    waitFor(documentReference.set(mergeData, SetOptions.merge()));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testCanMergeServerTimestamps() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue = map("untouched", true);
    Map<String, Object> mergeData =
        map(
            "time",
            FieldValue.serverTimestamp(),
            "nested",
            map("time", FieldValue.serverTimestamp()));
    waitFor(documentReference.set(initialValue));
    waitFor(documentReference.set(mergeData, SetOptions.merge()));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertTrue(doc.getBoolean("untouched"));
    assertTrue(doc.get("time") instanceof Timestamp);
    assertTrue(doc.get("nested.time") instanceof Timestamp);
  }

  @Test
  public void testCanMergeEmptyObject() {
    DocumentReference documentReference = testDocument();
    EventAccumulator<DocumentSnapshot> eventAccumulator = new EventAccumulator<>();
    ListenerRegistration listenerRegistration =
        documentReference.addSnapshotListener(eventAccumulator.listener());
    eventAccumulator.await();

    documentReference.set(Collections.emptyMap());
    DocumentSnapshot snapshot = eventAccumulator.await();
    assertEquals(Collections.emptyMap(), snapshot.getData());

    waitFor(documentReference.set(map("a", Collections.emptyMap()), SetOptions.mergeFields("a")));
    snapshot = eventAccumulator.await();
    assertEquals(map("a", Collections.emptyMap()), snapshot.getData());

    waitFor(documentReference.set(map("b", Collections.emptyMap()), SetOptions.merge()));
    snapshot = eventAccumulator.await();
    assertEquals(map("a", Collections.emptyMap(), "b", Collections.emptyMap()), snapshot.getData());

    snapshot = waitFor(documentReference.get(Source.SERVER));
    assertEquals(map("a", Collections.emptyMap(), "b", Collections.emptyMap()), snapshot.getData());
    listenerRegistration.remove();
  }

  @Test
  public void testUpdateWithEmptyObjectReplacesAllFields() {
    DocumentReference documentReference = testDocument();
    documentReference.set(map("a", "a"));

    waitFor(documentReference.update("a", Collections.emptyMap()));
    DocumentSnapshot snapshot = waitFor(documentReference.get());
    assertEquals(map("a", Collections.emptyMap()), snapshot.getData());
  }

  @Test
  public void testMergeWithEmptyObjectReplacesAllFields() {
    DocumentReference documentReference = testDocument();
    documentReference.set(map("a", "a"));

    waitFor(documentReference.set(map("a", Collections.emptyMap()), SetOptions.merge()));
    DocumentSnapshot snapshot = waitFor(documentReference.get());
    assertEquals(map("a", Collections.emptyMap()), snapshot.getData());
  }

  @Test
  public void testCanDeleteFieldUsingMerge() {
    DocumentReference documentReference = testCollection("rooms").document("eros");

    Map<String, Object> initialValue =
        map("untouched", true, "foo", "bar", "nested", map("untouched", true, "foo", "bar"));
    waitFor(documentReference.set(initialValue));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertTrue(doc.getBoolean("untouched"));
    assertTrue(doc.getBoolean("nested.untouched"));
    assertTrue(doc.contains("foo"));
    assertTrue(doc.contains("nested.foo"));

    Map<String, Object> mergeData =
        map("foo", FieldValue.delete(), "nested", map("foo", FieldValue.delete()));
    waitFor(documentReference.set(mergeData, SetOptions.merge()));
    doc = waitFor(documentReference.get());
    assertTrue(doc.getBoolean("untouched"));
    assertTrue(doc.getBoolean("nested.untouched"));
    assertFalse(doc.contains("foo"));
    assertFalse(doc.contains("nested.foo"));
  }

  @Test
  public void testCanDeleteFieldUsingMergeFields() {
    DocumentReference documentReference = testCollection("rooms").document("eros");

    Map<String, Object> initialValue =
        map(
            "untouched",
            true,
            "foo",
            "bar",
            "inner",
            map("removed", true, "foo", "bar"),
            "nested",
            map("untouched", true, "foo", "bar"));
    Map<String, Object> mergeData =
        map(
            "foo",
            FieldValue.delete(),
            "inner",
            map("foo", FieldValue.delete()),
            "nested",
            map("untouched", FieldValue.delete(), "foo", FieldValue.delete()));
    Map<String, Object> finalData =
        map("untouched", true, "inner", map(), "nested", map("untouched", true));

    waitFor(documentReference.set(initialValue));
    waitFor(documentReference.set(mergeData, SetOptions.mergeFields("foo", "inner", "nested.foo")));

    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testCanSetServerTimestampsUsingMergeFields() {
    DocumentReference documentReference = testCollection("rooms").document("eros");

    Map<String, Object> initialValue =
        map("untouched", true, "foo", "bar", "nested", map("untouched", true, "foo", "bar"));
    waitFor(documentReference.set(initialValue));
    Map<String, Object> mergeData =
        map(
            "foo",
            FieldValue.serverTimestamp(),
            "inner",
            map("foo", FieldValue.serverTimestamp()),
            "nested",
            map("foo", FieldValue.serverTimestamp()));

    waitFor(documentReference.set(mergeData, SetOptions.mergeFields("foo", "inner", "nested.foo")));

    DocumentSnapshot doc = waitFor(documentReference.get());
    assertTrue(doc.exists());
    assertTrue(doc.get("foo") instanceof Timestamp);
    assertTrue(doc.get("inner.foo") instanceof Timestamp);
    assertTrue(doc.get("nested.foo") instanceof Timestamp);
  }

  @Test
  public void testMergeReplacesArrays() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map(
            "untouched",
            true,
            "data",
            "old",
            "topLevel",
            Arrays.asList("old", "old"),
            "mapInArray",
            Arrays.asList(map("data", "old")));
    Map<String, Object> mergeData =
        map(
            "data",
            "new",
            "topLevel",
            Arrays.asList("new"),
            "mapInArray",
            Arrays.asList(map("data", "new")));
    Map<String, Object> finalData =
        map(
            "untouched",
            true,
            "data",
            "new",
            "topLevel",
            Arrays.asList("new"),
            "mapInArray",
            Arrays.asList(map("data", "new")));
    waitFor(documentReference.set(initialValue));
    waitFor(documentReference.set(mergeData, SetOptions.merge()));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testCanDeepMergeDataWithAnExistingDocumentUsingSet() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("owner.data", map("name", "Jonny", "email", "old@xyz.com"));
    Map<String, Object> finalData =
        map(
            "desc",
            "NewDescription",
            "owner.data",
            map("name", "Sebastian", "email", "old@xyz.com"));
    waitFor(documentReference.set(initialValue));
    waitFor(
        documentReference.set(
            map(
                "desc",
                "NewDescription",
                "owner.data",
                map("name", "Sebastian", "email", "new@xyz.com")),
            SetOptions.mergeFieldPaths(
                Arrays.asList(FieldPath.of("desc"), FieldPath.of("owner.data", "name")))));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testFieldMaskCannotContainMissingFields() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    expectError(
        () ->
            documentReference.set(
                map("desc", "NewDescription"), SetOptions.mergeFields("desc", "owner")),
        "Field 'owner' is specified in your field mask but not in your input data.");
  }

  @Test
  public void testFieldsNotInFieldMaskAreIgnored() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData =
        map("desc", "NewDescription", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    waitFor(documentReference.set(initialValue));
    waitFor(
        documentReference.set(
            map("desc", "NewDescription", "owner", "Sebastian"), SetOptions.mergeFields("desc")));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testFieldDeletesNotInFieldMaskAreIgnored() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData =
        map("desc", "NewDescription", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    waitFor(documentReference.set(initialValue));
    waitFor(
        documentReference.set(
            map("desc", "NewDescription", "owner", FieldValue.delete()),
            SetOptions.mergeFields("desc")));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testFieldTransformsNotInFieldMaskAreIgnored() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData =
        map("desc", "NewDescription", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    waitFor(documentReference.set(initialValue));
    waitFor(
        documentReference.set(
            map("desc", "NewDescription", "owner", FieldValue.serverTimestamp()),
            SetOptions.mergeFields("desc")));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testFieldMaskEmpty() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData = initialValue;
    waitFor(documentReference.set(initialValue));
    waitFor(documentReference.set(map("desc", "NewDescription"), SetOptions.mergeFields()));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testFieldInFieldMaskMultipleTimes() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData =
        map("desc", "Description", "owner", map("name", "Sebastian", "email", "new@new.com"));

    waitFor(documentReference.set(initialValue));
    waitFor(
        documentReference.set(
            map(
                "desc",
                "NewDescription",
                "owner",
                map("name", "Sebastian", "email", "new@new.com")),
            SetOptions.mergeFields("owner.name", "owner", "owner")));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testCanDeleteAFieldWithAnUpdate() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> initialValue =
        map("desc", "Description", "owner", map("name", "Jonny", "email", "abc@xyz.com"));
    Map<String, Object> finalData = map("desc", "Description", "owner", map("name", "Jonny"));
    waitFor(documentReference.set(initialValue));
    waitFor(
        documentReference.update("owner.email", com.google.firebase.firestore.FieldValue.delete()));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(finalData, doc.getData());
  }

  @Test
  public void testCanUpdateFieldsWithDots() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    waitFor(documentReference.set(map("a.b", "old", "c.d", "old")));

    waitFor(documentReference.update(FieldPath.of("a.b"), "new"));
    waitFor(documentReference.update(FieldPath.of("c.d"), "new"));

    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(map("a.b", "new", "c.d", "new"), doc.getData());
  }

  @Test
  public void testCanUpdateNestedFields() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    waitFor(documentReference.set(map("a", map("b", "old"), "c", map("d", "old"))));

    waitFor(documentReference.update("a.b", "new"));
    waitFor(documentReference.update(map("c.d", "new")));

    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(map("a", map("b", "new"), "c", map("d", "new")), doc.getData());
  }

  @Test
  public void testDeleteDocument() {
    DocumentReference documentReference = testCollection("rooms").document("eros");
    Map<String, Object> data = map("value", "bar");
    waitFor(documentReference.set(data));
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(data, doc.getData());
    waitFor(documentReference.delete());
    doc = waitFor(documentReference.get());
    assertFalse(doc.exists());
  }

  @Test
  public void testCannotUpdateNonexistentDocument() {
    DocumentReference documentReference = testCollection("rooms").document();
    Exception e = waitForException(documentReference.update(map("owner", "abc")));
    assertNotNull(e);
    assertTrue(e instanceof FirebaseFirestoreException);
    assertEquals(Code.NOT_FOUND, ((FirebaseFirestoreException) e).getCode());
  }

  @Test
  public void testCanRetrieveNonexistentDocument() {
    DocumentReference documentReference = testCollection("rooms").document();
    DocumentSnapshot documentSnapshot = waitFor(documentReference.get());
    assertFalse(documentSnapshot.exists());
    assertNull(documentSnapshot.getData());
    assertNull(documentSnapshot.toObject(Object.class));

    TaskCompletionSource<Void> barrier = new TaskCompletionSource<>();

    ListenerRegistration listenerRegistration = null;

    try {
      listenerRegistration =
          documentReference.addSnapshotListener(
              (value, error) -> {
                assertFalse(documentSnapshot.exists());
                assertNull(documentSnapshot.getData());
                barrier.setResult(null);
              });
      waitFor(barrier.getTask());
    } finally {
      if (listenerRegistration != null) {
        listenerRegistration.remove();
      }
    }
  }

  @Test
  public void testCannotSetOrUpdateInvalidDocuments() {
    List<Object> documents = Arrays.asList(null, 0, "foo", Arrays.asList("a"), new Date());
    DocumentReference documentReference = testCollection("rooms").document();
    for (Object o : documents) {
      Exception caught = null;
      try {
        documentReference.set(o);
      } catch (Exception e) {
        caught = e;
      }

      assertNotNull(caught);
    }
  }

  @Test
  public void testAddingToACollectionYieldsTheCorrectDocumentReference() {
    Map<String, Object> data = map("foo", 1.0);
    DocumentReference documentReference = waitFor(testCollection("rooms").add(data));
    DocumentSnapshot document = waitFor(documentReference.get());
    assertEquals(data, document.getData());
  }

  @Test
  public void testSnapshotsInSyncListenerFiresAfterListenersInSync() {
    Map<String, Object> data = map("foo", 1.0);
    CollectionReference collection = testCollection();
    DocumentReference documentReference = waitFor(collection.add(data));
    List<String> events = new ArrayList<>();

    Semaphore gotInitialSnapshot = new Semaphore(0);
    Semaphore done = new Semaphore(0);

    ListenerRegistration listenerRegistration = null;

    documentReference.addSnapshotListener(
        (value, error) -> {
          events.add("doc");
          gotInitialSnapshot.release();
        });
    waitFor(gotInitialSnapshot);
    events.clear();

    try {
      listenerRegistration =
          documentReference
              .getFirestore()
              .addSnapshotsInSyncListener(
                  () -> {
                    events.add("snapshots-in-sync");
                    if (events.size() == 3) {
                      // We should have an initial snapshots-in-sync event, then a snapshot event
                      // for set(), then another event to indicate we're in sync again.
                      assertEquals(
                          Arrays.asList("snapshots-in-sync", "doc", "snapshots-in-sync"), events);
                      done.release();
                    }
                  });
      waitFor(documentReference.set(map("foo", 3.0)));
      waitFor(done);
    } finally {
      if (listenerRegistration != null) {
        listenerRegistration.remove();
      }
    }
  }

  @Test
  public void testQueriesAreValidatedOnClient() {
    // NOTE: Failure cases are validated in ValidationTest.
    CollectionReference collection = testCollection();
    final Query query = collection.whereGreaterThanOrEqualTo("x", 32);
    // Same inequality field works.
    query.whereLessThanOrEqualTo("x", "cat");
    // Equality on different field works.
    query.whereEqualTo("y", "cat");
    // Array contains on different field works.
    query.whereArrayContains("y", "cat");
    // Array contains any on different field works.
    query.whereArrayContainsAny("y", Arrays.asList("cat"));
    // In on different field works.
    query.whereIn("y", Arrays.asList("cat"));

    // Ordering by inequality field succeeds.
    query.orderBy("x");
    collection.orderBy("x").whereGreaterThanOrEqualTo("x", 32);

    // Inequality same as first order by works.
    query.orderBy("x").orderBy("y");
    collection.orderBy("x").orderBy("y").whereGreaterThanOrEqualTo("x", 32);
    collection.orderBy("x", Direction.DESCENDING).whereEqualTo("y", "true");

    // Equality different than orderBy works.
    collection.orderBy("x").whereEqualTo("y", "cat");
    // Array contains different than orderBy works.
    collection.orderBy("x").whereArrayContains("y", "cat");
    // Array contains any different than orderBy works.
    collection.orderBy("x").whereArrayContainsAny("y", Arrays.asList("cat"));
    // In different than orderBy works.
    collection.orderBy("x").whereIn("y", Arrays.asList("cat"));
  }

  @Test
  public void testDocumentSnapshotEventsNonExistent() {
    DocumentReference docRef = testCollection("rooms").document();
    CountDownLatch latch = new CountDownLatch(1);
    ListenerRegistration listener =
        docRef.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (doc, error) -> {
              assertNull(error);
              assertNotNull(doc);
              assertFalse(doc.exists());
              assertEquals(1, latch.getCount());
              latch.countDown();
            });
    waitFor(latch);
    listener.remove();
  }

  @Test
  public void testDocumentSnapshotEventsForAdd() {
    DocumentReference docRef = testCollection("rooms").document();
    CountDownLatch dataLatch = new CountDownLatch(3);
    CountDownLatch emptyLatch = new CountDownLatch(1);
    ListenerRegistration listener =
        docRef.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (doc, error) -> {
              if (emptyLatch.getCount() > 0) {
                dataLatch.countDown();
                emptyLatch.countDown();
                assertFalse(doc.exists());
                return;
              }

              assertTrue(doc.exists());
              long count = dataLatch.getCount();
              assertTrue(count > 0);
              dataLatch.countDown();
              if (count == 2) {
                assertEquals(map("a", 1.0), doc.getData());
                assertTrue(doc.getMetadata().hasPendingWrites());
              } else if (count == 1) {
                assertEquals(map("a", 1.0), doc.getData());
                assertFalse(doc.getMetadata().hasPendingWrites());
              }
            });
    waitFor(emptyLatch);
    docRef.set(map("a", 1.0));
    waitFor(dataLatch);
    listener.remove();
  }

  @Test
  public void testDocumentSnapshotEventsForChange() {
    Map<String, Object> initialData = map("a", 1.0);
    Map<String, Object> updateData = map("a", 2.0);
    CollectionReference testCollection = testCollectionWithDocs(map("doc", initialData));
    DocumentReference docRef = testCollection.document("doc");
    CountDownLatch initialLatch = new CountDownLatch(1);
    CountDownLatch latch = new CountDownLatch(3);
    ListenerRegistration listener =
        docRef.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (doc, error) -> {
              long latchCount = latch.getCount();
              latch.countDown();
              switch ((int) latchCount) {
                case 3:
                  assertEquals(initialData, doc.getData());
                  assertFalse(doc.getMetadata().hasPendingWrites());
                  assertFalse(doc.getMetadata().isFromCache());
                  initialLatch.countDown();
                  break;
                case 2:
                  assertEquals(updateData, doc.getData());
                  assertTrue(doc.getMetadata().hasPendingWrites());
                  assertFalse(doc.getMetadata().isFromCache());
                  break;
                case 1:
                  assertEquals(updateData, doc.getData());
                  assertFalse(doc.getMetadata().hasPendingWrites());
                  assertFalse(doc.getMetadata().isFromCache());
                  break;
                default:
                  fail("unexpected latch count");
              }
            });
    waitFor(initialLatch);
    waitFor(docRef.update(updateData));
    waitFor(latch);
    listener.remove();
  }

  @Test
  public void testDocumentSnapshotEventsForDelete() {
    Map<String, Object> initialData = map("a", 1.0);
    DocumentReference docRef = testCollectionWithDocs(map("doc", initialData)).document("doc");
    CountDownLatch initialLatch = new CountDownLatch(1);
    CountDownLatch latch = new CountDownLatch(2);
    ListenerRegistration listener =
        docRef.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (doc, error) -> {
              long count = latch.getCount();
              latch.countDown();
              switch ((int) count) {
                case 2:
                  assertTrue(doc.exists());
                  assertEquals(initialData, doc.getData());
                  assertFalse(doc.getMetadata().hasPendingWrites());
                  initialLatch.countDown();
                  break;
                case 1:
                  assertFalse(doc.exists());
                  break;
                default:
                  fail("unexpected latch count");
              }
            });
    waitFor(initialLatch);
    docRef.delete();
    waitFor(latch);
    listener.remove();
  }

  @Test
  public void testQuerySnapshotEventsForAdd() {
    CollectionReference collection = testCollection();
    DocumentReference docRef = collection.document();
    Map<String, Object> data = map("a", 1.0);
    CountDownLatch emptyLatch = new CountDownLatch(1);
    CountDownLatch dataLatch = new CountDownLatch(3);
    ListenerRegistration listener =
        collection.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (snapshot, error) -> {
              long count = dataLatch.getCount();
              dataLatch.countDown();
              assertTrue(count > 0);
              switch ((int) count) {
                case 3:
                  emptyLatch.countDown();
                  assertEquals(0, snapshot.size());
                  break;
                case 2:
                  assertEquals(1, snapshot.size());
                  DocumentSnapshot doc = snapshot.getDocuments().get(0);
                  assertEquals(data, doc.getData());
                  assertTrue(doc.getMetadata().hasPendingWrites());
                  break;
                case 1:
                  assertEquals(1, snapshot.size());
                  DocumentSnapshot doc2 = snapshot.getDocuments().get(0);
                  assertEquals(data, doc2.getData());
                  assertFalse(doc2.getMetadata().hasPendingWrites());
                  break;
                default:
                  fail("unexpected call to onSnapshot: " + snapshot);
              }
            });
    waitFor(emptyLatch);
    docRef.set(data);
    waitFor(dataLatch);
    listener.remove();
  }

  @Test
  public void testQuerySnapshotEventsForChange() {
    Map<String, Object> initialData = map("b", 1.0);
    Map<String, Object> updateData = map("b", 2.0);

    CollectionReference collection = testCollectionWithDocs(map("doc", initialData));
    DocumentReference docRef = collection.document("doc");

    CountDownLatch initialLatch = new CountDownLatch(1);
    CountDownLatch dataLatch = new CountDownLatch(3);
    ListenerRegistration listener =
        collection.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (snapshot, error) -> {
              long count = dataLatch.getCount();
              dataLatch.countDown();
              switch ((int) count) {
                case 3:
                  initialLatch.countDown();
                  assertEquals(1, snapshot.size());
                  DocumentSnapshot document = snapshot.getDocuments().get(0);
                  assertEquals(initialData, document.getData());
                  assertFalse(document.getMetadata().hasPendingWrites());
                  break;
                case 2:
                  assertEquals(1, snapshot.size());
                  DocumentSnapshot document2 = snapshot.getDocuments().get(0);
                  assertEquals(updateData, document2.getData());
                  assertTrue(document2.getMetadata().hasPendingWrites());
                  break;
                case 1:
                  assertEquals(1, snapshot.size());
                  DocumentSnapshot document3 = snapshot.getDocuments().get(0);
                  assertEquals(updateData, document3.getData());
                  assertFalse(document3.getMetadata().hasPendingWrites());
                  break;
                default:
                  fail("unexpected event " + snapshot);
              }
            });
    waitFor(initialLatch);
    waitFor(docRef.set(updateData));
    waitFor(dataLatch);
    listener.remove();
  }

  @Test
  public void testQuerySnapshotEventsForDelete() {
    Map<String, Object> initialData = map("a", 1.0);
    CollectionReference collection = testCollectionWithDocs(map("doc", initialData));
    DocumentReference docRef = collection.document("doc");

    CountDownLatch initialLatch = new CountDownLatch(1);
    CountDownLatch dataLatch = new CountDownLatch(2);
    ListenerRegistration listener =
        collection.addSnapshotListener(
            MetadataChanges.INCLUDE,
            (snapshot, error) -> {
              long count = dataLatch.getCount();
              dataLatch.countDown();
              switch ((int) count) {
                case 2:
                  initialLatch.countDown();
                  assertEquals(1, snapshot.size());
                  DocumentSnapshot document = snapshot.getDocuments().get(0);
                  assertEquals(map("a", 1.0), document.getData());
                  assertFalse(document.getMetadata().hasPendingWrites());
                  break;
                case 1:
                  assertEquals(0, snapshot.size());
                  break;
                default:
                  fail("unexpected event " + snapshot);
              }
            });
    waitFor(initialLatch);
    waitFor(docRef.delete());
    waitFor(dataLatch);
    listener.remove();
  }

  @Test
  public void testMetadataOnlyChangesAreNotFiredWhenNoOptionsProvided() {
    DocumentReference docRef = testCollection().document();

    Map<String, Object> initialData = map("a", 1.0);
    Map<String, Object> updateData = map("b", 1.0);

    CountDownLatch dataLatch = new CountDownLatch(2);
    ListenerRegistration listener =
        docRef.addSnapshotListener(
            (snapshot, error) -> {
              long count = dataLatch.getCount();
              dataLatch.countDown();
              switch ((int) count) {
                case 2:
                  assertEquals(map("a", 1.0), snapshot.getData());
                  break;
                case 1:
                  assertEquals(map("b", 1.0), snapshot.getData());
                  break;
                default:
                  fail("unexpected event " + snapshot);
              }
            });

    waitFor(docRef.set(initialData));
    waitFor(docRef.set(updateData));
    waitFor(dataLatch);
    listener.remove();
  }

  @Test
  public void testDocumentReferenceExposesFirestore() {
    FirebaseFirestore firestore = testFirestore();
    assertSame(firestore.document("foo/bar").getFirestore(), firestore);
  }

  @Test
  public void testCollectionReferenceExposesFirestore() {
    FirebaseFirestore firestore = testFirestore();
    assertSame(firestore.collection("foo").getFirestore(), firestore);
  }

  @Test
  public void testDocumentReferenceEquality() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference docRef = firestore.document("foo/bar");
    assertEquals(docRef, firestore.document("foo/bar"));
    assertEquals(docRef.collection("blah").getParent(), docRef);

    assertNotEquals(docRef, firestore.document("foo/BAR"));

    FirebaseFirestore otherFirestore = testFirestore();
    assertNotEquals(docRef, otherFirestore.document("foo/bar"));
  }

  @Test
  public void testQueryReferenceEquality() {
    FirebaseFirestore firestore = testFirestore();
    Query query = firestore.collection("foo").orderBy("bar").whereEqualTo("baz", 42);
    Query query2 = firestore.collection("foo").orderBy("bar").whereEqualTo("baz", 42);
    assertEquals(query, query2);

    Query query3 = firestore.collection("foo").orderBy("BAR").whereEqualTo("baz", 42);
    assertNotEquals(query, query3);

    FirebaseFirestore otherFirestore = testFirestore();
    Query query4 = otherFirestore.collection("foo").orderBy("bar").whereEqualTo("baz", 42);
    assertNotEquals(query4, query);
  }

  @Test
  public void testCanTraverseCollectionsAndDocuments() {
    FirebaseFirestore firestore = testFirestore();
    String expected = "a/b/c/d";
    // doc path from root Firestore.
    assertEquals(expected, firestore.document("a/b/c/d").getPath());
    // collection path from root Firestore.
    assertEquals(expected, firestore.collection("a/b/c").document("d").getPath());
    // doc path from CollectionReference.
    assertEquals(expected, firestore.collection("a").document("b/c/d").getPath());
    // collection path from DocumentReference.
    assertEquals(firestore.document("a/b").collection("c/d/e").getPath(), expected + "/e");
  }

  @Test
  public void testCanTraverseCollectionAndDocumentParents() {
    FirebaseFirestore firestore = testFirestore();
    CollectionReference collection = firestore.collection("a/b/c");
    assertEquals("a/b/c", collection.getPath());

    DocumentReference doc = collection.getParent();
    assertEquals("a/b", doc.getPath());

    collection = doc.getParent();
    assertEquals("a", collection.getPath());

    DocumentReference nullDoc = collection.getParent();
    assertNull(nullDoc);
  }

  @Test
  public void testCanQueueWritesWhileOffline() {
    // Arrange
    DocumentReference documentReference = testCollection("rooms").document("eros");
    FirebaseFirestore firestore = documentReference.getFirestore();
    Map<String, Object> data =
        map("desc", "Description", "owner", map("name", "Sebastian", "email", "abc@xyz.com"));

    // Act
    waitFor(firestore.disableNetwork());
    Task<Void> pendingWrite = documentReference.set(data);
    assertTrue(!pendingWrite.isComplete());
    waitFor(firestore.enableNetwork());
    waitFor(pendingWrite);

    // Assert
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(data, doc.getData());
    assertFalse(doc.getMetadata().isFromCache());
  }

  @Test
  public void testCantGetDocumentsWhileOffline() {
    DocumentReference documentReference = testCollection("rooms").document();
    FirebaseFirestore firestore = documentReference.getFirestore();
    waitFor(firestore.disableNetwork());
    waitForException(documentReference.get());

    // Write the document to the local cache.
    Map<String, Object> data =
        map("desc", "Description", "owner", map("name", "Sebastian", "email", "abc@xyz.com"));
    Task<Void> pendingWrite = documentReference.set(data);

    // The network is offline and we return a cached result.
    DocumentSnapshot doc = waitFor(documentReference.get());
    assertEquals(data, doc.getData());
    assertTrue(doc.getMetadata().isFromCache());

    // Enable the network and fetch the document again.
    waitFor(firestore.enableNetwork());
    waitFor(pendingWrite);
    doc = waitFor(documentReference.get());
    assertEquals(data, doc.getData());
    assertFalse(doc.getMetadata().isFromCache());
  }

  @Test
  public void testWriteStreamReconnectsAfterIdle() throws Exception {
    DocumentReference doc = testDocument();
    FirebaseFirestore firestore = doc.getFirestore();

    waitFor(doc.set(map("foo", "bar")));
    getAsyncQueue(firestore).runDelayedTasksUntil(TimerId.WRITE_STREAM_IDLE);
    waitFor(doc.set(map("foo", "bar")));
  }

  @Test
  public void testWatchStreamReconnectsAfterIdle() throws Exception {
    DocumentReference doc = testDocument();
    FirebaseFirestore firestore = doc.getFirestore();

    waitForOnlineSnapshot(doc);
    getAsyncQueue(firestore).runDelayedTasksUntil(TimerId.LISTEN_STREAM_IDLE);
    waitForOnlineSnapshot(doc);
  }

  @Test
  public void testCanDisableAndEnableNetworking() {
    // There's not currently a way to check if networking is in fact disabled,
    // so for now just test that the method is well-behaved and doesn't throw.
    FirebaseFirestore firestore = testFirestore();
    waitFor(firestore.enableNetwork());
    waitFor(firestore.enableNetwork());
    waitFor(firestore.disableNetwork());
    waitFor(firestore.disableNetwork());
    waitFor(firestore.enableNetwork());
  }

  @Test
  public void testClientCallsAfterTerminateFails() {
    FirebaseFirestore firestore = testFirestore();
    waitFor(firestore.terminate());
    expectError(
        () -> waitFor(firestore.disableNetwork()), "The client has already been terminated");
  }

  @Test
  public void testMaintainsPersistenceAfterRestarting() {
    FirebaseFirestore firestore =
        testFirestore(provider().projectId(), Level.DEBUG, newTestSettings(), "dbPersistenceKey");
    DocumentReference docRef = firestore.collection("col1").document("doc1");
    waitFor(docRef.set(map("foo", "bar")));
    waitFor(firestore.terminate());
    IntegrationTestUtil.removeFirestore(firestore);

    // We restart the app with the same name and options to check that the previous instance's
    // persistent storage is actually cleared after the restart. Calling testFirestore() without the
    // parameters would create a new instance of firestore, which defeats the purpose of this test.
    FirebaseFirestore firestore2 =
        testFirestore(provider().projectId(), Level.DEBUG, newTestSettings(), "dbPersistenceKey");
    DocumentReference docRef2 = firestore2.document(docRef.getPath());
    DocumentSnapshot doc = waitFor(docRef2.get());
    assertTrue(doc.exists());
  }

  @Test
  public void testCanClearPersistenceAfterRestarting() throws Exception {
    FirebaseFirestore firestore =
        testFirestore(provider().projectId(), Level.DEBUG, newTestSettings(), "dbPersistenceKey");
    DocumentReference docRef = firestore.collection("col1").document("doc1");
    waitFor(docRef.set(map("foo", "bar")));
    waitFor(firestore.terminate());
    IntegrationTestUtil.removeFirestore(firestore);
    waitFor(AccessHelper.clearPersistence(firestore));

    // We restart the app with the same name and options to check that the previous instance's
    // persistent storage is actually cleared after the restart. Calling testFirestore() without the
    // parameters would create a new instance of firestore, which defeats the purpose of this test.
    FirebaseFirestore firestore2 =
        testFirestore(provider().projectId(), Level.DEBUG, newTestSettings(), "dbPersistenceKey");
    DocumentReference docRef2 = firestore2.document(docRef.getPath());
    Exception e = waitForException(docRef2.get(Source.CACHE));
    assertEquals(Code.UNAVAILABLE, ((FirebaseFirestoreException) e).getCode());
  }

  @Test
  public void testClearPersistenceWhileRunningFails() {
    FirebaseFirestore firestore = testFirestore();
    waitFor(firestore.enableNetwork());

    Task<Void> transactionTask = AccessHelper.clearPersistence(firestore);
    waitForException(transactionTask);
    assertFalse(transactionTask.isSuccessful());
    Exception e = transactionTask.getException();
    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
    assertEquals(Code.FAILED_PRECONDITION, firestoreException.getCode());
  }

  @Test
  public void testRestartFirestoreLeadsToNewInstance() {
    FirebaseApp app = testFirebaseApp();
    FirebaseFirestore instance = FirebaseFirestore.getInstance(app);
    instance.setFirestoreSettings(newTestSettings());
    FirebaseFirestore sameInstance = FirebaseFirestore.getInstance(app);

    assertSame(instance, sameInstance);
    waitFor(instance.document("abc/123").set(Collections.singletonMap("field", 100L)));

    instance.terminate();
    FirebaseFirestore newInstance = FirebaseFirestore.getInstance(app);
    newInstance.setFirestoreSettings(newTestSettings());

    // Verify new instance works.
    DocumentSnapshot doc = waitFor(newInstance.document("abc/123").get());
    assertEquals(100L, doc.get("field"));
    waitFor(newInstance.document("abc/123").delete());

    // Verify it is different instance.
    assertNotSame(instance, newInstance);
  }

  @Test
  public void testAppDeleteLeadsToFirestoreTerminate() {
    FirebaseApp app = testFirebaseApp();
    FirebaseFirestore instance = FirebaseFirestore.getInstance(app);
    instance.setFirestoreSettings(newTestSettings());
    waitFor(instance.document("abc/123").set(Collections.singletonMap("Field", 100)));

    app.delete();

    assertTrue(instance.getClient().isTerminated());
  }

  @Test
  public void testNewOperationThrowsAfterFirestoreTerminate() {
    FirebaseFirestore instance = testFirestore();
    DocumentReference reference = instance.document("abc/123");
    waitFor(reference.set(Collections.singletonMap("Field", 100)));

    instance.terminate();

    final String expectedMessage = "The client has already been terminated";
    expectError(() -> waitFor(reference.get()), expectedMessage);
    expectError(() -> waitFor(reference.update("Field", 1)), expectedMessage);
    expectError(
        () -> waitFor(reference.set(Collections.singletonMap("Field", 1))), expectedMessage);
    expectError(
        () -> waitFor(instance.runBatch((batch) -> batch.update(reference, "Field", 1))),
        expectedMessage);
    expectError(
        () -> waitFor(instance.runTransaction(transaction -> transaction.get(reference))),
        expectedMessage);
  }

  @Test
  public void testTerminateCalledMultipleTimes() {
    FirebaseFirestore instance = testFirestore();
    DocumentReference reference = instance.document("abc/123");
    waitFor(reference.set(Collections.singletonMap("Field", 100)));

    instance.terminate();

    final String expectedMessage = "The client has already been terminated";
    expectError(() -> waitFor(reference.get()), expectedMessage);

    // Calling a second time should go through and change nothing.
    instance.terminate();

    expectError(() -> waitFor(reference.get()), expectedMessage);
  }

  @Test
  public void testCanStopListeningAfterTerminate() {
    FirebaseFirestore instance = testFirestore();
    DocumentReference reference = instance.document("abc/123");
    EventAccumulator<DocumentSnapshot> eventAccumulator = new EventAccumulator<>();
    ListenerRegistration registration = reference.addSnapshotListener(eventAccumulator.listener());
    eventAccumulator.await();

    waitFor(instance.terminate());

    // This should proceed without error.
    registration.remove();
    // Multiple calls should proceed as an effectively no-op.
    registration.remove();
  }

  @Test
  public void testWaitForPendingWritesResolves() {
    DocumentReference documentReference = testCollection("abc").document("123");
    FirebaseFirestore firestore = documentReference.getFirestore();
    Map<String, Object> data = map("foo", "bar");

    waitFor(firestore.disableNetwork());
    Task<Void> awaitsPendingWrites1 = firestore.waitForPendingWrites();
    Task<Void> pendingWrite = documentReference.set(data);
    Task<Void> awaitsPendingWrites2 = firestore.waitForPendingWrites();

    // `awaitsPendingWrites1` completes immediately because there are no pending writes at
    // the time it is created.
    waitFor(awaitsPendingWrites1);
    assertTrue(awaitsPendingWrites1.isComplete() && awaitsPendingWrites1.isSuccessful());
    assertTrue(!pendingWrite.isComplete());
    assertTrue(!awaitsPendingWrites2.isComplete());

    firestore.enableNetwork();
    waitFor(awaitsPendingWrites2);
    assertTrue(awaitsPendingWrites2.isComplete() && awaitsPendingWrites2.isSuccessful());
  }

  @Test
  public void testWaitForPendingWritesFailsWhenUserChanges() {
    DocumentReference documentReference = testCollection("abc").document("123");
    FirebaseFirestore firestore = documentReference.getFirestore();
    Map<String, Object> data = map("foo", "bar");

    // Prevent pending writes receiving acknowledgement.
    waitFor(firestore.disableNetwork());
    Task<Void> pendingWrite = documentReference.set(data);
    Task<Void> awaitsPendingWrites = firestore.waitForPendingWrites();
    assertTrue(!pendingWrite.isComplete());
    assertTrue(!awaitsPendingWrites.isComplete());

    testChangeUserTo(new User("new user"));

    assertTrue(!pendingWrite.isComplete());
    assertEquals(
        "'waitForPendingWrites' task is cancelled due to User change.",
        waitForException(awaitsPendingWrites).getMessage());
  }

  @Test
  public void testPendingWriteTaskResolveWhenOfflineIfThereIsNoPending() {
    DocumentReference documentReference = testCollection("abc").document("123");
    FirebaseFirestore firestore = documentReference.getFirestore();

    // Prevent pending writes receiving acknowledgement.
    waitFor(firestore.disableNetwork());
    Task<Void> awaitsPendingWrites = firestore.waitForPendingWrites();
    waitFor(awaitsPendingWrites);

    assertTrue(awaitsPendingWrites.isComplete() && awaitsPendingWrites.isSuccessful());
  }
}
