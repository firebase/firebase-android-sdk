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

import static java.util.logging.Level.WARNING;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.core.ZombieVerifier;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class EventTest {
  private static Logger LOGGER = Logger.getLogger(EventTest.class.getName());
  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }
  // NOTE: skipping test on valid types.

  @Test
  public void writeLeafNodeExpectValue() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper readerHelper =
        new EventHelper().addValueExpectation(reader, 42).startListening(true);
    EventHelper writerHelper =
        new EventHelper().addValueExpectation(writer, 42).startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    writer.setValue(42);
    assertTrue(writerHelper.waitForEvents());
    assertTrue(readerHelper.waitForEvents());
    writerHelper.cleanup();
    readerHelper.cleanup();
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeNestedLeafNodeWaitForEvents() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    EventHelper helper =
        new EventHelper()
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(ref)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(ref);

    ref.child("foo").setValue(42);
    assertTrue(helper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void writeTwoLeafNodeThenChangeThem() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(reader.child("foo"), 42)
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(reader)
            .addValueExpectation(reader.child("bar"), 24)
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "bar")
            .addValueExpectation(reader)
            .addValueExpectation(reader.child("foo"), 31415)
            .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "foo")
            .addValueExpectation(reader)
            .startListening(true);

    EventHelper writeHelper =
        new EventHelper()
            .addValueExpectation(writer.child("foo"), 42)
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(writer)
            .addValueExpectation(writer.child("bar"), 24)
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "bar")
            .addValueExpectation(writer)
            .addValueExpectation(writer.child("foo"), 31415)
            .addChildExpectation(writer, Event.EventType.CHILD_CHANGED, "foo")
            .addValueExpectation(writer)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    writer.child("foo").setValue(42);
    writer.child("bar").setValue(24);

    writer.child("foo").setValue(31415);
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeFloatValueThenChangeToInteger() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference node = refs.get(0);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(node, 1337)
            .addValueExpectation(node, 1337.1)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    node.setValue((float) 1337.0);
    node.setValue(1337); // This does not fire events.
    node.setValue((float) 1337.0); // This does not fire events.
    node.setValue(1337.1);

    IntegrationTestHelpers.waitForRoundtrip(node);
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeDoubleValueThenChangeToInteger() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference node = refs.get(0);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(node, 1337)
            .addValueExpectation(node, 1337.1)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    node.setValue(1337.0);
    node.setValue(1337); // This does not fire events.
    node.setValue(1337.1);

    IntegrationTestHelpers.waitForRoundtrip(node);
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeDoubleValueThenChangeToIntegerWithDifferentPriority()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference node = refs.get(0);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(node, 1337)
            .addValueExpectation(node, 1337)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    node.setValue(1337.0);
    node.setValue(1337, 1337);

    IntegrationTestHelpers.waitForRoundtrip(node);
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeIntegerValueThenChangeToDouble() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference node = refs.get(0);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(node, 1337)
            .addValueExpectation(node, 1337.1)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    node.setValue(1337);
    node.setValue(1337.0); // This does not fire events.
    node.setValue(1337.1);

    IntegrationTestHelpers.waitForRoundtrip(node);
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeIntegerValueThenChangeToDoubleWithDifferentPriority()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference node = refs.get(0);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(node, 1337)
            .addValueExpectation(node, 1337)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    node.setValue(1337);
    node.setValue(1337.0, 1337);

    IntegrationTestHelpers.waitForRoundtrip(node);
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void writeLargeLongValueThenIncrement() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference node = refs.get(0);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(node, Long.MAX_VALUE)
            .addValueExpectation(node, Long.MAX_VALUE * 2.0)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);
    node.setValue(Long.MAX_VALUE);
    node.setValue(Long.MAX_VALUE * 2.0);

    IntegrationTestHelpers.waitForRoundtrip(node);
    assertTrue(readHelper.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void setMultipleEventListenersOnSameNode() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper writeHelper =
        new EventHelper().addValueExpectation(writer, 42).startListening(true);
    EventHelper writeHelper2 =
        new EventHelper().addValueExpectation(writer, 42).startListening(true);
    EventHelper readHelper = new EventHelper().addValueExpectation(reader, 42).startListening(true);
    EventHelper readHelper2 =
        new EventHelper().addValueExpectation(reader, 42).startListening(true);

    ZombieVerifier.verifyRepoZombies(refs);

    writer.setValue(42);
    assertTrue(writeHelper.waitForEvents());
    assertTrue(writeHelper2.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    assertTrue(readHelper2.waitForEvents());
    ZombieVerifier.verifyRepoZombies(refs);
  }

  @Test
  public void setDataMultipleTimesEnsureValueIsCalledAppropriately()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilEquals(ref, 2L, /*ignoreFirstNull=*/ true);
    ZombieVerifier.verifyRepoZombies(ref);

    for (int i = 0; i < 3; ++i) {
      ref.setValue(i);
    }

    List<EventRecord> events = readFuture.timedGet();
    for (long i = 0; i < 3; ++i) {
      DataSnapshot snap = events.get((int) i).getSnapshot();
      assertEquals(i, snap.getValue());
    }
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void unsubscribeEventsAndConfirmEventsNoLongerFire()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final AtomicInteger callbackCount = new AtomicInteger(0);

    ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.getValue() != null) {
                  callbackCount.incrementAndGet();
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not be cancelled");
              }
            });
    ZombieVerifier.verifyRepoZombies(ref);

    for (int i = 0; i < 3; ++i) {
      ref.setValue(i);
    }

    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);

    for (int i = 10; i < 13; ++i) {
      ref.setValue(i);
    }

    for (int i = 20; i < 22; ++i) {
      ref.setValue(i);
    }
    new WriteFuture(ref, 22).timedGet();
    assertEquals(3, callbackCount.get());
  }

  @Test
  public void subscribeThenUnsubscribeWithoutProblems()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ValueEventListener listenerHandle = ref.addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle);
    ZombieVerifier.verifyRepoZombies(ref);
    ValueEventListener listenerHandle2 = ref.addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle2);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void subscribeThenUnsubscribeWithoutProblemsWithLimit()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ValueEventListener listenerHandle = ref.limitToLast(100).addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle);
    ZombieVerifier.verifyRepoZombies(ref);
    ValueEventListener listenerHandle2 = ref.limitToLast(100).addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
    ref.removeEventListener(listenerHandle2);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void writeChunkOfJSONButGetMoreGranularEventsForIndividualChanges()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final AtomicBoolean readerSawA = new AtomicBoolean(false);
    final AtomicBoolean readerSawB = new AtomicBoolean(false);
    final AtomicBoolean readerSawB2 = new AtomicBoolean(false);
    final AtomicBoolean writerSawA = new AtomicBoolean(false);
    final AtomicBoolean writerSawB = new AtomicBoolean(false);
    final AtomicBoolean writerSawB2 = new AtomicBoolean(false);
    final Semaphore readerReady = new Semaphore(0);
    final Semaphore writerReady = new Semaphore(0);

    reader
        .child("a")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null && val == 10L) {
                  assertTrue(readerSawA.compareAndSet(false, true));
                  readerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    reader
        .child("b")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null) {
                  if (val == 20L) {
                    assertTrue(readerSawB.compareAndSet(false, true));
                  } else if (val == 30L) {
                    assertTrue(readerSawB2.compareAndSet(false, true));
                  }
                  readerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer
        .child("a")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null) {
                  assertTrue(writerSawA.compareAndSet(false, true));
                  writerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer
        .child("b")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                Long val = (Long) snapshot.getValue();
                if (val != null) {
                  if (val == 20L) {
                    assertTrue(writerSawB.compareAndSet(false, true));
                  } else if (val == 30L) {
                    assertTrue(writerSawB2.compareAndSet(false, true));
                  }
                  writerReady.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ZombieVerifier.verifyRepoZombies(refs);

    writer.setValue(new MapBuilder().put("a", 10).put("b", 20).build());
    IntegrationTestHelpers.waitFor(writerReady, 2);
    IntegrationTestHelpers.waitFor(readerReady, 2);

    writer.setValue(new MapBuilder().put("a", 10).put("b", 30).build());
    IntegrationTestHelpers.waitFor(writerReady);
    IntegrationTestHelpers.waitFor(readerReady);
  }

  @Test
  public void valueIsTriggeredForEmptyNodes()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    DataSnapshot snap = new ReadFuture(ref).timedGet().get(0).getSnapshot();
    ZombieVerifier.verifyRepoZombies(ref);
    assertNull(snap.getValue());
  }

  @Test
  public void correctEventsAreRaisedWhenALeafNodeTurnsIntoAnInternalNode()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 4);

    ZombieVerifier.verifyRepoZombies(ref);

    ref.setValue(42);
    ref.setValue(new MapBuilder().put("a", 2).build());
    ref.setValue(84);
    ref.setValue(null);

    List<EventRecord> events = readFuture.timedGet();
    ZombieVerifier.verifyRepoZombies(ref);

    assertEquals(42L, events.get(0).getSnapshot().getValue());
    assertEquals(2L, events.get(1).getSnapshot().child("a").getValue());
    assertEquals(84L, events.get(2).getSnapshot().getValue());
    assertNull(events.get(3).getSnapshot().getValue());
  }

  @Test
  public void canRegisterTheSameCallbackMultipleTimesNeedToUnregisterItMultipleTimes()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final AtomicInteger callbackCount = new AtomicInteger(0);
    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.getValue() != null) {
              callbackCount.incrementAndGet();
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ref.addValueEventListener(listener);
    ref.addValueEventListener(listener);
    ref.addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);

    new WriteFuture(ref, 42).timedGet();
    assertEquals(3, callbackCount.get());

    ref.removeEventListener(listener);
    new WriteFuture(ref, 84).timedGet();
    assertEquals(5, callbackCount.get());
    ZombieVerifier.verifyRepoZombies(ref);

    ref.removeEventListener(listener);
    new WriteFuture(ref, 168).timedGet();
    assertEquals(6, callbackCount.get());
    ZombieVerifier.verifyRepoZombies(ref);

    ref.removeEventListener(listener);
    new WriteFuture(ref, 376).timedGet();
    assertEquals(6, callbackCount.get());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void unregisterSameCallbackTooManyTimesSilentlyDoesNothing()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {
                // no-op
              }
            });

    ZombieVerifier.verifyRepoZombies(ref);

    ref.removeEventListener(listener);
    ref.removeEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void removesHappenImmediately()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (snapshot.getValue() != null) {
              assertTrue(called.compareAndSet(false, true));
              try {
                IntegrationTestHelpers.waitFor(blockSem);
              } catch (InterruptedException e) {
                LOGGER.log(WARNING, "unexpected error", e);
              }
              ref.removeEventListener(this);
              try {
                // this doesn't block immediately because we are already on the repo thread.
                // we kick off the verify and let the unit test block on the endingsemaphore
                ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
              } catch (InterruptedException e) {
                LOGGER.log(WARNING, "unexpected error", e);
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });
    ZombieVerifier.verifyRepoZombies(ref);

    ref.setValue(42);
    IntegrationTestHelpers.waitForQueue(ref);
    ref.setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    IntegrationTestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void removesHappenImmediatelyOnOuterRef()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore gotInitialEvent = new Semaphore(0);
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.limitToFirst(5)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                gotInitialEvent.release();
                if (snapshot.getValue() != null) {
                  assertTrue(called.compareAndSet(false, true));
                  try {
                    IntegrationTestHelpers.waitFor(blockSem);
                  } catch (InterruptedException e) {
                    LOGGER.log(WARNING, "unexpected error", e);
                  }
                  ref.removeEventListener(this);
                  try {
                    // this doesn't block immediately because we are already on the repo thread.
                    // we kick off the verify and let the unit test block on the endingsemaphore
                    ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
                  } catch (InterruptedException e) {
                    LOGGER.log(WARNING, "unexpected error", e);
                  }
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not be cancelled");
              }
            });
    ZombieVerifier.verifyRepoZombies(ref);

    IntegrationTestHelpers.waitFor(gotInitialEvent);

    ref.child("a").setValue(42);
    IntegrationTestHelpers.waitForQueue(ref);
    ref.child("b").setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    IntegrationTestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void removesHappenImmediatelyOnMultipleRef()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore gotInitialEvent = new Semaphore(0);
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ValueEventListener listener =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            gotInitialEvent.release();
            if (snapshot.getValue() != null) {
              assertTrue(called.compareAndSet(false, true));
              try {
                IntegrationTestHelpers.waitFor(blockSem);
              } catch (InterruptedException e) {
                LOGGER.log(WARNING, "unexpected error", e);
              }
              ref.removeEventListener(this);
              try {
                // this doesn't block immediately because we are already on the repo thread.
                // we kick off the verify and let the unit test block on the endingsemaphore
                ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
              } catch (InterruptedException e) {
                LOGGER.log(WARNING, "unexpected error", e);
              }
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        };

    ref.addValueEventListener(listener);
    ref.limitToFirst(5).addValueEventListener(listener);
    ZombieVerifier.verifyRepoZombies(ref);

    IntegrationTestHelpers.waitFor(gotInitialEvent, 2);
    ref.child("a").setValue(42);
    IntegrationTestHelpers.waitForQueue(ref);
    ref.child("b").setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    IntegrationTestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void removesHappenImmediatelyChild()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore blockSem = new Semaphore(0);
    final Semaphore endingSemaphore = new Semaphore(0);

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            if (snapshot.getValue() != null) {
              assertTrue(called.compareAndSet(false, true));
              try {
                IntegrationTestHelpers.waitFor(blockSem);
              } catch (InterruptedException e) {
                LOGGER.log(WARNING, "unexpected error", e);
              }
              ref.removeEventListener(this);
              try {
                // this doesn't block immediately because we are already on the repo thread.
                // we kick off the verify and let the unit test block on the endingsemaphore
                ZombieVerifier.verifyRepoZombies(ref, endingSemaphore);
              } catch (InterruptedException e) {
                LOGGER.log(WARNING, "unexpected error", e);
              }
            }
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    ZombieVerifier.verifyRepoZombies(ref);

    ref.child("a").setValue(42);
    IntegrationTestHelpers.waitForQueue(ref);
    ref.child("b").setValue(84);
    blockSem.release();
    new WriteFuture(ref, null).timedGet();
    IntegrationTestHelpers.waitFor(endingSemaphore);
  }

  @Test
  public void onceFiresExactlyOnce()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final AtomicBoolean called = new AtomicBoolean(false);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertTrue(called.compareAndSet(false, true));
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    ZombieVerifier.verifyRepoZombies(ref);

    ref.setValue(42);
    ref.setValue(84);
    new WriteFuture(ref, null).timedGet();
    ZombieVerifier.verifyRepoZombies(ref);
  }

  // NOTE: skipped tests on testing 'once' with child events. Not supported in Java SDK

  @Test
  public void valueOnEmptyChildFires()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    DataSnapshot snap = new ReadFuture(ref.child("test")).timedGet().get(0).getSnapshot();
    assertNull(snap.getValue());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void valueOnEmptyChildFiresImmediatelyEvenAfterParentIsSynced()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    // Sync parent
    new ReadFuture(ref).timedGet();

    DataSnapshot snap = new ReadFuture(ref.child("test")).timedGet().get(0).getSnapshot();
    assertNull(snap.getValue());
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void childEventsAreRaised()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> firstValue =
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "x").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "x").put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", "x").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "x").put(".priority", 3).build())
            .put("e", new MapBuilder().put(".value", "x").put(".priority", 4).build())
            .put("f", new MapBuilder().put(".value", "x").put(".priority", 5).build())
            .put("g", new MapBuilder().put(".value", "x").put(".priority", 6).build())
            .put("h", new MapBuilder().put(".value", "x").put(".priority", 7).build())
            .build();

    Map<String, Object> secondValue =
        new MapBuilder()
            // added
            .put("aa", new MapBuilder().put(".value", "x").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "x").put(".priority", 1).build())
            // added
            .put("bb", new MapBuilder().put(".value", "x").put(".priority", 2).build())
            // removed c
            // changed
            .put("d", new MapBuilder().put(".value", "y").put(".priority", 3).build())
            .put("e", new MapBuilder().put(".value", "x").put(".priority", 4).build())
            // moved + changed
            .put("a", new MapBuilder().put(".value", "x").put(".priority", 6).build())
            // moved + changed
            .put("f", new MapBuilder().put(".value", "x").put(".priority", 7).build())
            // removed g
            // changed
            .put("h", new MapBuilder().put(".value", "y").put(".priority", 7).build())
            .build();

    final List<String> events = new ArrayList<String>();
    ref.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            events.add("added " + snapshot.getKey());
          }

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
            events.add("changed " + snapshot.getKey());
          }

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {
            events.add("removed " + snapshot.getKey());
          }

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            events.add("moved " + snapshot.getKey());
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });
    new WriteFuture(ref, firstValue).timedGet();
    events.clear();
    new WriteFuture(ref, secondValue).timedGet();

    List<String> expected =
        Arrays.asList(
            "removed c",
            "removed g",
            "added aa",
            "added bb",
            "moved a",
            "moved f",
            "changed d",
            "changed a",
            "changed f",
            "changed h");
    String expectedString = expected.toString();
    String actualString = events.toString();
    assertEquals(expectedString, actualString);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void childEventsAreRaisedWithAQuery()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> firstValue =
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "x").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "x").put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", "x").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "x").put(".priority", 3).build())
            .put("e", new MapBuilder().put(".value", "x").put(".priority", 4).build())
            .put("f", new MapBuilder().put(".value", "x").put(".priority", 5).build())
            .put("g", new MapBuilder().put(".value", "x").put(".priority", 6).build())
            .put("h", new MapBuilder().put(".value", "x").put(".priority", 7).build())
            .build();

    Map<String, Object> secondValue =
        new MapBuilder()
            // added
            .put("aa", new MapBuilder().put(".value", "x").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "x").put(".priority", 1).build())
            // added
            .put("bb", new MapBuilder().put(".value", "x").put(".priority", 2).build())
            // removed c
            // changed
            .put("d", new MapBuilder().put(".value", "y").put(".priority", 3).build())
            .put("e", new MapBuilder().put(".value", "x").put(".priority", 4).build())
            // moved
            .put("a", new MapBuilder().put(".value", "x").put(".priority", 6).build())
            // moved
            .put("f", new MapBuilder().put(".value", "x").put(".priority", 7).build())
            // removed g
            // changed
            .put("h", new MapBuilder().put(".value", "y").put(".priority", 7).build())
            .build();

    final List<String> events = new ArrayList<String>();
    ref.limitToLast(10)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                events.add("added " + snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                events.add("changed " + snapshot.getKey());
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                events.add("removed " + snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                events.add("moved " + snapshot.getKey());
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });
    new WriteFuture(ref, firstValue).timedGet();
    events.clear();
    new WriteFuture(ref, secondValue).timedGet();

    List<String> expected =
        Arrays.asList(
            "removed c",
            "removed g",
            "added aa",
            "added bb",
            "moved a",
            "moved f",
            "changed d",
            "changed a",
            "changed f",
            "changed h");
    String expectedString = expected.toString();
    String actualString = events.toString();
    assertEquals(expectedString, actualString);
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void priorityChangeShouldRaiseChildMovedAndChildChangedAndValueOnParentAndChild()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("bar"), 42)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "bar")
            .addValueExpectation(ref)
            .addValueExpectation(ref.child("foo"), 42)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(ref)
            .startListening(true);

    ref.child("bar").setValue(42, 10);
    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.child("foo").setValue(42, 20);

    assertTrue(helper.waitForEvents());
    helper
        .addValueExpectation(ref.child("bar"), 42)
        .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "bar")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "bar")
        .addValueExpectation(ref)
        .startListening();

    ref.child("bar").setPriority(30);
    assertTrue(helper.waitForEvents());
    helper.cleanup();
    ZombieVerifier.verifyRepoZombies(ref);
  }

  @Test
  public void priorityChangeShouldRaiseChildMovedAndChildChangedAndValueOnParentAndChild2()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("bar"), 42)
            .addValueExpectation(ref.child("foo"), 42)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "bar")
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "foo")
            .addValueExpectation(ref)
            .startListening(true);

    ZombieVerifier.verifyRepoZombies(ref);
    ref.setValue(
        new MapBuilder()
            .put("bar", new MapBuilder().put(".value", 42).put(".priority", 10).build())
            .put("foo", new MapBuilder().put(".value", 42).put(".priority", 20).build())
            .build());
    assertTrue(helper.waitForEvents());
    helper
        .addValueExpectation(ref.child("bar"), 42)
        .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "bar")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "bar")
        .addValueExpectation(ref)
        .startListening();

    ZombieVerifier.verifyRepoZombies(ref);
    ref.setValue(
        new MapBuilder()
            .put("foo", new MapBuilder().put(".value", 42).put(".priority", 20).build())
            .put("bar", new MapBuilder().put(".value", 42).put(".priority", 30).build())
            .build());
    assertTrue(helper.waitForEvents());
    helper.cleanup();
    ZombieVerifier.verifyRepoZombies(ref);
  }
}
