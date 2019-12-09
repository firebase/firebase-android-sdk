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

package com.google.firebase.database;

import static com.google.firebase.database.IntegrationTestHelpers.fromSingleQuotedString;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.DatabaseReference.CompletionListener;
import com.google.firebase.database.core.AuthTokenProvider;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
@SuppressWarnings("FutureReturnValueIgnored")
public class TransactionTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void newValueIsImmediatelyVisible()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.child("foo")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                try {
                  currentData.setValue(42);
                } catch (DatabaseException e) {
                  throw new AssertionError("Should not fail", e);
                }
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                if (error != null || !committed) {
                  fail("Transaction should succeed");
                }
              }
            });

    DataSnapshot snap = new ReadFuture(ref.child("foo")).timedGet().get(0).getSnapshot();

    assertEquals(42L, snap.getValue());
  }

  @Test
  public void eventIsRaisedForNewValue() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper = new EventHelper().addValueExpectation(ref).startListening();

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            try {
              currentData.setValue(42);
            } catch (DatabaseException e) {
              throw new AssertionError("Should not fail", e);
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            // No-op
          }
        });

    assertTrue(helper.waitForEvents());
  }

  @Test
  public void nonAbortedTransactionSetsCommittedToTrueInCallback()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            try {
              currentData.setValue(42);
            } catch (DatabaseException e) {
              throw new AssertionError("Should not fail", e);
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            if (error != null || !committed) {
              fail("Transaction should succeed");
            } else {
              assertEquals(42L, currentData.getValue());
              semaphore.release(1);
            }
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void abortedTransactionSetsCommittedToFalseInCallback()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            return Transaction.abort();
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertFalse(committed);
            assertNull(currentData.getValue());
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void setDataReconnectDoTransactionThatAbortsVerifyCorrectEvents()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);

    new WriteFuture(refs.get(0), 42).timedGet();

    DatabaseReference node = refs.get(1);

    // Go offline to ensure our listen doesn't complete before the transaction runs.
    node.getDatabase().goOffline();

    final AtomicInteger count = new AtomicInteger(0);
    ReadFuture readFuture =
        new ReadFuture(
            node,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                Object latestValue = events.get(events.size() - 1).getSnapshot().getValue();
                if (events.size() == 1) {
                  assertEquals("temp value", latestValue);
                } else if (events.size() == 2) {
                  assertEquals(42L, latestValue);
                } else {
                  fail("An extra event was detected");
                }
                Object val = events.get(events.size() - 1).getSnapshot().getValue();
                if (val != null) {
                  return count.incrementAndGet() == 2;
                }
                return false;
              }
            });

    node.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            if (currentData.getValue() == null) {
              try {
                currentData.setValue("temp value");
              } catch (DatabaseException e) {
                fail("Exception thrown: " + e.toString());
              }
              return Transaction.success(currentData);
            } else {
              return Transaction.abort();
            }
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertFalse(committed);
            assertEquals(42L, currentData.getValue());
          }
        });

    node.getDatabase().goOnline();

    List<EventRecord> events = readFuture.timedGet();
    Object result = events.get(0).getSnapshot().getValue();
    assertEquals("temp value", result);
    result = events.get(1).getSnapshot().getValue();
    assertEquals(42L, result);
  }

  @Test
  public void useTransactionToCreateANodeMakeSureOneEventReceived()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final AtomicInteger events = new AtomicInteger(0);
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // ignore initial null from the server if we get it.
            if (!(events.get() == 0 && snapshot.getValue() == null)) {
              events.incrementAndGet();
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    final Semaphore semaphore = new Semaphore(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            try {
              currentData.setValue(42);
            } catch (DatabaseException e) {
              throw new AssertionError("Should not fail", e);
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            if (error != null || !committed) {
              fail("Transaction should succeed");
            } else {
              assertEquals(42L, currentData.getValue());
              semaphore.release(1);
            }
          }
        });
    IntegrationTestHelpers.waitFor(semaphore);
    assertEquals(1, events.get());
  }

  @Test
  public void useTransactionToUpdateOneOfTwoExistingChildNodes()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(reader.child("a"))
            .addValueExpectation(reader.child("b"))
            .startListening(true);

    writer.child("a").setValue(42);
    new WriteFuture(writer.child("b"), 42).timedGet();

    assertTrue(helper.waitForEvents());

    helper.addValueExpectation(reader.child("b")).startListening();

    final Semaphore semaphore = new Semaphore(0);
    reader.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            try {
              currentData.child("a").setValue(42);
              currentData.child("b").setValue(87);
              return Transaction.success(currentData);
            } catch (DatabaseException e) {
              fail("Should not throw");
              return Transaction.abort();
            }
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            Map expected = new MapBuilder().put("a", 42L).put("b", 87L).build();
            assertEquals(expected, currentData.getValue());
            semaphore.release(1);
          }
        });

    assertTrue(helper.waitForEvents());
    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void transactionIsOnlyCalledOnceWhenInitializingAnEmptyNode()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final AtomicInteger called = new AtomicInteger(0);
    final Semaphore semaphore = new Semaphore(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            called.incrementAndGet();
            assertNull(currentData.getValue());
            try {
              currentData.child("a").setValue(5);
              currentData.child("b").setValue(6);
              return Transaction.success(currentData);
            } catch (DatabaseException e) {
              fail("Should not throw");
              return Transaction.abort();
            }
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
    assertEquals(1, called.get());
  }

  @Test
  public void secondTransactionGetsRunImmediatelyOnPreviousOutputAndOnlyRunsOnce()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref = refs.get(0);

    final AtomicBoolean firstRun = new AtomicBoolean(false);
    final Semaphore first = new Semaphore(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            assertTrue(firstRun.compareAndSet(false, true));
            try {
              currentData.setValue(42);
              return Transaction.success(currentData);
            } catch (DatabaseException e) {
              fail("Should not throw");
              return Transaction.abort();
            }
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertTrue(committed);
            first.release(1);
          }
        });

    final AtomicBoolean secondRun = new AtomicBoolean(false);
    final Semaphore second = new Semaphore(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            assertTrue(secondRun.compareAndSet(false, true));
            try {
              currentData.setValue(84);
              return Transaction.success(currentData);
            } catch (DatabaseException e) {
              fail("Should not throw");
              return Transaction.abort();
            }
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertTrue(committed);
            second.release(1);
          }
        });
    IntegrationTestHelpers.waitFor(first);
    IntegrationTestHelpers.waitFor(second);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(refs.get(1));
    assertEquals(84L, snap.getValue());
  }

  @Test
  public void setCancelsPendingTransactionsAndRerunsAffectedTransactions()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    // We do 3 transactions: 1) At /foo, 2) At /, and 3) At /bar.
    // Only #1 is sent to the server immediately (since 2 depends on 1 and 3 depends on 2).
    // We set /foo to 0.
    // - Transaction #1 should complete as planned (since it was already sent).
    // - Transaction #2 should be aborted by the set. We keep it from completing by hijacking the
    //   hash
    // - Transaction #3 should be re-run after #2 is reverted, and then be sent to the server and
    //   succeed.

    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    final List<DataSnapshot> nodeSnaps = new ArrayList<>();
    final List<DataSnapshot> nodeFooSnaps = new ArrayList<>();
    final AtomicBoolean firstDone = new AtomicBoolean(false);
    final AtomicBoolean secondDone = new AtomicBoolean(false);
    final AtomicInteger thirdRunCount = new AtomicInteger(0);

    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            nodeSnaps.add(snapshot);
            if (nodeSnaps.size() == 1) {
              // we got the initial data
              semaphore.release(1);
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });
    ref.child("foo")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                nodeFooSnaps.add(snapshot);
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not be cancelled");
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    final AtomicBoolean firstRun = new AtomicBoolean(false);
    ref.child("foo")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                assertTrue(firstRun.compareAndSet(false, true));
                currentData.setValue(42);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot snapshot) {
                assertTrue(committed);
                assertEquals(42L, snapshot.getValue());
                firstDone.set(true);
              }
            });

    final AtomicBoolean secondRun = new AtomicBoolean(false);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            ref.getRepo().setHijackHash(true);
            assertTrue(secondRun.compareAndSet(false, true));
            currentData.child("foo").setValue(84);
            currentData.child("bar").setValue(1);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot snapshot) {
            ref.getRepo().setHijackHash(false);
            assertFalse(committed);
            secondDone.set(true);
          }
        });

    ref.child("bar")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                int count = thirdRunCount.incrementAndGet();
                if (count == 1) {
                  assertEquals(1L, currentData.getValue());
                  currentData.setValue("first");
                  return Transaction.success(currentData);
                } else {
                  // NOTE: This may get hit more than once because the previous transaction
                  // may still be hijacking transaction hashes.
                  assertNull(currentData.getValue());
                  currentData.setValue("second");
                  return Transaction.success(currentData);
                }
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot snapshot) {
                assertEquals(null, error);
                assertTrue(committed);
                assertEquals("second", snapshot.getValue());
                semaphore.release();
              }
            });

    // This rolls back the second transaction, and triggers a re-run of the third.
    // However, a new value event won't be triggered until the listener is complete,
    // so we're left with the last value event
    ref.child("foo").setValue(0);

    IntegrationTestHelpers.waitFor(semaphore);

    assertTrue(firstDone.get());
    assertTrue(secondDone.get());

    // Note that the set actually raises two events, one overlaid on top of the original transaction
    // value, and a second one with the re-run value from the third transaction
    assertEquals(nodeFooSnaps.size(), 4); // three transactions with one redo
  }

  @Test
  public void transactionSetSetShouldWork() throws InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            assertNull(currentData.getValue());
            currentData.setValue("hi!");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            semaphore.release(1);
          }
        });

    ref.setValue("foo");
    ref.setValue("bar");
    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void priorityIsNotPreservedWhenSettingData() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);

    final List<DataSnapshot> snaps = new ArrayList<DataSnapshot>();
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            snaps.add(snapshot);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    ref.setValue("test", 5);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("new value");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    assertEquals(2, snaps.size());
    assertNull(snaps.get(1).getPriority());
  }

  // Note: skipping test with nested transactions

  @Test
  public void resultingSnapshotIsPassedToOnComplete() throws InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    // Add an event listener at this node so we hang on to local state in-between transaction runs
    ref1.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    ref1.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("hello!");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals("hello!", currentData.getValue());
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    // Do it again for the aborted case
    ref1.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            return Transaction.abort();
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertFalse(committed);
            assertEquals("hello!", currentData.getValue());
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    // Now on a fresh connection...
    ref2.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            if (currentData.getValue() == null) {
              currentData.setValue("hello!");
              return Transaction.success(currentData);
            }
            return Transaction.abort();
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertFalse(committed);
            assertEquals("hello!", currentData.getValue());
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void transactionsAbortAfter25Retries() throws InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.setHijackHash(true);
    final AtomicInteger retries = new AtomicInteger(0);
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            int tries = retries.getAndIncrement();
            assertTrue(tries < 25);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNotNull(error);
            assertEquals(DatabaseError.MAX_RETRIES, error.getCode());
            assertFalse(committed);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
    assertEquals(25, retries.get());
    ref.setHijackHash(false);
  }

  @Test
  public void setShouldCancelAlreadySentTransactionsThatComeBackAsDatastale()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, 5).timedGet();

    try {
      ref.getRepo().setHijackHash(true);
      ref.runTransaction(
          new Transaction.Handler() {
            @Override
            public Transaction.Result doTransaction(MutableData currentData) {
              assertNull(currentData.getValue());
              currentData.setValue(72);
              return Transaction.success(currentData);
            }

            @Override
            public void onComplete(
                DatabaseError error, boolean committed, DataSnapshot currentData) {
              assertEquals(DatabaseError.OVERRIDDEN_BY_SET, error.getCode());
              assertFalse(committed);
              semaphore.release(1);
            }
          });
      ref.setValue(
          32,
          new CompletionListener() {
            @Override
            public void onComplete(DatabaseError error, DatabaseReference ref) {
              ref.getRepo().setHijackHash(false);
            }
          });
      IntegrationTestHelpers.waitFor(semaphore);
    } finally {
      ref.getRepo().setHijackHash(false);
    }
  }

  @Test
  public void updateShouldNotCancelUnrelatedTransactions()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore fooTransaction = new Semaphore(0);
    final Semaphore barTransaction = new Semaphore(0);

    new WriteFuture(ref.child("foo"), 5).timedGet();
    ref.setHijackHash(true);

    // This transaction should get cancelled as we update "foo" later on.
    ref.child("foo")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                try {
                  // Sleep to prevent too many retries due to hash hijacking.
                  Thread.sleep(10);
                } catch (InterruptedException ignore) { // NOLINT
                }
                currentData.setValue(72);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                assertEquals(DatabaseError.OVERRIDDEN_BY_SET, error.getCode());
                assertFalse(committed);
                fooTransaction.release();
              }
            });

    // This transaction should not get cancelled since we don't update "bar".
    ref.child("bar")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                try {
                  // Sleep to prevent too many retries due to hash hijacking.
                  Thread.sleep(10);
                } catch (InterruptedException ignore) { // NOLINT
                }
                currentData.setValue(72);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                assertNull(error);
                assertTrue(committed);
                barTransaction.release();
              }
            });

    ref.updateChildren(
        fromSingleQuotedString(
            "{'foo': 'newValue', 'boo': 'newValue', 'loo' : {'doo' : {'boo': 'newValue'}}}"),
        new CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertTrue(
                "Should have gotten cancelled before the update",
                fooTransaction.availablePermits() > 0);
            assertTrue("Should run after the update", barTransaction.availablePermits() == 0);
            ref.setHijackHash(false);
          }
        });

    IntegrationTestHelpers.waitFor(barTransaction);
  }

  @Test
  public void transactionsWorkOnWackyUnicode()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, "♜♞♝♛♚♝♞♜").timedGet();

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            if (currentData.getValue() != null) {
              assertEquals("♜♞♝♛♚♝♞♜", currentData.getValue());
            }
            currentData.setValue("♖♘♗♕♔♗♘♖");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals("♖♘♗♕♔♗♘♖", currentData.getValue());
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void immediatelyAbortingATransactionWorks() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            return Transaction.abort();
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertFalse(committed);
            semaphore.release(1);
          }
        });
    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void addToAnArrayWithATransaction()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, Arrays.asList("cat", "horse")).timedGet();
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            Object val = currentData.getValue();
            if (val != null) {
              @SuppressWarnings("unchecked")
              List<String> update = (List<String>) val;
              update.add("dog");
              currentData.setValue(update);
            } else {
              currentData.setValue(Arrays.asList("dog"));
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            ArrayList<String> expected = new ArrayList<String>(3);
            expected.addAll(Arrays.asList("cat", "horse", "dog"));
            Object result = currentData.getValue();
            DeepEquals.assertEquals(expected, result);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void mergedTransactionsHaveCorrectSnapshotInOnComplete()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final String nodeName = ref.getKey();
    new WriteFuture(ref, new MapBuilder().put("a", 0).build()).timedGet();

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            Object val = currentData.getValue();
            if (val != null) {
              Map<String, Object> expected = new MapBuilder().put("a", 0L).build();
              DeepEquals.assertEquals(expected, val);
            }
            currentData.setValue(new MapBuilder().put("a", 1L).build());
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertTrue(committed);
            assertEquals(nodeName, currentData.getKey());
            Object val = currentData.getValue();
            // Per new behavior, will include the accepted value of the transaction, if it was
            // successful.
            Map<String, Object> expected = new MapBuilder().put("a", 1L).build();
            DeepEquals.assertEquals(expected, val);
            semaphore.release(1);
          }
        });

    ref.child("a")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                Object val = currentData.getValue();
                if (val != null) {
                  assertEquals(1L, val);
                }
                currentData.setValue(2);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                assertTrue(committed);
                assertEquals("a", currentData.getKey());
                assertEquals(2L, currentData.getValue());
                semaphore.release(1);
              }
            });
    IntegrationTestHelpers.waitFor(semaphore, 2);
  }

  // Note: skipping tests for reentrant API calls

  @Test
  public void pendingTransactionsAreCancelledOnDisconnect()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    new WriteFuture(ref, "initial").timedGet();

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("new");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertFalse(committed);
            assertEquals(DatabaseError.DISCONNECTED, error.getCode());
            semaphore.release(1);
          }
        });

    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);
    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void transactionWithLocalEvents1() throws InterruptedException {
    final Semaphore semaphore = new Semaphore(0);
    final Semaphore completeSemaphore = new Semaphore(0);
    final List<DataSnapshot> results = new ArrayList<DataSnapshot>();
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            results.add(snapshot);
            if (results.size() == 1) {
              semaphore.release(1);
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("hello!");
            semaphore.release(1);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            try {
              IntegrationTestHelpers.waitFor(semaphore);
              assertTrue(committed);
              completeSemaphore.release(1);
            } catch (InterruptedException e) {
              throw new AssertionError("Should not throw", e);
            }
          }
        },
        false);

    IntegrationTestHelpers.waitFor(semaphore);
    assertEquals(1, results.size());
    assertNull(results.get(0).getValue());
    // Let the completion handler run
    semaphore.release(1);
    IntegrationTestHelpers.waitFor(completeSemaphore);

    assertEquals(2, results.size());
    assertEquals("hello!", results.get(1).getValue());
  }

  @Test
  public void transactionWithoutLocalEvents2()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    final DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    final Semaphore done = new Semaphore(0);
    final List<DataSnapshot> events = new ArrayList<DataSnapshot>();
    ref1.setHijackHash(true);
    ref1.setValue(
        0,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            done.release();
          }
        });
    IntegrationTestHelpers.waitFor(done);

    ref1.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            events.add(snapshot);
            if (events.size() == 1) {
              done.release(1);
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    IntegrationTestHelpers.waitFor(done);

    final AtomicInteger retries = new AtomicInteger(0);
    ref1.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            retries.getAndIncrement();
            Object val = currentData.getValue();
            if (val.equals(3L)) {
              // Will take effect the next time
              ref1.setHijackHash(false);
            }
            currentData.setValue("txn result");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals("txn result", currentData.getValue());
            done.release(1);
          }
        },
        false);

    for (int i = 0; i < 4; ++i) {
      new WriteFuture(ref2, i).timedGet();
    }

    IntegrationTestHelpers.waitFor(done);

    assertTrue(retries.get() > 1);
    int size = events.size();
    assertEquals("txn result", events.get(size - 1).getValue());
    // Note: this test doesn't map cleanly, there is some potential for race conditions.
    // Check that end state is what we want
  }

  // NOTE: skipping test for reentrant API calls

  @Test
  public void transactionRunsOnNullOnlyOnceAfterReconnectCase1981()
      throws TestFailure, ExecutionException, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    new WriteFuture(ref, 42).timedGet();

    DatabaseConfig ctx = IntegrationTestHelpers.newTestConfig();
    ctx.setLogLevel(Logger.Level.DEBUG);
    DatabaseReference newRef = new DatabaseReference(ref.toString(), ctx);

    final Semaphore done = new Semaphore(0);
    final AtomicInteger runs = new AtomicInteger(0);
    newRef.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            int run = runs.incrementAndGet();
            if (run == 1) {
              assertNull(currentData.getValue());
            } else if (run == 2) {
              assertEquals(42L, currentData.getValue());
            } else {
              fail("Too many calls");
              return Transaction.abort();
            }
            currentData.setValue(3.14);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals(2, runs.get());
            assertEquals(3.14, currentData.getValue());
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void transactionRespectsPriority() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore done = new Semaphore(0);
    final List<DataSnapshot> values = new ArrayList<DataSnapshot>();
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            values.add(snapshot);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue(5);
            currentData.setPriority(5);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertTrue(committed);
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            assertEquals(5L, currentData.getValue());
            assertEquals(5.0, currentData.getPriority());
            currentData.setValue(10);
            currentData.setPriority(10);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertTrue(committed);
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);

    assertEquals(5L, values.get(values.size() - 2).getValue());
    assertEquals(5.0, values.get(values.size() - 2).getPriority());
    assertEquals(10L, values.get(values.size() - 1).getValue());
    assertEquals(10.0, values.get(values.size() - 1).getPriority());
  }

  @Test
  public void transactionRevertsDataWhenAddADeeperListen() throws InterruptedException {
    final List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);

    final Semaphore gotTest = new Semaphore(0);
    refs.get(0)
        .child("y")
        .setValue(
            "test",
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                refs.get(1)
                    .runTransaction(
                        new Transaction.Handler() {
                          @Override
                          public Transaction.Result doTransaction(MutableData currentData) {
                            if (currentData.getValue() == null) {
                              currentData.child("x").setValue(5);
                              return Transaction.success(currentData);
                            } else {
                              return Transaction.abort();
                            }
                          }

                          @Override
                          public void onComplete(
                              DatabaseError error, boolean committed, DataSnapshot currentData) {}
                        });

                refs.get(1)
                    .child("y")
                    .addValueEventListener(
                        new ValueEventListener() {
                          @Override
                          public void onDataChange(DataSnapshot snapshot) {
                            if ("test".equals(snapshot.getValue())) {
                              gotTest.release(1);
                            }
                          }

                          @Override
                          public void onCancelled(DatabaseError error) {}
                        });
              }
            });
    IntegrationTestHelpers.waitFor(gotTest);
  }

  @Test
  public void transactionWithNumericKeys() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore done = new Semaphore(0);

    Map<String, Object> initial =
        new MapBuilder().put("1", 1L).put("5", 5L).put("10", 5L).put("20", 20L).build();

    ref.setValue(
        initial,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.runTransaction(
                new Transaction.Handler() {
                  @Override
                  public Transaction.Result doTransaction(MutableData currentData) {
                    currentData.setValue(42);
                    return Transaction.success(currentData);
                  }

                  @Override
                  public void onComplete(
                      DatabaseError error, boolean committed, DataSnapshot currentData) {
                    assertTrue(committed);
                    assertNull(error);
                    done.release(1);
                  }
                });
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void canRemoveChildWithPriority0()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore done = new Semaphore(0);

    long value = 1378744239756L;
    new WriteFuture(ref, value, 0).timedGet();

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue(null);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertTrue(committed);
            assertNull(currentData.getValue());
            assertNull(error);
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void userCodeExceptionsAbortTheTransaction() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore done = new Semaphore(0);

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            throw new NullPointerException("lol! user code!");
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertFalse(committed);
            assertEquals(DatabaseError.USER_CODE_EXCEPTION, error.getCode());
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);

    // Now try it with a Throwable, rather than exception
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            throw new StackOverflowError();
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertFalse(committed);
            assertEquals(DatabaseError.USER_CODE_EXCEPTION, error.getCode());
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  // https://app.asana.com/0/5673976843758/9259161251948
  @Test
  public void bubbleAppTransactionBug() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore done = new Semaphore(0);
    ref.child("a")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                currentData.setValue(1);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {}
            });

    ref.child("a")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                if (currentData.getValue() != null) {
                  currentData.setValue((Long) currentData.getValue() + 42);
                } else {
                  currentData.setValue(42);
                }
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {}
            });

    ref.child("b")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                currentData.setValue(7);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {}
            });

    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            if (currentData.getValue() != null) {
              currentData.setValue(
                  (Long) currentData.child("a").getValue()
                      + (Long) currentData.child("b").getValue());
            } else {
              currentData.setValue("dummy");
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals(50L, currentData.getValue());
            done.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void testLocalServerValuesEventuallyButNotImmediatelyMatchServerWithTxns()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore completionSemaphore = new Semaphore(0);
    final List<DataSnapshot> readSnaps = new ArrayList<DataSnapshot>();
    final List<DataSnapshot> writeSnaps = new ArrayList<DataSnapshot>();

    reader.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onCancelled(DatabaseError error) {}

          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.getValue() != null) {
              readSnaps.add(snapshot);
              completionSemaphore.release();
            }
          }
        });

    writer.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onCancelled(DatabaseError error) {}

          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.getValue() != null) {
              writeSnaps.add(snapshot);
              completionSemaphore.release();
            }
          }
        });

    // Go offline for a few ms to make sure we get a different timestamp than the server.
    writer.getDatabase().goOffline();
    writer.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue(ServerValue.TIMESTAMP);
            currentData.setPriority(ServerValue.TIMESTAMP);
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            if (error != null || !committed) {
              fail("Transaction should succeed");
            }
          }
        });

    Thread.sleep(5);
    writer.getDatabase().goOnline();

    IntegrationTestHelpers.waitFor(completionSemaphore, 3);

    assertEquals(readSnaps.size(), 1);
    assertEquals(writeSnaps.size(), 2);
    assertTrue(Math.abs(System.currentTimeMillis() - (Long) writeSnaps.get(0).getValue()) < 3000);
    assertTrue(
        Math.abs(System.currentTimeMillis() - (Double) writeSnaps.get(0).getPriority()) < 3000);
    assertTrue(Math.abs(System.currentTimeMillis() - (Long) writeSnaps.get(1).getValue()) < 3000);
    assertTrue(
        Math.abs(System.currentTimeMillis() - (Double) writeSnaps.get(1).getPriority()) < 3000);
    assertFalse(writeSnaps.get(0).getValue().equals(writeSnaps.get(1).getValue()));
    assertFalse(writeSnaps.get(0).getPriority().equals(writeSnaps.get(1).getPriority()));
    assertEquals(writeSnaps.get(1).getValue(), readSnaps.get(0).getValue());
    assertEquals(writeSnaps.get(1).getPriority(), readSnaps.get(0).getPriority());
  }

  @Test
  public void testTransactionWithQueryListen() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);

    ref.setValue(
        new MapBuilder().put("a", 1).put("b", 2).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.limitToFirst(1)
                .addChildEventListener(
                    new ChildEventListener() {
                      @Override
                      public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}

                      @Override
                      public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

                      @Override
                      public void onChildRemoved(DataSnapshot snapshot) {}

                      @Override
                      public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });

            ref.child("a")
                .runTransaction(
                    new Transaction.Handler() {
                      @Override
                      public Transaction.Result doTransaction(MutableData currentData) {
                        return Transaction.success(currentData);
                      }

                      @Override
                      public void onComplete(
                          DatabaseError error, boolean committed, DataSnapshot currentData) {
                        assertNull(error);
                        assertTrue(committed);
                        assertEquals(1L, currentData.getValue());
                        semaphore.release();
                      }
                    });
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void testTransactionDoesNotPickUpCachedDataFromPreviousOnce()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    final DatabaseReference me = refs.get(0);
    final DatabaseReference other = refs.get(1);

    final Semaphore semaphore = new Semaphore(0);

    me.setValue(
        "not null",
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            semaphore.release();
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    me.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            semaphore.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(semaphore);

    other.setValue(
        null,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            semaphore.release();
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    me.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            if (currentData.getValue() == null) {
              currentData.setValue("it was null!");
            } else {
              currentData.setValue("it was not null!");
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals("it was null!", currentData.getValue());
            semaphore.release();
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void testTransactionDoesNotPickUpCachedDataFromPreviousTransaction()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    final DatabaseReference me = refs.get(0);
    final DatabaseReference other = refs.get(1);

    final Semaphore semaphore = new Semaphore(0);

    me.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("not null");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            semaphore.release();
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    other.setValue(
        null,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            semaphore.release();
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    me.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            if (currentData.getValue() == null) {
              currentData.setValue("it was null!");
            } else {
              currentData.setValue("it was not null!");
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            assertNull(error);
            assertTrue(committed);
            assertEquals("it was null!", currentData.getValue());
            semaphore.release();
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void transactionOnQueriedLocationDoesntRunInitiallyOnNull()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    DatabaseReference child = ref.push();
    final Map<String, Object> initialData = new MapBuilder().put("a", 1L).put("b", 2L).build();
    new WriteFuture(child, initialData).timedGet();

    final Semaphore semaphore = new Semaphore(0);

    ChildEventListener listener =
        ref.limitToFirst(1)
            .addChildEventListener(
                new TestChildEventListener() {
                  @Override
                  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    snapshot
                        .getRef()
                        .runTransaction(
                            new Transaction.Handler() {
                              @Override
                              public Transaction.Result doTransaction(MutableData currentData) {
                                Assert.assertEquals(initialData, currentData.getValue());
                                currentData.setValue(null);
                                return Transaction.success(currentData);
                              }

                              @Override
                              public void onComplete(
                                  DatabaseError error,
                                  boolean committed,
                                  DataSnapshot currentData) {
                                Assert.assertNull(error);
                                Assert.assertTrue(committed);
                                Assert.assertNull(currentData.getValue());
                                semaphore.release();
                              }
                            });
                  }

                  @Override
                  public void onChildRemoved(DataSnapshot snapshot) {}
                });

    IntegrationTestHelpers.waitFor(semaphore);

    // cleanup
    ref.removeEventListener(listener);
  }

  @Test
  public void transactionsRaiseCorrectChildChangedEventsOnQueries()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> value =
        new MapBuilder().put("foo", new MapBuilder().put("value", 1).build()).build();
    new WriteFuture(ref, value).timedGet();

    final List<DataSnapshot> snapshots = new ArrayList<DataSnapshot>();

    ChildEventListener listener =
        new TestChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            snapshots.add(snapshot);
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            snapshots.add(snapshot);
          }
        };

    ref.endAt(Double.MIN_VALUE).addChildEventListener(listener);

    final Semaphore semaphore = new Semaphore(0);

    ref.child("foo")
        .runTransaction(
            new Transaction.Handler() {

              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                currentData.child("value").setValue(2);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                assertNull(error);
                assertTrue(committed);
                semaphore.release();
              }
            },
            false);

    IntegrationTestHelpers.waitFor(semaphore);

    Assert.assertEquals(2, snapshots.size());
    DataSnapshot addedSnapshot = snapshots.get(0);
    Assert.assertEquals("foo", addedSnapshot.getKey());
    Assert.assertEquals(new MapBuilder().put("value", 1L).build(), addedSnapshot.getValue());
    DataSnapshot changedSnapshot = snapshots.get(1);
    Assert.assertEquals("foo", changedSnapshot.getKey());
    Assert.assertEquals(new MapBuilder().put("value", 2L).build(), changedSnapshot.getValue());

    // cleanup
    ref.removeEventListener(listener);
  }

  @Test
  public void transactionsUseLocalMerges()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    // Go offline to ensure the update doesn't complete before the transaction runs.
    IntegrationTestHelpers.goOffline(IntegrationTestHelpers.getContext(0));
    ref.updateChildren(new MapBuilder().put("foo", "bar").build());
    ref.child("foo")
        .runTransaction(
            new Transaction.Handler() {

              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                assertEquals("bar", currentData.getValue());
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                assertEquals(null, error);
                assertTrue(committed);
                semaphore.release();
              }
            });

    IntegrationTestHelpers.goOnline(IntegrationTestHelpers.getContext(0));
    IntegrationTestHelpers.waitFor(semaphore);
  }

  // See https://app.asana.com/0/15566422264127/23303789496881
  @Test
  public void outOfOrderRemoveWritesAreHandledCorrectly()
      throws DatabaseException, InterruptedException, ExecutionException, TestFailure,
          TimeoutException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(new MapBuilder().put("foo", "bar").build());
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("transaction-1");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(
              DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    ref.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            currentData.setValue("transaction-2");
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(
              DatabaseError error, boolean committed, DataSnapshot currentData) {}
        });
    final Semaphore done = new Semaphore(0);
    // This will trigger an abort of the transaction which should not cause the client to crash
    ref.updateChildren(
        new MapBuilder().put("qux", "quu").build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            done.release();
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void returningNullReturnsNullPointerExceptionError() throws Throwable {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);

    ref.child("foo")
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                return null;
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {
                assertNotNull(error);
                semaphore.release();
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void unsentTransactionsAreNotCancelledOnDisconnect() throws InterruptedException {
    // Hack: To trigger us to disconnect before restoring state, we inject a bad auth token.
    // In real-world usage the much more common case is that we get redirected to a different
    // server, but that's harder to manufacture from a test.
    final DatabaseConfig cfg = IntegrationTestHelpers.newTestConfig();
    cfg.setAuthTokenProvider(
        new AuthTokenProvider() {
          private int count = 0;

          @Override
          public void getToken(boolean forceRefresh, final GetTokenCompletionListener listener) {
            // Return "bad-token" once to trigger a disconnect, and then a null token.
            IntegrationTestHelpers.getExecutorService(cfg)
                .schedule(
                    new Runnable() {
                      @Override
                      public void run() {
                        if (count == 0) {
                          count++;
                          listener.onSuccess("bad-token");
                        } else {
                          listener.onSuccess(null);
                        }
                      }
                    },
                    10,
                    TimeUnit.MILLISECONDS);
          }

          @Override
          public void addTokenChangeListener(
              ExecutorService executorService, TokenChangeListener listener) {}

          @Override
          public void removeTokenChangeListener(TokenChangeListener listener) {}
        });

    // Queue a transaction offline.
    DatabaseReference ref = IntegrationTestHelpers.rootWithConfig(cfg);
    ref.getDatabase().goOffline();
    final Semaphore semaphore = new Semaphore(0);
    ref.push()
        .runTransaction(
            new Transaction.Handler() {
              @Override
              public Transaction.Result doTransaction(MutableData currentData) {
                currentData.setValue(42);
                return Transaction.success(currentData);
              }

              @Override
              public void onComplete(
                  DatabaseError error, boolean committed, DataSnapshot currentData) {

                assertNull(error);
                assertTrue(committed);
                semaphore.release();
              }
            });

    ref.getDatabase().goOnline();
    IntegrationTestHelpers.waitFor(semaphore);
  }
}
