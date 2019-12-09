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

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeoutException;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class OrderByTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  @Test
  public void snapshotsAreIteratedInOrder()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> initial =
        IntegrationTestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"nuggets\": 60},"
                + "\"greg\": {\"nuggets\": 52},"
                + "\"rob\": {\"nuggets\": 56},"
                + "\"vassili\": {\"nuggets\": 55.5},"
                + "\"tony\": {\"nuggets\": 52}"
                + "}");
    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili", "rob", "alex");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony", "vassili", "rob");

    Query query = ref.orderByChild("nuggets");

    final List<String> valueOrder = new ArrayList<String>();
    final List<String> childOrder = new ArrayList<String>();
    final List<String> childPrevNames = new ArrayList<String>();
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void canUseDeepPathsForIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> initial =
        IntegrationTestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"deep\": {\"nuggets\": 60}},"
                + "\"greg\": {\"deep\": {\"nuggets\": 52}},"
                + "\"rob\": {\"deep\": {\"nuggets\": 56}},"
                + "\"vassili\": {\"deep\": {\"nuggets\": 55.5}},"
                + "\"tony\": {\"deep\": {\"nuggets\": 52}}"
                + "}");
    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony");

    new WriteFuture(ref, initial).timedGet();

    Query query = ref.orderByChild("deep/nuggets").limitToFirst(3);

    final List<String> valueOrder = new ArrayList<String>();
    final List<String> childOrder = new ArrayList<String>();
    final List<String> childPrevNames = new ArrayList<String>();
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    IntegrationTestHelpers.waitForRoundtrip(ref);

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void snapshotsAreIteratedInOrderForValueIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> initial =
        IntegrationTestHelpers.fromJsonString(
            "{"
                + "\"alex\": 60,"
                + "\"greg\": 52,"
                + "\"rob\": 56,"
                + "\"vassili\": 55.5,"
                + "\"tony\": 52"
                + "}");
    List<String> expectedOrder = Arrays.asList("greg", "tony", "vassili", "rob", "alex");
    List<String> expectedPrevNames = Arrays.asList(null, "greg", "tony", "vassili", "rob");

    Query query = ref.orderByValue();

    final List<String> valueOrder = new ArrayList<String>();
    final List<String> childOrder = new ArrayList<String>();
    final List<String> childPrevNames = new ArrayList<String>();
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void childMovedEventsAreFired() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    Map<String, Object> initial =
        IntegrationTestHelpers.fromJsonString(
            "{"
                + "\"alex\": {\"nuggets\": 60},"
                + "\"greg\": {\"nuggets\": 52},"
                + "\"rob\": {\"nuggets\": 56},"
                + "\"vassili\": {\"nuggets\": 55.5},"
                + "\"tony\": {\"nuggets\": 52}"
                + "}");

    Query query = ref.orderByChild("nuggets");

    final Semaphore semaphore = new Semaphore(0);
    final String[] prevName = new String[1];
    final DataSnapshot[] snapshot = new DataSnapshot[1];
    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {}

              @Override
              public void onChildMoved(DataSnapshot snap, String previousChildName) {
                snapshot[0] = snap;
                prevName[0] = previousChildName;
                semaphore.release();
              }

              @Override
              public void onChildChanged(DataSnapshot snap, String previousChildName) {}
            });

    ref.setValue(initial);
    ref.child("greg/nuggets").setValue(57);

    IntegrationTestHelpers.waitFor(semaphore);

    Assert.assertEquals("greg", snapshot[0].getKey());
    Assert.assertEquals("rob", prevName[0]);
    Map<String, Long> expectedValue = new HashMap<String, Long>();
    expectedValue.put("nuggets", 57L);
    Assert.assertEquals(expectedValue, snapshot[0].getValue());

    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
  }

  @Test
  public void callbackRemovalWorks() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    final int[] reads = new int[1];
    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener fooListener =
        ref.orderByChild("foo")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    reads[0]++;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });
    ValueEventListener barListener =
        ref.orderByChild("bar")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    reads[0]++;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });
    ValueEventListener bazListener =
        ref.orderByChild("baz")
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {
                    reads[0]++;
                    semaphore.release();
                  }

                  @Override
                  public void onCancelled(DatabaseError error) {
                    Assert.fail();
                  }
                });
    ValueEventListener defaultListener =
        ref.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                reads[0]++;
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    // wait for initial null events.
    IntegrationTestHelpers.waitFor(semaphore, 4);
    Assert.assertEquals(4, reads[0]);

    ref.setValue(1);

    IntegrationTestHelpers.waitFor(semaphore, 4);
    Assert.assertEquals(8, reads[0]);

    ref.removeEventListener(fooListener);
    ref.setValue(2);

    IntegrationTestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(11, reads[0]);

    // Should be a no-op resulting in 3 more reads
    ref.orderByChild("foo").removeEventListener(bazListener);
    ref.setValue(3);

    IntegrationTestHelpers.waitFor(semaphore, 3);
    Assert.assertEquals(14, reads[0]);

    ref.orderByChild("bar").removeEventListener(barListener);
    ref.setValue(4);
    IntegrationTestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(16, reads[0]);

    // Now, remove everything
    ref.removeEventListener(bazListener);
    ref.removeEventListener(defaultListener);
    ref.setValue(5);

    // No more reads
    IntegrationTestHelpers.waitForRoundtrip(ref);
    Assert.assertEquals(16, reads[0]);
  }

  @Test
  public void childAddedEventsAreInCorrectOrder() throws InterruptedException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> initial =
        new MapBuilder()
            .put("a", new MapBuilder().put("value", 5L).build())
            .put("c", new MapBuilder().put("value", 3L).build())
            .build();

    final List<String> snapshotNames = new ArrayList<String>();
    final List<String> prevNames = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ChildEventListener testListener =
        ref.orderByChild("value")
            .addChildEventListener(
                new TestChildEventListener() {
                  @Override
                  public void onChildAdded(DataSnapshot snap, String prevName) {
                    snapshotNames.add(snap.getKey());
                    prevNames.add(prevName);
                    semaphore.release();
                  }
                });

    ref.setValue(initial);
    IntegrationTestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(Arrays.asList("c", "a"), snapshotNames);
    Assert.assertEquals(Arrays.asList(null, "c"), prevNames);

    Map<String, Object> updates = new HashMap<String, Object>();
    updates.put("b", new MapBuilder().put("value", 4).build());
    updates.put("d", new MapBuilder().put("value", 2).build());
    ref.updateChildren(updates);

    IntegrationTestHelpers.waitFor(semaphore, 2);
    Assert.assertEquals(Arrays.asList("c", "a", "d", "b"), snapshotNames);
    Assert.assertEquals(Arrays.asList(null, "c", null, "c"), prevNames);
    ref.removeEventListener(testListener);
  }

  @Test
  public void updatesForUnindexedQuery()
      throws InterruptedException, ExecutionException, TestFailure, TimeoutException {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference reader = refs.get(0);
    DatabaseReference writer = refs.get(1);

    final List<DataSnapshot> snapshots = new ArrayList<DataSnapshot>();

    Map<String, Object> value = new HashMap<String, Object>();
    value.put("one", new MapBuilder().put("index", 1).put("value", "one").build());
    value.put("two", new MapBuilder().put("index", 2).put("value", "two").build());
    value.put("three", new MapBuilder().put("index", 3).put("value", "three").build());

    new WriteFuture(writer, value).timedGet();

    final Semaphore semaphore = new Semaphore(0);

    Query query = reader.orderByChild("index").limitToLast(2);
    ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                snapshots.add(snapshot);
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    Assert.assertEquals(1, snapshots.size());

    Map<String, Object> expected1 = new HashMap<String, Object>();
    expected1.put("two", new MapBuilder().put("index", 2L).put("value", "two").build());
    expected1.put("three", new MapBuilder().put("index", 3L).put("value", "three").build());
    Assert.assertEquals(expected1, snapshots.get(0).getValue());

    // update child which should trigger value event
    writer.child("one/index").setValue(4);
    IntegrationTestHelpers.waitFor(semaphore);

    Assert.assertEquals(2, snapshots.size());
    Map<String, Object> expected2 = new HashMap<String, Object>();
    expected2.put("three", new MapBuilder().put("index", 3L).put("value", "three").build());
    expected2.put("one", new MapBuilder().put("index", 4L).put("value", "one").build());
    Assert.assertEquals(expected2, snapshots.get(1).getValue());

    // cleanup
    IntegrationTestHelpers.waitForRoundtrip(reader);
    reader.removeEventListener(listener);
  }

  @Test
  public void queriesWorkOnLeafNodes()
      throws DatabaseException, InterruptedException, ExecutionException, TestFailure,
          TimeoutException {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    final Semaphore semaphore = new Semaphore(0);
    new WriteFuture(ref, "leaf-node").timedGet();

    final List<DataSnapshot> snapshots = new ArrayList<DataSnapshot>();
    Query query = ref.orderByChild("foo").limitToLast(1);
    ValueEventListener listener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                snapshots.add(snapshot);
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    Assert.assertEquals(1, snapshots.size());
    Assert.assertNull(snapshots.get(0).getValue());

    // cleanup
    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(listener);
  }

  @Test
  public void serverRespectsKeyIndex()
      throws InterruptedException, ExecutionException, TimeoutException, TestFailure {
    List<DatabaseReference> refs = IntegrationTestHelpers.getRandomNode(2);
    DatabaseReference writer = refs.get(0);
    DatabaseReference reader = refs.get(1);

    Map<String, Object> initial = new MapBuilder().put("a", 1).put("b", 2).put("c", 3).build();
    // If the server doesn't respect the index, it will send down limited data, but with no offset,
    // so the expected
    // and actual data don't match
    Query query = reader.orderByKey().startAt("b").limitToFirst(2);

    List<String> expectedChildren = Arrays.asList("b", "c");

    new WriteFuture(writer, initial).timedGet();

    final List<String> actualChildren = new ArrayList<String>();
    final Semaphore semaphore = new Semaphore(0);
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  actualChildren.add(child.getKey());
                }
                semaphore.release();
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    IntegrationTestHelpers.waitFor(semaphore);

    Assert.assertEquals(expectedChildren, actualChildren);

    // cleanup
    reader.removeEventListener(valueListener);
  }

  @Test
  public void startAtAndEndAtWorkOnValueIndex() throws Throwable {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Map<String, Object> initial =
        IntegrationTestHelpers.fromJsonString(
            "{"
                + "\"alex\": 60,"
                + "\"greg\": 52,"
                + "\"rob\": 56,"
                + "\"vassili\": 55.5,"
                + "\"tony\": 52"
                + "}");
    List<String> expectedOrder = Arrays.asList("tony", "vassili", "rob");
    List<String> expectedPrevNames = Arrays.asList(null, "tony", "vassili");

    Query query = ref.orderByValue().startAt(52, "tony").endAt(56);

    final List<String> valueOrder = new ArrayList<String>();
    final List<String> childOrder = new ArrayList<String>();
    final List<String> childPrevNames = new ArrayList<String>();
    ValueEventListener valueListener =
        query.addValueEventListener(
            new ValueEventListener() {
              @Override
              public void onDataChange(DataSnapshot snapshot) {
                for (DataSnapshot child : snapshot.getChildren()) {
                  valueOrder.add(child.getKey());
                }
              }

              @Override
              public void onCancelled(DatabaseError error) {
                Assert.fail();
              }
            });

    ChildEventListener testListener =
        query.addChildEventListener(
            new TestChildEventListener() {
              @Override
              public void onChildAdded(DataSnapshot snapshot, String previousChildName) {
                childOrder.add(snapshot.getKey());
                childPrevNames.add(previousChildName);
              }
            });

    new WriteFuture(ref, initial).timedGet();

    Assert.assertEquals(expectedOrder, valueOrder);
    Assert.assertEquals(expectedOrder, childOrder);
    Assert.assertEquals(expectedPrevNames, childPrevNames);

    // cleanup
    IntegrationTestHelpers.waitForRoundtrip(ref);
    ref.removeEventListener(testListener);
    ref.removeEventListener(valueListener);
  }

  @Test
  public void removingDefaultListenerRemovesNonDefaultListenWithLoadsAllData() throws Throwable {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    Object initialData = new MapBuilder().put("key", "value").build();
    new WriteFuture(ref, initialData).timedGet();

    ValueEventListener listener =
        ref.orderByKey()
            .addValueEventListener(
                new ValueEventListener() {
                  @Override
                  public void onDataChange(DataSnapshot snapshot) {}

                  @Override
                  public void onCancelled(DatabaseError error) {}
                });

    ref.addValueEventListener(listener);
    // Should remove both listener and should remove the listen sent to the server
    ref.removeEventListener(listener);

    // This used to crash because a listener for ref.orderByKey() existed already
    Object result = new ReadFuture(ref.orderByKey()).waitForLastValue();
    assertEquals(initialData, result);
  }
}
