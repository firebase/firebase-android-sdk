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

import static com.google.firebase.database.IntegrationTestHelpers.childKeySet;
import static com.google.firebase.database.IntegrationTestHelpers.defaultQueryAt;
import static com.google.firebase.database.IntegrationTestHelpers.failOnFirstUncaughtException;
import static com.google.firebase.database.IntegrationTestHelpers.fromSingleQuotedString;
import static com.google.firebase.database.IntegrationTestHelpers.goOffline;
import static com.google.firebase.database.IntegrationTestHelpers.goOnline;
import static com.google.firebase.database.IntegrationTestHelpers.newFrozenTestConfig;
import static com.google.firebase.database.IntegrationTestHelpers.path;
import static com.google.firebase.database.IntegrationTestHelpers.rootWithConfig;
import static com.google.firebase.database.IntegrationTestHelpers.setForcedPersistentCache;
import static com.google.firebase.database.IntegrationTestHelpers.waitFor;
import static com.google.firebase.database.IntegrationTestHelpers.waitForQueue;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.hamcrest.Matchers.lessThan;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.EventRecord;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.RetryRule;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.Context;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.EventRegistration;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.SyncTree;
import com.google.firebase.database.core.Tag;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.core.utilities.TestClock;
import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.future.ReadFuture;
import com.google.firebase.database.future.WriteFuture;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class PersistenceTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  private final QuerySpec defaultFooQuery = defaultQueryAt("foo");
  private final QuerySpec limit3FooQuery =
      new QuerySpec(new Path("foo"), QueryParams.DEFAULT_PARAMS.limitToLast(3));
  private final QuerySpec limit2FooQuery =
      new QuerySpec(new Path("foo"), QueryParams.DEFAULT_PARAMS.limitToLast(2));

  @After
  public void tearDown() throws IOException {
    failOnFirstUncaughtException();
  }

  private PersistenceManager newTestPersistenceManager() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    engine.disableTransactionCheck = true;
    return new DefaultPersistenceManager(newFrozenTestConfig(), engine, CachePolicy.NONE);
  }

  private SyncTree newTestSyncTree(PersistenceManager persistenceManager) {
    return newTestSyncTree(
        persistenceManager, Collections.<UserWriteRecord>emptyList(), new MockListenProvider());
  }

  private SyncTree newTestSyncTree(
      PersistenceManager persistenceManager, List<UserWriteRecord> records) {
    return newTestSyncTree(persistenceManager, records, new MockListenProvider());
  }

  private SyncTree newTestSyncTree(
      PersistenceManager persistenceManager, SyncTree.ListenProvider listenProvider) {
    return newTestSyncTree(
        persistenceManager, Collections.<UserWriteRecord>emptyList(), listenProvider);
  }

  private SyncTree newTestSyncTree(
      PersistenceManager persistenceManager,
      List<UserWriteRecord> records,
      SyncTree.ListenProvider listenProvider) {
    Context ctx = newFrozenTestConfig();
    SyncTree tree = new SyncTree(ctx, persistenceManager, listenProvider);
    for (UserWriteRecord record : records) {
      if (record.isOverwrite()) {
        tree.applyUserOverwrite(
            record.getPath(),
            record.getOverwrite(),
            record.getOverwrite(),
            record.getWriteId(),
            true,
            true);
      } else {
        tree.applyUserMerge(
            record.getPath(), record.getMerge(), record.getMerge(), record.getWriteId(), true);
      }
    }
    return tree;
  }

  private DatabaseReference refWithConfig(DatabaseConfig ctx, PersistenceManager manager) {
    setForcedPersistentCache(ctx, manager);
    return rootWithConfig(ctx);
  }

  static class TestEventRegistration extends EventRegistration {
    private final QuerySpec query;
    private final Event.EventType[] types;

    public TestEventRegistration(QuerySpec query, final Event.EventType... types) {
      this.query = query;
      this.types = types;
    }

    @Override
    public boolean respondsTo(Event.EventType eventType) {
      return Arrays.asList(types).contains(eventType);
    }

    @Override
    public DataEvent createEvent(Change change, QuerySpec query) {
      // We only need enough data filled for our tests
      DatabaseReference ref;
      DataSnapshot snapshot;
      if (change.getEventType() == Event.EventType.VALUE) {
        ref = InternalHelpers.createReference(null, query.getPath());
        snapshot = InternalHelpers.createDataSnapshot(ref, change.getIndexedNode());
      } else {
        ref = InternalHelpers.createReference(null, query.getPath().child(change.getChildKey()));
        snapshot = InternalHelpers.createDataSnapshot(ref, change.getIndexedNode());
      }
      return new DataEvent(Event.EventType.VALUE, this, snapshot, null);
    }

    @Override
    public void fireEvent(DataEvent dataEvent) {
      // no-op
    }

    @Override
    public void fireCancelEvent(DatabaseError error) {
      // no-op
    }

    @Override
    public EventRegistration clone(QuerySpec newQuery) {
      return new TestEventRegistration(newQuery, types);
    }

    @Override
    public boolean isSameListener(EventRegistration other) {
      return other == this;
    }

    @NotNull
    @Override
    public QuerySpec getQuerySpec() {
      return query;
    }
  }

  private EventRegistration testEventRegistration(
      @NotNull final QuerySpec query, final Event.EventType... types) {
    return new TestEventRegistration(query, types);
  }

  @Test
  public void listenRaisesValueEventWithCachedData() {
    PersistenceManager manager = newTestPersistenceManager();
    manager.setQueryActive(defaultFooQuery);
    manager.updateServerCache(defaultFooQuery, NodeFromJSON("leaf-value"));

    SyncTree tree = newTestSyncTree(manager);

    List<? extends Event> events =
        tree.addEventRegistration(testEventRegistration(defaultFooQuery, Event.EventType.VALUE));
    assertEquals(1, events.size());
    DataEvent event = (DataEvent) events.get(0);
    assertEquals(new Path("foo"), event.getPath());
    assertEquals("leaf-value", event.getSnapshot().getValue());
  }

  @Test
  public void listenRaisesValueEventWithCachedDataForQuery() {
    PersistenceManager manager = newTestPersistenceManager();
    Map<String, Object> data = fromSingleQuotedString("{'a':'1', 'b':'2', 'c':'3', 'd':'4'}");
    manager.setQueryActive(limit3FooQuery);
    manager.setTrackedQueryKeys(limit3FooQuery, childKeySet("b", "c", "d"));
    manager.updateServerCache(limit3FooQuery, NodeFromJSON(data));

    SyncTree tree = newTestSyncTree(manager);

    List<? extends Event> events =
        tree.addEventRegistration(testEventRegistration(limit3FooQuery, Event.EventType.VALUE));
    assertEquals(1, events.size());
    DataEvent event = (DataEvent) events.get(0);
    assertEquals(new Path("foo"), event.getPath());
    assertEquals(
        fromSingleQuotedString("{'b':'2','c':'3','d':'4'}"), event.getSnapshot().getValue());
  }

  @Test
  public void listenRaisesNoValueButCachedChildEventsForIncompleteQueries() {
    PersistenceManager manager = newTestPersistenceManager();
    manager.setQueryActive(defaultQueryAt("foo/a"));
    manager.setQueryActive(defaultQueryAt("foo/b"));
    manager.setQueryActive(defaultQueryAt("foo/c"));
    manager.setQueryActive(defaultQueryAt("foo/d"));

    manager.updateServerCache(defaultQueryAt("foo/a"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/b"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/c"), NodeFromJSON("3"));
    manager.updateServerCache(defaultQueryAt("foo/d"), NodeFromJSON("4"));

    SyncTree tree = newTestSyncTree(manager);

    List<? extends Event> events =
        tree.addEventRegistration(
            testEventRegistration(
                defaultFooQuery, Event.EventType.VALUE, Event.EventType.CHILD_ADDED));
    assertEquals(4, events.size());
    DataEvent event1 = (DataEvent) events.get(0);
    assertEquals(new Path("foo/a"), event1.getPath());
    assertEquals("1", event1.getSnapshot().getValue());

    DataEvent event2 = (DataEvent) events.get(1);
    assertEquals(new Path("foo/b"), event2.getPath());
    assertEquals("2", event2.getSnapshot().getValue());

    DataEvent event3 = (DataEvent) events.get(2);
    assertEquals(new Path("foo/c"), event3.getPath());
    assertEquals("3", event3.getSnapshot().getValue());

    DataEvent event4 = (DataEvent) events.get(3);
    assertEquals(new Path("foo/d"), event4.getPath());
    assertEquals("4", event4.getSnapshot().getValue());
  }

  @Test
  public void listenRaisesNoValueButCachedChildEventsForIncompleteNonDefaultQueries() {
    PersistenceManager manager = newTestPersistenceManager();

    manager.setQueryActive(defaultQueryAt("foo/a"));
    manager.setQueryActive(defaultQueryAt("foo/b"));
    manager.setQueryActive(defaultQueryAt("foo/c"));
    manager.setQueryActive(defaultQueryAt("foo/d"));

    manager.updateServerCache(defaultQueryAt("foo/a"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/b"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/c"), NodeFromJSON("3"));
    manager.updateServerCache(defaultQueryAt("foo/d"), NodeFromJSON("4"));

    SyncTree tree = newTestSyncTree(manager);

    List<? extends Event> events =
        tree.addEventRegistration(
            testEventRegistration(
                limit3FooQuery, Event.EventType.VALUE, Event.EventType.CHILD_ADDED));
    assertEquals(3, events.size());

    DataEvent event1 = (DataEvent) events.get(0);
    assertEquals(new Path("foo/b"), event1.getPath());
    assertEquals("2", event1.getSnapshot().getValue());

    DataEvent event2 = (DataEvent) events.get(1);
    assertEquals(new Path("foo/c"), event2.getPath());
    assertEquals("3", event2.getSnapshot().getValue());

    DataEvent event3 = (DataEvent) events.get(2);
    assertEquals(new Path("foo/d"), event3.getPath());
    assertEquals("4", event3.getSnapshot().getValue());
  }

  @Test
  public void listenRaisesValueEventIfQueryWasMarkedComplete() {
    PersistenceManager manager = newTestPersistenceManager();
    manager.setQueryActive(limit3FooQuery);
    manager.updateServerCache(defaultQueryAt("foo/a"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/b"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/c"), NodeFromJSON("3"));
    manager.updateServerCache(defaultQueryAt("foo/d"), NodeFromJSON("4"));
    manager.setTrackedQueryKeys(limit3FooQuery, childKeySet("b", "c", "d"));
    manager.setQueryComplete(limit3FooQuery);

    SyncTree tree = newTestSyncTree(manager);

    List<? extends Event> events =
        tree.addEventRegistration(testEventRegistration(limit3FooQuery, Event.EventType.VALUE));
    assertEquals(1, events.size());
    DataEvent event = (DataEvent) events.get(0);
    assertEquals(new Path("foo"), event.getPath());
    assertEquals(
        fromSingleQuotedString("{'b':'2','c':'3','d':'4'}"), event.getSnapshot().getValue());

    List<? extends Event> events2 =
        tree.addEventRegistration(testEventRegistration(limit2FooQuery, Event.EventType.VALUE));
    assertEquals(0, events2.size());
  }

  @Test
  public void persistedWritesAreUsed() {
    PersistenceManager manager = newTestPersistenceManager();
    manager.setQueryActive(limit3FooQuery);
    manager.updateServerCache(defaultQueryAt("foo/a"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/b"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/c"), NodeFromJSON("3"));
    manager.updateServerCache(defaultQueryAt("foo/d"), NodeFromJSON("4"));
    manager.setQueryComplete(limit3FooQuery);

    UserWriteRecord write1 =
        new UserWriteRecord(1L, new Path("foo/b"), NodeFromJSON("new-b-value"), true);
    Map<Path, Node> merge = new HashMap<Path, Node>();
    merge.put(new Path("d"), NodeFromJSON("prev-d-value"));
    merge.put(new Path("f"), NodeFromJSON("f-value"));
    UserWriteRecord write2 =
        new UserWriteRecord(2L, new Path("foo"), CompoundWrite.fromPathMerge(merge));
    UserWriteRecord write3 =
        new UserWriteRecord(3L, new Path("foo/d"), NodeFromJSON("new-d-value"), true);
    UserWriteRecord write4 =
        new UserWriteRecord(4L, new Path("foo/e"), NodeFromJSON("e-value"), true);

    SyncTree tree = newTestSyncTree(manager, Arrays.asList(write1, write2, write3, write4));

    List<? extends Event> events =
        tree.addEventRegistration(testEventRegistration(limit3FooQuery, Event.EventType.VALUE));
    assertEquals(1, events.size());
    DataEvent event = (DataEvent) events.get(0);
    assertEquals(new Path("foo"), event.getPath());
    assertEquals(
        fromSingleQuotedString("{'d':'new-d-value','e':'e-value','f':'f-value'}"),
        event.getSnapshot().getValue());
  }

  @Test
  public void persistedWritesAreUsedInRepo() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    manager.setQueryActive(limit3FooQuery);
    manager.updateServerCache(defaultQueryAt("foo/a"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/b"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/c"), NodeFromJSON("3"));
    manager.updateServerCache(defaultQueryAt("foo/d"), NodeFromJSON("4"));
    manager.setQueryComplete(limit3FooQuery);

    manager.saveUserOverwrite(new Path("foo/b"), NodeFromJSON("new-b-value"), 1);
    Map<Path, Node> merge = new HashMap<Path, Node>();
    merge.put(new Path("d"), NodeFromJSON("prev-d-value"));
    merge.put(new Path("f"), NodeFromJSON("f-value"));
    manager.saveUserMerge(new Path("foo"), CompoundWrite.fromPathMerge(merge), 2);
    manager.saveUserOverwrite(new Path("foo/d"), NodeFromJSON("new-d-value"), 3);
    manager.saveUserOverwrite(new Path("foo/e"), NodeFromJSON("e-value"), 4);

    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager).getRoot().child("foo");

    goOffline(cfg);

    EventRecord record = ReadFuture.untilCount(ref.limitToLast(3), 1).timedGet().get(0);

    assertEquals(record.getEventType(), Event.EventType.VALUE);
    Object expected = fromSingleQuotedString("{'d':'new-d-value','e':'e-value','f':'f-value'}");
    assertEquals(expected, record.getSnapshot().getValue());
  }

  @Test
  public void persistedWritesCanLeadToValueEvents() {
    PersistenceManager manager = newTestPersistenceManager();
    manager.updateServerCache(defaultQueryAt("foo/a"), NodeFromJSON("1"));
    manager.updateServerCache(defaultQueryAt("foo/b"), NodeFromJSON("2"));
    manager.updateServerCache(defaultQueryAt("foo/c"), NodeFromJSON("3"));

    Node writeNode = NodeFromJSON(fromSingleQuotedString("{'foo':'bar'}"));

    SyncTree tree =
        newTestSyncTree(
            manager, Arrays.asList(new UserWriteRecord(1L, new Path("foo"), writeNode, true)));

    List<? extends Event> events =
        tree.addEventRegistration(testEventRegistration(defaultFooQuery, Event.EventType.VALUE));
    assertEquals(1, events.size());
    DataEvent event = (DataEvent) events.get(0);
    assertEquals(new Path("foo"), event.getPath());
    assertEquals(fromSingleQuotedString("{'foo':'bar'}"), event.getSnapshot().getValue());
  }

  @Test
  public void serverOperationUpdatesCacheAndMarksComplete() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    PersistenceManager manager =
        new DefaultPersistenceManager(newFrozenTestConfig(), engine, CachePolicy.NONE);

    SyncTree tree = newTestSyncTree(manager);

    tree.addEventRegistration(testEventRegistration(defaultFooQuery, Event.EventType.VALUE));
    tree.applyServerOverwrite(new Path("foo"), NodeFromJSON("bar"));
    Node node = engine.getCurrentNode(new Path("foo"));
    assertEquals(NodeFromJSON("bar"), node);
    assertTrue(manager.serverCache(defaultFooQuery).isFullyInitialized());
  }

  @Test
  public void serverOperationMergeUpdatesCache() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    PersistenceManager manager =
        new DefaultPersistenceManager(newFrozenTestConfig(), engine, CachePolicy.NONE);

    SyncTree tree = newTestSyncTree(manager);

    tree.applyServerOverwrite(
        new Path("foo"), NodeFromJSON(fromSingleQuotedString("{'foo':'foo-value'}")));
    Map<Path, Node> merge = new HashMap<Path, Node>();
    merge.put(new Path("bar"), NodeFromJSON("bar-value"));
    merge.put(new Path("baz"), NodeFromJSON("baz-value"));
    tree.applyServerMerge(new Path("foo"), merge);
    Node node = engine.getCurrentNode(new Path("foo"));
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString("{'foo':'foo-value', 'bar':'bar-value', 'baz':'baz-value'}"));
    assertEquals(expected, node);
  }

  @Test
  public void listenCompleteMarksPersistenceComplete() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    PersistenceManager manager =
        new DefaultPersistenceManager(newFrozenTestConfig(), engine, CachePolicy.NONE);

    SyncTree tree = newTestSyncTree(manager);
    tree.addEventRegistration(testEventRegistration(defaultFooQuery, Event.EventType.VALUE));
    tree.applyListenComplete(new Path("foo"));
    assertTrue(manager.serverCache(defaultQueryAt("foo")).isFullyInitialized());
  }

  @Test // this could probably happen if pruning happens at the wrong time.
  public void listenCompleteForUntrackedListenIsAllowed() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    PersistenceManager manager =
        new DefaultPersistenceManager(newFrozenTestConfig(), engine, CachePolicy.NONE);

    SyncTree tree = newTestSyncTree(manager);
    tree.applyListenComplete(new Path("foo"));
  }

  @Test
  public void keepSyncedQueriesAreListenedTo() {
    PersistenceManager manager = newTestPersistenceManager();
    MockListenProvider listenProvider = new MockListenProvider();
    SyncTree tree = newTestSyncTree(manager, listenProvider);
    tree.keepSynced(defaultFooQuery, true);
    assertTrue(listenProvider.hasListen(defaultFooQuery));

    // stop keeping-synced and make sure listen goes away.
    tree.keepSynced(defaultFooQuery, false);
    assertFalse(listenProvider.hasListen(defaultFooQuery));
  }

  @Test
  public void keepSyncedQueriesCacheData() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    tree.keepSynced(defaultFooQuery, true);
    tree.applyServerOverwrite(new Path("foo"), NodeFromJSON("leaf-value"));

    List<? extends Event> events =
        tree.addEventRegistration(testEventRegistration(defaultFooQuery, Event.EventType.VALUE));
    assertEquals(1, events.size());
    DataEvent event = (DataEvent) events.get(0);
    assertEquals(new Path("foo"), event.getPath());
    assertEquals("leaf-value", event.getSnapshot().getValue());
  }

  @Test
  public void pruningSanityCheck() {
    MockPersistenceStorageEngine storage = new MockPersistenceStorageEngine();
    TestCachePolicy cachePolicy =
        new TestCachePolicy(
            /* percentToPruneAtOnce= */ 1.0f, /* maxNumberToKeep= */ Long.MAX_VALUE);
    PersistenceManager manager =
        new DefaultPersistenceManager(newFrozenTestConfig(), storage, cachePolicy);
    SyncTree tree = newTestSyncTree(manager);

    QuerySpec fooQuery = defaultQueryAt("foo");
    EventRegistration fooReg = testEventRegistration(fooQuery, Event.EventType.VALUE);
    tree.addEventRegistration(fooReg);

    QuerySpec fooDeepQuery = defaultQueryAt("foo/deep");
    EventRegistration fooDeepReg = testEventRegistration(fooDeepQuery, Event.EventType.VALUE);
    tree.addEventRegistration(fooDeepReg);

    Map<String, Object> data = fromSingleQuotedString("{'deep':'foo-deep', 'deep2':'foo-deep2'}");
    tree.applyServerOverwrite(new Path("foo"), NodeFromJSON(data));

    QuerySpec barQuery = defaultQueryAt("bar");
    EventRegistration barReg = testEventRegistration(barQuery, Event.EventType.VALUE);
    tree.addEventRegistration(barReg);

    QuerySpec barLimitQuery =
        new QuerySpec(new Path("bar"), QueryParams.DEFAULT_PARAMS.limitToLast(3));
    EventRegistration barLimitReg = testEventRegistration(barLimitQuery, Event.EventType.VALUE);
    tree.addEventRegistration(barLimitReg);

    data = fromSingleQuotedString("{'a':1, 'b':2, 'c':3, 'd':4}");
    tree.applyServerOverwrite(new Path("bar"), NodeFromJSON(data));

    QuerySpec bazQuery = defaultQueryAt("baz");
    tree.keepSynced(bazQuery, true);
    tree.applyServerOverwrite(new Path("baz"), NodeFromJSON("baz-val"));

    ArrayList<QuerySpec> expectedQueries =
        new ArrayList<QuerySpec>(
            Arrays.asList(fooQuery, fooDeepQuery, barQuery, barLimitQuery, bazQuery));
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());

    data =
        fromSingleQuotedString(
            "{'foo': {'deep': 'foo-deep', 'deep2': 'foo-deep2'}, 'bar': "
                + "{'a':1, 'b':2, 'c':3, 'd':4}, 'baz': 'baz-val'}");
    Node expectedData = NodeFromJSON(data);
    assertTrue(storage.serverCache(Path.getEmptyPath()).equals(expectedData));

    // Trigger pruning (nothing should happen).
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertTrue(storage.serverCache(Path.getEmptyPath()).equals(expectedData));

    // Remove /foo listener.  Query should be pruned, and foo/deep2 removed.
    tree.removeEventRegistration(fooReg);
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());
    expectedQueries.remove(fooQuery);
    expectedData = expectedData.updateChild(new Path("foo/deep2"), EmptyNode.Empty());
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertTrue(storage.serverCache(Path.getEmptyPath()).equals(expectedData));

    // Remove /bar listener.  Query should be pruned, but no data removed.
    tree.removeEventRegistration(barReg);
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());
    expectedQueries.remove(barQuery);
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertTrue(storage.serverCache(Path.getEmptyPath()).equals(expectedData));

    // Remove keep-synced baz query.  Query and data should be pruned.
    tree.keepSynced(bazQuery, false);
    expectedQueries.remove(bazQuery);
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());
    expectedData = expectedData.updateChild(new Path("baz"), EmptyNode.Empty());
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));

    // Remove foo/deep and bar limit 3 listens.  Everything should be pruned.
    tree.removeEventRegistration(fooDeepReg);
    tree.removeEventRegistration(barLimitReg);
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());

    expectedData = EmptyNode.Empty();
    expectedQueries.clear();
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));
  }

  @Test
  public void pruningDoesOldestFirst() throws InterruptedException {
    MockPersistenceStorageEngine storage = new MockPersistenceStorageEngine();
    TestCachePolicy cachePolicy =
        new TestCachePolicy(
            /* percentToPruneAtOnce= */ 0.5f, /* maxNumberToKeep= */ Long.MAX_VALUE);
    TestClock clock = new TestClock();
    PersistenceManager manager =
        new DefaultPersistenceManager(newFrozenTestConfig(), storage, cachePolicy, clock);
    QuerySpec fooQuery = defaultQueryAt("foo");
    QuerySpec barQuery = defaultQueryAt("bar");
    QuerySpec bazQuery = defaultQueryAt("baz");
    QuerySpec quxQuery = defaultQueryAt("qux");

    EventRegistration dummyRegFoo = testEventRegistration(fooQuery, Event.EventType.VALUE);
    EventRegistration dummyRegBar = testEventRegistration(barQuery, Event.EventType.VALUE);
    EventRegistration dummyRegBaz = testEventRegistration(bazQuery, Event.EventType.VALUE);
    EventRegistration dummyRegQux = testEventRegistration(quxQuery, Event.EventType.VALUE);
    SyncTree tree = newTestSyncTree(manager);

    tree.addEventRegistration(dummyRegFoo);
    tree.addEventRegistration(dummyRegBar);
    tree.addEventRegistration(dummyRegBaz);
    tree.addEventRegistration(dummyRegQux);

    Map<String, Object> data =
        fromSingleQuotedString(
            "{'foo': 'foo-val', 'bar': 'bar-val', 'baz': 'baz-val', 'qux': 'qux-val'}");
    Node node = NodeFromJSON(data);
    tree.applyServerOverwrite(new Path("foo"), node.getChild(new Path("foo")));
    tree.applyServerOverwrite(new Path("bar"), node.getChild(new Path("bar")));
    tree.applyServerOverwrite(new Path("baz"), node.getChild(new Path("baz")));
    tree.applyServerOverwrite(new Path("qux"), node.getChild(new Path("qux")));

    // Now remove everything so it's prunable.
    tree.removeEventRegistration(dummyRegFoo);
    clock.tick();
    tree.removeEventRegistration(dummyRegBar);
    clock.tick();
    tree.removeEventRegistration(dummyRegBaz);
    clock.tick();
    tree.removeEventRegistration(dummyRegQux);

    ArrayList<QuerySpec> expectedQueries =
        new ArrayList<QuerySpec>(Arrays.asList(fooQuery, barQuery, bazQuery, quxQuery));
    Node expectedData = node;
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));

    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());

    // We should have pruned the oldest 50%.
    expectedQueries.remove(fooQuery);
    expectedQueries.remove(barQuery);
    expectedData = expectedData.updateChild(new Path("foo"), EmptyNode.Empty());
    expectedData = expectedData.updateChild(new Path("bar"), EmptyNode.Empty());
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));

    // Prune the oldest 50% again (1).
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());
    expectedQueries.remove(bazQuery);
    expectedData = expectedData.updateChild(new Path("baz"), EmptyNode.Empty());
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));

    // Prune the last one.
    cachePolicy.pruneOnNextServerUpdate();
    tree.applyServerMerge(Path.getEmptyPath(), Collections.<Path, Node>emptyMap());
    expectedQueries.remove(quxQuery);
    expectedData = expectedData.updateChild(new Path("qux"), EmptyNode.Empty());
    assertTrackedQueriesMatch(expectedQueries, storage.loadTrackedQueries());
    assertEquals(expectedData, storage.serverCache(Path.getEmptyPath()));
  }

  private void assertTrackedQueriesMatch(List<QuerySpec> expected, List<TrackedQuery> actual) {
    assertEquals(expected.size(), actual.size());
    HashSet<QuerySpec> expectedSet = new HashSet<QuerySpec>(expected);
    for (TrackedQuery trackedQuery : actual) {
      assertTrue(expectedSet.contains(trackedQuery.querySpec));
    }
  }

  @Test
  public void taggedListenCompleteMarksPersistenceCompleteForThatQuery() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    tree.addEventRegistration(testEventRegistration(limit3FooQuery));
    tree.applyTaggedListenComplete(new Tag(1));
    assertTrue(manager.serverCache(limit3FooQuery).isFullyInitialized());
    assertFalse(manager.serverCache(defaultFooQuery).isFullyInitialized());
    assertFalse(manager.serverCache(limit2FooQuery).isFullyInitialized());
  }

  @Test
  public void taggedServerMergeIsApplied() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    tree.addEventRegistration(testEventRegistration(limit3FooQuery));
    tree.applyServerOverwrite(
        new Path("foo"), NodeFromJSON(fromSingleQuotedString("{'foo':'foo-value'}")));
    Map<Path, Node> merge = new HashMap<Path, Node>();
    merge.put(new Path("bar"), NodeFromJSON("bar-value"));
    merge.put(new Path("baz"), NodeFromJSON("baz-value"));
    tree.applyTaggedQueryMerge(new Path("foo"), merge, new Tag(1));
    CacheNode cacheNode = manager.serverCache(limit3FooQuery);
    assertTrue(cacheNode.isFullyInitialized());
    Node expected =
        NodeFromJSON(
            fromSingleQuotedString("{'foo':'foo-value', 'bar':'bar-value', 'baz':'baz-value'}"));
    assertEquals(expected, cacheNode.getNode());
  }

  @Test
  public void deltaSyncTrackedKeysAreSaved() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);

    // Seed some data in the cache.
    tree.addEventRegistration(testEventRegistration(defaultQueryAt("foo/baz")));
    tree.applyServerOverwrite(new Path("foo/baz"), NodeFromJSON("two"));

    // Do a query and mark it complete for existing data.
    tree.addEventRegistration(testEventRegistration(limit3FooQuery));
    tree.applyTaggedListenComplete(new Tag(1));

    // We should have a fully initialized cache for that query now.
    CacheNode cacheNode = manager.serverCache(limit3FooQuery);
    assertTrue(cacheNode.isFullyInitialized());
    Node expected = NodeFromJSON(fromSingleQuotedString("{ 'baz': 'two' }"));
    assertEquals(expected, cacheNode.getNode());
  }

  @Test
  public void ackIsAppliedToCacheIfNoListenExists() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    applyUserOverwrite(tree, path("foo"), NodeFromJSON("foo-value"), 1);
    CacheNode serverCache = manager.serverCache(defaultQueryAt("foo"));
    assertFalse(serverCache.isFullyInitialized());
    assertTrue(serverCache.getNode().isEmpty());

    tree.ackUserWrite(1, /*revert=*/ false, /*persist=*/ true, new TestClock());

    serverCache = manager.serverCache(defaultQueryAt("foo"));
    assertTrue(serverCache.isFullyInitialized());
    assertEquals(NodeFromJSON("foo-value"), serverCache.getNode());
  }

  @Test
  public void ackIsAppliedToCacheIfNoDefaultListenExists() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    tree.addEventRegistration(testEventRegistration(limit3FooQuery));
    applyUserOverwrite(tree, path("foo"), NodeFromJSON("foo-value"), 1);
    CacheNode serverCache = manager.serverCache(defaultFooQuery);
    assertFalse(serverCache.isFullyInitialized());
    assertTrue(serverCache.getNode().isEmpty());

    tree.ackUserWrite(1, /*revert=*/ false, /*persist=*/ true, new TestClock());

    serverCache = manager.serverCache(defaultFooQuery);
    assertTrue(serverCache.isFullyInitialized());
    assertEquals(NodeFromJSON("foo-value"), serverCache.getNode());
  }

  @Test
  public void ackIsNotAppliedIfDefaultListenExists() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    tree.addEventRegistration(testEventRegistration(defaultFooQuery));
    applyUserOverwrite(tree, path("foo"), NodeFromJSON("foo-value"), 1);
    CacheNode serverCache = manager.serverCache(defaultFooQuery);
    assertFalse(serverCache.isFullyInitialized());
    assertTrue(serverCache.getNode().isEmpty());

    tree.ackUserWrite(1, /*revert=*/ false, /*persist=*/ true, new TestClock());

    serverCache = manager.serverCache(defaultFooQuery);
    assertFalse(serverCache.isFullyInitialized());
    assertTrue(serverCache.getNode().isEmpty());
  }

  @Test
  public void userOverwriteIsSaved() {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager);
    goOffline(cfg);
    ref.setValue("foo-value");
    waitForQueue(ref);
    List<UserWriteRecord> records = manager.loadUserWrites();
    assertEquals(1, records.size());
    UserWriteRecord record = records.get(0);
    assertEquals(ref.getPath(), record.getPath());
    assertEquals(1, record.getWriteId());
    assertEquals(NodeFromJSON("foo-value"), record.getOverwrite());
  }

  @Test
  public void userMergeIsSaved() {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager);
    goOffline(cfg);
    Map<String, Object> merge = new HashMap<String, Object>();
    merge.put("foo", "foo-value");
    ref.updateChildren(merge);
    waitForQueue(ref);
    List<UserWriteRecord> records = manager.loadUserWrites();
    assertEquals(1, records.size());
    UserWriteRecord record = records.get(0);
    assertEquals(ref.getPath(), record.getPath());
    assertEquals(1, record.getWriteId());
    Map<String, Object> expected = new HashMap<String, Object>();
    expected.put("foo", "foo-value");
    assertEquals(expected, record.getMerge().getValue(true));
  }

  @Test
  public void ackRemovesWriteFromPersistence() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager);
    goOffline(cfg);
    final Semaphore done = new Semaphore(0);
    ref.setValue(
        "foo-value",
        new DatabaseReference.CompletionListener() {
          @Override
          public void onComplete(DatabaseError error, DatabaseReference ref) {
            done.release();
          }
        });
    waitForQueue(ref);
    List<UserWriteRecord> records = manager.loadUserWrites();
    assertEquals(1, records.size());
    goOnline(cfg);
    waitFor(done);
    assertTrue(manager.loadUserWrites().isEmpty());
  }

  @Test
  public void revertIsNotAppliedToCache() {
    PersistenceManager manager = newTestPersistenceManager();
    SyncTree tree = newTestSyncTree(manager);
    applyUserOverwrite(tree, path("foo"), NodeFromJSON("foo-value"), 1);

    CacheNode serverCache = manager.serverCache(defaultFooQuery);
    assertFalse(serverCache.isFullyInitialized());
    assertTrue(serverCache.getNode().isEmpty());

    tree.ackUserWrite(1, /*revert=*/ true, /*persist=*/ true, new TestClock());

    serverCache = manager.serverCache(defaultFooQuery);
    assertFalse(serverCache.isFullyInitialized());
    assertTrue(serverCache.getNode().isEmpty());
  }

  @Test
  public void serverValuesArePreservedInWrite() {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager);
    goOffline(cfg);
    ref.setValue(ServerValue.TIMESTAMP);
    waitForQueue(ref);
    List<UserWriteRecord> records = manager.loadUserWrites();
    assertEquals(1, records.size());
    UserWriteRecord record = records.get(0);
    assertEquals(NodeFromJSON(ServerValue.TIMESTAMP), record.getOverwrite());
  }

  @Test
  public void ackForServerValueYieldsResolvedValueInCache() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager);
    QuerySpec query = QuerySpec.defaultQueryAtPath(ref.getPath());

    new WriteFuture(ref, ServerValue.TIMESTAMP).timedGet();

    Node node = manager.serverCache(query).getNode();
    long drift = System.currentTimeMillis() - (Long) node.getValue();
    assertThat(Math.abs(drift), lessThan(2000l));
  }

  @Test
  public void persistedServerValuesAreResolved() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    manager.saveUserOverwrite(new Path("foo"), NodeFromJSON(ServerValue.TIMESTAMP), 1);
    DatabaseConfig cfg = newFrozenTestConfig();
    DatabaseReference ref = refWithConfig(cfg, manager).getRoot().child("foo");
    List<EventRecord> records = ReadFuture.untilCount(ref, 2).timedGet();
    // There will be two events, one local and one when the server responds
    long now = System.currentTimeMillis();
    long drift1 = (Long) records.get(0).getSnapshot().getValue() - now;
    long drift2 = (Long) records.get(1).getSnapshot().getValue() - now;
    assertThat(Math.abs(drift1), lessThan(2000l));
    assertThat(Math.abs(drift2), lessThan(2000l));
  }

  @Test
  public void writeIdsAreRestored() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg1 = newFrozenTestConfig();
    DatabaseReference ref1 = refWithConfig(cfg1, manager);
    goOffline(cfg1);
    ref1.push().setValue("value-1");
    waitForQueue(ref1);
    DatabaseConfig cfg2 = newFrozenTestConfig();
    DatabaseReference ref2 = refWithConfig(cfg2, manager);
    goOffline(cfg2);
    ref2.push().setValue("value-2");
    waitForQueue(ref2);
    List<UserWriteRecord> writes = manager.loadUserWrites();
    long lastWriteId = Long.MIN_VALUE;
    assertEquals(2, writes.size());
    for (UserWriteRecord record : writes) {
      assertTrue(lastWriteId < record.getWriteId());
      lastWriteId = record.getWriteId();
    }
  }

  // There was a case where the merge was sent to the server with a slash at the beginning, which
  // was rejected by the server. This tests that a persisted merge will successfully by applied by
  // the server
  @Test
  public void simpleMergeTest() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg1 = newFrozenTestConfig();
    DatabaseReference ref1 = refWithConfig(cfg1, manager).push();
    goOffline(cfg1);
    Map<String, Object> updates = new HashMap<String, Object>();
    updates.put("foo", "bar");
    ref1.updateChildren(updates);
    waitForQueue(ref1);

    DatabaseConfig cfg2 = newFrozenTestConfig();
    DatabaseReference ref2 = refWithConfig(cfg2, manager);
    ref2 = ref2.child(ref1.getKey());

    Object value = new ReadFuture(ref2).waitForLastValue();
    assertEquals(updates, value);
  }

  // This tests that a persisted deep update will be properly applied by the server.
  @Test
  public void deepUpdateTest() throws Throwable {
    PersistenceManager manager = newTestPersistenceManager();
    DatabaseConfig cfg1 = newFrozenTestConfig();
    DatabaseReference ref1 = refWithConfig(cfg1, manager).push();
    goOffline(cfg1);
    Map<String, Object> updates = new HashMap<String, Object>();
    updates.put("foo/deep/update", "bar");
    ref1.updateChildren(updates);
    waitForQueue(ref1);

    DatabaseConfig cfg2 = newFrozenTestConfig();
    DatabaseReference ref2 = refWithConfig(cfg2, manager);
    ref2 = ref2.child(ref1.getKey());

    Object value = new ReadFuture(ref2).waitForLastValue();
    assertEquals(value, fromSingleQuotedString("{ 'foo': { 'deep': { 'update': 'bar' } } }"));
  }

  private void applyUserOverwrite(SyncTree tree, Path path, Node node, long id) {
    tree.applyUserOverwrite(path, node, node, id, /*visible=*/ true, /*persist=*/ true);
  }
}
