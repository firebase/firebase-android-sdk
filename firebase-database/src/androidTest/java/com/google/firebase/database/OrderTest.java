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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class OrderTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  // Make sure we're connected before any of these tests run
  @Before
  public void setUp()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode().getRoot();

    ReadFuture.untilEquals(ref.child(".info/connected"), true).timedGet();
  }

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void pushABunchOfDataAndEnumerateItBack()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    for (int i = 0; i < 10; ++i) {
      ref.push().setValue(i);
    }

    DataSnapshot snap = new ReadFuture(ref).timedGet().get(0).getSnapshot();

    long i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }

    assertEquals(10L, i);
  }

  @Test
  public void pushABunchOfDataThenWriteEnsureOrderIsCorrect()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    List<DatabaseReference> paths = new ArrayList<DatabaseReference>(20);
    // Generate children quickly to try to get a few in the same millisecond
    for (int i = 0; i < 20; ++i) {
      paths.add(ref.push());
    }

    for (int i = 0; i < paths.size(); ++i) {
      paths.get(i).setValue(i);
    }

    DataSnapshot snap = new ReadFuture(ref).timedGet().get(0).getSnapshot();

    long i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }

    assertEquals(20L, i);
  }

  @Test
  public void pushABunchOfDataReconnectReadItBack()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);

    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    for (int i = 0; i < 9; ++i) {
      writer.push().setValue(i);
    }
    new WriteFuture(writer.push(), 9).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(writer);
    long i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }
    assertEquals(10L, i);

    snap = IntegrationTestHelpers.getSnap(reader);
    i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }
    assertEquals(10L, i);
  }

  @Test
  public void pushABunchOfDataWithExplicitPriorityReconnectReadBackInOrder()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);

    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    for (int i = 0; i < 9; ++i) {
      writer.push().setValue(i, 10 - i);
    }
    new WriteFuture(writer.push(), 9, 1).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(writer);
    long i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);

    snap = IntegrationTestHelpers.getSnap(reader);
    i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);
  }

  @Test
  public void pushDataWithExponentialPriorityAndCheckOrder()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);

    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    for (int i = 0; i < 9; ++i) {
      writer.push().setValue(i, Float.MAX_VALUE / Math.pow(10, i));
    }
    new WriteFuture(writer.push(), 9, Float.MAX_VALUE / Math.pow(10, 9)).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(writer);
    long i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);

    snap = IntegrationTestHelpers.getSnap(reader);
    i = 9;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i--;
    }
    assertEquals(-1L, i);
  }

  @Test
  public void verifyNodesWithoutValuesAreNotEnumerated()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.child("foo");
    ref.child("bar").setValue("test");

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    int i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      i++;
      assertEquals("bar", child.getKey());
    }
    assertEquals(1, i);
  }

  @Test
  public void receiveChildMovedEventWhenPriorityChanges()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    EventHelper helper =
        new EventHelper()
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "a")
            .addValueExpectation(ref)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "b")
            .addValueExpectation(ref)
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "c")
            .addValueExpectation(ref)
            .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "a")
            .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "a")
            .addValueExpectation(ref)
            .startListening(true);

    ref.child("a").setValue("first", 1);
    ref.child("b").setValue("second", 5);
    ref.child("c").setValue("third", 10);
    ref.child("a").setPriority(15);

    assertTrue(helper.waitForEvents());
  }

  @Test
  public void canResetPriorityToNull() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.child("a").setValue("a", 1);
    ref.child("b").setValue("b", 2);

    IntegrationTestHelpers.waitForRoundtrip(ref);
    EventHelper helper =
        new EventHelper()
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "a")
            .addChildExpectation(ref, Event.EventType.CHILD_ADDED, "b")
            .addValueExpectation(ref)
            .startListening();

    assertTrue(helper.waitForEvents());

    helper
        .addChildExpectation(ref, Event.EventType.CHILD_MOVED, "b")
        .addChildExpectation(ref, Event.EventType.CHILD_CHANGED, "b")
        .addValueExpectation(ref)
        .startListening();

    ref.child("b").setPriority(null);
    assertTrue(helper.waitForEvents());

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref);
    assertNull(snap.child("b").getPriority());
    helper.cleanup();
  }

  @Test
  public void insertingANodeUnderALeafPreservesItsPriority()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilCountAfterNull(ref, 2);

    ref.setValue("a", 10);
    ref.child("deeper").setValue("deeper");

    DataSnapshot snap = readFuture.timedGet().get(1).getSnapshot();
    assertEquals(10.0, snap.getPriority());
  }

  @Test
  public void verifyOrderOfMixedNumbersStringAndNoPriorities()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    // Need to use a String set to null rather than null literal, otherwise method call setValue is
    // ambiguous. The null could also refer to the CompletionListener
    String noPriority = null;
    writer.child("alpha42").setValue(1, "zed");
    writer.child("noPriorityC").setValue(1, noPriority);
    writer.child("num41").setValue(1, 500);
    writer.child("noPriorityB").setValue(1, noPriority);
    writer.child("num80").setValue(1, 4000.1);
    writer.child("num50").setValue(1, 4000);
    writer.child("num10").setValue(1, 24);
    writer.child("alpha41").setValue(1, "zed");
    writer.child("alpha20").setValue(1, "horse");
    writer.child("num20").setValue(1, 123);
    writer.child("num70").setValue(1, 4000.01);
    writer.child("noPriorityA").setValue(1, noPriority);
    writer.child("alpha30").setValue(1, "tree");
    writer.child("num30").setValue(1, 300);
    writer.child("num60").setValue(1, 4000.001);
    writer.child("alpha10").setValue(1, "0horse");
    writer.child("num42").setValue(1, 500);
    writer.child("alpha40").setValue(1, "zed");
    new WriteFuture(writer.child("num40"), 1, 500).timedGet();

    List<String> expected = new ArrayList<String>();
    expected.addAll(
        Arrays.asList(
            "noPriorityA",
            "noPriorityB",
            "noPriorityC",
            "num10",
            "num20",
            "num30",
            "num40",
            "num41",
            "num42",
            "num50",
            "num60",
            "num70",
            "num80",
            "alpha10",
            "alpha20",
            "alpha30",
            "alpha40",
            "alpha41",
            "alpha42"));
    List<String> actual = new ArrayList<String>(expected.size());
    DataSnapshot snap = IntegrationTestHelpers.getSnap(writer);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    DeepEquals.assertEquals(expected, actual);

    actual.clear();
    snap = IntegrationTestHelpers.getSnap(reader);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    DeepEquals.assertEquals(expected, actual);
  }

  @Test
  public void verifyOrderOfIntegerKeys()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);

    writer.child("foo").setValue(0);
    writer.child("bar").setValue(0);
    writer.child("03").setValue(0);
    writer.child("0").setValue(0);
    writer.child("100").setValue(0);
    writer.child("20").setValue(0);
    writer.child("5").setValue(0);
    writer.child("3").setValue(0);
    writer.child("003").setValue(0);
    new WriteFuture(writer.child("9"), 0).timedGet();

    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("0", "3", "03", "003", "5", "9", "20", "100", "bar", "foo"));
    List<String> actual = new ArrayList<String>(expected.size());
    DataSnapshot snap = IntegrationTestHelpers.getSnap(writer);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    DeepEquals.assertEquals(expected, actual);
  }

  @Test
  public void verifyOrderOfLargeIntegerKeys()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);

    writer.child("2000000000").setValue(0);
    new WriteFuture(writer.child("-2000000000"), 0).timedGet();

    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("-2000000000", "2000000000"));
    List<String> actual = new ArrayList<String>(expected.size());
    DataSnapshot snap = IntegrationTestHelpers.getSnap(writer);
    for (DataSnapshot child : snap.getChildren()) {
      actual.add(child.getKey());
    }
    DeepEquals.assertEquals(expected, actual);
  }

  @Test
  public void ensurePrevNameIsCorrectOnChildAddedEvent()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                results.add(snapshot.getKey());
                results.add(previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build());

    IntegrationTestHelpers.waitFor(semaphore, 3);
    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("a", null, "b", "a", "c", "b"));
    DeepEquals.assertEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void ensurePrevNameIsCorrectWhenAddingNewNodes()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                results.add(snapshot.getKey());
                results.add(previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValue(new MapBuilder().put("b", 2).put("c", 3).put("d", 4).build());

    ref.child("a").setValue(1);
    ref.child("e").setValue(5);

    IntegrationTestHelpers.waitFor(semaphore, 5);
    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("b", null, "c", "b", "d", "c", "a", null, "e", "d"));
    DeepEquals.assertEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void ensurePrevNameIsCorrectWhenAddingNewNodesWithJSON()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                results.add(snapshot.getKey());
                results.add(previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValue(new MapBuilder().put("b", 2).put("c", 3).put("d", 4).build());

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build());
    ref.setValue(
        new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).put("e", 5).build());

    IntegrationTestHelpers.waitFor(semaphore, 5);
    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("b", null, "c", "b", "d", "c", "a", null, "e", "d"));
    DeepEquals.assertEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void ensurePrevNameIsCorrectWhenMovingNodes()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("a").setValue("a", 1);
    ref.child("b").setValue("b", 2);
    ref.child("c").setValue("c", 3);
    ref.child("d").setValue("d", 4);

    ref.child("d").setPriority(0);

    ref.child("a").setPriority(4);

    ref.child("c").setPriority(0.5);

    IntegrationTestHelpers.waitFor(semaphore, 6);

    List<String> expected = new ArrayList<String>();
    expected.add("MOVED:d/null");
    expected.add("CHANGED:d/null");
    expected.add("MOVED:a/c");
    expected.add("CHANGED:a/c");
    expected.add("MOVED:c/d");
    expected.add("CHANGED:c/d");
    DeepEquals.assertEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void ensurePrevNameIsCorrectWhenMovingNodesBySettingWholeJSON()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 3).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 4).build())
            .build());

    ref.setValue(
        new MapBuilder()
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 0).build())
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 3).build())
            .build());

    ref.setValue(
        new MapBuilder()
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 3).build())
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 4).build())
            .build());

    ref.setValue(
        new MapBuilder()
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 0).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 0.5).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 2).build())
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 4).build())
            .build());

    IntegrationTestHelpers.waitFor(semaphore, 6);

    List<String> expected = new ArrayList<String>();
    expected.add("MOVED:d/null");
    expected.add("CHANGED:d/null");
    expected.add("MOVED:a/c");
    expected.add("CHANGED:a/c");
    expected.add("MOVED:c/d");
    expected.add("CHANGED:c/d");
    DeepEquals.assertEquals(expected, results);
    ref.removeEventListener(listener);
  }

  @Test
  public void case595ShouldNotGetChildMovedWhenDeletingPrioritizedGrandChild()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                // No-op
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                fail("Should not happen");
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("test/foo").setValue(42, "5");
    ref.child("test/f002").setValue(42, "10");
    ref.child("test/foo").removeValue();
    new WriteFuture(ref.child("test/foo2"), null).timedGet();
    // If child_moved has been raised, the test will have failed by now
    ref.removeEventListener(listener);
  }

  @Test
  public void canSetValuePriorityToZero()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = new ReadFuture(ref);
    ref.setValue("test", 0);

    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();

    assertEquals(0.0, snap.getPriority());
  }

  @Test
  public void canSetObjectPriorityToZero()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = new ReadFuture(ref);
    ref.setValue(new MapBuilder().put("x", "test").put("y", 7).build(), 0);

    DataSnapshot snap = readFuture.timedGet().get(0).getSnapshot();

    assertEquals(0.0, snap.getPriority());
  }

  @Test
  public void case2003ShouldGetChildMovedForAnyPriorityChangeRegardlessOfOrder()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 3).build())
            .build());

    ref.child("b").setPriority(1.5);
    IntegrationTestHelpers.waitFor(semaphore, 2);

    assertEquals(2, results.size());
    assertEquals(Arrays.asList("MOVED:b/a", "CHANGED:b/a"), results);
    ref.removeEventListener(listener);
  }

  @Test
  public void case2003ShouldGetChildMovedForAnyPriorityChangeRegardlessOfOrder2()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> results = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                results.add("CHANGED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                fail("Should not happen");
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                results.add("MOVED:" + snapshot.getKey() + "/" + previousChildName);
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {
                fail("Should not happen");
              }
            });

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 3).build())
            .build());

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", "a").put(".priority", 0).build())
            .put("b", new MapBuilder().put(".value", "b").put(".priority", 1.5).build())
            .put("c", new MapBuilder().put(".value", "c").put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", "d").put(".priority", 3).build())
            .build());

    IntegrationTestHelpers.waitFor(semaphore, 2);

    assertEquals(2, results.size());
    assertEquals(Arrays.asList("MOVED:b/a", "CHANGED:b/a"), results);
    ref.removeEventListener(listener);
  }
}
