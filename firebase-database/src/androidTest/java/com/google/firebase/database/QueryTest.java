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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.RepoManager;
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
import org.junit.After;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class QueryTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  @Test
  public void canCreateBasicQueries() throws DatabaseException {
    // Just make sure they don't throw anything
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.limitToLast(10);
    ref.startAt("199").limitToLast(10);
    ref.startAt("199", "test").limitToLast(10);
    ref.endAt(199).limitToLast(1);
    ref.startAt(50, "test").endAt(100, "tree");
    ref.startAt(4).endAt(10);
    ref.startAt(null).endAt(10);
    ref.orderByChild("child");
    ref.orderByChild("child/deep/path");
    ref.orderByValue();
    ref.orderByPriority();
  }

  @Test
  public void invalidPathsToOrderByThrow() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    List<String> badKeys =
        Arrays.asList(
            "$child/foo",
            "$childfoo",
            "$/foo",
            "$child/foo/bar",
            "$child/.foo",
            ".priority",
            "$priority",
            "$key",
            ".key",
            "$child/.priority");
    for (String key : badKeys) {
      try {
        ref.orderByChild(key);
        fail("Should throw");
      } catch (DatabaseException e) { // ignore
      } catch (IllegalArgumentException e) { // ignore
      }
    }
  }

  @Test
  public void invalidQueriesThrow() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    try {
      ref.orderByKey().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().orderByValue();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().orderByChild("foo");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByKey();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByValue();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByValue().orderByChild("foo");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByPriority();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByKey();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByValue();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().startAt(1);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().startAt(null);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().endAt(null);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().equalTo(null);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().startAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().endAt(1);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().endAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByKey().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().orderByKey();
      fail("Should throw");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().orderByValue();
      fail("Should throw");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().orderByPriority();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToLast(1).limitToLast(1);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToFirst(1).limitToLast(1);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToLast(1).limitToFirst(1);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo(true).endAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo(true).startAt("test", "test");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo(true).equalTo("test", "test");
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.equalTo("test").equalTo(true);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByChild("foo").orderByKey();
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.limitToFirst(5).limitToLast(10);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.startAt(5).equalTo(10);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().startAt(false);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().endAt(true);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
    try {
      ref.orderByPriority().equalTo(true);
      fail("Should throw");
    } catch (DatabaseException e) { // ignore
    } catch (IllegalArgumentException e) { // ignore
    }
  }

  @Test
  public void passingInvalidKeysToStartAtOrEndAtThrows() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    List<String> badKeys =
        Arrays.asList(
            ".test", "test.", "fo$o", "[what", "ever]", "ha#sh", "/thing", "th/ing", "thing/");
    for (String key : badKeys) {
      try {
        ref.startAt(null, key);
        fail("Should throw");
      } catch (DatabaseException e) { // ignore

      }

      try {
        ref.endAt(null, key);
        fail("Should throw");
      } catch (DatabaseException e) { // ignore

      }
    }
  }

  // TODO: test queryObject

  @Test
  public void listenerCanBeRemovedFromSpecificQuery()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener listener =
        ref.limitToLast(5)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    ref.setValue(new MapBuilder().put("a", 5).put("b", 6).build());
    semaphore.acquire();
    ref.limitToLast(5).removeEventListener(listener);
    new WriteFuture(ref, new MapBuilder().put("a", 6).put("b", 5).build()).timedGet();
    IntegrationTestHelpers.waitForQueue(ref);
    assertEquals(0, semaphore.availablePermits());
  }

  @Test
  public void removingListenersWorks()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener listener =
        ref.limitToLast(5)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    ref.setValue(new MapBuilder().put("a", 5).put("b", 6).build());
    IntegrationTestHelpers.waitFor(semaphore, 1);
    ref.limitToLast(5).removeEventListener(listener);
    new WriteFuture(ref, new MapBuilder().put("a", 6).put("b", 5).build()).timedGet();
    IntegrationTestHelpers.waitForQueue(ref);

    assertEquals(0, semaphore.availablePermits());
  }

  // NOTE: skipping test for off w/o an event. Not applicable for java api

  @Test
  public void limit5ShouldHave5Children()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("a", 1)
            .put("b", 2)
            .put("c", 3)
            .put("d", 4)
            .put("e", 5)
            .put("f", 6)
            .build());

    DataSnapshot snap = new ReadFuture(ref.limitToLast(5)).timedGet().get(0).getSnapshot();

    assertEquals(5, snap.getChildrenCount());
    assertFalse(snap.hasChild("a"));
    assertTrue(snap.hasChild("b"));
    assertTrue(snap.hasChild("f"));
  }

  @Test
  public void serverShouldOnlySend5Items()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    for (int i = 0; i < 9; ++i) {
      ref.push().setValue(i);
    }
    new WriteFuture(ref.push(), 9).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.limitToLast(5));

    long i = 5;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(i, child.getValue());
      i++;
    }

    assertEquals(10L, i);
  }

  @Test
  public void setVariousLimitsEnsureDataIsCorrect() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.limitToLast(1), new MapBuilder().put("c", 3L).build());
    expectations.add(ref.endAt(null).limitToLast(1), new MapBuilder().put("c", 3L).build());
    expectations.add(ref.limitToLast(2), new MapBuilder().put("b", 2L).put("c", 3L).build());
    expectations.add(
        ref.limitToLast(3), new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).build());
    expectations.add(
        ref.limitToLast(4), new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).build());

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build());

    expectations.waitForEvents();
  }

  @Test
  public void setVariousLimitsWithStartAtName() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.startAt(null).limitToFirst(1), new MapBuilder().put("a", 1L).build());
    expectations.add(ref.startAt(null, "c").limitToFirst(1), new MapBuilder().put("c", 3L).build());
    expectations.add(ref.startAt(null, "b").limitToFirst(1), new MapBuilder().put("b", 2L).build());
    expectations.add(
        ref.startAt(null, "b").limitToFirst(2), new MapBuilder().put("b", 2L).put("c", 3L).build());
    expectations.add(
        ref.startAt(null, "b").limitToFirst(3), new MapBuilder().put("b", 2L).put("c", 3L).build());

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build());

    expectations.waitForEvents();
  }

  @Test
  public void setVariousLimitsWithStartAtNameWithServerData()
      throws DatabaseException, InterruptedException, TestFailure, ExecutionException,
          TimeoutException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    // TODO: this test kinda has race conditions. The listens are added sequentially, so we get a
    // lot of partial data back from the server. This all correct, and we end up in the correct
    // state, but it's still kinda weird. Consider having ValueExpectationHelper deal with initial
    // state.

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.startAt(null).limitToFirst(1), new MapBuilder().put("a", 1L).build());
    expectations.add(ref.startAt(null, "c").limitToFirst(1), new MapBuilder().put("c", 3L).build());
    expectations.add(ref.startAt(null, "b").limitToFirst(1), new MapBuilder().put("b", 2L).build());
    expectations.add(
        ref.startAt(null, "b").limitToFirst(2), new MapBuilder().put("b", 2L).put("c", 3L).build());
    expectations.add(
        ref.startAt(null, "b").limitToFirst(3), new MapBuilder().put("b", 2L).put("c", 3L).build());

    expectations.waitForEvents();
  }

  @Test
  public void setLimitEnsureChildRemovedAndChildAddedHitWhenLimitIsHit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    ref.limitToLast(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    added.clear();
    assertTrue(removed.isEmpty());
    new WriteFuture(ref.child("d"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("d", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void setLimitEnsureChildRemovedAndChildAddedHitWhenLimitIsHitWithServerData()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(semaphore, 2);
    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    added.clear();
    assertTrue(removed.isEmpty());
    new WriteFuture(ref.child("d"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("d", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void setLimitEnsureChildRemovedAndChildAddedHitWhenLimitIsHitFromFront()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    ref.startAt(null, "a")
        .limitToFirst(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    List<String> expected = new ArrayList<String>();
    expected.add("a");
    expected.add("b");
    DeepEquals.assertEquals(expected, added);
    added.clear();
    assertTrue(removed.isEmpty());
    new WriteFuture(ref.child("aa"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("aa", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void setLimitEnsureChildRemovedAndChildAddedHitWhenLimitIsHitFromFrontWithServerData()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ref.startAt(null, "a")
        .limitToFirst(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(semaphore, 2);
    List<String> expected = new ArrayList<String>();
    expected.add("a");
    expected.add("b");
    DeepEquals.assertEquals(expected, added);
    added.clear();
    assertTrue(removed.isEmpty());
    new WriteFuture(ref.child("aa"), 4).timedGet();
    assertEquals(1, added.size());
    assertEquals("aa", added.get(0));
    assertEquals(1, removed.size());
    assertEquals("b", removed.get(0));
  }

  @Test
  public void setStartAndLimitEnsureChildAddedFiredWhenLimitIsntHit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    ref.startAt(null, "a")
        .limitToFirst(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("c", 3).build()).timedGet();
    List<String> expected = new ArrayList<String>();
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
    added.clear();
    expected.clear();
    new WriteFuture(ref.child("b"), 4).timedGet();
    expected.add("b");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
  }

  @Test
  public void setStartAndLimitEnsureChildAddedFiredWhenLimitIsntHitWithServerData()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, new MapBuilder().put("c", 3).build()).timedGet();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ref.startAt(null, "a")
        .limitToFirst(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(semaphore);
    List<String> expected = new ArrayList<String>();
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
    added.clear();
    expected.clear();
    new WriteFuture(ref.child("b"), 4).timedGet();
    expected.add("b");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
  }

  @Test
  public void setLimitEnsureChildAddedAndChildRemovedAreFiredWhenAnElementIsRemoved()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    ref.limitToLast(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
    added.clear();
    expected.clear();
    new WriteFuture(ref.child("b"), null).timedGet();
    expected.add("a");
    DeepEquals.assertEquals(expected, added);
    expected.clear();
    expected.add("b");
    DeepEquals.assertEquals(expected, removed);
  }

  @Test
  public void setLimitEnsureChildAddedAndChildRemovedAreFiredWhenAnElementIsRemovedUsingServerData()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
                semaphore.release(1);
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(semaphore, 2);
    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
    added.clear();
    expected.clear();
    new WriteFuture(ref.child("b"), null).timedGet();
    expected.add("a");
    DeepEquals.assertEquals(expected, added);
    expected.clear();
    expected.add("b");
    DeepEquals.assertEquals(expected, removed);
  }

  @Test
  public void setLimitEnsureChildRemovedFiredWhenAllElementsRemoved()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();

    ref.limitToLast(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                added.add(snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removed.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // no-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("b", 2).put("c", 3).build()).timedGet();
    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
    added.clear();
    expected.clear();
    new WriteFuture(ref.child("b"), null).timedGet();
    assertTrue(added.isEmpty());
    expected.add("b");
    DeepEquals.assertEquals(expected, removed);
    new WriteFuture(ref.child("c"), null).timedGet();
    assertTrue(added.isEmpty());
    expected.add("c");
    DeepEquals.assertEquals(expected, removed);
  }

  @Test
  public void setLimitEnsureChildRemovedFiredWhenAllElementsRemovedUsingServerData()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, new MapBuilder().put("b", 2).put("c", 3).build()).timedGet();
    final List<String> added = new ArrayList<String>();
    final List<String> removed = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener listener =
        ref.limitToLast(2)
            .addChildEventListener(
                new ChildEventListener() {
                  @Override
                  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    added.add(snapshot.getKey());
                    semaphore.release(1);
                  }

                  @Override
                  public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    // no-op
                  }

                  @Override
                  public void onChildRemoved(DataSnapshot snapshot) {
                    removed.add(snapshot.getKey());
                  }

                  @Override
                  public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                    // no-op
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    IntegrationTestHelpers.waitFor(semaphore, 2);
    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    DeepEquals.assertEquals(expected, added);
    assertTrue(removed.isEmpty());
    added.clear();
    expected.clear();
    new WriteFuture(ref.child("b"), null).timedGet();
    assertTrue(added.isEmpty());
    expected.add("b");
    DeepEquals.assertEquals(expected, removed);
    new WriteFuture(ref.child("c"), null).timedGet();
    assertTrue(added.isEmpty());
    expected.add("c");
    DeepEquals.assertEquals(expected, removed);
    ref.limitToLast(2).removeEventListener(listener);
  }

  @Test
  public void startAtEndAtWithPriorityWorks() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(
        ref.startAt("w").endAt("y"),
        new MapBuilder().put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(ref.startAt("w").endAt("w"), new MapBuilder().put("d", 4L).build());
    helper.add(ref.startAt("a").endAt("c"), null);

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", 1).put(".priority", "z").build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", "y").build())
            .put("c", new MapBuilder().put(".value", 3).put(".priority", "x").build())
            .put("d", new MapBuilder().put(".value", 4).put(".priority", "w").build())
            .build());

    helper.waitForEvents();
  }

  @Test
  public void startAtEndAtWithPriorityWorksWithServerData()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", 1).put(".priority", "z").build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", "y").build())
            .put("c", new MapBuilder().put(".value", 3).put(".priority", "x").build())
            .put("d", new MapBuilder().put(".value", 4).put(".priority", "w").build())
            .build());

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(
        ref.startAt("w").endAt("y"),
        new MapBuilder().put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(ref.startAt("w").endAt("w"), new MapBuilder().put("d", 4L).build());
    helper.add(ref.startAt("a").endAt("c"), null);

    helper.waitForEvents();
  }

  @Test
  public void startAtEndAtWithPriorityAndNameWorks()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(
        ref.startAt(1, "a").endAt(2, "d"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(
        ref.startAt(1, "b").endAt(2, "c"), new MapBuilder().put("b", 2L).put("c", 3L).build());
    helper.add(ref.startAt(1, "c").endAt(2), new MapBuilder().put("c", 3L).put("d", 4L).build());

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", 1).put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", 3).put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", 4).put(".priority", 2).build())
            .build());

    helper.waitForEvents();
  }

  @Test
  public void startAtEndAtWithPriorityAndNameWorksWithServerData()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", 1).put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", 1).build())
            .put("c", new MapBuilder().put(".value", 3).put(".priority", 2).build())
            .put("d", new MapBuilder().put(".value", 4).put(".priority", 2).build())
            .build());

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(
        ref.startAt(1, "a").endAt(2, "d"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(
        ref.startAt(1, "b").endAt(2, "c"), new MapBuilder().put("b", 2L).put("c", 3L).build());
    helper.add(ref.startAt(1, "c").endAt(2), new MapBuilder().put("c", 3L).put("d", 4L).build());

    helper.waitForEvents();
  }

  @Test
  public void startAtEndAtWithPriorityAndNameWorks2()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(
        ref.startAt(1, "c").endAt(2, "b"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(
        ref.startAt(1, "d").endAt(2, "a"), new MapBuilder().put("d", 4L).put("a", 1L).build());
    helper.add(ref.startAt(1, "e").endAt(2), new MapBuilder().put("a", 1L).put("b", 2L).build());

    ref.setValue(
        new MapBuilder()
            .put("c", new MapBuilder().put(".value", 3).put(".priority", 1).build())
            .put("d", new MapBuilder().put(".value", 4).put(".priority", 1).build())
            .put("a", new MapBuilder().put(".value", 1).put(".priority", 2).build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", 2).build())
            .build());

    helper.waitForEvents();
  }

  @Test
  public void startAtEndAtWithPriorityAndNameWorksWithServerData2()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("c", new MapBuilder().put(".value", 3).put(".priority", 1).build())
            .put("d", new MapBuilder().put(".value", 4).put(".priority", 1).build())
            .put("a", new MapBuilder().put(".value", 1).put(".priority", 2).build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", 2).build())
            .build());

    ValueExpectationHelper helper = new ValueExpectationHelper();
    helper.add(
        ref.startAt(1, "c").endAt(2, "b"),
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build());
    helper.add(
        ref.startAt(1, "d").endAt(2, "a"), new MapBuilder().put("d", 4L).put("a", 1L).build());
    helper.add(ref.startAt(1, "e").endAt(2), new MapBuilder().put("a", 1L).put("b", 2L).build());

    helper.waitForEvents();
  }

  @Test
  public void ensurePrevNameWorksWithLimit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> names = new ArrayList<String>();
    ref.limitToLast(2)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                names.add(snapshot.getKey() + " " + previousChildName);
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
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("a").setValue(1);
    ref.child("c").setValue(3);
    ref.child("b").setValue(2);
    new WriteFuture(ref.child("d"), 4).timedGet();

    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("a null", "c a", "b null", "d c"));
    DeepEquals.assertEquals(expected, names);
  }

  // NOTE: skipping server data test here, it really doesn't test anything extra

  @Test
  public void setALimitMoveNodesCheckPrevName()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> names = new ArrayList<String>();
    ref.limitToLast(2)
        .addChildEventListener(
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
                names.add(snapshot.getKey() + " " + previousChildName);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("a").setValue("a", 10);
    ref.child("b").setValue("b", 20);
    ref.child("c").setValue("c", 30);
    ref.child("d").setValue("d", 40);

    // Start moving things
    ref.child("c").setPriority(50);
    ref.child("c").setPriority(35);
    new WriteFuture(ref.child("b"), "b", 33).timedGet();

    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("c d", "c null"));
    DeepEquals.assertEquals(expected, names);
  }

  // NOTE: skipping server data version of the above test, it doesn't really test anything new

  // NOTE: skipping numeric priority test, the same functionality is tested above

  // NOTE: skipping local add / remove test w/ limits. Tested above

  @Test
  public void setALimitAddNodesRemotelyWatchForEvents()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final List<String> events = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future = new ReadFuture(reader);
    ChildEventListener listener =
        reader
            .limitToLast(2)
            .addChildEventListener(
                new ChildEventListener() {
                  @Override
                  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    events.add(snapshot.getValue().toString() + " added");
                    semaphore.release(1);
                  }

                  @Override
                  public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                    // No-op
                  }

                  @Override
                  public void onChildRemoved(DataSnapshot snapshot) {
                    events.add(snapshot.getValue().toString() + " removed");
                  }

                  @Override
                  public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                    // No-op
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    fail("Should not be cancelled");
                  }
                });
    // Wait for initial load
    future.timedGet();

    for (int i = 0; i < 4; ++i) {
      writer.push().setValue(i);
    }
    new WriteFuture(writer.push(), 4).timedGet();
    List<String> expected = new ArrayList<String>();
    expected.addAll(
        Arrays.asList(
            "0 added",
            "1 added",
            "0 removed",
            "2 added",
            "1 removed",
            "3 added",
            "2 removed",
            "4 added"));
    // Make sure we wait for all the events
    IntegrationTestHelpers.waitFor(semaphore, 5);
    DeepEquals.assertEquals(expected, events);
    reader.limitToLast(2).removeEventListener(listener);
  }

  @Test
  public void attachingAListenerReturnsTheListener() throws DatabaseException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueEventListener listener =
        ref.limitToLast(1)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    // No-op
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    assertNotNull(listener);
  }

  @Test
  public void limitOnUnsyncedNodeFiresValueEvent()
      throws DatabaseException, TestFailure, TimeoutException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    // This will timeout if value never fires
    org.junit.Assert.assertEquals(1, new ReadFuture(ref.limitToLast(1)).timedGet().size());
  }

  @Test
  public void filteringToOnlyNullPrioritiesWorks() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".priority", null).put(".value", 0).build())
            .put("b", new MapBuilder().put(".priority", null).put(".value", 1).build())
            .put("c", new MapBuilder().put(".priority", "2").put(".value", 2).build())
            .put("d", new MapBuilder().put(".priority", 3).put(".value", 3).build())
            .put("e", new MapBuilder().put(".priority", "hi").put(".value", 4).build())
            .build());

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.endAt(null));
    Map<String, Object> expected = new MapBuilder().put("a", 0L).put("b", 1L).build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void nullPrioritiesIncludedInEndAt2() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".priority", null).put(".value", 0).build())
            .put("b", new MapBuilder().put(".priority", null).put(".value", 1).build())
            .put("c", new MapBuilder().put(".priority", 2).put(".value", 2).build())
            .put("d", new MapBuilder().put(".priority", 3).put(".value", 3).build())
            .put("e", new MapBuilder().put(".priority", "hi").put(".value", 4).build())
            .build());

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.endAt(2));
    Map<String, Object> expected = new MapBuilder().put("a", 0L).put("b", 1L).put("c", 2L).build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void nullPrioritiesIncludedInStartAt2() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".priority", null).put(".value", 0).build())
            .put("b", new MapBuilder().put(".priority", null).put(".value", 1).build())
            .put("c", new MapBuilder().put(".priority", 2).put(".value", 2).build())
            .put("d", new MapBuilder().put(".priority", 3).put(".value", 3).build())
            .put("e", new MapBuilder().put(".priority", "hi").put(".value", 4).build())
            .build());

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.startAt(2));
    Map<String, Object> expected = new MapBuilder().put("c", 2L).put("d", 3L).put("e", 4L).build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  // TODO: find a way to expose listens from PersistentConnection interface
  private String dumpListens(Query ref) throws InterruptedException {
    Path path = ref.getPath();
    throw new RuntimeException("dumpListens not implemented");
    // return ListenAggregator.dumpListens(ref.getRepo(), path).toString();
  }

  private ValueEventListener dummyListener() {
    return new ValueEventListener() {
      @Override
      public void onDataChange(DataSnapshot snapshot) {
        // No-op
      }

      @Override
      public void onCancelled(DatabaseError error) {
        fail("Should not be cancelled");
      }
    };
  }

  @Test
  @Ignore // TODO: re-enable once dumpListens is implemented again
  public void dedupeListensListenOnParent() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ValueEventListener listener = dummyListener();

    assertEquals("[]", dumpListens(ref));
    ref.child("a").addValueEventListener(listener);

    String listens = dumpListens(ref);
    assertEquals("[/a:[default]]", listens);

    ref.addValueEventListener(listener);

    listens = dumpListens(ref);
    assertEquals("[:[default]]", listens);

    ref.removeEventListener(listener);

    listens = dumpListens(ref);
    assertEquals("[/a:[default]]", listens);

    ref.child("a").removeEventListener(listener);

    listens = dumpListens(ref);
    assertEquals("[]", listens);
  }

  @Test
  @Ignore // TODO: re-enable once dumpListens is implemented again
  public void dedupeListensListenOnGrandChild() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ValueEventListener listener = dummyListener();

    ref.addValueEventListener(listener);

    String listens = dumpListens(ref);
    assertEquals("[:[default]]", listens);

    ref.child("a/aa").addValueEventListener(listener);

    listens = dumpListens(ref);
    assertEquals("[:[default]]", listens);

    ref.removeEventListener(listener);
    ref.child("a/aa").removeEventListener(listener);

    listens = dumpListens(ref);
    assertEquals("[]", listens);
  }

  @Test
  @Ignore // TODO: re-enable once dumpListens is implemented again
  public void dedupeListensListenOnGrandparentOfTwoChildren()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ValueEventListener listener = dummyListener();

    String listens = dumpListens(ref);
    assertEquals("[]", listens);

    ref.child("a/aa").addValueEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[/a/aa:[default]]", listens);

    ref.child("a/bb").addValueEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[/a/aa:[default], /a/bb:[default]]", listens);

    ref.addValueEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[:[default]]", listens);

    ref.removeEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[/a/aa:[default], /a/bb:[default]]", listens);

    ref.child("a/aa").removeEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[/a/bb:[default]]", listens);

    ref.child("a/bb").removeEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[]", listens);
  }

  @Test
  @Ignore // TODO: This test depends on a specific JSON rendering of listens and is
  // broken with org.json.  Need to make more robust.
  public void dedupeQueriedListensMultipleListensNoDupes()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ValueEventListener listener = dummyListener();

    ref.child("a").limitToLast(1).addValueEventListener(listener);
    String listens = dumpListens(ref);
    assertEquals("[/a:[{vf=r, l=1}]]", listens);

    ref.limitToLast(1).addValueEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[:[{vf=r, l=1}], /a:[{vf=r, l=1}]]", listens);

    ref.child("a").limitToLast(5).addValueEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[:[{vf=r, l=1}], /a:[{vf=r, l=1}, {vf=r, l=5}]]", listens);

    ref.limitToLast(1).removeEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[/a:[{vf=r, l=1}, {vf=r, l=5}]]", listens);

    ref.child("a").limitToLast(1).removeEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[/a:[{vf=r, l=5}]]", listens);

    ref.child("a").limitToLast(5).removeEventListener(listener);
    listens = dumpListens(ref);
    assertEquals("[]", listens);
  }

  @Test
  public void limitWithMixOfNullAndNonNullPrioritiesUsingServerData()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    Map<String, Object> toSet =
        new MapBuilder()
            .put(
                "Vikrum",
                new MapBuilder()
                    .put(".priority", 1000.0)
                    .put("score", 1000L)
                    .put("name", "Vikrum")
                    .build())
            .put(
                "Mike",
                new MapBuilder()
                    .put(".priority", 500.0)
                    .put("score", 500L)
                    .put("name", "Mike")
                    .build())
            .put(
                "Andrew",
                new MapBuilder()
                    .put(".priority", 50.0)
                    .put("score", 50L)
                    .put("name", "Andrew")
                    .build())
            .put(
                "James",
                new MapBuilder()
                    .put(".priority", 7.0)
                    .put("score", 7L)
                    .put("name", "James")
                    .build())
            .put(
                "Sally",
                new MapBuilder()
                    .put(".priority", -7.0)
                    .put("score", -7L)
                    .put("name", "Sally")
                    .build())
            .put("Fred", new MapBuilder().put("score", 0.0).put("name", "Fred").build())
            .build();
    ref.setValue(toSet);
    final Semaphore semaphore = new Semaphore(0);
    final List<String> names = new ArrayList<String>();
    ref.limitToLast(5)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                names.add(snapshot.getKey());
                semaphore.release(1);
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
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(semaphore, 5);
    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("Sally", "James", "Andrew", "Mike", "Vikrum"));
    DeepEquals.assertEquals(expected, names);
  }

  @Test
  public void limitOnNodeWithPriority() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);

    final Map data = new MapBuilder().put("a", "blah").put(".priority", "priority").build();

    ref.setValue(
        data,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.limitToLast(2)
                .addListenerForSingleValueEvent(
                    new ValueEventListener() {
                      @Override
                      public void onDataChange(DataSnapshot snapshot) {
                        Map expected = new MapBuilder().put("a", "blah").build();
                        DeepEquals.assertEquals(expected, snapshot.getValue(true));
                        semaphore.release();
                      }

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void limitWithMixOfNullAndNonNullPriorities()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    Map<String, Object> toSet =
        new MapBuilder()
            .put(
                "Vikrum",
                new MapBuilder()
                    .put(".priority", 1000.0)
                    .put("score", 1000L)
                    .put("name", "Vikrum")
                    .build())
            .put(
                "Mike",
                new MapBuilder()
                    .put(".priority", 500.0)
                    .put("score", 500L)
                    .put("name", "Mike")
                    .build())
            .put(
                "Andrew",
                new MapBuilder()
                    .put(".priority", 50.0)
                    .put("score", 50L)
                    .put("name", "Andrew")
                    .build())
            .put(
                "James",
                new MapBuilder()
                    .put(".priority", 7.0)
                    .put("score", 7L)
                    .put("name", "James")
                    .build())
            .put(
                "Sally",
                new MapBuilder()
                    .put(".priority", -7.0)
                    .put("score", -7L)
                    .put("name", "Sally")
                    .build())
            .put("Fred", new MapBuilder().put("score", 0.0).put("name", "Fred").build())
            .build();

    final Semaphore semaphore = new Semaphore(0);
    final List<String> names = new ArrayList<String>();
    ChildEventListener listener =
        ref.limitToLast(5)
            .addChildEventListener(
                new ChildEventListener() {
                  @Override
                  public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                    names.add(snapshot.getKey());
                    semaphore.release(1);
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
                    // No-op
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    fail("Should not be cancelled");
                  }
                });
    ref.setValue(toSet);
    IntegrationTestHelpers.waitFor(semaphore, 5);
    List<String> expected = new ArrayList<String>();
    expected.addAll(Arrays.asList("Sally", "James", "Andrew", "Mike", "Vikrum"));
    DeepEquals.assertEquals(expected, names);
    ref.limitToLast(5).removeEventListener(listener);
  }

  // NOTE: skipping tests for js context argument

  @Test
  public void handlesAnUpdateThatDeletesEntireQueryWindow()
      throws DatabaseException, InterruptedException, TestFailure, TimeoutException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture readFuture = ReadFuture.untilCount(ref.limitToLast(2), 3);

    // wait for null event
    IntegrationTestHelpers.waitForRoundtrip(ref);

    ref.setValue(
        new MapBuilder()
            .put("a", new MapBuilder().put(".value", 1).put(".priority", 1).build())
            .put("b", new MapBuilder().put(".value", 2).put(".priority", 2).build())
            .put("c", new MapBuilder().put(".value", 3).put(".priority", 3).build())
            .build());

    ref.updateChildren(new MapBuilder().put("b", null).put("c", null).build());
    List<EventRecord> events = readFuture.timedGet();
    DataSnapshot snap = events.get(1).getSnapshot();

    Map<String, Object> expected = new MapBuilder().put("b", 2L).put("c", 3L).build();
    Object result = snap.getValue();
    DeepEquals.assertEquals(expected, result);

    // The original set is still outstanding (synchronous API), so we have a full cache to
    // re-window against
    snap = events.get(2).getSnapshot();
    expected = new MapBuilder().put("a", 1L).build();
    result = snap.getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void handlesAnOutOfViewQueryOnAChild()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture parentFuture = ReadFuture.untilCountAfterNull(ref.limitToLast(1), 2);

    final List<DataSnapshot> childSnaps = new ArrayList<DataSnapshot>();
    ref.child("a")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                childSnaps.add(snapshot);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).build()).timedGet();
    assertEquals(1L, childSnaps.get(0).getValue());
    ref.updateChildren(new MapBuilder().put("c", 3).build());
    List<EventRecord> events = parentFuture.timedGet();
    DataSnapshot snap = events.get(0).getSnapshot();
    Object result = snap.getValue();

    Map<String, Object> expected = new MapBuilder().put("b", 2L).build();
    DeepEquals.assertEquals(expected, result);

    snap = events.get(1).getSnapshot();
    result = snap.getValue();

    expected = new MapBuilder().put("c", 3L).build();
    DeepEquals.assertEquals(expected, result);
    assertEquals(1, childSnaps.size());
    assertEquals(1L, childSnaps.get(0).getValue());
  }

  @Test
  public void handlesAChildQueryGoingOutOfViewOfTheParent()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ReadFuture parentFuture = ReadFuture.untilCountAfterNull(ref.limitToLast(1), 3);

    final List<DataSnapshot> childSnaps = new ArrayList<DataSnapshot>();
    ValueEventListener listener =
        ref.child("a")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    childSnaps.add(snapshot);
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    fail("Should not be cancelled");
                  }
                });

    new WriteFuture(ref, new MapBuilder().put("a", 1).build()).timedGet();
    assertEquals(1L, childSnaps.get(0).getValue());
    new WriteFuture(ref.child("b"), 2).timedGet();
    assertEquals(1, childSnaps.size());
    new WriteFuture(ref.child("b"), null).timedGet();
    List<EventRecord> events = parentFuture.timedGet();
    assertEquals(1, childSnaps.size());

    Object result;
    Map<String, Object> expected;

    result = events.get(0).getSnapshot().getValue();
    expected = new MapBuilder().put("a", 1L).build();
    DeepEquals.assertEquals(expected, result);

    result = events.get(1).getSnapshot().getValue();
    expected = new MapBuilder().put("b", 2L).build();
    DeepEquals.assertEquals(expected, result);

    result = events.get(0).getSnapshot().getValue();
    expected = new MapBuilder().put("a", 1L).build();
    DeepEquals.assertEquals(expected, result);
    ref.child("a").removeEventListener(listener);
  }

  @Test
  public void handlesDivergingViews()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<DataSnapshot> cSnaps = new ArrayList<DataSnapshot>();
    final List<DataSnapshot> dSnaps = new ArrayList<DataSnapshot>();

    ref.endAt(null, "c")
        .limitToLast(1)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.getValue() != null) {
                  cSnaps.add(snapshot);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.endAt(null, "d")
        .limitToLast(1)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (snapshot.getValue() != null) {
                  dSnaps.add(snapshot);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();
    assertEquals(1, cSnaps.size());
    Map<String, Object> cExpected = new MapBuilder().put("c", 3L).build();
    DeepEquals.assertEquals(cExpected, cSnaps.get(0).getValue());

    new WriteFuture(ref.child("d"), 4).timedGet();
    assertEquals(1, cSnaps.size());

    assertEquals(2, dSnaps.size());
    Map<String, Object> dExpected = cExpected;
    DeepEquals.assertEquals(dExpected, dSnaps.get(0).getValue());

    dExpected = new MapBuilder().put("d", 4L).build();
    DeepEquals.assertEquals(dExpected, dSnaps.get(1).getValue());
  }

  @Test
  public void handlesRemovingAQueriedElement()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<Long> vals = new ArrayList<Long>();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(1)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Long val = (Long) snapshot.getValue();
                vals.add(val);
                if (val == 1L) {
                  semaphore.release(1);
                }
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
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).build());
    ref.child("b").removeValue();
    IntegrationTestHelpers.waitFor(semaphore);
    List<Long> expected = new ArrayList<Long>();
    expected.addAll(Arrays.asList(2L, 1L));
    DeepEquals.assertEquals(expected, vals);
  }

  @Test
  public void startAtLimitWorks() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).build());
    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.limitToFirst(1));

    assertEquals(1L, snap.child("a").getValue());
  }

  @Test
  public void startAtLimitWorksWhenChildIsRemovedCase1664()
      throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.setValue(new MapBuilder().put("a", 1).put("b", 2).build());
    final List<Long> vals = new ArrayList<Long>();
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToFirst(1)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                Long val = (Long) snapshot.getValue();
                vals.add(val);
                semaphore.release(1);
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
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Wait for first value
    IntegrationTestHelpers.waitFor(semaphore);
    assertEquals((Long) 1L, vals.get(0));
    ref.child("a").removeValue();
    IntegrationTestHelpers.waitFor(semaphore);
    assertEquals((Long) 2L, vals.get(1));
  }

  @Test
  public void startAtWithTwoArgumentsWorksCase1169()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(
            ref,
            new MapBuilder()
                .put(
                    "Walker",
                    new MapBuilder()
                        .put("name", "Walker")
                        .put("score", 20)
                        .put(".priority", 20)
                        .build())
                .put(
                    "Michael",
                    new MapBuilder()
                        .put("name", "Michael")
                        .put("score", 100)
                        .put(".priority", 100)
                        .build())
                .build())
        .timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.startAt(20, "Walker").limitToFirst(2));
    List<String> expected = Arrays.asList("Walker", "Michael");
    int i = 0;
    for (DataSnapshot child : snap.getChildren()) {
      assertEquals(expected.get(i), child.getKey());
      i++;
    }
    assertEquals(2, i);
  }

  @Test
  public void handlesMultipleQueriesOnTheSameNode()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(
            ref,
            new MapBuilder()
                .put("a", 1)
                .put("b", 2)
                .put("c", 3)
                .put("d", 4)
                .put("e", 5)
                .put("f", 6)
                .build())
        .timedGet();

    final AtomicBoolean limit2Called = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore(0);
    ref.limitToLast(2)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                // Should only be called once
                assertTrue(limit2Called.compareAndSet(false, true));
                semaphore.release(1);
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Skipping nested calls, no re-entrant APIs in Java

    IntegrationTestHelpers.waitFor(semaphore);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.limitToLast(1));
    Map<String, Object> expected = new MapBuilder().put("f", 6L).build();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void handlesOnceCalledOnANodeWithADefaultListener()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(
            ref,
            new MapBuilder()
                .put("a", 1)
                .put("b", 2)
                .put("c", 3)
                .put("d", 4)
                .put("e", 5)
                .put("f", 6)
                .build())
        .timedGet();

    final AtomicBoolean onCalled = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore(0);
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // Should only be called once
            assertTrue(onCalled.compareAndSet(false, true));
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(semaphore);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.limitToLast(1));
    Map<String, Object> expected = new MapBuilder().put("f", 6L).build();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void handlesOnceCalledOnANodeWithADefaultListenerAndNonCompleteLimit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref, new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build()).timedGet();

    final AtomicBoolean onCalled = new AtomicBoolean(false);
    final Semaphore semaphore = new Semaphore(0);
    ref.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            // Should only be called once
            assertTrue(onCalled.compareAndSet(false, true));
            semaphore.release(1);
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.limitToLast(5));
    Map<String, Object> expected = new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).build();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void remoteRemoveEventTriggers()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    final DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> expected =
        new MapBuilder()
            .put("a", "a")
            .put("b", "b")
            .put("c", "c")
            .put("d", "d")
            .put("e", "e")
            .build();

    new WriteFuture(writer, expected).timedGet();

    List<EventRecord> events =
        new ReadFuture(
                reader.limitToLast(5),
                new ReadFuture.CompletionCondition() {
                  @Override
                  public boolean isComplete(List<EventRecord> events) {
                    if (events.size() == 1) {
                      try {
                        writer.child("c").removeValue();
                      } catch (DatabaseException e) { // ignore
                        throw new AssertionError("Should not throw", e);
                      }
                    }

                    return events.size() == 2;
                  }
                })
            .timedGet();

    DeepEquals.assertEquals(expected, events.get(0).getSnapshot().getValue());
    expected.remove("c");
    DeepEquals.assertEquals(expected, events.get(1).getSnapshot().getValue());
  }

  @Test
  public void endAtWithTwoArgumentsAndLimitWorks()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> toSet =
        new MapBuilder()
            .put("a", "a")
            .put("b", "b")
            .put("c", "c")
            .put("d", "d")
            .put("e", "e")
            .put("f", "f")
            .put("g", "g")
            .put("h", "h")
            .build();

    new WriteFuture(ref, toSet).timedGet();

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.endAt(null, "f").limitToLast(5));
    Map<String, Object> expected =
        new MapBuilder()
            .put("b", "b")
            .put("c", "c")
            .put("d", "d")
            .put("e", "e")
            .put("f", "f")
            .build();

    DeepEquals.assertEquals(expected, snap.getValue());
  }

  @Test
  public void complexUpdateAtQueryRootRaisesCorrectValueEvent()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> toSet =
        new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).put("e", 5).build();

    new WriteFuture(writer, toSet).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future =
        new ReadFuture(
            reader.limitToFirst(4),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  semaphore.release(1);
                }
                return events.size() == 2;
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);
    Map<String, Object> update =
        new MapBuilder()
            .put("b", null)
            .put("c", "a")
            .put("cc", "new")
            .put("cd", "new2")
            .put("d", "gone")
            .build();
    writer.updateChildren(update);
    List<EventRecord> events = future.timedGet();

    Map<String, Object> expected =
        new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).put("d", 4L).build();
    Object result = events.get(0).getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);

    expected =
        new MapBuilder().put("a", 1L).put("c", "a").put("cc", "new").put("cd", "new2").build();
    result = events.get(1).getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void updateAtQueryRootRaisesCorrectValueEvent()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> toSet =
        new MapBuilder().put("bar", "a").put("baz", "b").put("bam", "c").build();

    new WriteFuture(writer, toSet).timedGet();
    final Semaphore semaphore = new Semaphore(0);
    ReadFuture future =
        new ReadFuture(
            reader.limitToLast(10),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  semaphore.release(1);
                }
                return events.size() == 2;
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);
    Map<String, Object> update =
        new MapBuilder().put("bar", "d").put("bam", null).put("bat", "e").build();
    writer.updateChildren(update);
    List<EventRecord> events = future.timedGet();

    Map<String, Object> expected =
        new MapBuilder().put("bar", "a").put("baz", "b").put("bam", "c").build();
    Object result = events.get(0).getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);

    expected = new MapBuilder().put("bar", "d").put("baz", "b").put("bat", "e").build();
    result = events.get(1).getSnapshot().getValue();
    DeepEquals.assertEquals(expected, result);
  }

  @Test
  public void listenForChildAddedEventsWithLimit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValue(1);
    writer.child("b").setValue("b");
    final Map<String, Object> deepObject =
        new MapBuilder()
            .put("deep", "path")
            .put("of", new MapBuilder().put("stuff", true).build())
            .build();
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    reader
        .limitToLast(3)
        .addChildEventListener(
            new ChildEventListener() {
              int count = 0;

              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                if (count == 0) {
                  assertEquals("a", snapshot.getKey());
                  assertEquals(1L, snapshot.getValue());
                } else if (count == 1) {
                  assertEquals("b", snapshot.getKey());
                  assertEquals("b", snapshot.getValue());
                } else if (count == 2) {
                  assertEquals("c", snapshot.getKey());
                  DeepEquals.assertEquals(deepObject, snapshot.getValue());
                } else {
                  fail("Too many events");
                }
                count++;
                semaphore.release(1);
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
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    IntegrationTestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void listenForChildChangedEventsWithLimit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    new WriteFuture(
            writer,
            new MapBuilder().put("a", "something").put("b", "we'll").put("c", "overwrite").build())
        .timedGet();
    final Map<String, Object> deepObject =
        new MapBuilder()
            .put("deep", "path")
            .put("of", new MapBuilder().put("stuff", true).build())
            .build();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader
        .limitToLast(3)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (loaded.compareAndSet(false, true)) {
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Wait for the read to be initialized
    IntegrationTestHelpers.waitFor(semaphore);

    reader
        .limitToLast(3)
        .addChildEventListener(
            new ChildEventListener() {
              int count = 0;

              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                if (count == 0) {
                  assertEquals("a", snapshot.getKey());
                  assertEquals(1L, snapshot.getValue());
                } else if (count == 1) {
                  assertEquals("b", snapshot.getKey());
                  assertEquals("b", snapshot.getValue());
                } else if (count == 2) {
                  assertEquals("c", snapshot.getKey());
                  DeepEquals.assertEquals(deepObject, snapshot.getValue());
                } else {
                  fail("Too many events");
                }
                count++;
                semaphore.release(1);
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                // No-op
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer.child("a").setValue(1);
    writer.child("b").setValue("b");
    writer.child("c").setValue(deepObject);

    IntegrationTestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void listenForChildRemoveEventsWithLimit()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValue(1);
    writer.child("b").setValue("b");
    final Map<String, Object> deepObject =
        new MapBuilder()
            .put("deep", "path")
            .put("of", new MapBuilder().put("stuff", true).build())
            .build();
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader
        .limitToLast(3)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (loaded.compareAndSet(false, true)) {
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Wait for the read to be initialized
    IntegrationTestHelpers.waitFor(semaphore);

    reader
        .limitToLast(3)
        .addChildEventListener(
            new ChildEventListener() {
              int count = 0;

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
                if (count == 0) {
                  assertEquals("a", snapshot.getKey());
                } else if (count == 1) {
                  assertEquals("b", snapshot.getKey());
                } else if (count == 2) {
                  assertEquals("c", snapshot.getKey());
                } else {
                  fail("Too many events");
                }
                count++;
                semaphore.release(1);
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer.child("a").removeValue();
    writer.child("b").removeValue();
    writer.child("c").removeValue();

    IntegrationTestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void listenForChildRemovedWhenParentRemoved()
      throws DatabaseException, InterruptedException, TestFailure, ExecutionException,
          TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValue(1);
    writer.child("b").setValue("b");
    final Map<String, Object> deepObject =
        new MapBuilder()
            .put("deep", "path")
            .put("of", new MapBuilder().put("stuff", true).build())
            .build();
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader
        .limitToLast(3)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (loaded.compareAndSet(false, true)) {
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Wait for the read to be initialized
    IntegrationTestHelpers.waitFor(semaphore);

    reader
        .limitToLast(3)
        .addChildEventListener(
            new ChildEventListener() {
              int count = 0;

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
                if (count == 0) {
                  assertEquals("a", snapshot.getKey());
                } else if (count == 1) {
                  assertEquals("b", snapshot.getKey());
                } else if (count == 2) {
                  assertEquals("c", snapshot.getKey());
                } else {
                  fail("Too many events");
                }
                count++;
                semaphore.release(1);
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer.removeValue();

    IntegrationTestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void listenForChildRemovedWhenParentSetToScalar()
      throws DatabaseException, InterruptedException, TestFailure, ExecutionException,
          TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    writer.child("a").setValue(1);
    writer.child("b").setValue("b");
    final Map<String, Object> deepObject =
        new MapBuilder()
            .put("deep", "path")
            .put("of", new MapBuilder().put("stuff", true).build())
            .build();
    new WriteFuture(writer.child("c"), deepObject).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);
    reader
        .limitToLast(3)
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                if (loaded.compareAndSet(false, true)) {
                  semaphore.release(1);
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    // Wait for the read to be initialized
    IntegrationTestHelpers.waitFor(semaphore);

    reader
        .limitToLast(3)
        .addChildEventListener(
            new ChildEventListener() {
              int count = 0;

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
                if (count == 0) {
                  assertEquals("a", snapshot.getKey());
                } else if (count == 1) {
                  assertEquals("b", snapshot.getKey());
                } else if (count == 2) {
                  assertEquals("c", snapshot.getKey());
                } else {
                  fail("Too many events");
                }
                count++;
                semaphore.release(1);
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    writer.setValue("scalar");

    IntegrationTestHelpers.waitFor(semaphore, 3);
  }

  @Test
  public void queriesBehaveAfterOnce()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> toSet =
        new MapBuilder().put("a", 1).put("b", 2).put("c", 3).put("d", 4).build();

    new WriteFuture(writer, toSet).timedGet();

    IntegrationTestHelpers.getSnap(reader);

    final Semaphore semaphore = new Semaphore(0);
    final AtomicInteger queryAddedCount = new AtomicInteger(0);
    reader
        .startAt(null, "d")
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                queryAddedCount.incrementAndGet();
                semaphore.release(1);
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
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    final AtomicInteger defaultAddedCount = new AtomicInteger(0);
    reader.addChildEventListener(
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
            defaultAddedCount.incrementAndGet();
            semaphore.release(1);
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
            // No-op
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(semaphore, 5);
    assertEquals(1, queryAddedCount.get());
    assertEquals(4, defaultAddedCount.get());
  }

  @Test
  public void case2003CorrectlyGetEventsForStartAtEndAtQueriesWhenPriorityChanges()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final List<String> addedFirst = new ArrayList<String>();
    final List<String> removedFirst = new ArrayList<String>();
    final List<String> addedSecond = new ArrayList<String>();
    final List<String> removedSecond = new ArrayList<String>();

    ref.startAt(0)
        .endAt(10)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                addedFirst.add(snapshot.getKey());
              }

              @Override
              public void onChildChanged(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onChildRemoved(DataSnapshot snapshot) {
                removedFirst.add(snapshot.getKey());
              }

              @Override
              public void onChildMoved(DataSnapshot snapshot, String previousChildName) {
                // No-op
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.startAt(10)
        .endAt(20)
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                addedSecond.add(snapshot.getKey());
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
                removedSecond.add(snapshot.getKey());
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    ref.child("a").setValue("a", 5);
    ref.child("a").setValue("a", 15);
    ref.child("a").setValue("a", 10);
    new WriteFuture(ref.child("a"), "a", 5).timedGet();

    assertEquals(2, addedFirst.size());
    assertEquals("a", addedFirst.get(0));
    assertEquals("a", addedFirst.get(1));

    assertEquals(1, removedFirst.size());
    assertEquals("a", removedFirst.get(0));

    assertEquals(1, addedSecond.size());
    assertEquals("a", addedSecond.get(0));

    assertEquals(1, removedSecond.size());
    assertEquals("a", removedSecond.get(0));
  }

  @Test
  public void behavesWithDivergingQueries()
      throws DatabaseException, TestFailure, ExecutionException, TimeoutException,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    final DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    final Map<String, Object> toSet =
        new MapBuilder()
            .put("a", new MapBuilder().put("b", 1L).put("c", 2L).build())
            .put("e", 3L)
            .build();

    new WriteFuture(writer, toSet).timedGet();

    final AtomicBoolean childCalled = new AtomicBoolean(false);
    reader
        .child("a/b")
        .addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                assertTrue(childCalled.compareAndSet(false, true));
                assertEquals(1L, snapshot.getValue());
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });

    new ReadFuture(
            reader.limitToLast(2),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                if (events.size() == 1) {
                  DeepEquals.assertEquals(toSet, events.get(0).getSnapshot().getValue());
                  try {
                    writer.child("d").setValue(4);
                  } catch (DatabaseException e) { // ignore
                    throw new AssertionError("Should not throw", e);
                  }
                  return false;
                } else {
                  Map<String, Object> expected = new MapBuilder().put("d", 4L).put("e", 3L).build();
                  DeepEquals.assertEquals(expected, events.get(1).getSnapshot().getValue());
                  return true;
                }
              }
            })
        .timedGet();
    assertTrue(childCalled.get());
  }

  @Test
  public void staleItemsRemovedFromTheCache()
      throws InterruptedException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final AtomicBoolean startChecking = new AtomicBoolean(false);
    final Semaphore ready = new Semaphore(0);
    ReadFuture future =
        new ReadFuture(
            reader.limitToLast(2),
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                DataSnapshot snap = events.get(events.size() - 1).getSnapshot();
                Object result = snap.getValue();
                if (startChecking.compareAndSet(false, true) && result == null) {
                  ready.release(1);
                  return false;
                }
                // We already initialized the location, and now the remove has happened so that we
                // have no more data
                return startChecking.get() && result == null;
              }
            });

    IntegrationTestHelpers.waitFor(ready);
    for (int i = 0; i < 4; ++i) {
      writer.child("k" + i).setValue(i);
    }

    writer.removeValue();
    future.timedGet();
  }

  @Test
  public void integerKeysBehaveNumerically1()
      throws InterruptedException, TestFailure, TimeoutException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        new MapBuilder()
            .put("1", true)
            .put("50", true)
            .put("550", true)
            .put("6", true)
            .put("600", true)
            .put("70", true)
            .put("8", true)
            .put("80", true)
            .build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.startAt(null, "80")
                .addListenerForSingleValueEvent(
                    new ValueEventListener() {
                      @Override
                      public void onDataChange(DataSnapshot snapshot) {
                        Map<String, Object> expected =
                            new MapBuilder()
                                .put("80", true)
                                .put("550", true)
                                .put("600", true)
                                .build();
                        DeepEquals.assertEquals(expected, snapshot.getValue());
                        done.release();
                      }

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void integerKeysBehaveNumerically2()
      throws InterruptedException, TestFailure, TimeoutException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        new MapBuilder()
            .put("1", true)
            .put("50", true)
            .put("550", true)
            .put("6", true)
            .put("600", true)
            .put("70", true)
            .put("8", true)
            .put("80", true)
            .build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.endAt(null, "50")
                .addListenerForSingleValueEvent(
                    new ValueEventListener() {
                      @Override
                      public void onDataChange(DataSnapshot snapshot) {
                        Map<String, Object> expected =
                            new MapBuilder()
                                .put("1", true)
                                .put("6", true)
                                .put("8", true)
                                .put("50", true)
                                .build();
                        DeepEquals.assertEquals(expected, snapshot.getValue());
                        done.release();
                      }

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void integerKeysBehaveNumerically3()
      throws InterruptedException, TestFailure, TimeoutException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        new MapBuilder()
            .put("1", true)
            .put("50", true)
            .put("550", true)
            .put("6", true)
            .put("600", true)
            .put("70", true)
            .put("8", true)
            .put("80", true)
            .build(),
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            ref.startAt(null, "50")
                .endAt(null, "80")
                .addListenerForSingleValueEvent(
                    new ValueEventListener() {
                      @Override
                      public void onDataChange(DataSnapshot snapshot) {
                        Map<String, Object> expected =
                            new MapBuilder()
                                .put("50", true)
                                .put("70", true)
                                .put("80", true)
                                .build();
                        DeepEquals.assertEquals(expected, snapshot.getValue());
                        done.release();
                      }

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });
          }
        });

    IntegrationTestHelpers.waitFor(done);
  }

  @Test
  public void moveOutsideOfWindowIntoWindow()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    Map<String, Object> initialValue =
        new MapBuilder()
            .put("a", new MapBuilder().put(".priority", 1L).put(".value", "a").build())
            .put("b", new MapBuilder().put(".priority", 2L).put(".value", "b").build())
            .put("c", new MapBuilder().put(".priority", 3L).put(".value", "c").build())
            .build();
    new WriteFuture(ref, initialValue).timedGet();
    final Query query = ref.limitToLast(2);
    final Semaphore ready = new Semaphore(0);
    final AtomicBoolean loaded = new AtomicBoolean(false);

    query.addValueEventListener(
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            if (loaded.compareAndSet(false, true)) {
              assertEquals(2, snapshot.getChildrenCount());
              assertTrue(snapshot.hasChild("b"));
              assertTrue(snapshot.hasChild("c"));
              ready.release(1);
            } else {
              System.out.println("Got snap: " + snapshot.getValue().toString());
            }
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        });

    IntegrationTestHelpers.waitFor(ready);

    ref.child("a")
        .setPriority(
            4L,
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                ready.release(1);
              }
            });

    IntegrationTestHelpers.waitFor(ready);
  }

  @Test
  public void emptyLimitWithBadHash() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.getRepo().setHijackHash(true);

    DataSnapshot snap = IntegrationTestHelpers.getSnap(ref.limitToLast(1));
    assertNull(snap.getValue());

    ref.getRepo().setHijackHash(false);
  }

  @Test
  public void addingQueriesDoesNotAffectOthers()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    new WriteFuture(ref.child("0"), "test1").timedGet();

    ChildEventListener childEventListener =
        new ChildEventListener() {
          @Override
          public void onChildAdded(DataSnapshot dataSnapshot, String s) {}

          @Override
          public void onChildChanged(DataSnapshot dataSnapshot, String s) {}

          @Override
          public void onChildRemoved(DataSnapshot dataSnapshot) {}

          @Override
          public void onChildMoved(DataSnapshot dataSnapshot, String s) {}

          @Override
          public void onCancelled(DatabaseError firebaseError) {}
        };
    ref.startAt("a").endAt("b").addChildEventListener(childEventListener);

    DataSnapshot snapshot = new ReadFuture(ref).timedGet().get(0).getSnapshot();
    List<Object> expected = new ArrayList<Object>();
    expected.add("test1");
    assertEquals(expected, snapshot.getValue());
  }

  @Test
  public void equalToOnlyReturnsChildrenEqualTo() throws DatabaseException, InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ValueExpectationHelper expectations = new ValueExpectationHelper();
    expectations.add(ref.equalTo(1), new MapBuilder().put("a", "vala").build());
    expectations.add(ref.equalTo(2), new MapBuilder().put("b", "valb").build());
    expectations.add(ref.equalTo("abc"), new MapBuilder().put("z", "valz").build());
    expectations.add(ref.equalTo(2, "no_key"), null);
    expectations.add(ref.equalTo(2, "b"), new MapBuilder().put("b", "valb").build());

    ref.child("a").setValue("vala", 1);
    ref.child("b").setValue("valb", 2);
    ref.child("c").setValue("valc", 3);
    ref.child("z").setValue("valz", "abc");

    expectations.waitForEvents();
  }

  @Test
  public void removeListenerOnDefaultQueryRemovesAllQueryListeners()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    new WriteFuture(ref.child("a"), "foo", 100).timedGet();

    final Semaphore semaphore = new Semaphore(0);
    final DataSnapshot[] snapshotHolder = new DataSnapshot[1];

    ValueEventListener listener =
        ref.startAt(99)
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    snapshotHolder[0] = snapshot;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail("Unexpected error: " + error);
                  }
                });

    IntegrationTestHelpers.waitFor(semaphore);
    Map<String, Object> expected = new MapBuilder().put("a", "foo").build();
    DeepEquals.assertEquals(expected, snapshotHolder[0].getValue());

    ref.removeEventListener(listener);

    new WriteFuture(ref.child("a"), "bar", 100).timedGet();
    // the listener is removed the value should have not changed
    DeepEquals.assertEquals(expected, snapshotHolder[0].getValue());
  }

  @Test
  public void handlesFallbackForOrderBy() throws InterruptedException {
    final DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore done = new Semaphore(0);

    Map<String, Object> initial =
        new MapBuilder()
            .put("a", new MapBuilder().put("foo", 3).build())
            .put("b", new MapBuilder().put("foo", 1).build())
            .put("c", new MapBuilder().put("foo", 2).build())
            .build();

    ref.setValue(
        initial,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            done.release();
          }
        });

    IntegrationTestHelpers.waitFor(done);

    final List<String> children = new ArrayList<String>();
    ref.orderByChild("foo")
        .addChildEventListener(
            new ChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                children.add(snapshot.getKey());
                if (children.size() == 3) {
                  done.release();
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

    IntegrationTestHelpers.waitFor(done);

    List<String> expected = new ArrayList<String>();
    expected.add("b");
    expected.add("c");
    expected.add("a");
    DeepEquals.assertEquals(expected, children);
  }

  @Test
  public void notifiesOfDeletesWhileOffline() throws DatabaseException, InterruptedException {
    // Create a fresh connection so we can be sure we won't get any other data updates for stuff.
    DatabaseReference writerRef = IntegrationTestHelpers.getRandomNode();
    final DatabaseConfig ctx = IntegrationTestHelpers.newTestConfig();
    final DatabaseReference queryRef = new DatabaseReference(writerRef.toString(), ctx);
    final List<DataSnapshot> readSnaps = new ArrayList<DataSnapshot>();
    final Semaphore semaphore = new Semaphore(0);
    final Map data = new MapBuilder().put("a", 1L).put("b", 2L).put("c", 3L).build();

    // Write 3 children and then start our limit query.
    writerRef.setValue(
        data,
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            queryRef
                .limitToLast(3)
                .addValueEventListener(
                    new ValueEventListener() {
                      @Override
                      public void onDataChange(DataSnapshot snapshot) {
                        readSnaps.add(snapshot);
                        if (readSnaps.size() == 1) {
                          DeepEquals.assertEquals(data, snapshot.getValue());
                          semaphore.release();
                        } else if (readSnaps.size() == 2) {
                          assertNull(snapshot.child("b").getValue());
                          semaphore.release();
                        } else {
                          fail("An extra value event received!");
                        }
                      }

                      @Override
                      public void onCancelled(DatabaseError error) {}
                    });
          }
        });

    IntegrationTestHelpers.waitFor(semaphore);

    // Now make the queryRef go offline so we don't get updates.
    RepoManager.interrupt(ctx);

    // Delete an item in the query and then bring our connection back up.
    writerRef
        .child("b")
        .removeValue(
            new DatabaseReference.CompletionListener() {
              @Override
              public void onComplete(DatabaseError error, DatabaseReference ref) {
                // Bring the queryRef back online.
                RepoManager.resume(ctx);
              }
            });

    // Now wait for us to get notified that b is deleted.
    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void querySnapshotChildrenRespectDefaultOrdering()
      throws DatabaseException, ExecutionException, TimeoutException, TestFailure,
          InterruptedException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);
    final Semaphore semaphore = new Semaphore(0);

    final Map list =
        new MapBuilder()
            .put(
                "a",
                new MapBuilder()
                    .put(
                        "thisvaluefirst",
                        new MapBuilder().put(".value", true).put(".priority", 1).build())
                    .put(
                        "name",
                        new MapBuilder().put(".value", "Michael").put(".priority", 2).build())
                    .put(
                        "thisvaluelast",
                        new MapBuilder().put(".value", true).put(".priority", 3).build())
                    .build())
            .put(
                "b",
                new MapBuilder()
                    .put(
                        "thisvaluefirst",
                        new MapBuilder().put(".value", true).put(".priority", null).build())
                    .put("name", new MapBuilder().put(".value", "Rob").put(".priority", 2).build())
                    .put(
                        "thisvaluelast",
                        new MapBuilder().put(".value", true).put(".priority", 3).build())
                    .build())
            .put(
                "c",
                new MapBuilder()
                    .put(
                        "thisvaluefirst",
                        new MapBuilder().put(".value", true).put(".priority", 1).build())
                    .put(
                        "name", new MapBuilder().put(".value", "Jonny").put(".priority", 2).build())
                    .put(
                        "thisvaluelast",
                        new MapBuilder().put(".value", true).put(".priority", "somestring").build())
                    .build())
            .build();

    new WriteFuture(writer, list).timedGet();

    reader
        .orderByChild("name")
        .addListenerForSingleValueEvent(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                List<String> expectedKeys = new ArrayList<String>();
                expectedKeys.add("thisvaluefirst");
                expectedKeys.add("name");
                expectedKeys.add("thisvaluelast");

                List<String> expectedNames = new ArrayList<String>();
                expectedNames.add("Jonny");
                expectedNames.add("Michael");
                expectedNames.add("Rob");

                // Validate that snap.child() resets order to default for child snaps
                List orderedKeys = new ArrayList();
                for (DataSnapshot childSnap : snapshot.child("b").getChildren()) {
                  orderedKeys.add(childSnap.getKey());
                }
                Assert.assertEquals(expectedKeys, orderedKeys);

                // Validate that snap.forEach() resets ordering to default for child snaps
                List orderedNames = new ArrayList();
                for (DataSnapshot childSnap : snapshot.getChildren()) {
                  orderedNames.add(childSnap.child("name").getValue());
                  orderedKeys.clear();
                  for (DataSnapshot grandchildSnap : childSnap.getChildren()) {
                    orderedKeys.add(grandchildSnap.getKey());
                  }
                  Assert.assertEquals(expectedKeys, orderedKeys);
                }
                Assert.assertEquals(expectedNames, orderedNames);
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {}
            });
    IntegrationTestHelpers.waitFor(semaphore);
  }

  @Test
  public void testAddingListensForTheSamePathDoesNotCheckFail() throws Throwable {
    // This bug manifests itself if there's a hierarchy of query listener, default listener and
    // one-time listener underneath. During one-time listener registration, sync-tree traversal
    // stopped as soon as it found a complete server cache (this is the case for not indexed query
    // view). The problem is that the same traversal was looking for a ancestor default view, and
    // the early exit prevented from finding the default listener above the one-time listener. Event
    // removal code path wasn't removing the listener because it stopped as soon as it found the
    // default view. This left the zombie one-time listener and check failed on the second attempt
    // to create a listener for the same path (asana#61028598952586).

    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);

    ValueEventListener dummyListen =
        new ValueEventListener() {
          @Override
          public void onDataChange(DataSnapshot snapshot) {
            semaphore.release();
          }

          @Override
          public void onCancelled(DatabaseError error) {}
        };

    ref.child("child").setValue(IntegrationTestHelpers.fromJsonString("{\"name\": \"John\"}"));

    ref.orderByChild("name").equalTo("John").addValueEventListener(dummyListen);
    ref.child("child").addValueEventListener(dummyListen);
    IntegrationTestHelpers.waitFor(semaphore, 2);

    ref.child("child").child("favoriteToy").addListenerForSingleValueEvent(dummyListen);
    IntegrationTestHelpers.waitFor(semaphore, 1);

    ref.child("child").child("favoriteToy").addListenerForSingleValueEvent(dummyListen);
    IntegrationTestHelpers.waitFor(semaphore, 1);

    ref.removeEventListener(dummyListen);
    ref.child("child").removeEventListener(dummyListen);
  }
}
