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

package com.google.firebase.database.core.persistence;

import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.IntegrationTestHelpers;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.Query;
import com.google.firebase.database.RetryRule;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class KeepSyncedTest {

  @Rule public RetryRule retryRule = new RetryRule(3);

  @After
  public void tearDown() {
    IntegrationTestHelpers.failOnFirstUncaughtException();
  }

  static long globalKeepSyncedTestCounter = 0;

  private void assertIsKeptSynced(Query query) throws Exception {
    DatabaseReference ref = query.getRef();

    // First set a unique value to the value of a child.
    long counter = globalKeepSyncedTestCounter++;
    final Map<String, Object> value = new MapBuilder().put("child", counter).build();
    new WriteFuture(ref, value).timedGet();

    // Next go offline, if it's kept synced we should have kept the value, after going offline no
    // way to get the value except from cache
    ref.getDatabase().goOffline();

    new ReadFuture(
            query,
            (List<EventRecord> events) -> {
              assertEquals(1, events.size());
              assertEquals(value, events.get(0).getSnapshot().getValue());
              return true;
            })
        .timedGet();

    // All good, go back online
    ref.getDatabase().goOnline();
  }

  private void assertNotKeptSynced(Query query) throws Exception {
    DatabaseReference ref = query.getRef();

    // First set a unique value to the value of a child.
    long current = globalKeepSyncedTestCounter++;
    final Map<String, Object> oldValue = new MapBuilder().put("child", current).build();

    long next = globalKeepSyncedTestCounter++;
    final Map<String, Object> nextValue = new MapBuilder().put("child", next).build();

    new WriteFuture(ref, oldValue).timedGet();

    // Next go offline, if it's kept synced we should have kept the value and we'll get an even
    // with the *old* value.
    ref.getDatabase().goOffline();

    ReadFuture readFuture =
        new ReadFuture(
            query,
            (List<EventRecord> events) -> {
              // We expect this to get called with the next value, not the old value.
              assertEquals(1, events.size());
              assertEquals(nextValue, events.get(0).getSnapshot().getValue());
              return true;
            });

    // By now, if we had it synced we should have gotten an event with the wrong value
    // Write a new value so the value event listener will be triggered
    ref.setValue(nextValue);
    readFuture.timedGet();

    // All good, go back online
    ref.getDatabase().goOnline();
  }

  @Test
  public void keepSynced() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    ref.keepSynced(false);
    assertNotKeptSynced(ref);
  }

  // NOTE: This is not ideal behavior and should be fixed in a future release
  @Test
  public void keepSyncedAffectsQueries() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    ref.keepSynced(true);
    Query query = ref.limitToFirst(5);
    query.keepSynced(true);
    assertIsKeptSynced(ref);

    ref.keepSynced(false);
    assertNotKeptSynced(ref);
    assertNotKeptSynced(
        query); // currently, setting false on the default query affects all queries at that
    // location
  }

  @Test
  public void manyKeepSyncedCallsDontAccumulate() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.keepSynced(true);
    ref.keepSynced(true);
    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    // If it were balanced, this would not be enough
    ref.keepSynced(false);
    ref.keepSynced(false);
    assertNotKeptSynced(ref);

    // If it were balanced, this would not be enough
    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    // cleanup
    ref.keepSynced(false);
  }

  // NOTE: No RemoveAllListenersDoesNotAffectKeepSynced test, since JVM client doesn't have
  // removeAllListeners...

  @Test
  public void removeSingleListenerDoesNotAffectKeepSynced() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    // This will add and remove a listener.
    new ReadFuture(ref, (List<EventRecord> events) -> true).timedGet();

    assertIsKeptSynced(ref);

    // cleanup
    ref.keepSynced(false);
  }

  @Test
  public void keepSyncedNoDoesNotAffectExistingListener() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();

    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    ReadFuture readFuture =
        new ReadFuture(
            ref,
            new ReadFuture.CompletionCondition() {
              @Override
              public boolean isComplete(List<EventRecord> events) {
                return events.get(events.size() - 1).getSnapshot().getValue().equals("done");
              }
            });

    // cleanup
    ref.keepSynced(false);

    // Should trigger our listener.
    ref.setValue("done");
    readFuture.timedGet();
  }

  @Test
  public void differentQueriesAreIndependent() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    Query query1 = ref.limitToFirst(1);
    Query query2 = ref.limitToFirst(2);

    query1.keepSynced(true);
    assertIsKeptSynced(query1);
    assertNotKeptSynced(query2);

    query2.keepSynced(true);
    assertIsKeptSynced(query1);
    assertIsKeptSynced(query2);

    query1.keepSynced(false);
    assertIsKeptSynced(query2);
    assertNotKeptSynced(query1);

    query2.keepSynced(false);
    assertNotKeptSynced(query1);
    assertNotKeptSynced(query2);
  }

  @Test
  public void childIsKeptSynced() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode();
    DatabaseReference child = ref.child("random-child");

    ref.keepSynced(true);
    assertIsKeptSynced(child);

    // cleanup
    ref.keepSynced(false);
  }

  @Test
  public void rootIsKeptSynced() throws Exception {
    DatabaseReference ref = IntegrationTestHelpers.getRandomNode().getRoot();

    ref.keepSynced(true);
    assertIsKeptSynced(ref);

    // cleanup
    ref.keepSynced(false);
  }

  // TODO[offline]: Cancel listens for keep synced....
}
