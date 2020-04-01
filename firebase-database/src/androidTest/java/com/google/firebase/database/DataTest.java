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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoManager;
import com.google.firebase.database.core.ServerValues;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Phaser;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.After;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class DataTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void basicInstantiation() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    assertTrue(ref != null);
  }

  @Test
  public void writeData() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    // just make sure it doesn't throw
    ref.setValue(42);
    assertTrue(true);
  }

  @Test
  public void readAndWrite()
      throws DatabaseException, ExecutionException, InterruptedException, TimeoutException,
          TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ReadFuture future = ReadFuture.untilNonNull(ref);

    ref.setValue(42);
    List<EventRecord> events = future.timedGet();
    assertEquals(42L, events.get(events.size() - 1).getSnapshot().getValue());
  }

  @Test
  public void valueReturnsJSONForNodesWithChildren()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    Map<String, Object> expected = new HashMap<String, Object>();
    Map<String, Object> innerExpected = new HashMap<String, Object>();
    innerExpected.put("bar", 5L);
    expected.put("foo", innerExpected);

    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture future = ReadFuture.untilNonNull(ref);

    ref.setValue(expected);
    List<EventRecord> events = future.timedGet();
    EventRecord eventRecord = events.get(events.size() - 1);
    Object result = eventRecord.getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void writeDataAndWaitForServerConfirmation()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.setValue(42);

    ReadFuture future = new ReadFuture(ref);

    EventRecord eventRecord = future.timedGet().get(0);
    assertEquals(42L, eventRecord.getSnapshot().getValue());
  }

  @Test
  public void writeAValueReconnectRead()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    WriteFuture writeFuture = new WriteFuture(writer, 42);
    writeFuture.timedGet();

    ReadFuture future = new ReadFuture(reader);

    EventRecord eventRecord = future.timedGet().get(0);
    long result = (Long) eventRecord.getSnapshot().getValue();
    assertEquals(42L, result);
    assertEquals(42, (int) eventRecord.getSnapshot().getValue(Integer.class));
  }

  @Test
  public void writeABunchOfDataReconnectRead()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    writer.child("a").child("b").child("c").setValue(1);
    writer.child("a").child("d").child("e").setValue(2);
    writer.child("a").child("d").child("f").setValue(3);
    WriteFuture writeFuture = new WriteFuture(writer.child("g"), 4);
    writeFuture.timedGet();

    Map<String, Object> expected =
        new MapBuilder()
            .put(
                "a",
                new MapBuilder()
                    .put("b", new MapBuilder().put("c", 1L).build())
                    .put("d", new MapBuilder().put("e", 2L).put("f", 3L).build())
                    .build())
            .put("g", 4L)
            .build();
    ReadFuture readFuture = new ReadFuture(reader);

    GenericTypeIndicator<Map<String, Object>> t =
        new GenericTypeIndicator<Map<String, Object>>() {};
    Map<String, Object> result = readFuture.timedGet().get(0).getSnapshot().getValue(t);
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void writeLeafNodeOverwriteParentNodeWaitForEvents()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(ref.child("a"))
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
            .addValueExpectation(ref.child("a"))
            .startListening(true);

    ref.child("a/aa").setValue(1);
    ref.child("a").setValue(new MapBuilder().put("aa", 2).build());

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  @Test
  public void writeLeafNodeOverwriteAtParentMultipleTimesWaitForExpectedEvents()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final AtomicInteger bbCount = new AtomicInteger(0);
    ValueEventListener listener =
        ref.child("a/bb")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    assertNull(snapshot.getValue());
                    assertEquals(1, bbCount.incrementAndGet());
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(ref.child("a"))
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
            .addValueExpectation(ref.child("a"))
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
            .addValueExpectation(ref.child("a"))
            .startListening(true);

    ref.child("a/aa").setValue(1);
    ref.child("a").setValue(new MapBuilder().put("aa", 2).build());
    ref.child("a").setValue(new MapBuilder().put("aa", 3).build());
    ref.child("a").setValue(new MapBuilder().put("aa", 3).build());

    assertTrue(helper.waitForEvents());
    helper.cleanup();
    assertEquals(1, bbCount.get());
    ref.child("a/bb").removeEventListener(listener);
  }

  @Test
  public void writeParentNodeOverwriteAtLeafNodeWaitForEvents()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(ref.child("a"))
            .addValueExpectation(ref.child("a/aa"))
            .addChildExpectation(ref.child("a"), Event.EventType.CHILD_CHANGED, "aa")
            .addValueExpectation(ref.child("a"))
            .startListening(true);

    ref.child("a").setValue(new MapBuilder().put("aa", 2).build());
    ref.child("a/aa").setValue(1);

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  @Test
  public void writeLeafNodeRemoveParentWaitForEvents()
      throws DatabaseException, InterruptedException, TimeoutException, TestFailure,
          ExecutionException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper writeHelper =
        new EventHelper()
            .addValueExpectation(writer.child("a/aa"))
            .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(writer.child("a"))
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(writer)
            .startListening(true);

    WriteFuture w = new WriteFuture(writer.child("a/aa"), 42);
    assertTrue(writeHelper.waitForEvents());

    w.timedGet();
    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(reader.child("a/aa"))
            .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(reader.child("a"))
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(reader)
            .startListening();

    assertTrue(readHelper.waitForEvents());

    readHelper
        .addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a")
        .addValueExpectation(reader)
        .startListening();

    writeHelper
        .addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a")
        .addValueExpectation(reader)
        .startListening();

    writer.child("a").removeValue();
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    writeHelper.cleanup();
    readHelper.cleanup();

    // Make sure we actually have null there now
    assertNull(IntegrationTestHelpers.getSnap(reader).getValue());
    assertNull(IntegrationTestHelpers.getSnap(writer).getValue());

    ReadFuture readFuture = ReadFuture.untilNonNull(reader);

    ReadFuture writeFuture = ReadFuture.untilNonNull(writer);

    writer.child("a/aa").setValue(3.1415);

    List<EventRecord> readerEvents = readFuture.timedGet();
    List<EventRecord> writerEvents = writeFuture.timedGet();

    DataSnapshot readerSnap = readerEvents.get(readerEvents.size() - 1).getSnapshot();
    readerSnap = readerSnap.child("a/aa");
    assertEquals(3.1415, readerSnap.getValue());

    DataSnapshot writerSnap = writerEvents.get(writerEvents.size() - 1).getSnapshot();
    writerSnap = writerSnap.child("a/aa");
    assertEquals(3.1415, writerSnap.getValue());
  }

  @Test
  public void writeLeafNodeRemoveLeafNodeWaitForEvents()
      throws DatabaseException, InterruptedException, TimeoutException, TestFailure,
          ExecutionException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper writeHelper =
        new EventHelper()
            .addValueExpectation(writer.child("a/aa"))
            .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(writer.child("a"))
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(writer)
            .startListening(true);

    WriteFuture w = new WriteFuture(writer.child("a/aa"), 42);
    assertTrue(writeHelper.waitForEvents());

    w.timedGet();
    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(reader.child("a/aa"))
            .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(reader.child("a"))
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(reader)
            .startListening();

    assertTrue(readHelper.waitForEvents());

    readHelper
        .addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a")
        .addValueExpectation(reader)
        .startListening();

    writeHelper
        .addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_REMOVED, "a")
        .addValueExpectation(reader)
        .startListening();

    writer.child("a/aa").removeValue();
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());
    writeHelper.cleanup();
    readHelper.cleanup();

    DataSnapshot readerSnap = IntegrationTestHelpers.getSnap(reader);
    assertNull(readerSnap.getValue());

    DataSnapshot writerSnap = IntegrationTestHelpers.getSnap(writer);
    assertNull(writerSnap.getValue());

    readerSnap = IntegrationTestHelpers.getSnap(reader.child("a/aa"));
    assertNull(readerSnap.getValue());

    writerSnap = IntegrationTestHelpers.getSnap(writer.child("a/aa"));
    assertNull(writerSnap.getValue());

    ReadFuture readFuture = ReadFuture.untilNonNull(reader);
    ReadFuture writeFuture = ReadFuture.untilNonNull(writer);

    writer.child("a/aa").setValue(3.1415);

    List<EventRecord> readerEvents = readFuture.timedGet();
    List<EventRecord> writerEvents = writeFuture.timedGet();

    readerSnap = readerEvents.get(readerEvents.size() - 1).getSnapshot();
    readerSnap = readerSnap.child("a/aa");
    assertEquals(3.1415, readerSnap.getValue());

    writerSnap = writerEvents.get(writerEvents.size() - 1).getSnapshot();
    writerSnap = writerSnap.child("a/aa");
    assertEquals(3.1415, writerSnap.getValue());
  }

  @Test
  public void writeMultipleLeafNodesRemoveOneLeafNodeWaitForEvents()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    EventHelper writeHelper =
        new EventHelper()
            .addValueExpectation(writer.child("a/aa"))
            .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(writer.child("a"))
            .addChildExpectation(writer, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(writer)
            .addChildExpectation(writer.child("a"), Event.EventType.CHILD_ADDED, "bb")
            .addValueExpectation(writer.child("a"))
            .addChildExpectation(writer, Event.EventType.CHILD_CHANGED, "a")
            .addValueExpectation(writer)
            .startListening(true);

    EventHelper readHelper =
        new EventHelper()
            .addValueExpectation(reader.child("a/aa"))
            .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "aa")
            .addValueExpectation(reader.child("a"))
            .addChildExpectation(reader, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(reader)
            .addChildExpectation(reader.child("a"), Event.EventType.CHILD_ADDED, "bb")
            .addValueExpectation(reader.child("a"))
            .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "a")
            .addValueExpectation(reader)
            .startListening(true);

    writer.child("a/aa").setValue(42);
    writer.child("a/bb").setValue(24);

    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());

    readHelper
        .addValueExpectation(reader.child("a/aa"))
        .addChildExpectation(reader.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(reader.child("a"))
        .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "a")
        .addValueExpectation(reader)
        .startListening();

    writeHelper
        .addValueExpectation(writer.child("a/aa"))
        .addChildExpectation(writer.child("a"), Event.EventType.CHILD_REMOVED, "aa")
        .addValueExpectation(writer.child("a"))
        .addChildExpectation(writer, Event.EventType.CHILD_CHANGED, "a")
        .addValueExpectation(writer)
        .startListening();

    writer.child("a/aa").removeValue();
    assertTrue(writeHelper.waitForEvents());
    assertTrue(readHelper.waitForEvents());

    DataSnapshot readerSnap = IntegrationTestHelpers.getSnap(reader);
    DataSnapshot writerSnap = IntegrationTestHelpers.getSnap(writer);
    Map<String, Object> expected =
        new MapBuilder().put("a", new MapBuilder().put("bb", 24L).build()).build();
    DeepEquals.assertEquals(expected, readerSnap.getValue());
    DeepEquals.assertEquals(expected, writerSnap.getValue());

    readHelper.cleanup();
    writeHelper.cleanup();
  }

  @Test
  public void verifyCantNameNodesStartingWithAPeriod() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    try {
      ref.child(".foo");
      fail("Should fail");
    } catch (DatabaseException e) {
      // No-op
    }

    try {
      ref.child("foo/.foo");
      fail("Should fail");
    } catch (DatabaseException e) {
      // No-op
    }
  }

  // NOTE: skipping test re: writing .keys and .length. Those features will require a new client
  // anyways

  @Test
  public void numericKeysGetTurnedIntoArrays() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.child("0").setValue("alpha");
    ref.child("1").setValue("bravo");
    ref.child("2").setValue("charlie");
    ref.child("3").setValue("delta");
    ref.child("4").setValue("echo");

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    List<Object> expected = new ArrayList<Object>();
    expected.addAll(Arrays.asList((Object) "alpha", "bravo", "charlie", "delta", "echo"));
    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void canWriteFullJSONObjectsWithSetAndGetThemBack()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> expected =
        new MapBuilder()
            .put("a", new MapBuilder().put("aa", 5L).put("ab", 3L).build())
            .put(
                "b",
                new MapBuilder()
                    .put("ba", "hey there!")
                    .put("bb", new MapBuilder().put("bba", false).build())
                    .build())
            .put(
                "c",
                new ListBuilder()
                    .put(0L)
                    .put(new MapBuilder().put("c_1", 4L).build())
                    .put("hey")
                    .put(true)
                    .put(false)
                    .put("dude")
                    .build())
            .build();

    ReadFuture readFuture = ReadFuture.untilNonNull(ref);

    ref.setValue(expected);
    List<EventRecord> events = readFuture.timedGet();
    Object result = events.get(events.size() - 1).getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);
  }

  // NOTE: skipping test for value in callback. DataSnapshot is same instance in callback as after
  // future is complete

  // NOTE: skipping test for passing a value to push. Not applicable.

  @Test
  public void removeCallbackIsHit()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    WriteFuture writeFuture = new WriteFuture(ref, 42);
    writeFuture.timedGet();

    ReadFuture readFuture =
        new ReadFuture(
            ref,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                return events.get(events.size() - 1).getSnapshot().getValue() == null;
              }
            });

    final Semaphore callbackHit = new Semaphore(0);
    ref.removeValue(
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
            assertEquals(ref, callbackRef);
            callbackHit.release(1);
          }
        });

    readFuture.timedGet();
    assertTrue(callbackHit.tryAcquire(1, IntegrationTestValues.getTimeout(), MILLISECONDS));
    // We test the value in the completion condition
  }

  @Test
  public void removeCallbackIsHitForNodesThatAreAlreadyRemoved()
      throws DatabaseException, InterruptedException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore callbackHit = new Semaphore(0);
    ref.removeValue(
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
            assertEquals(ref, callbackRef);
            callbackHit.release(1);
          }
        });
    ref.removeValue(
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
            assertEquals(ref, callbackRef);
            callbackHit.release(1);
          }
        });

    assertTrue(
        callbackHit.tryAcquire(2, IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS));
  }

  @Test
  public void usingNumbersAsKeysDoesntCreateHugeSparseArrays()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.child("3024").setValue(5);
    ReadFuture future = new ReadFuture(ref);

    List<EventRecord> events = future.timedGet();
    assertFalse(events.get(0).getSnapshot().getValue() instanceof List);
  }

  @Test
  public void onceWithCallbackHitsServerToGetData()
      throws DatabaseException, InterruptedException, ExecutionException, TimeoutException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(3);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    DatabaseReference reader2 = refs.get(2);

    final Semaphore semaphore = new Semaphore(0);
    reader.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals(null, snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Shouldn't happen");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
    new WriteFuture(writer, 42).timedGet();

    reader2.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals(42L, snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Shouldn't happen");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  // NOTE: skipping forEach abort test. Not relevant, we return an iterable that can be stopped any
  // time.

  @Test
  public void setAndThenListenForValueEvents()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, "cabbage").timedGet();
    EventRecord event = new ReadFuture(ref).timedGet().get(0);

    assertEquals("cabbage", event.getSnapshot().getValue());
  }

  @Test
  public void hasChildrenWorksCorrectly()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("one", 42)
            .put("two", new MapBuilder().put("a", 5).build())
            .put("three", new MapBuilder().put("a", 5).put("b", 6).build())
            .build());
    final AtomicBoolean removedTwo = new AtomicBoolean(false);
    ReadFuture readFuture =
        new ReadFuture(
            ref,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (removedTwo.compareAndSet(false, true)) {
                  // removedTwo did equal false, now equals true
                  try {
                    ref.child("two").removeValue();
                  } catch (DatabaseException e) {
                    throw new AssertionError("Should not fail", e);
                  }
                }
                return events.size() == 2;
              }
            });

    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot firstSnap = events.get(0).getSnapshot();
    assertEquals(3, firstSnap.getChildrenCount());
    assertEquals(0, firstSnap.child("one").getChildrenCount());
    assertEquals(1, firstSnap.child("two").getChildrenCount());
    assertEquals(2, firstSnap.child("three").getChildrenCount());
    assertEquals(0, firstSnap.child("four").getChildrenCount());

    DataSnapshot secondSnap = events.get(1).getSnapshot();
    assertEquals(2, secondSnap.getChildrenCount());
    assertEquals(0, secondSnap.child("two").getChildrenCount());
  }

  @Test
  public void setANodeWithChildrenToAPrimitiveThenBack()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    final DatabaseReference writer = refs.get(1);

    final Map<String, Object> json = new MapBuilder().put("a", 5L).put("b", 6L).build();
    final long primitive = 76L;

    new WriteFuture(writer, json).timedGet();

    final AtomicBoolean sawJson = new AtomicBoolean(false);
    final AtomicBoolean sawPrimitive = new AtomicBoolean(false);

    ReadFuture readFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (sawJson.compareAndSet(false, true)) {
                  try {
                    writer.setValue(primitive);
                  } catch (DatabaseException e) {
                    fail("Shouldn't happen: " + e.toString());
                  }
                } else {
                  // Saw the json already
                  if (sawPrimitive.compareAndSet(false, true)) {
                    try {
                      writer.setValue(json);
                    } catch (DatabaseException e) {
                      fail("Shouldn't happen: " + e.toString());
                    }
                  }
                }
                return events.size() == 3;
              }
            });

    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot readSnap = events.get(0).getSnapshot();
    assertTrue(readSnap.hasChildren());
    DeepEquals.assertEquals(json, readSnap.getValue());

    readSnap = events.get(1).getSnapshot();
    assertFalse(readSnap.hasChildren());
    assertEquals(primitive, readSnap.getValue());

    readSnap = events.get(2).getSnapshot();
    assertTrue(readSnap.hasChildren());
    DeepEquals.assertEquals(json, readSnap.getValue());
  }

  @Test
  public void writeLeafNodeRemoveItTryToAddChildToRemovedNode()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    writer.setValue(5);
    writer.removeValue();
    new WriteFuture(writer.child("abc"), 5).timedGet();

    DataSnapshot snap = new ReadFuture(reader).timedGet().get(0).getSnapshot();

    assertEquals(5L, ((Map) snap.getValue()).get("abc"));
  }

  @Test
  public void listenForValueThenWriteOnANodeWithExistingData()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    new WriteFuture(writer, new MapBuilder().put("a", 5).put("b", 2).build()).timedGet();

    ReadFuture readFuture = new ReadFuture(reader);

    // Slight race condition. We're banking on this local set being processed before the
    // network catches up with the writer's broadcast.
    reader.child("a").setValue(10);

    EventRecord event = readFuture.timedGet().get(0);

    assertEquals(10L, event.getSnapshot().child("a").getValue());
  }

  @Test
  public void setPriorityOnNonexistentNodeFails() throws DatabaseException, InterruptedException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    ref.setPriority(
        1,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
            assertEquals(ref, callbackRef);
            assertNotNull(error);
            semaphore.release(1);
          }
        });

    assertTrue(semaphore.tryAcquire(1, IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS));
  }

  @Test
  public void setPriorityOnExistingNodeSucceeds() throws DatabaseException, InterruptedException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue("hello!");
    final Semaphore semaphore = new Semaphore(0);
    ref.setPriority(
        10,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference callbackRef) {
            assertEquals(ref, callbackRef);
            assertNull(error);
            semaphore.release(1);
          }
        });

    assertTrue(semaphore.tryAcquire(1, IntegrationTestValues.getTimeout(), TimeUnit.MILLISECONDS));
  }

  @Test
  public void setWithPrioritySetsPriorityAndValue()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    ReadFuture readFuture = ReadFuture.untilNonNull(ref1);

    new WriteFuture(ref1, "hello", 5).timedGet();
    List<EventRecord> result = readFuture.timedGet();
    DataSnapshot snap = result.get(result.size() - 1).getSnapshot();
    assertEquals(5.0, snap.getPriority());
    assertEquals("hello", snap.getValue());

    result = ReadFuture.untilNonNull(ref2).timedGet();
    snap = result.get(result.size() - 1).getSnapshot();
    assertEquals(5.0, snap.getPriority());
    assertEquals("hello", snap.getValue());
  }

  // NOTE: skipped test of getPriority on snapshot. Tested above

  // NOTE: skipped test of immediately visible priority changes. Tested above

  @Test
  public void setOverwritesPriorityOfTopLevelNodesAndSubnodes()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    ref1.setValue(new MapBuilder().put("a", 5).build());
    ref1.setPriority(10);
    ref1.child("a").setPriority(18);
    new WriteFuture(ref1, new MapBuilder().put("a", 7).build()).timedGet();

    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();

    assertNull(snap.getPriority());
    assertNull(snap.child("a").getPriority());
  }

  @Test
  public void setWithPriorityOfALeafSavesCorrectly()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1, "testleaf", "992").timedGet();

    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();

    assertEquals("992", snap.getPriority());
  }

  @Test
  public void setPriorityOfAnObjectSavesCorrectly()
      throws DatabaseException, ExecutionException, TimeoutException, InterruptedException,
          TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1, new MapBuilder().put("a", 5).build(), "991").timedGet();

    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();

    assertEquals("991", snap.getPriority());
  }

  // NOTE: skipping a test about following setPriority with set, it's tested above

  @Test
  public void getPriorityReturnsTheCorrectType()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 7);

    ref.setValue("a");
    ref.setValue("b", 5);
    ref.setValue("c", "6");
    ref.setValue("d", 7);
    ref.setValue(new MapBuilder().put(".value", "e").put(".priority", 8).build());
    ref.setValue(new MapBuilder().put(".value", "f").put(".priority", "8").build());
    ref.setValue(new MapBuilder().put(".value", "g").put(".priority", null).build());

    List<EventRecord> events = readFuture.timedGet();
    assertNull(events.get(0).getSnapshot().getPriority());
    assertEquals(5.0, events.get(1).getSnapshot().getPriority());
    assertEquals("6", events.get(2).getSnapshot().getPriority());
    assertEquals(7.0, events.get(3).getSnapshot().getPriority());
    assertEquals(8.0, events.get(4).getSnapshot().getPriority());
    assertEquals("8", events.get(5).getSnapshot().getPriority());
    assertNull(events.get(6).getSnapshot().getPriority());
  }

  @Test
  public void normalizeDifferentIntegerAndDoubleValues()
      throws DatabaseException, InterruptedException, TimeoutException, TestFailure {
    final long intMaxPlusOne = 2147483648L;

    DatabaseReference node = IntegrationTestHelpers.getRandomNode();

    Object[] writtenValues = {
      intMaxPlusOne,
      (double) intMaxPlusOne,
      -intMaxPlusOne,
      (double) -intMaxPlusOne,
      Integer.MAX_VALUE,
      0L,
      0.0,
      -0.0f,
      0
    };

    Object[] readValues = {intMaxPlusOne, -intMaxPlusOne, (long) Integer.MAX_VALUE, 0L};

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(node, readValues.length);

    for (Object value : writtenValues) {
      node.setValue(value);
    }

    List<EventRecord> events = readFuture.timedGet();

    for (int i = 0; i < readValues.length; ++i) {
      assertEquals(readValues[i], events.get(i).getSnapshot().getValue());
    }
  }

  @Test
  public void exportFormatIncludesPriorities()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> expected =
        new MapBuilder()
            .put(
                "foo",
                new MapBuilder()
                    .put("bar", new MapBuilder().put(".priority", 7.0).put(".value", 5L).build())
                    .put(".priority", "hi")
                    .build())
            .build();
    ReadFuture readFuture = ReadFuture.untilNonNull(ref);
    ref.setValue(expected);
    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();
    Object result = snap.getValue(true);
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void priorityIsOverwrittenByServerPriority()
      throws DatabaseException, TimeoutException, InterruptedException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    final DatabaseReference ref2 = refs.get(1);

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref1, 2);

    ref1.setValue("hi", 100);

    new ReadFuture(
            ref2,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                DataSnapshot snap = events.get(events.size() - 1).getSnapshot();
                Object priority = snap.getPriority();
                if (priority != null && priority.equals(100.0)) {
                  try {
                    ref2.setValue("whatever");
                  } catch (DatabaseException e) {
                    fail("Shouldn't happen: " + e.toString());
                  }
                  return true;
                }
                return false;
              }
            })
        .timedGet();

    List<EventRecord> events = readFuture.timedGet();
    assertEquals(100.0, events.get(0).getSnapshot().getPriority());
    assertNull(events.get(1).getSnapshot().getPriority());
  }

  @Test
  public void largeNumericPrioritiesWork()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException,
          ExecutionException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    double priority = 1356721306842.0;

    new WriteFuture(ref1, 5, priority).timedGet();
    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();
    assertEquals(priority, snap.getPriority());
  }

  @Test
  public void urlEncodingAndDecodingWorks()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    DatabaseReference ref =
        new DatabaseReference(
            IntegrationTestValues.getNamespace() + "/a%b&c@d/space: /non-ascii:ø", ctx);
    String result = ref.toString();
    String encoded =
        IntegrationTestValues.getNamespace() + "/a%25b%26c%40d/space%3A%20/non-ascii%3A%C3%B8";
    assertEquals(encoded, result);

    String child = "" + new Random().nextInt(100000000);
    new WriteFuture(ref.child(child), "testdata").timedGet();
    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.child(child));
    assertEquals("testdata", snap.getValue());
  }

  @Test
  public void nameWorksForRootAndNonRootLocations()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    DatabaseReference ref = new DatabaseReference(IntegrationTestValues.getNamespace(), ctx);
    assertNull(ref.getKey());
    assertEquals("a", ref.child("a").getKey());
    assertEquals("c", ref.child("b/c").getKey());
  }

  @Test
  public void nameAndRefWorkForSnapshots()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    DatabaseReference ref = new DatabaseReference(IntegrationTestValues.getNamespace(), ctx);
    // Clear any data there
    new WriteFuture(ref, new MapBuilder().put("foo", 10).build()).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    assertNull(snap.getKey());
    assertEquals(ref.toString(), snap.getRef().toString());
    DataSnapshot childSnap = snap.child("a");
    assertEquals("a", childSnap.getKey());
    assertEquals(ref.child("a").toString(), childSnap.getRef().toString());
    childSnap = childSnap.child("b/c");
    assertEquals("c", childSnap.getKey());
    assertEquals(ref.child("a/b/c").toString(), childSnap.getRef().toString());
  }

  @Test
  public void parentWorksForRootAndNonRootLocations() throws DatabaseException {
    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    DatabaseReference ref = new DatabaseReference(IntegrationTestValues.getNamespace(), ctx);
    assertNull(ref.getParent());
    DatabaseReference child = ref.child("a");
    assertEquals(ref, child.getParent());
    child = ref.child("a/b/c");
    assertEquals(ref, child.getParent().getParent().getParent());
  }

  @Test
  public void rootWorksForRootAndNonRootLocations() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref = ref.getRoot();
    assertEquals(IntegrationTestValues.getNamespace(), ref.toString());
    ref = ref.getRoot(); // Should be a no-op
    assertEquals(IntegrationTestValues.getNamespace(), ref.toString());
  }

  // NOTE: skip test about child accepting numbers. Not applicable in a type-safe language

  // NOTE: skip test on numeric keys. We throw an exception if they aren't strings

  @Test
  public void setAChildAndListenAtTheRoot()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1.child("foo"), "hi").timedGet();
    DataSnapshot snap = new ReadFuture(ref2).timedGet().get(0).getSnapshot();
    Map<String, Object> expected = new MapBuilder().put("foo", "hi").build();
    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void accessingInvalidPathsShouldThrow() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);

    List<String> badPaths = Arrays.asList(".test", "test.", "fo$o", "[what", "ever]", "ha#sh");

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);

    for (String path : badPaths) {
      try {
        ref.child(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        new DatabaseReference(IntegrationTestValues.getNamespace() + "/" + path, ctx);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        new DatabaseReference(IntegrationTestValues.getNamespace() + "/tests/" + path, ctx);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        snap.child(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        snap.hasChild(path);
        fail("Should not be a valid path: " + path);
      } catch (DatabaseException e) {
        // No-op, expected
      }
    }
  }

  @Test
  public void invalidKeysThrowErrors() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    List<String> badKeys =
        Arrays.asList(
            ".test", "test.", "fo$o", "[what", "ever]", "ha#sh", "/thing", "thi/ing", "thing/", "");

    List<Object> badObjects = new ArrayList<Object>();
    for (String key : badKeys) {
      badObjects.add(new MapBuilder().put(key, "test").build());
      badObjects.add(
          new MapBuilder().put("deeper", new MapBuilder().put(key, "test").build()).build());
    }

    // Skipping 'push' portion, that api doesn't exist in Java client

    for (Object badObject : badObjects) {
      try {
        ref.setValue(badObject);
        fail("Should not be a valid object: " + badObject);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        ref.onDisconnect().setValue(badObject);
        fail("Should not be a valid object: " + badObject);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      // TODO: Skipping transaction portion for now
    }
  }

  @Test
  public void invalidUpdateThrowErrors() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    List<Map<String, Object>> badUpdates =
        Arrays.asList(
            new MapBuilder().put("/", "t").put("a", "t").build(),
            new MapBuilder().put("a", "t").put("a/b", "t").build(),
            new MapBuilder().put("/a", "t").put("a/b", "t").build(),
            new MapBuilder().put("/a/b", "t").put("a", "t").build(),
            new MapBuilder().put("/a/b", "t").put("/a/b/.priority", 1.0).build(),
            new MapBuilder()
                .put("/a/b/" + ServerValues.NAME_SUBKEY_SERVERVALUE, ServerValues.NAME_OP_TIMESTAMP)
                .build(),
            new MapBuilder().put("/a/b/.value", "t").build(),
            new MapBuilder().put("/a/b/.priority", new MapBuilder().put("x", "y").build()).build());
    for (Map<String, Object> badUpdate : badUpdates) {
      try {
        ref.updateChildren(badUpdate);
        fail("Should not be a valid update: " + badUpdate);
      } catch (DatabaseException e) {
        // No-op, expected
      }

      try {
        ref.onDisconnect().updateChildren(badUpdate);
        fail("Should not be a valid object: " + badUpdate);
      } catch (DatabaseException e) {
        // No-op, expected
      }
    }
  }

  @Test
  public void asciiControlCharactersIllegal()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference node = IntegrationTestHelpers.getRandomNode();
    // Test all controls characters PLUS 0x7F (127).
    for (int i = 0; i <= 32; i++) {
      String ch = new String(Character.toChars(i < 32 ? i : 127));
      HashMap obj = IntegrationTestHelpers.buildObjFromPath(new Path(ch), "test_value");
      try {
        node.setValue(obj);
        fail("Ascii control character should not be allowed in path.");
      } catch (DatabaseException e) {
        // expected
      }
    }
  }

  @Test
  public void invalidDoubleValues()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference node = IntegrationTestHelpers.getRandomNode();
    Object[] invalidValues =
        new Object[] {
          Double.NEGATIVE_INFINITY,
          Double.POSITIVE_INFINITY,
          Double.NaN,
          Float.NEGATIVE_INFINITY,
          Float.POSITIVE_INFINITY,
          Float.NaN
        };
    for (Object invalidValue : invalidValues) {
      try {
        node.setValue(invalidValue);
        fail("NaN or Inf are not allowed as values.");
      } catch (DatabaseException expected) {
        assertEquals("Invalid value: Value cannot be NaN, Inf or -Inf.", expected.getMessage());
      }
    }
  }

  @Test
  public void pathKeyLengthLimits()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    final int maxPathLengthBytes = 768;
    final int maxPathDepth = 32;
    final String fire = new String(Character.toChars(128293));
    final String base = new String(Character.toChars(26594));

    List<String> goodKeys =
        Arrays.asList(
            IntegrationTestHelpers.repeatedString("k", maxPathLengthBytes - 1),
            IntegrationTestHelpers.repeatedString(fire, maxPathLengthBytes / 4 - 1),
            IntegrationTestHelpers.repeatedString(base, maxPathLengthBytes / 3 - 1),
            IntegrationTestHelpers.repeatedString("key/", maxPathDepth - 1) + "key");

    class BadGroup {
      String expectedError;
      List<String> keys;

      BadGroup(String expectedError, List<String> keys) {
        this.expectedError = expectedError;
        this.keys = keys;
      }
    }

    List<BadGroup> badGroups =
        Arrays.asList(
            new BadGroup(
                "key path longer than 768 bytes",
                Arrays.asList(
                    IntegrationTestHelpers.repeatedString("k", maxPathLengthBytes),
                    IntegrationTestHelpers.repeatedString(fire, maxPathLengthBytes / 4),
                    IntegrationTestHelpers.repeatedString(base, maxPathLengthBytes / 3),
                    IntegrationTestHelpers.repeatedString("j", maxPathLengthBytes / 2)
                        + '/'
                        + IntegrationTestHelpers.repeatedString("k", maxPathLengthBytes / 2))),
            new BadGroup(
                "Path specified exceeds the maximum depth",
                Arrays.asList(
                    IntegrationTestHelpers.repeatedString("key/", maxPathDepth) + "key")));

    DatabaseReference node = IntegrationTestHelpers.getRandomNode().getRoot();

    // Ensure "good keys" work from the root.
    for (String key : goodKeys) {
      Path path = new Path(key);
      HashMap obj = IntegrationTestHelpers.buildObjFromPath(path, "test_value");
      node.setValue(obj);
      ReadFuture future = ReadFuture.untilNonNull(node);
      assertEquals("test_value", IntegrationTestHelpers.applyPath(future.waitForLastValue(), path));

      node.child(key).setValue("another_value");
      future = ReadFuture.untilNonNull(node.child(key));
      Assert.assertEquals("another_value", future.waitForLastValue());

      node.updateChildren(obj);
      future = ReadFuture.untilNonNull(node);
      assertEquals("test_value", IntegrationTestHelpers.applyPath(future.waitForLastValue(), path));
    }

    // Ensure "good keys" fail when created from child node (relative paths too long).
    DatabaseReference nodeChild = IntegrationTestHelpers.getRandomNode();
    for (String key : goodKeys) {
      HashMap obj = IntegrationTestHelpers.buildObjFromPath(new Path(key), "test_value");
      try {
        nodeChild.setValue(obj);
        fail("Too-long path for setValue should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
      try {
        nodeChild.child(key).setValue("another_value");
        fail("Too-long path before setValue should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
      try {
        nodeChild.updateChildren(obj);
        fail("Too-long path for updateChildren should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
      try {
        Map<String, Object> deepUpdate = new MapBuilder().put(key, "test_value").build();
        nodeChild.updateChildren(deepUpdate);
        fail("Too-long path in deep update for updateChildren should throw exception.");
      } catch (DatabaseException e) {
        // expected
      }
    }

    for (BadGroup badGroup : badGroups) {
      for (String key : badGroup.keys) {
        HashMap obj = IntegrationTestHelpers.buildObjFromPath(new Path(key), "test_value");
        try {
          node.setValue(obj);
          fail("Expected setValue(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.child(key).setValue("another_value");
          fail("Expected child(\"" + key + "\").setValue() to throw exception: " + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.updateChildren(obj);
          fail("Expected updateChildrean(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          Map<String, Object> deepUpdate = new MapBuilder().put(key, "test_value").build();
          node.updateChildren(deepUpdate);
          fail("Expected updateChildrean(bad deep update key) to throw exception: " + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.onDisconnect().setValue(obj);
          fail("Expected onDisconnect.setValue(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          node.onDisconnect().updateChildren(obj);
          fail("Expected onDisconnect.updateChildren(bad key) to throw exception: " + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
        try {
          Map<String, Object> deepUpdate = new MapBuilder().put(key, "test_value").build();
          node.onDisconnect().updateChildren(deepUpdate);
          fail(
              "Expected onDisconnect.updateChildren(bad deep update key) to throw exception: "
                  + key);
        } catch (DatabaseException e) {
          IntegrationTestHelpers.assertContains(e.getMessage(), badGroup.expectedError);
        }
      }
    }
  }

  @Test
  public void namespaceAreCaseInsensitive()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseConfig ctx1 = IntegrationTestHelpers.getContext(0);
    DatabaseConfig ctx2 = IntegrationTestHelpers.getContext(1);

    String child = "" + new Random().nextInt(100000000);
    DatabaseReference ref1 =
        new DatabaseReference(IntegrationTestValues.getNamespace() + "/" + child, ctx1);
    DatabaseReference ref2 =
        new DatabaseReference(
            "http://"
                + IntegrationTestValues.getProjectId().toUpperCase()
                + "."
                + IntegrationTestValues.getServer()
                + "/"
                + child,
            ctx2);

    new WriteFuture(ref1, "testdata").timedGet();
    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref2);
    assertEquals("testdata", snap.getValue());
  }

  @Test
  public void namespacesAreCaseInsensitiveInToString() throws DatabaseException {
    DatabaseConfig ctx = IntegrationTestHelpers.getContext(0);
    DatabaseReference ref1 = new DatabaseReference(IntegrationTestValues.getNamespace(), ctx);
    DatabaseReference ref2 =
        new DatabaseReference(
            "http://"
                + IntegrationTestValues.getProjectId().toUpperCase()
                + "."
                + IntegrationTestValues.getServer(),
            ctx);

    assertEquals(ref1.toString(), ref2.toString());
  }

  // NOTE: skipping test on re-entrant remove call. Not an issue w/ work queue architecture

  @Test
  public void setANodeWithAQuotedKey()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> expected = new MapBuilder().put("\"herp\"", 1234L).build();
    new WriteFuture(ref, expected).timedGet();
    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void setAChildWithAQuote()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = new ReadFuture(ref);
    new WriteFuture(ref.child("\""), 1).timedGet();
    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();
    assertEquals(1L, snap.child("\"").getValue());
  }

  @Test
  public void emptyChildrenGetValueEventBeforeParent()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("a/aa/aaa"))
            .addValueExpectation(ref.child("a/aa"))
            .addValueExpectation(ref.child("a"))
            .startListening();
    ref.setValue(new MapBuilder().put("b", 5).build());

    assertTrue(helper.waitForEvents());
    helper.cleanup();
  }

  // NOTE: skipping test for recursive sets. Java does not have reentrant firebase api calls

  @Test
  public void onAfterSetWaitsForLatestData()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    new WriteFuture(ref1, 5).timedGet();
    new WriteFuture(ref2, 42).timedGet();

    DataSnapshot snap = new ReadFuture(ref1).timedGet().get(0).getSnapshot();
    assertEquals(42L, snap.getValue());
  }

  @Test
  public void onceWaitsForLatestDataEachTime()
      throws DatabaseException, InterruptedException, TestFailure, ExecutionException,
          TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);

    final Semaphore semaphore = new Semaphore(0);
    ref1.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertNull(snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    new WriteFuture(ref2, 5).timedGet();
    ref1.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals(5L, snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    new WriteFuture(ref2, 42).timedGet();

    ref1.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals(42L, snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not be cancelled");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void memoryFreeingOnUnlistenDoesNotCorruptData()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException,
          ExecutionException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference ref1 = refs.get(0);
    DatabaseReference ref2 = refs.get(1);
    DatabaseReference other = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean hasRun = new AtomicBoolean(false);
    ValueEventListener listener =
        ref1.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (hasRun.compareAndSet(false, true)) {
                  assertNull(snapshot.getValue());
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not fail");
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    new WriteFuture(ref1, "test").timedGet();
    ref1.removeEventListener(listener);
    new WriteFuture(other, "hello").timedGet();
    ref1.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals("test", snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not fail");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    ref2.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals("test", snapshot.getValue());
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {
            fail("Should not fail");
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void updateRaisesCorrectLocalEvents()
      throws DatabaseException, InterruptedException, TestFailure, TimeoutException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 2);

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build());

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("a"))
            .addValueExpectation(ref.child("d"))
            .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "a")
            .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "d")
            .addValueExpectation(ref)
            .startListening(true);

    ref.updateChildren(new MapBuilder().put("a", 4).put("d", 1).build());
    helper.waitForEvents();
    List<EventRecord> events = readFuture.timedGet();
    helper.cleanup();

    Map<String, Object> expected =
        new MapBuilder().put("a", 4L).put("b", 2L).put("c", 3L).put("d", 1L).build();
    Object result = events.get(events.size() - 1).getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void updateRaisesCorrectRemoteEvents()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    new WriteFuture(
            writer, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build())
        .timedGet();

    EventHelper helper =
        new EventHelper()
            .addValueExpectation(reader.child("a"))
            .addValueExpectation(reader.child("d"))
            .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "a")
            .addChildExpectation(reader, Event.EventType.CHILD_CHANGED, "d")
            .addValueExpectation(reader)
            .startListening(true);

    writer.updateChildren(new MapBuilder().put("a", 4).put("d", 1).build());
    helper.waitForEvents();
    helper.cleanup();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(reader);
    Map<String, Object> expected =
        new MapBuilder().put("a", 4L).put("b", 2L).put("c", 3L).put("d", 1L).build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void updateRaisesChildEventsOnNewListener()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    EventHelper helper =
        new EventHelper()
            .addValueExpectation(ref.child("a"))
            .addValueExpectation(ref.child("d"))
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "a")
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "d")
            .addValueExpectation(ref.child("c"))
            .addValueExpectation(ref.child("d"))
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "c")
            .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "d")
            .startListening();

    ref.updateChildren(new MapBuilder().put("a", 11).put("d", 44).build());
    ref.updateChildren(new MapBuilder().put("c", 33).put("d", 45).build());
    helper.waitForEvents();
  }

  @Test
  public void updateAfterSetLeafNodeWorks()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);
    final Map<String, Object> expected = new MapBuilder().put("a", 1L).put("b", 2L).build();

    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (DeepEquals.deepEquals(snapshot.getValue(), expected)) {
              semaphore.release();
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });
    ref.setValue(42);
    ref.updateChildren(expected);

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void updateChangesAreStoredCorrectlyByTheServer()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    new WriteFuture(
            writer, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build())
        .timedGet();

    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(
        new MapBuilder().put("a", 42).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(reader);
    Map<String, Object> expected =
        new MapBuilder().put("a", 42L).put("b", 2L).put("c", 3L).put("d", 4L).build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void updateDoesntAffectPriorityLocally()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 2);

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build(), "testpri");
    ref.updateChildren(new MapBuilder().put("a", 4).build());

    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot snap = events.get(0).getSnapshot();
    assertEquals("testpri", snap.getPriority());

    snap = events.get(1).getSnapshot();
    assertEquals(4L, snap.child("a").getValue());
    assertEquals("testpri", snap.getPriority());
  }

  @Test
  public void updateDoesntAffectPriorityRemotely()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    new WriteFuture(writer, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build(), "testpri")
        .timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(reader);
    assertEquals("testpri", snap.getPriority());

    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(
        new MapBuilder().put("a", 4).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
    snap = IntegrationTestHelpers.getSnap(reader);
    assertEquals("testpri", snap.getPriority());
  }

  @Test
  public void updateReplacesChildren()
      throws DatabaseException, InterruptedException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(writer, 2);

    writer.setValue(
        new MapBuilder().put("a", new MapBuilder().put("aa", 1).put("ab", 2).build()).build());
    final Semaphore semaphore = new Semaphore(0);
    Map<String, Object> expected =
        new MapBuilder().put("a", new MapBuilder().put("aa", 1L).build()).build();
    writer.updateChildren(
        expected,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(reader);
    DeepEquals.assertEquals(expected, snap.getValue());

    snap = readFuture.timedGet().get(1).getSnapshot();
    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void deepUpdateWorks()
      throws DatabaseException, InterruptedException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    ReadFuture readFuture = ReadFuture.untilCount(writer, 2);

    writer.setValue(
        new MapBuilder().put("a", new MapBuilder().put("aa", 1).put("ab", 2).build()).build());
    Map<String, Object> expected =
        new MapBuilder().put("a", new MapBuilder().put("aa", 10L).put("ab", 20L).build()).build();
    Map<String, Object> update =
        new MapBuilder()
            .put("a/aa", 10)
            .put(".priority", 3.0)
            .put("a/ab", new MapBuilder().put(".priority", 2.0).put(".value", 20).build())
            .build();

    final Semaphore semaphore = new Semaphore(0);
    writer.updateChildren(
        update,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(reader);
    DeepEquals.assertEquals(expected, snap.getValue());
    assertEquals(3.0, snap.getPriority());

    snap = readFuture.timedGet().get(1).getSnapshot();
    DeepEquals.assertEquals(expected, snap.getValue());
    assertEquals(3.0, snap.getPriority());

    snap = IntegrationTestHelpers.getSnap(reader.child("a/ab"));
    assertEquals(2.0, snap.getPriority());
  }

  // NOTE: skipping test of update w/ scalar value. Disallowed by the type system

  @Test
  public void updateWithNoChangesWorks() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    ref.updateChildren(
        new HashMap<String, Object>(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  // TODO: consider implementing update stress test
  // NOTE: skipping update stress test for now

  @Test
  public void updateFiresCorrectEventWhenAChildIsDeleted()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    ReadFuture writerFuture = ReadFuture.untilCountAfterNull(writer, 2);
    new WriteFuture(writer, new MapBuilder().put("a", 12).put("b", 6).build()).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  semaphore.release();
                }
                return events.size() == 2;
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    writer.updateChildren(new MapBuilder().put("a", null).build());
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    Map<String, Object> expected = new MapBuilder().put("b", 6L).build();
    DeepEquals.assertEquals(expected, snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void updateFiresCorrectEventOnNewChildren()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore readerInitializedSemaphore = new Semaphore(0);
    final Semaphore writerInitializedSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  writerInitializedSemaphore.release();
                }
                return events.size() == 2;
              }
            });
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  readerInitializedSemaphore.release();
                }
                return events.size() == 2;
              }
            });

    IntegrationTestHelpers.waitFor(readerInitializedSemaphore);
    IntegrationTestHelpers.waitFor(writerInitializedSemaphore);

    writer.updateChildren(new MapBuilder().put("a", 42).build());
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    Map<String, Object> expected = new MapBuilder().put("a", 42L).build();
    DeepEquals.assertEquals(expected, snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void updateFiresCorrectEventWhenAllChildrenAreDeleted()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    ReadFuture writerFuture = ReadFuture.untilCountAfterNull(writer, 2);
    new WriteFuture(writer, new MapBuilder().put("a", 12).build()).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  semaphore.release();
                }
                return events.size() == 2;
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    writer.updateChildren(new MapBuilder().put("a", null).build());
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    assertNull(snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    assertNull(snap.getValue());
  }

  @Test
  public void updateFiresCorrectEventOnChangedChildren()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    ReadFuture writerFuture = ReadFuture.untilCountAfterNull(writer, 2);
    new WriteFuture(writer, new MapBuilder().put("a", 12).build()).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  semaphore.release();
                }
                return events.size() == 2;
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    writer.updateChildren(new MapBuilder().put("a", 11).build());
    DataSnapshot snap = writerFuture.timedGet().get(1).getSnapshot();

    Map<String, Object> expected = new MapBuilder().put("a", 11L).build();
    DeepEquals.assertEquals(expected, snap.getValue());

    snap = readerFuture.timedGet().get(1).getSnapshot();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void updateOfPriorityWorks() throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map writeValue = new MapBuilder().put("a", 5).put(".priority", "pri1").build();

    Map updateValue =
        new MapBuilder()
            .put("a", 6)
            .put(".priority", "pri2")
            .put("b", new MapBuilder().put(".priority", "pri3").put("c", 10).build())
            .build();

    final Semaphore semaphore = new Semaphore(0);
    writer.setValue(writeValue);
    writer.updateChildren(
        updateValue,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            assertNull(error);
            semaphore.release(1);
          }
        });
    IntegrationTestHelpers.waitFor(semaphore);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(reader);
    assertEquals(6L, snap.child("a").getValue());
    assertEquals("pri2", snap.getPriority());
    assertEquals("pri3", snap.child("b").getPriority());
    assertEquals(10L, snap.child("b/c").getValue());
  }

  // NOTE: skipping test for circular data structures. StackOverflowError is thrown, they'll see it.

  // NOTE: skipping test for creating a child name 'hasOwnProperty'

  // NOTE: skipping nesting tests. The Java api is async and doesn't do reentrant callbacks

  @Test
  public void parentDeleteShadowsChildListeners()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference deleter = refs.get(1);

    String childName = writer.push().getKey();

    final AtomicBoolean initialized = new AtomicBoolean(false);
    final AtomicBoolean called = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener listener =
        deleter
            .child(childName)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    Object val = snapshot.getValue();
                    boolean inited = initialized.get();
                    if (val == null && inited) {
                      assertTrue(called.compareAndSet(false, true));
                    } else if (!inited && val != null) {
                      assertTrue(initialized.compareAndSet(false, true));
                      semaphore.release(1);
                    }
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });
    writer.child(childName).setValue("foo");
    // Make sure we get the data in the listener before we delete it
    IntegrationTestHelpers.waitFor(semaphore);

    deleter.removeValue(
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
    assertTrue(called.get());
    deleter.child(childName).removeEventListener(listener);
  }

  @Test
  public void parentDeleteShadowsNonDefaultChildListeners()
      throws DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference deleter = refs.get(1);

    String childName = writer.push().getKey();

    final AtomicBoolean queryCalled = new AtomicBoolean(false);
    final AtomicBoolean deepChildCalled = new AtomicBoolean(false);

    ValueEventListener queryListener =
        deleter
            .child(childName)
            .startAt(null, "b")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    assertNull(snapshot.getValue());
                    assertTrue(queryCalled.compareAndSet(false, true));
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    fail("Should not be cancelled");
                  }
                });

    ValueEventListener listener =
        deleter
            .child(childName)
            .child("a")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    assertNull(snapshot.getValue());
                    assertTrue(deepChildCalled.compareAndSet(false, true));
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    fail("Should not be cancelled");
                  }
                });

    writer.child(childName).setValue("foo");
    final Semaphore semaphore = new Semaphore(0);
    deleter.removeValue(
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            semaphore.release(1);
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
    assertTrue(queryCalled.get());
    assertTrue(deepChildCalled.get());
    deleter.child(childName).startAt(null, "b").removeEventListener(queryListener);
    deleter.child(childName).child("a").removeEventListener(listener);
  }

  @Test
  public void testServerTimestampSetWithPriorityRemoteEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                Object snap = events.get(events.size() - 1).getSnapshot().getValue();
                return snap != null;
              }
            });

    // Wait for initial (null) value.
    IntegrationTestHelpers.waitFor(valSemaphore, 1);

    Map<String, Object> initialValues =
        new MapBuilder()
            .put("a", ServerValue.TIMESTAMP)
            .put(
                "b",
                new MapBuilder()
                    .put(".value", ServerValue.TIMESTAMP)
                    .put(".priority", ServerValue.TIMESTAMP)
                    .build())
            .build();

    writer.setValue(
        initialValues,
        ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = readerFuture.timedGet().get(1);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a").getValue().getClass(), Long.class);
    assertEquals(snap.getPriority().getClass(), Double.class);
    assertEquals(snap.getPriority(), snap.child("b").getPriority());
    assertEquals(snap.child("a").getValue(), snap.child("b").getValue());
    long drift = System.currentTimeMillis() - Long.parseLong(snap.child("a").getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerTimestampSetPriorityRemoteEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final AtomicLong priority = new AtomicLong(0);
    reader.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            priority.set(((Double) snapshot.getPriority()).longValue());
            valSemaphore.release();
          }
        });

    Map<String, Object> initialValues =
        new MapBuilder()
            .put("a", 1)
            .put("b", new MapBuilder().put(".value", 1).put(".priority", 1).build())
            .build();

    writer.setValue(
        initialValues,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("a")
        .setPriority(
            ServerValue.TIMESTAMP,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    IntegrationTestHelpers.waitFor(valSemaphore);

    long drift = System.currentTimeMillis() - priority.get();
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerTimestampUpdateRemoteEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            reader,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 3;
              }
            });

    // Wait for initial (null) value.
    IntegrationTestHelpers.waitFor(valSemaphore, 1);

    writer
        .child("a/b/d")
        .setValue(
            1,
            ServerValue.TIMESTAMP,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValue =
        new MapBuilder()
            .put(
                "b",
                new MapBuilder()
                    .put("c", ServerValue.TIMESTAMP)
                    .put("d", ServerValue.TIMESTAMP)
                    .build())
            .build();

    writer
        .child("a")
        .updateChildren(
            updatedValue,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                assertNull(error);
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = readerFuture.timedGet().get(2);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a/b/c").getValue().getClass(), Long.class);
    assertEquals(snap.child("a/b/d").getValue().getClass(), Long.class);
    long drift =
        System.currentTimeMillis() - Long.parseLong(snap.child("a/b/c").getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerTimestampSetWithPriorityLocalEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                Object snap = events.get(events.size() - 1).getSnapshot().getValue();
                return snap != null;
              }
            });

    // Wait for initial (null) value.
    IntegrationTestHelpers.waitFor(valSemaphore, 1);

    Map<String, Object> initialValues =
        new MapBuilder()
            .put("a", ServerValue.TIMESTAMP)
            .put(
                "b",
                new MapBuilder()
                    .put(".value", ServerValue.TIMESTAMP)
                    .put(".priority", ServerValue.TIMESTAMP)
                    .build())
            .build();

    writer.setValue(
        initialValues,
        ServerValue.TIMESTAMP,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = writerFuture.timedGet().get(1);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a").getValue().getClass(), Long.class);
    assertEquals(snap.getPriority().getClass(), Double.class);
    assertEquals(snap.getPriority(), snap.child("b").getPriority());
    assertEquals(snap.child("a").getValue(), snap.child("b").getValue());
    long drift = System.currentTimeMillis() - Long.parseLong(snap.child("a").getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerTimestampSetPriorityLocalEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    final AtomicLong priority = new AtomicLong(0);
    writer.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildChanged(DataSnapshot snapshot, String previousChildName) {}

          @Override
          public void onChildRemoved(DataSnapshot snapshot) {}

          @Override
          public void onCancelled(DatabaseError error) {}

          @Override
          public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
            priority.set(((Double) snapshot.getPriority()).longValue());
            valSemaphore.release();
          }
        });

    Map<String, Object> initialValues =
        new MapBuilder()
            .put("a", 1)
            .put("b", new MapBuilder().put(".value", 1).put(".priority", 1).build())
            .build();

    writer.setValue(
        initialValues,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            opSemaphore.release();
          }
        });
    IntegrationTestHelpers.waitFor(opSemaphore);

    writer
        .child("a")
        .setPriority(
            ServerValue.TIMESTAMP,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    IntegrationTestHelpers.waitFor(valSemaphore);

    long drift = System.currentTimeMillis() - priority.get();
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerTimestampUpdateLocalEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);

    final Semaphore opSemaphore = new Semaphore(0);
    final Semaphore valSemaphore = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore.release();
                return events.size() == 4;
              }
            });

    // Wait for initial (null) value.
    IntegrationTestHelpers.waitFor(valSemaphore, 1);

    writer
        .child("a/b/d")
        .setValue(
            1,
            ServerValue.TIMESTAMP,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    Map<String, Object> updatedValue =
        new MapBuilder()
            .put(
                "b",
                new MapBuilder()
                    .put("c", ServerValue.TIMESTAMP)
                    .put("d", ServerValue.TIMESTAMP)
                    .build())
            .build();

    writer
        .child("a")
        .updateChildren(
            updatedValue,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                opSemaphore.release();
              }
            });
    IntegrationTestHelpers.waitFor(opSemaphore);

    EventRecord readerEventRecord = writerFuture.timedGet().get(3);
    DataSnapshot snap = readerEventRecord.getSnapshot();
    assertEquals(snap.child("a/b/c").getValue().getClass(), Long.class);
    assertEquals(snap.child("a/b/d").getValue().getClass(), Long.class);
    long drift =
        System.currentTimeMillis() - Long.parseLong(snap.child("a/b/c").getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerTimestampTransactionLocalEvents()
      throws TestFailure, TimeoutException, DatabaseException, InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(1);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(0);

    final Semaphore valSemaphore1 = new Semaphore(0);
    ReadFuture writerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore1.release();
                return events.size() == 2;
              }
            });

    final Semaphore valSemaphore2 = new Semaphore(0);
    ReadFuture readerFuture =
        new ReadFuture(
            writer,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                valSemaphore2.release();
                return events.size() == 2;
              }
            });

    // Wait for local (null) events.
    IntegrationTestHelpers.waitFor(valSemaphore1);
    IntegrationTestHelpers.waitFor(valSemaphore2);

    writer.runTransaction(
        new Transaction.Handler() {
          @Override
          public Transaction.Result doTransaction(MutableData currentData) {
            try {
              currentData.setValue(ServerValue.TIMESTAMP);
            } catch (DatabaseException e) {
              throw new AssertionError("Should not fail", e);
            }
            return Transaction.success(currentData);
          }

          @Override
          public void onComplete(DatabaseError error, boolean committed, DataSnapshot currentData) {
            if (error != null || !committed) {
              fail("Transaction should succeed");
            }
          }
        });

    // Wait for local events.
    IntegrationTestHelpers.waitFor(valSemaphore1);
    IntegrationTestHelpers.waitFor(valSemaphore2);

    EventRecord writerEventRecord = writerFuture.timedGet().get(1);
    DataSnapshot snap1 = writerEventRecord.getSnapshot();
    assertEquals(snap1.getValue().getClass(), Long.class);
    long drift = System.currentTimeMillis() - Long.parseLong(snap1.getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));

    EventRecord readerEventRecord = readerFuture.timedGet().get(1);
    DataSnapshot snap2 = readerEventRecord.getSnapshot();
    assertEquals(snap2.getValue().getClass(), Long.class);
    drift = System.currentTimeMillis() - Long.parseLong(snap2.getValue().toString());
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void testServerIncrementOverwritesExistingDataOnline()
      throws DatabaseException, TimeoutException, InterruptedException {
    serverIncrementOverwritesExistingData(/* online= */ true);
  }

  @Test
  public void testServerIncrementOverwritesExistingDataOffline()
      throws DatabaseException, TimeoutException, InterruptedException {
    serverIncrementOverwritesExistingData(/* online= */ false);
  }

  public void serverIncrementOverwritesExistingData(boolean online)
      throws DatabaseException, TimeoutException, InterruptedException {
    DatabaseConfig cfg = IntegrationTestHelpers.newTestConfig();
    DatabaseReference ref = IntegrationTestHelpers.rootWithConfig(cfg);
    List<Object> foundValues = new ArrayList<>();
    // Note: all numeric values will be long, so they must be cast before being inserted.
    List<Object> expectedValues = new ArrayList<>();

    // Going offline ensures that local events get queued up before server events
    if (!online) {
      IntegrationTestHelpers.goOffline(cfg);
    }

    // Phaser is the closest built-in to a bidrectional latch. We could use a semaphore with a fixed
    // number of permits, but the test would be fragile since the permit count isn't closely related
    // to the test cases.
    final Phaser latch = new Phaser(0);

    ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                foundValues.add(snapshot.getValue());
                latch.arrive();
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    try {
      // null + incr
      latch.register();
      ref.setValue(ServerValue.increment(1));
      expectedValues.add((long) 1);

      // number + incr
      latch.bulkRegister(2);
      ref.setValue(5);
      ref.setValue(ServerValue.increment(1));
      expectedValues.add((long) 5);
      expectedValues.add((long) 6);

      // string + incr
      latch.bulkRegister(2);
      ref.setValue("hello");
      ref.setValue(ServerValue.increment(1));
      expectedValues.add("hello");
      expectedValues.add((long) 1);

      // object + incr
      latch.bulkRegister(2);
      Map<String, Object> obj = new MapBuilder().put("hello", "world").build();
      ref.setValue(obj);
      ref.setValue(ServerValue.increment(1));
      expectedValues.add(obj);
      expectedValues.add((long) 1);

      latch.awaitAdvanceInterruptibly(0, IntegrationTestValues.getTimeout(), MILLISECONDS);
      assertEquals(expectedValues, foundValues);
    } finally {
      ref.removeEventListener(listener);
      if (!online) {
        IntegrationTestHelpers.goOnline(cfg);
      }
    }
  }

  @Test
  public void testServerIncrementPriorityOnline()
      throws DatabaseException, TimeoutException, InterruptedException {
    serverIncrementPriority(/* online= */ true);
  }

  @Test
  public void testServerIncrementPriorityOffline()
      throws DatabaseException, TimeoutException, InterruptedException {
    serverIncrementPriority(/* online= */ false);
  }

  public void serverIncrementPriority(boolean online)
      throws DatabaseException, TimeoutException, InterruptedException {
    DatabaseConfig cfg = IntegrationTestHelpers.newTestConfig();
    DatabaseReference ref = IntegrationTestHelpers.rootWithConfig(cfg);
    List<Object> foundPriorities = new ArrayList<>();
    // Note: all numeric values will be long or double, so they must be cast before being inserted.
    List<Object> expectedPriorities = new ArrayList<>();

    // Going offline ensures that local events get queued up before server events
    if (!online) {
      IntegrationTestHelpers.goOffline(cfg);
    }

    // Phaser is the closest built-in to a bidrectional latch. We could use a semaphore with a fixed
    // number of permits, but the test would be fragile since the permit count isn't closely related
    // to the test cases.
    final Phaser latch = new Phaser(0);

    ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                foundPriorities.add(snapshot.getPriority());
                latch.arrive();
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    try {
      // null + increment
      latch.register();
      ref.setValue(0, ServerValue.increment(1));
      // numeric priorities are always doubles
      expectedPriorities.add(1.0);

      // long + double
      latch.register();
      ref.setValue(0, ServerValue.increment(1.5));
      expectedPriorities.add(2.5);

      // Checking types first makes failures much more obvious
      latch.awaitAdvanceInterruptibly(0, IntegrationTestValues.getTimeout(), MILLISECONDS);
      assertEquals(expectedPriorities, foundPriorities);
    } finally {
      ref.removeEventListener(listener);
      if (!online) {
        IntegrationTestHelpers.goOnline(cfg);
      }
    }
  }

  @Test
  public void testServerIncrementOverflowAndTypeCoercion()
      throws DatabaseException, TimeoutException, InterruptedException {
    DatabaseConfig cfg = IntegrationTestHelpers.newTestConfig();
    DatabaseReference ref = IntegrationTestHelpers.rootWithConfig(cfg);
    List<Object> foundValues = new ArrayList<>();
    // Note: all numeric values will be long or double, so they must be cast before being inserted.
    List<Object> expectedValues = new ArrayList<>();

    // Going offline ensures that local events get queued up before server events
    IntegrationTestHelpers.goOffline(cfg);

    // Phaser can be used as a bidirectional latch. We need this because
    // IntegrationTestHelpers.waitForQueue doesn't handle priority changes.
    Phaser latch = new Phaser(0);

    ValueEventListener listener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                foundValues.add(snapshot.getValue());
                latch.arrive();
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    try {
      // long + double = double
      latch.bulkRegister(2);
      ref.setValue(1);
      ref.setValue(ServerValue.increment(1.5));
      expectedValues.add((long) 1);
      expectedValues.add(2.5);

      // double + long = double
      latch.bulkRegister(2);
      ref.setValue(1.5);
      ref.setValue(ServerValue.increment(1));
      expectedValues.add(1.5);
      expectedValues.add(2.5);

      // long overflow = double
      latch.bulkRegister(2);
      ref.setValue(Long.MAX_VALUE - 1);
      ref.setValue(ServerValue.increment(2));
      expectedValues.add(Long.MAX_VALUE - 1);
      expectedValues.add((double) Long.MAX_VALUE + 1.0);

      // long underflow = double
      latch.bulkRegister(2);
      ref.setValue(Long.MIN_VALUE + 1);
      ref.setValue(ServerValue.increment(-2));
      expectedValues.add(Long.MIN_VALUE + 1);
      expectedValues.add((double) Long.MIN_VALUE - 1.0);

      latch.awaitAdvanceInterruptibly(0, IntegrationTestValues.getTimeout(), MILLISECONDS);

      // Checking types first makes failures much more obvious
      List<Class> expectedTypes = new ArrayList<>();
      for (Object o : expectedValues) {
        expectedTypes.add(o.getClass());
      }
      List<Class> foundTypes = new ArrayList<>();
      for (Object o : foundValues) {
        foundTypes.add(o.getClass());
      }
      assertEquals(expectedTypes, foundTypes);
      assertEquals(expectedValues, foundValues);
    } finally {
      ref.removeEventListener(listener);
      IntegrationTestHelpers.goOnline(cfg);
    }
  }

  @Test
  public void testUpdateAfterChildSet() throws DatabaseException, InterruptedException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore doneSemaphore = new Semaphore(0);

    Map<String, Object> initial = new MapBuilder().put("a", "a").build();
    ref.setValue(
        initial,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    Map res = (Map) snapshot.getValue();
                    if (res.size() == 3
                        && res.containsKey("a")
                        && res.containsKey("b")
                        && res.containsKey("c")) {
                      doneSemaphore.release();
                    }
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

            ref.child("b").setValue("b");

            Map<String, Object> update = new MapBuilder().put("c", "c").build();
            ref.updateChildren(update);
          }
        });

    IntegrationTestHelpers.waitFor(doneSemaphore);
  }

  @Test
  public void testMaxFrameSize()
      throws InterruptedException, TestFailure, ExecutionException, TimeoutException {
    String msg = "";
    // This will generate a string well over max frame size
    for (int i = 0; i < 16384; ++i) {
      msg += i;
    }

    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, msg).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    assertEquals(msg, snap.getValue());
  }

  @Test
  public void testDeltaSyncNoDataUpdatesAfterReconnect() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    // Create a fresh connection so we can be sure we won't get any other data updates for stuff.
    DatabaseConfig ctx = IntegrationTestHelpers.newTestConfig();
    final DatabaseReference ref2 = new DatabaseReference(ref.toString(), ctx);

    final Map data =
        new MapBuilder()
            .put("a", 1L)
            .put("b", 2L)
            .put("c", new MapBuilder().put(".value", 3L).put(".priority", 3.0).build())
            .put("d", 4L)
            .build();

    final Semaphore gotData = new Semaphore(0);

    ref.setValue(
        data,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref2.addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    assertFalse(gotData.tryAcquire());
                    gotData.release();

                    DeepEquals.assertEquals(data, snapshot.getValue(true));
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });
          }
        });

    IntegrationTestHelpers.waitFor(gotData);

    final Semaphore done = new Semaphore(0);
    assertEquals(1, ref2.repo.dataUpdateCount);

    // Bounce connection.
    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);

    ref2.getRoot()
        .child(".info/connected")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if ((Boolean) snapshot.getValue()) {
                  // We're connected.  Do one more round-trip to make sure all state restoration is
                  // done
                  ref2.getRoot()
                      .child("foobar/empty/blah")
                      .setValue(
                          null,
                          new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError error, DatabaseReference ref) {
                              assertEquals(1, ref2.repo.dataUpdateCount);
                              done.release();
                            }
                          });
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void testDeltaSyncWithQueryNoDataUpdatesAfterReconnect() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    // Create a fresh connection so we can be sure we won't get any other data updates for stuff.
    DatabaseConfig ctx = IntegrationTestHelpers.newTestConfig();
    final DatabaseReference ref2 = new DatabaseReference(ref.toString(), ctx);

    final Map data =
        new MapBuilder()
            .put("a", 1L)
            .put("b", 2L)
            .put("c", new MapBuilder().put(".value", 3L).put(".priority", 3.0).build())
            .put("d", 4L)
            .build();

    final Map expected =
        new MapBuilder()
            .put("c", new MapBuilder().put(".value", 3L).put(".priority", 3.0).build())
            .put("d", 4L)
            .build();

    final Semaphore gotData = new Semaphore(0);

    ref.setValue(
        data,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref2.limitToLast(2)
                .addValueEventListener(
                    new ValueEventListener() {
                      @Override
                      public void onDataChange(DataSnapshot snapshot) {
                        assertFalse(gotData.tryAcquire());
                        gotData.release();

                        DeepEquals.assertEquals(expected, snapshot.getValue(true));
                      }

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });
          }
        });

    IntegrationTestHelpers.waitFor(gotData);

    final Semaphore done = new Semaphore(0);
    assertEquals(1, ref2.repo.dataUpdateCount);

    // Bounce connection.
    RepoManager.interrupt(ctx);
    RepoManager.resume(ctx);

    ref2.getRoot()
        .child(".info/connected")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if ((Boolean) snapshot.getValue()) {
                  // We're connected.  Do one more round-trip to make sure all state restoration is
                  // done.
                  ref2.getRoot()
                      .child("foobar/empty/blah")
                      .setValue(
                          null,
                          new DatabaseReference.CompletionListener() {
                            @Override
                            public void onComplete(DatabaseError error, DatabaseReference ref) {
                              assertEquals(1, ref2.repo.dataUpdateCount);
                              done.release();
                            }
                          });
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void testDeltaSyncWithUnfilteredQuery()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writeRef = refs.get(0), readRef = refs.get(1);

    // List must be large enough to trigger delta sync.
    Map<String, Object> longList = new HashMap<String, Object>();
    for (long i = 0; i < 50; i++) {
      String key = writeRef.push().getKey();
      longList.put(
          key, new MapBuilder().put("order", i).put("text", "This is an awesome message!").build());
    }

    new WriteFuture(writeRef, longList).timedGet();

    // start listening.
    final List<DataSnapshot> readSnapshots = new ArrayList<DataSnapshot>();
    final Semaphore readSemaphore = new Semaphore(0);
    readRef
        .orderByChild("order")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                readSnapshots.add(snapshot);
                readSemaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(readSemaphore);
    DeepEquals.assertEquals(longList, readSnapshots.get(0).getValue());

    // Add a new child while readRef is offline.
    readRef.getDatabase().goOffline();

    DatabaseReference newChildRef = writeRef.push();
    Map<String, Object> newChild =
        new MapBuilder().put("order", 50L).put("text", "This is a new child!").build();
    new WriteFuture(newChildRef, newChild).timedGet();
    longList.put(newChildRef.getKey(), newChild);

    // Go back online and make sure we get the new item.
    readRef.getDatabase().goOnline();
    IntegrationTestHelpers.waitFor(readSemaphore);
    DeepEquals.assertEquals(longList, readSnapshots.get(1).getValue());
  }

  @Test
  public void negativeIntegersDontCreateArrayValue()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(
            ref, new MapBuilder().put("-1", "minus-one").put("0", "zero").put("1", "one").build())
        .timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    Map<String, Object> expected =
        new MapBuilder().put("-1", "minus-one").put("0", "zero").put("1", "one").build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void testLocalServerTimestampEventuallyButNotImmediatelyMatchServer()
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
              if (snapshot.getValue() != null) {
                writeSnaps.add(snapshot);
                completionSemaphore.release();
              }
            }
          }
        });

    writer.setValue(ServerValue.TIMESTAMP, ServerValue.TIMESTAMP);

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

  private static class DumbBean {
    public String name;
    public DumbBean nestedBean;
  }

  @Test
  public void testBasicObjectMappingRoundTrip()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {

    DumbBean bean = new DumbBean();
    bean.name = "bean";

    DumbBean nestedBean = new DumbBean();
    nestedBean.name = "nested-bean";
    bean.nestedBean = nestedBean;

    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    new WriteFuture(ref, bean).timedGet();

    final Semaphore done = new Semaphore(0);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            DumbBean bean = snapshot.getValue(DumbBean.class);
            assertEquals("bean", bean.name);
            assertEquals("nested-bean", bean.nestedBean.name);
            assertNull(bean.nestedBean.nestedBean);
            done.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void testUpdateChildrenWithObjectMapping()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {

    DumbBean bean1 = new DumbBean();
    bean1.name = "bean1";

    DumbBean bean2 = new DumbBean();
    bean2.name = "bean2";

    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore writeComplete = new Semaphore(0);
    ref.updateChildren(
        new MapBuilder().put("bean1", bean1).put("bean2", bean2).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            writeComplete.release();
          }
        });
    IntegrationTestHelpers.waitFor(writeComplete);

    final Semaphore done = new Semaphore(0);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            Map<String, DumbBean> beans =
                snapshot.getValue(new GenericTypeIndicator<Map<String, DumbBean>>() {});
            assertEquals("bean1", beans.get("bean1").name);
            assertEquals("bean2", beans.get("bean2").name);
            done.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void testUpdateChildrenDeepUpdatesWithObjectMapping()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {

    DumbBean bean1 = new DumbBean();
    bean1.name = "bean1";

    DumbBean bean2 = new DumbBean();
    bean2.name = "bean2";

    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore writeComplete = new Semaphore(0);
    ref.updateChildren(
        new MapBuilder().put("bean1", bean1).put("deep/bean2", bean2).build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            writeComplete.release();
          }
        });
    IntegrationTestHelpers.waitFor(writeComplete);

    final Semaphore done = new Semaphore(0);
    ref.addListenerForSingleValueEvent(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            assertEquals("bean1", snapshot.child("bean1").getValue(DumbBean.class).name);
            assertEquals("bean2", snapshot.child("deep/bean2").getValue(DumbBean.class).name);
            done.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(done);
  }
}
