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
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.firebase.firestore.FirebaseFirestoreException.Code;
import com.google.firebase.firestore.testutil.IntegrationTestUtil;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class TransactionTest {
  interface TransactionStage {
    void runStage(Transaction transaction, DocumentReference docRef)
        throws FirebaseFirestoreException;
  }

  private static TransactionStage delete1 = Transaction::delete;

  private static TransactionStage update1 =
      (Transaction transaction, DocumentReference docRef) ->
          transaction.update(docRef, map("foo", "bar1"));

  private static TransactionStage update2 =
      (Transaction transaction, DocumentReference docRef) ->
          transaction.update(docRef, map("foo", "bar2"));

  private static TransactionStage set1 =
      (Transaction transaction, DocumentReference docRef) ->
          transaction.set(docRef, map("foo", "bar1"));

  private static TransactionStage set2 =
      (Transaction transaction, DocumentReference docRef) ->
          transaction.set(docRef, map("foo", "bar2"));

  private static TransactionStage get = Transaction::get;

  private enum FromDocumentType {
    // The operation will be performed on a document that exists.
    EXISTING,
    // The operation will be performed on a document that has never existed.
    NON_EXISTENT,
    // The operation will be performed on a document that existed, but was deleted.
    DELETED,
  }

  /**
   * Used for testing that all possible combinations of executing transactions result in the desired
   * document value or error.
   *
   * <p>`run()`, `withExistingDoc()`, `withNonexistentDoc()` and `withDeletedDoc()` don't actually
   * do anything except assign variables into the TransactionTester.
   *
   * <p>`expectDoc()`, `expectNoDoc()`, and `expectError()` will trigger the transaction to run and
   * assert that the end result matches the input.
   */
  private static class TransactionTester {
    private FirebaseFirestore db;
    private DocumentReference docRef;
    private FromDocumentType fromDocumentType = FromDocumentType.NON_EXISTENT;
    private List<TransactionStage> stages = new ArrayList<>();

    TransactionTester(FirebaseFirestore inputDb) {
      db = inputDb;
    }

    @CanIgnoreReturnValue
    public TransactionTester withExistingDoc() {
      fromDocumentType = FromDocumentType.EXISTING;
      return this;
    }

    @CanIgnoreReturnValue
    public TransactionTester withNonexistentDoc() {
      fromDocumentType = FromDocumentType.NON_EXISTENT;
      return this;
    }

    @CanIgnoreReturnValue
    public TransactionTester withDeletedDoc() {
      fromDocumentType = FromDocumentType.DELETED;
      return this;
    }

    @CanIgnoreReturnValue
    public TransactionTester run(TransactionStage... inputStages) {
      stages = Arrays.asList(inputStages);
      return this;
    }

    public void expectDoc(Object expected) {
      try {
        prepareDoc();
        waitFor(runTransaction());
        DocumentSnapshot snapshot = waitFor(docRef.get());
        assertTrue(snapshot.exists());
        assertEquals(expected, snapshot.getData());
      } catch (Exception e) {
        fail(
            "Expected the sequence ("
                + listStages(stages)
                + ") to succeed, but got "
                + e.toString());
      }
      cleanupTester();
    }

    private void expectNoDoc() {
      try {
        prepareDoc();
        waitFor(runTransaction());
        DocumentSnapshot snapshot = waitFor(docRef.get());
        assertFalse(snapshot.exists());
      } catch (Exception e) {
        fail(
            "Expected the sequence ("
                + listStages(stages)
                + ") to succeed, but got "
                + e.toString());
      }
      cleanupTester();
    }

    private void expectError(Code expected) {
      prepareDoc();
      Task<Void> transactionTask = runTransaction();
      try {
        waitForException(transactionTask);
      } catch (Exception e) {
        throw new AssertionError(
            "Expected the sequence ("
                + listStages(stages)
                + ") to fail with the error "
                + expected.toString());
      }
      assertFalse(transactionTask.isSuccessful());
      Exception e = transactionTask.getException();
      assertEquals(expected, ((FirebaseFirestoreException) e).getCode());
      cleanupTester();
    }

    private void prepareDoc() {
      docRef = db.collection("tester-docref").document();

      switch (fromDocumentType) {
        case EXISTING:
          waitFor(docRef.set(map("foo", "bar0")));
          break;
        case NON_EXISTENT:
          // Nothing to do; document does not exist.
          break;
        case DELETED:
          waitFor(docRef.set(map("foo", "bar0")));
          waitFor(docRef.delete());
          break;
        default:
          throw new RuntimeException("invalid fromDocumentType: " + fromDocumentType);
      }
    }

    private Task<Void> runTransaction() {
      return db.runTransaction(
          transaction -> {
            for (TransactionStage stage : stages) {
              stage.runStage(transaction, docRef);
            }
            return null;
          });
    }

    private void cleanupTester() {
      stages = new ArrayList<>();
      // Set the docRef to something else to lose the original reference.
      docRef = db.collection("reset").document();
    }

    private static String listStages(List<TransactionStage> stages) {
      List<String> seqList = new ArrayList<>();
      for (TransactionStage stage : stages) {
        if (stage == delete1) {
          seqList.add("delete");
        } else if (stage == update1 || stage == update2) {
          seqList.add("update");
        } else if (stage == set1 || stage == set2) {
          seqList.add("set");
        } else if (stage == get) {
          seqList.add("get");
        } else {
          throw new IllegalArgumentException("Stage not recognized");
        }
      }
      return seqList.toString();
    }
  }

  @After
  public void tearDown() {
    IntegrationTestUtil.tearDown();
  }

  @Test
  public void testRunsTransactionsAfterGettingExistingDoc() {
    FirebaseFirestore firestore = testFirestore();
    TransactionTester tt = new TransactionTester(firestore);

    tt.withExistingDoc().run(get, delete1, delete1).expectNoDoc();
    tt.withExistingDoc().run(get, delete1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withExistingDoc().run(get, delete1, set2).expectDoc(map("foo", "bar2"));

    tt.withExistingDoc().run(get, update1, delete1).expectNoDoc();
    tt.withExistingDoc().run(get, update1, update2).expectDoc(map("foo", "bar2"));
    tt.withExistingDoc().run(get, update1, set2).expectDoc(map("foo", "bar2"));

    tt.withExistingDoc().run(get, set1, delete1).expectNoDoc();
    tt.withExistingDoc().run(get, set1, update2).expectDoc(map("foo", "bar2"));
    tt.withExistingDoc().run(get, set1, set2).expectDoc(map("foo", "bar2"));
  }

  @Test
  public void testRunsTransactionsAfterGettingNonexistentDoc() {
    FirebaseFirestore firestore = testFirestore();
    TransactionTester tt = new TransactionTester(firestore);

    tt.withNonexistentDoc().run(get, delete1, delete1).expectNoDoc();
    tt.withNonexistentDoc().run(get, delete1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withNonexistentDoc().run(get, delete1, set2).expectDoc(map("foo", "bar2"));

    tt.withNonexistentDoc().run(get, update1, delete1).expectError(Code.INVALID_ARGUMENT);
    tt.withNonexistentDoc().run(get, update1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withNonexistentDoc().run(get, update1, set2).expectError(Code.INVALID_ARGUMENT);

    tt.withNonexistentDoc().run(get, set1, delete1).expectNoDoc();
    tt.withNonexistentDoc().run(get, set1, update2).expectDoc(map("foo", "bar2"));
    tt.withNonexistentDoc().run(get, set1, set2).expectDoc(map("foo", "bar2"));
  }

  // This test is identical to the test above, except that withNonexistentDoc()
  // is replaced by withDeletedDoc(), to guard against regression of
  // https://github.com/firebase/firebase-js-sdk/issues/5871, where transactions
  // would incorrectly fail with FAILED_PRECONDITION when operations were
  // performed on a deleted document (rather than a non-existent document).
  @Test
  public void testRunsTransactionsAfterGettingDeletedDoc() {
    FirebaseFirestore firestore = testFirestore();
    TransactionTester tt = new TransactionTester(firestore);

    tt.withDeletedDoc().run(get, delete1, delete1).expectNoDoc();
    tt.withDeletedDoc().run(get, delete1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withDeletedDoc().run(get, delete1, set2).expectDoc(map("foo", "bar2"));

    tt.withDeletedDoc().run(get, update1, delete1).expectError(Code.INVALID_ARGUMENT);
    tt.withDeletedDoc().run(get, update1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withDeletedDoc().run(get, update1, set2).expectError(Code.INVALID_ARGUMENT);

    tt.withDeletedDoc().run(get, set1, delete1).expectNoDoc();
    tt.withDeletedDoc().run(get, set1, update2).expectDoc(map("foo", "bar2"));
    tt.withDeletedDoc().run(get, set1, set2).expectDoc(map("foo", "bar2"));
  }

  @Test
  public void testRunsTransactionsOnExistingDoc() {
    FirebaseFirestore firestore = testFirestore();
    TransactionTester tt = new TransactionTester(firestore);

    tt.withExistingDoc().run(delete1, delete1).expectNoDoc();
    tt.withExistingDoc().run(delete1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withExistingDoc().run(delete1, set2).expectDoc(map("foo", "bar2"));

    tt.withExistingDoc().run(update1, delete1).expectNoDoc();
    tt.withExistingDoc().run(update1, update2).expectDoc(map("foo", "bar2"));
    tt.withExistingDoc().run(update1, set2).expectDoc(map("foo", "bar2"));

    tt.withExistingDoc().run(set1, delete1).expectNoDoc();
    tt.withExistingDoc().run(set1, update2).expectDoc(map("foo", "bar2"));
    tt.withExistingDoc().run(set1, set2).expectDoc(map("foo", "bar2"));
  }

  @Test
  public void testRunsTransactionsOnNonexistentDoc() {
    FirebaseFirestore firestore = testFirestore();
    TransactionTester tt = new TransactionTester(firestore);

    tt.withNonexistentDoc().run(delete1, delete1).expectNoDoc();
    tt.withNonexistentDoc().run(delete1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withNonexistentDoc().run(delete1, set2).expectDoc(map("foo", "bar2"));

    tt.withNonexistentDoc().run(update1, delete1).expectError(Code.NOT_FOUND);
    tt.withNonexistentDoc().run(update1, update2).expectError(Code.NOT_FOUND);
    tt.withNonexistentDoc().run(update1, set2).expectError(Code.NOT_FOUND);

    tt.withNonexistentDoc().run(set1, delete1).expectNoDoc();
    tt.withNonexistentDoc().run(set1, update2).expectDoc(map("foo", "bar2"));
    tt.withNonexistentDoc().run(set1, set2).expectDoc(map("foo", "bar2"));
  }

  @Test
  public void testRunsTransactionsOnDeletedDoc() {
    FirebaseFirestore firestore = testFirestore();
    TransactionTester tt = new TransactionTester(firestore);

    tt.withDeletedDoc().run(delete1, delete1).expectNoDoc();
    tt.withDeletedDoc().run(delete1, update2).expectError(Code.INVALID_ARGUMENT);
    tt.withDeletedDoc().run(delete1, set2).expectDoc(map("foo", "bar2"));

    tt.withDeletedDoc().run(update1, delete1).expectError(Code.NOT_FOUND);
    tt.withDeletedDoc().run(update1, update2).expectError(Code.NOT_FOUND);
    tt.withDeletedDoc().run(update1, set2).expectError(Code.NOT_FOUND);

    tt.withDeletedDoc().run(set1, delete1).expectNoDoc();
    tt.withDeletedDoc().run(set1, update2).expectDoc(map("foo", "bar2"));
    tt.withDeletedDoc().run(set1, set2).expectDoc(map("foo", "bar2"));
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
    firestore.getAsyncQueue().skipDelaysForTimerId(TimerId.RETRY_TRANSACTION);
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
    AtomicInteger counter = new AtomicInteger(0);

    FirebaseFirestore firestore = testFirestore();
    firestore.getAsyncQueue().skipDelaysForTimerId(TimerId.RETRY_TRANSACTION);
    DocumentReference doc = firestore.collection("counters").document();
    waitFor(doc.set(map("count", 5.0, "other", "yes")));

    // Make 3 transactions that will all increment.
    for (int i = 0; i < 3; i++) {
      TaskCompletionSource<Void> resolveRead = new TaskCompletionSource<>();
      readTasks.add(resolveRead.getTask());
      transactionTasks.add(
          firestore.runTransaction(
              transaction -> {
                counter.incrementAndGet();
                DocumentSnapshot snapshot = transaction.get(doc);
                assertNotNull(snapshot);
                resolveRead.trySetResult(null);
                waitFor(barrier.getTask());
                transaction.update(doc, map("count", snapshot.getDouble("count") + 1.0));
                return null;
              }));
    }

    // Let all of the transactions fetch the old value and stop once.
    waitFor(Tasks.whenAll(readTasks));
    // There should be 3 initial transaction runs.
    assertEquals(3, counter.get());
    barrier.setResult(null);
    waitFor(Tasks.whenAll(transactionTasks));
    // There should be a maximum of 3 retries: once for the 2nd update, and twice for the 3rd update
    assertTrue(counter.get() <= 6);
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
    firestore.getAsyncQueue().skipDelaysForTimerId(TimerId.RETRY_TRANSACTION);
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
  public void testRetriesWhenDocumentThatWasReadWithoutBeingWrittenChanges() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc1 = firestore.collection("counters").document();
    DocumentReference doc2 = firestore.collection("counters").document();
    CountDownLatch latch = new CountDownLatch(2);
    waitFor(doc1.set(map("count", 15)));
    waitFor(
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
    DocumentSnapshot snapshot = waitFor(doc1.get());
    assertEquals(0, latch.getCount());
    assertTrue(snapshot.exists());
    assertEquals(1234L, snapshot.getData().get("count"));
  }

  @Test
  public void testReadingADocTwiceWithDifferentVersions() {
    FirebaseFirestore firestore = testFirestore();
    firestore.getAsyncQueue().skipDelaysForTimerId(TimerId.RETRY_TRANSACTION);
    DocumentReference doc = firestore.collection("counters").document();
    waitFor(doc.set(map("count", 15.0)));
    AtomicInteger counter = new AtomicInteger(0);
    Exception e =
        waitForException(
            firestore.runTransaction(
                transaction -> {
                  counter.incrementAndGet();
                  // Get the doc once.
                  transaction.get(doc);
                  // Do a write outside of the transaction. Because the transaction will retry, set
                  // the document to a different value each time.
                  waitFor(doc.set(map("count", 1234.0 + counter.get())));
                  // Get the doc again in the transaction with the new version.
                  transaction.get(doc);
                  // The get itself will fail, because we already read an earlier version of this
                  // document.
                  fail("Should have thrown exception");
                  return null;
                }));
    assertEquals(Code.ABORTED, ((FirebaseFirestoreException) e).getCode());
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
  public void testReadAndUpdateNonExistentDocumentWithExternalWrite() {
    FirebaseFirestore firestore = testFirestore();

    // Make a transaction that will fail
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              // Get and update a document that doesn't exist so that the transaction fails.
              DocumentReference doc = firestore.collection("nonexistent").document();
              transaction.get(doc);
              // Do a write outside of the transaction.
              doc.set(map("count", 1234));
              // Now try to update the other doc from within the transaction.
              // This should fail, because the document didn't exist at the
              // start of the transaction.
              transaction.update(doc, "count", 16);
              return null;
            });

    waitForException(transactionTask);
    assertFalse(transactionTask.isSuccessful());
    Exception e = transactionTask.getException();
    assertEquals(Code.INVALID_ARGUMENT, ((FirebaseFirestoreException) e).getCode());
    assertEquals("Can't update a document that doesn't exist.", e.getMessage());
  }

  @Test
  public void testCanHaveGetsWithoutMutations() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("foo").document();
    DocumentReference doc2 = firestore.collection("foo").document();
    waitFor(doc.set(map("foo", "bar")));
    waitFor(
        firestore.runTransaction(
            transaction -> {
              transaction.get(doc2);
              return transaction.get(doc);
            }));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertTrue(snapshot.exists());
    assertEquals(map("foo", "bar"), snapshot.getData());
  }

  @Test
  public void testDoesNotRetryOnPermanentError() {
    final FirebaseFirestore firestore = testFirestore();
    AtomicInteger count = new AtomicInteger(0);
    // Make a transaction that should fail with a permanent error
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              count.incrementAndGet();
              // Get and update a document that doesn't exist so that the transaction fails
              DocumentSnapshot doc =
                  transaction.get(firestore.collection("nonexistent").document());
              transaction.update(doc.getReference(), "foo", "bar");
              return null;
            });

    // Let all of the transactions fetch the old value and stop once.
    Exception e = waitForException(transactionTask);
    assertEquals(Code.INVALID_ARGUMENT, ((FirebaseFirestoreException) e).getCode());
    assertEquals(1, count.get());
  }

  @Test
  public void testRetryOnAlreadyExistsError() {
    final FirebaseFirestore firestore = testFirestore();
    DocumentReference doc = firestore.collection("foo").document();
    AtomicInteger transactionCallbackCount = new AtomicInteger(0);
    waitFor(
        firestore.runTransaction(
            transaction -> {
              int currentCount = transactionCallbackCount.incrementAndGet();
              transaction.get(doc);
              // Do a write outside of the transaction.
              if (currentCount == 1) waitFor(doc.set(map("foo1", "bar1")));
              // Now try to set the doc within the transaction. This should fail once
              // with ALREADY_EXISTS error.
              transaction.set(doc, map("foo2", "bar2"));
              return null;
            }));
    DocumentSnapshot snapshot = waitFor(doc.get());
    assertEquals(2, transactionCallbackCount.get());
    assertTrue(snapshot.exists());
    assertEquals(map("foo2", "bar2"), snapshot.getData());
  }

  @Test
  public void testMakesDefaultMaxAttempts() {
    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc1 = firestore.collection("counters").document();
    AtomicInteger count = new AtomicInteger(0);
    waitFor(doc1.set(map("count", 15)));
    Task<Void> transactionTask =
        firestore.runTransaction(
            transaction -> {
              // Get the first doc.
              transaction.get(doc1);
              // Do a write outside of the transaction to cause the transaction to fail.
              waitFor(doc1.set(map("count", 1234 + count.incrementAndGet())));
              return null;
            });

    Exception e = waitForException(transactionTask);
    assertEquals(Code.FAILED_PRECONDITION, ((FirebaseFirestoreException) e).getCode());
    assertEquals(TransactionOptions.DEFAULT_MAX_ATTEMPTS_COUNT, count.get());
  }

  @Test
  public void testMakesOptionSpecifiedMaxAttempts() {
    TransactionOptions options = new TransactionOptions.Builder().setMaxAttempts(1).build();

    FirebaseFirestore firestore = testFirestore();
    DocumentReference doc1 = firestore.collection("counters").document();
    AtomicInteger count = new AtomicInteger(0);
    waitFor(doc1.set(map("count", 15)));
    Task<Void> transactionTask =
        firestore.runTransaction(
            options,
            transaction -> {
              // Get the first doc.
              transaction.get(doc1);
              // Do a write outside of the transaction to cause the transaction to fail.
              waitFor(doc1.set(map("count", 1234 + count.incrementAndGet())));
              return null;
            });

    Exception e = waitForException(transactionTask);
    assertEquals(Code.FAILED_PRECONDITION, ((FirebaseFirestoreException) e).getCode());
    assertEquals(options.getMaxAttempts(), count.get());
  }

  @Test
  public void testTransactionOptionsZeroMaxAttempts_shouldThrowIllegalArgumentException() {
    assertThrows(
        IllegalArgumentException.class,
        () -> new TransactionOptions.Builder().setMaxAttempts(0).build());
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
