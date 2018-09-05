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

import static com.google.firebase.firestore.testutil.IntegrationTestUtil.testFirestore;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitFor;
import static com.google.firebase.firestore.testutil.IntegrationTestUtil.waitForException;
import static com.google.firebase.firestore.testutil.TestUtil.map;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;

import android.support.test.runner.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransactionTest {

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testGetDocuments() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("spaces").document();
    Map<String, Object> value = map("foo", 1, "desc", "Stuff", "owner", "Jonny");
    waitFor(doc.set(value));
    Exception e = waitForException(firestore.runTransaction(transaction -> transaction.get(doc)));
    // We currently require every document read to also be written.
    // TODO: Fix this check once we drop that requirement.
    assertEquals("Transaction failed all retries.", e.getMessage());
    assertEquals(
        "Every document read in a transaction must also be written.", e.getCause().getMessage());
  }

  @Test
  public void testDeleteDocument() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("towns").document();
    waitFor(doc.set(map("foo", "bar")));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals("bar", snapshot.getString("foo"));
    waitFor(
        firestore.runTransaction(
            transaction -> {
              transaction.delete(doc);
              return null;
            }));
    snapshot = waitFor(doc.get());
    assertFalse(snapshot.exists());
  }

  @Test
  public void testGetNonexistentDocumentThenCreate() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference docRef = firestore.collection("towns").document();
    waitFor(
        firestore.runTransaction(
            transaction -> {
              DocumentSnapshot docSnap = transaction.get(docRef);
              assertFalse(docSnap.exists());
              transaction.set(docRef, map("foo", "bar"));
              return null;
            }));
    DocumentSnapshot snapshot = waitFor(docRef.get());
    assertEquals("bar", snapshot.getString("foo"));
  }

  @Test
  public void testWriteDocumentTwice() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("towns").document();
    waitFor(
        firestore.runTransaction(
            transaction -> {
              transaction.set(doc, map("a", "b")).set(doc, map("c", "d"));
              return null;
            }));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(map("c", "d"), snapshot.getData());
  }

  @Test
  public void testSetDocumentWithMerge() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("towns").document();
    waitFor(
        firestore.runTransaction(
            transaction -> {
              transaction
                  .set(doc, map("a", "b", "nested", map("a", "b")))
                  .set(doc, map("c", "d", "nested", map("c", "d")), SetOptions.merge());
              return null;
            }));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(map("a", "b", "c", "d", "nested", map("a", "b", "c", "d")), snapshot.getData());
  }

  @Test
  public void testIncrementTransactionally() {
    // A set of concurrent transactions.
    ArrayList<Task<Void>> transactionTasks = new ArrayList<>();
    ArrayList<Task<Void>> readTasks = new ArrayList<>();
    // A barrier to make sure every transaction reaches the same spot.
    TaskCompletionSource<Void> barrier = new TaskCompletionSource<>();
    AtomicInteger started = new AtomicInteger(0);

    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("counters").document();
    waitFor(doc.set(map("count", 5.0)));

    // Make 3 transactions that will all increment.
    for (int i = 0; i < 3; i++) {
      TaskCompletionSource<Void> resolveRead = new TaskCompletionSource<>();
      readTasks.add(resolveRead.getTask());
      transactionTasks.add(
          firestore.runTransaction(
              transaction -> {
                DocumentSnapshot snapshot = transaction.get(doc);
                assertNotNull(snapshot);
                started.incrementAndGet();
                resolveRead.trySetResult(null);
                waitFor(barrier.getTask());
                transaction.set(doc, map("count", snapshot.getDouble("count") + 1.0));
                return null;
              }));
    }

    // Let all of the transactions fetch the old value and stop once.
    waitFor(Tasks.whenAll(readTasks));
    assertEquals(3, started.intValue());
    // Let all of the transactions continue and wait for them to finish.
    barrier.setResult(null);
    waitFor(Tasks.whenAll(transactionTasks));
    // Now all transaction should be completed, so check the result.
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(8, snapshot.getDouble("count").intValue());
  }

  @Test
  public void testTransactionRejectsUpdatesForNonexistentDocuments() {
    final FirebaseFirestore firestore = testFirestore();

    // Make a transaction that will fail
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              // Get and update a document that doesn't exist so that the transaction fails
              DocumentSnapshot doc =
                  transaction.get(firestore.collection("nonexistent").document());
              transaction.update(doc.getReference(), "foo", "bar");
              return null;
            });

    // Let all of the transactions fetch the old value and stop once.
    waitForException(transactionTask);
    assertFalse(transactionTask.isSuccessful());
    Exception e = transactionTask.getException();
    // TODO: should this really be raised as a FirebaseFirestoreException?
    // Note that this test might change if transaction.get throws a FirebaseFirestoreException.
    assertTrue(e instanceof IllegalStateException);
  }

  @Test
  public void testCantDeleteDocumentThenPatch() {
    final FirebaseFirestore firestore = testFirestore();
    final DocumentReference docRef = firestore.collection("docs").document();
    waitFor(docRef.set(map("foo", "bar")));

    // Make a transaction that will fail
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              DocumentSnapshot doc = transaction.get(docRef);
              assertTrue(doc.exists());
              transaction.delete(docRef);
              // Since we deleted the doc, the update will fail
              transaction.update(docRef, "foo", "bar");
              return null;
            });

    // Let all of the transactions fetch the old value and stop once.
    waitForException(transactionTask);
    assertFalse(transactionTask.isSuccessful());
    Exception e = transactionTask.getException();
    // TODO: should this really be raised as a FirebaseFirestoreException?
    // Note that this test might change if transaction.update throws a FirebaseFirestoreException.
    assertTrue(e instanceof IllegalStateException);
  }

  @Test
  public void testCantDeleteDocumentThenSet() {
    final FirebaseFirestore firestore = testFirestore();
    final DocumentReference docRef = firestore.collection("docs").document();
    waitFor(docRef.set(map("foo", "bar")));

    // Make a transaction that will fail
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              DocumentSnapshot doc = transaction.get(docRef);
              assertTrue(doc.exists());
              transaction.delete(docRef);
              // TODO: In theory this should work, but it's complex to make it work, so
              // instead we just let the transaction fail and verify it's unsupported for now
              transaction.set(docRef, map("foo", "new-bar"));
              return null;
            });

    // Let all of the transactions fetch the old value and stop once.
    waitForException(transactionTask);
    assertFalse(transactionTask.isSuccessful());
    Exception e = transactionTask.getException();
    assertTrue(e instanceof FirebaseFirestoreException);
    assertEquals(
        FirebaseFirestoreException.Code.ABORTED, ((FirebaseFirestoreException) e).getCode());
  }

  @Test
  public void testTransactionRaisesErrorsForInvalidUpdates() {
    final FirebaseFirestore firestore = testFirestore();

    // Make a transaction that will fail server-side.
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              // Try to read / write a document with an invalid path.
              DocumentSnapshot doc =
                  transaction.get(firestore.collection("nonexistent").document("__badpath__"));
              transaction.set(doc.getReference(), map("foo", "value"));
              return null;
            });

    // Let all of the transactions fetch the old value and stop once.
    waitForException(transactionTask);
    assertFalse(transactionTask.isSuccessful());
    Exception e = transactionTask.getException();
    assertNotNull(e);
    assertTrue(e instanceof FirebaseFirestoreException);
    FirebaseFirestoreException firestoreException = (FirebaseFirestoreException) e;
    assertEquals(Code.INVALID_ARGUMENT, firestoreException.getCode());
  }

  @Test
  public void testUpdateTransactionally() {
    // A set of concurrent transactions.
    ArrayList<Task<Void>> transactionTasks = new ArrayList<>();
    ArrayList<Task<Void>> readTasks = new ArrayList<>();
    // A barrier to make sure every transaction reaches the same spot.
    TaskCompletionSource<Void> barrier = new TaskCompletionSource<>();
    AtomicInteger started = new AtomicInteger(0);

    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("counters").document();
    waitFor(doc.set(map("count", 5.0, "other", "yes")));

    // Make 3 transactions that will all increment.
    for (int i = 0; i < 3; i++) {
      TaskCompletionSource<Void> resolveRead = new TaskCompletionSource<>();
      readTasks.add(resolveRead.getTask());
      transactionTasks.add(
          firestore.runTransaction(
              transaction -> {
                DocumentSnapshot snapshot = transaction.get(doc);
                assertNotNull(snapshot);
                started.incrementAndGet();
                resolveRead.trySetResult(null);
                waitFor(barrier.getTask());
                transaction.update(doc, map("count", snapshot.getDouble("count") + 1.0));
                return null;
              }));
    }

    // Let all of the transactions fetch the old value and stop once.
    waitFor(Tasks.whenAll(readTasks));
    assertEquals(3, started.intValue());
    // Let all of the transactions continue and wait for them to finish.
    barrier.setResult(null);
    waitFor(Tasks.whenAll(transactionTasks));
    // Now all transaction should be completed, so check the result.
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(8, snapshot.getDouble("count").intValue());
    assertEquals("yes", snapshot.getString("other"));
  }

  @Test
  public void testUpdateFieldsWithDotsTransactionally() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("fieldnames").document();
    waitFor(doc.set(map("a.b", "old", "c.d", "old")));

    waitFor(
        firestore.runTransaction(
            transaction -> {
              transaction.update(doc, FieldPath.of("a.b"), "new");
              transaction.update(doc, FieldPath.of("c.d"), "new");
              return null;
            }));

    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("a.b", "new", "c.d", "new"), snapshot.getData());
  }

  @Test
  public void testUpdateNestedFieldsTransactionally() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("fieldnames").document();
    waitFor(doc.set(map("a", map("b", "old"), "c", map("d", "old"))));

    waitFor(
        firestore.runTransaction(
            transaction -> {
              transaction.update(doc, "a.b", "new");
              transaction.update(doc, map("c.d", "new"));
              return null;
            }));

    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("a", map("b", "new"), "c", map("d", "new")), snapshot.getData());
  }

  private static class POJO {
    public POJO(double count, String modified, String untouched) {
      this.count = count;
      this.modified = modified;
      this.untouched = untouched;
    }

    public final double count;
    public final String modified;
    public final String untouched;
  }

  @Test
  public void testUpdatePOJOTransactionally() {
    // A set of concurrent transactions.
    ArrayList<Task<Void>> transactionTasks = new ArrayList<>();
    ArrayList<Task<Void>> readTasks = new ArrayList<>();
    // A barrier to make sure every transaction reaches the same spot.
    TaskCompletionSource<Void> barrier = new TaskCompletionSource<>();
    AtomicInteger started = new AtomicInteger(0);

    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("counters").document();
    waitFor(doc.set(new POJO(5.0, "no", "clean")));

    // Make 3 transactions that will all increment.
    for (int i = 0; i < 3; i++) {
      TaskCompletionSource<Void> resolveRead = new TaskCompletionSource<>();
      readTasks.add(resolveRead.getTask());
      transactionTasks.add(
          firestore.runTransaction(
              transaction -> {
                DocumentSnapshot snapshot = transaction.get(doc);
                assertNotNull(snapshot);
                started.incrementAndGet();
                resolveRead.trySetResult(null);
                waitFor(barrier.getTask());
                double newCount = snapshot.getDouble("count") + 1.0;
                transaction.set(
                    doc, new POJO(newCount, "maybe", "dirty"), SetOptions.mergeFields("count"));
                transaction.update(doc, "modified", "yes");
                return null;
              }));
    }

    // Let all of the transactions fetch the old value and stop once.
    waitFor(Tasks.whenAll(readTasks));
    assertEquals(3, started.intValue());
    // Let all of the transactions continue and wait for them to finish.
    barrier.setResult(null);
    waitFor(Tasks.whenAll(transactionTasks));
    // Now all transaction should be completed, so check the result.
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(8, snapshot.getDouble("count").intValue());
    assertEquals("yes", snapshot.getString("modified"));
    assertEquals("clean", snapshot.getString("untouched"));
  }

  @Test
  public void testHandleReadingOneDocAndWritingAnother() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc1 = firestore.collection("counters").document();
    DocumentReference doc2 = firestore.collection("counters").document();
    CountDownLatch latch = new CountDownLatch(2);
    waitFor(doc1.set(map("count", 15)));
    Exception e =
        waitForException(
            firestore.runTransaction(
                transaction -> {
                  latch.countDown();
                  // Get the first doc.
                  transaction.get(doc1);
                  // Do a write outside of the transaction. The first time the
                  // transaction is tried, this will bump the version, which
                  // will cause the write to doc2 to fail. The second time, it
                  // will be a no-op and not bump the version.
                  waitFor(doc1.set(map("count", 1234)));
                  // Now try to update the other doc from within the transaction.
                  // This should fail once, because we read 15 earlier.
                  transaction.set(doc2, map("count", 16));
                  return null;
                }));
    // We currently require every document read to also be written.
    // TODO: Add this check back once we drop that.
    // Document snapshot = waitFor(doc1.get());
    // assertEquals(0, tries.getCount());
    // assertEquals(1234, snapshot.getDouble("count"));
    // snapshot = waitFor(doc2.get());
    // assertEquals(16, snapshot.getDouble("count"));
    assertEquals("Transaction failed all retries.", e.getMessage());
    assertEquals(
        "Every document read in a transaction must also be written.", e.getCause().getMessage());
  }

  @Test
  public void testReadingADocTwiceWithDifferentVersions() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("counters").document();
    waitFor(doc.set(map("count", 15.0)));
    waitForException(
        firestore.runTransaction(
            transaction -> {
              // Get the doc once.
              DocumentSnapshot snapshot1 = transaction.get(doc);
              assertEquals(15, snapshot1.getDouble("count").intValue());
              // Do a write outside of the transaction.
              waitFor(doc.set(map("count", 1234.0)));
              // Get the doc again in the transaction with the new version.
              DocumentSnapshot snapshot2 = transaction.get(doc);
              assertEquals(1234, snapshot2.getDouble("count").intValue());
              // Now try to update the doc from within the transaction.
              // This should fail, because we read 15 earlier.
              transaction.set(doc, map("count", 16.0));
              return null;
            }));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(1234, snapshot.getDouble("count").intValue());
  }

  @Test
  public void testCannotReadAfterWriting() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("anything").document();
    Exception e =
        waitForException(
            firestore.runTransaction(
                transaction -> {
                  transaction.set(doc, map("foo", "bar"));
                  return transaction.get(doc);
                }));
    assertNotNull(e);
  }

  @Test
  public void testCannotHaveAGetWithoutMutations() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("foo").document();
    waitFor(doc.set(map("foo", "bar")));
    Exception e = waitForException(firestore.runTransaction(transaction -> transaction.get(doc)));
    // We currently require every document read to also be written.
    // TODO: Add this check back once we drop that.
    // assertEquals("bar", snapshot.getString("foo"));
    assertEquals("Transaction failed all retries.", e.getMessage());
    assertEquals(
        "Every document read in a transaction must also be written.", e.getCause().getMessage());
  }

  @Test
  public void testSuccessWithNoTransactionOperations() {
    FirebaseFirestore firestore = testFirestore();
    waitFor(firestore.runTransaction(transaction -> null));
  }

  @Test
  public void testCancellationOnThrow() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("towns").document();
    AtomicInteger count = new AtomicInteger(0);
    Exception e =
        waitForException(
            firestore.runTransaction(
                transaction -> {
                  count.incrementAndGet();
                  transaction.set(doc, map("foo", "bar"));
                  throw new RuntimeException("no");
                }));
    assertEquals("no", e.getMessage());
    assertEquals(1, count.intValue());
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertFalse(snapshot.exists());
  }
}
