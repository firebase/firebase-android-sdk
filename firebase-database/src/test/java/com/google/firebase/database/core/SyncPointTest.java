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

package com.google.firebase.database.core;

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.InternalHelpers;
import com.google.firebase.database.Query;
import com.google.firebase.database.UnitTestHelpers;
import com.google.firebase.database.annotations.NotNull;
import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.core.persistence.NoopPersistenceManager;
import com.google.firebase.database.core.utilities.TestClock;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.core.view.DataEvent;
import com.google.firebase.database.core.view.Event;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.NodeUtilities;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import org.junit.After;
import org.junit.Assert;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncPointTest {

  private static SyncTree.ListenProvider getNewListenProvider(final LogWrapper logger) {
    return new SyncTree.ListenProvider() {
      private final HashSet<QuerySpec> listens = new HashSet<QuerySpec>();

      @Override
      public void startListening(
          QuerySpec query,
          Tag tag,
          ListenHashProvider hash,
          SyncTree.CompletionListener onListenComplete) {
        Path path = query.getPath();
        logger.debug("Listening at " + path + " for Tag " + tag + ")");
        hardAssert(!listens.contains(query), "Duplicate listen");
        this.listens.add(query);
      }

      @Override
      public void stopListening(QuerySpec query, Tag tag) {
        Path path = query.getPath();
        logger.debug("Stop listening at " + path + " for Tag " + tag + ")");
        hardAssert(this.listens.contains(query), "Stopped listening for query already");
        this.listens.remove(query);
      }
    };
  }

  private static class TestEvent extends DataEvent {

    private final EventType eventType;
    private final DataSnapshot snapshot;
    private final Object eventRegistration;

    public TestEvent(
        EventType eventType, DataSnapshot snapshot, String prevName, Object eventRegistration) {
      super(eventType, null, snapshot, prevName);
      this.eventType = eventType;
      this.snapshot = snapshot;
      this.eventRegistration = eventRegistration;
    }

    private static boolean equalsOrNull(Object one, Object two) {
      if (one == null) {
        return two == null;
      } else {
        return one.equals(two);
      }
    }

    public static boolean eventsEqual(TestEvent one, TestEvent other) {
      if (!one.getPath().equals(other.getPath())) {
        return false;
      }
      if (!equalsOrNull(one.getSnapshot().getKey(), other.getSnapshot().getKey())) {
        return false;
      }
      if (!equalsOrNull(one.getSnapshot().getPriority(), other.getSnapshot().getPriority())) {
        return false;
      }
      if (!equalsOrNull(one.getSnapshot().getValue(), other.getSnapshot().getValue())) {
        return false;
      }
      if (one.getPreviousName() == null) {
        return other.getPreviousName() == null;
      } else {
        return one.getPreviousName().equals(other.getPreviousName());
      }
    }

    public static void assertEquals(TestEvent expectedEvent, TestEvent actualEvent) {
      Assert.assertEquals(expectedEvent.getPreviousName(), actualEvent.getPreviousName());
      Assert.assertEquals(expectedEvent.getPath(), actualEvent.getPath());
      Assert.assertEquals(
          expectedEvent.getSnapshot().getValue(true), actualEvent.getSnapshot().getValue(true));
    }

    @Override
    public Path getPath() {
      Path path = this.snapshot.getRef().getPath();
      if (this.eventType == EventType.VALUE) {
        return path;
      } else {
        return path.getParent();
      }
    }

    @Override
    public void fire() {
      throw new UnsupportedOperationException("Event doesn't support event runner for TestEvents");
    }
  }

  private static class TestEventRegistration extends EventRegistration {
    private QuerySpec query;

    public TestEventRegistration(QuerySpec query) {
      this.query = query;
    }

    @Override
    public boolean respondsTo(Event.EventType eventType) {
      return true;
    }

    @Override
    public DataEvent createEvent(Change change, QuerySpec query) {
      DataSnapshot snapshot;
      if (change.getEventType() == Event.EventType.VALUE) {
        snapshot =
            InternalHelpers.createDataSnapshot(
                InternalHelpers.createReference(null, query.getPath()), change.getIndexedNode());
      } else {
        snapshot =
            InternalHelpers.createDataSnapshot(
                InternalHelpers.createReference(null, query.getPath().child(change.getChildKey())),
                change.getIndexedNode());
      }
      String prevName = change.getPrevName() != null ? change.getPrevName().asString() : null;
      return new TestEvent(change.getEventType(), snapshot, prevName, this);
    }

    @Override
    public void fireEvent(DataEvent dataEvent) {
      throw new UnsupportedOperationException("Can't raise test events!");
    }

    @Override
    public void fireCancelEvent(DatabaseError error) {
      throw new UnsupportedOperationException("Can't raise test events!");
    }

    @Override
    public EventRegistration clone(QuerySpec newQuery) {
      return new TestEventRegistration(newQuery);
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

  private static EventRegistration getTestEventRegistration(QuerySpec query) {
    return new TestEventRegistration(query);
  }

  private static void assertEventExactMatch(List<TestEvent> expected, List<TestEvent> actual) {
    if (expected.size() < actual.size()) {
      Assert.assertTrue("Got extra events: " + actual, false);
    } else if (expected.size() > actual.size()) {
      Assert.assertTrue("Missing events: " + expected, false);
    } else {
      Iterator<TestEvent> expectedIterator = expected.iterator();
      Iterator<TestEvent> actualIterator = actual.iterator();
      while (expectedIterator.hasNext() && actualIterator.hasNext()) {
        TestEvent.assertEquals(expectedIterator.next(), actualIterator.next());
      }
      Assert.assertFalse(expectedIterator.hasNext());
      Assert.assertFalse(actualIterator.hasNext());
    }
  }

  private static void checkOrder(Map<Object, List<TestEvent>> eventsAtPath) {
    for (List<TestEvent> events : eventsAtPath.values()) {
      Event.EventType currentEventType = null;
      for (TestEvent event : events) {
        if (currentEventType != null) {
          Assert.assertTrue(
              "Events should be ordered!",
              currentEventType.ordinal() <= event.getEventType().ordinal());
        }
        currentEventType = event.getEventType();
      }
    }
  }

  private static void assertEventSetsMatch(
      List<TestEvent> expectedList, List<TestEvent> actualList) {
    List<TestEvent> currentExpected = new ArrayList<TestEvent>(expectedList);
    List<TestEvent> currentActual = new ArrayList<TestEvent>(actualList);
    for (TestEvent actual : currentActual) {
      Iterator<TestEvent> expectedIterator = currentExpected.iterator();
      boolean found = false;
      while (expectedIterator.hasNext()) {
        TestEvent expected = expectedIterator.next();
        if (TestEvent.eventsEqual(actual, expected)) {
          found = true;
          expectedIterator.remove();
          break;
        }
      }
      Assert.assertTrue(
          "Expected events did not contain actual event: " + actual + "\nExpected: " + expectedList,
          found);
    }
    Assert.assertTrue("Missing expected events: " + currentExpected, currentExpected.isEmpty());
    Path currentPath = null;
    Map<Object, List<TestEvent>> currentPathRegistrationMap =
        new HashMap<Object, List<TestEvent>>();
    List<TestEvent> allHandled = new ArrayList<TestEvent>();
    for (TestEvent currentEvent : actualList) {
      if (!currentEvent.getPath().equals(currentPath)) {
        checkOrder(currentPathRegistrationMap);
        currentPathRegistrationMap = new HashMap<Object, List<TestEvent>>();
        currentPath = currentEvent.getPath();
      }
      if (!currentPathRegistrationMap.containsKey(currentEvent.eventRegistration)) {
        currentPathRegistrationMap.put(currentEvent.eventRegistration, new ArrayList<TestEvent>());
      }
      List<TestEvent> registrationList =
          currentPathRegistrationMap.get(currentEvent.eventRegistration);
      registrationList.add(currentEvent);
      allHandled.add(currentEvent);
    }

    checkOrder(currentPathRegistrationMap);

    // make sure we actually
    Assert.assertEquals(actualList, allHandled);
  }

  @SuppressWarnings("unchecked")
  private static Query parseQuery(Query query, Map<String, Object> querySpec) {
    if (!querySpec.containsKey("tag")) {
      throw new RuntimeException("Non-default queries must have a tag");
    }
    Map<String, Object> remaining = new HashMap<String, Object>(querySpec);
    if (remaining.containsKey("orderBy")) {
      String key = (String) remaining.get("orderBy");
      query = query.orderByChild(key);
      remaining.remove("orderBy");
    } else if (remaining.containsKey("orderByKey")) {
      query = query.orderByKey();
      remaining.remove("orderByKey");
    } else if (remaining.containsKey("orderByPriority")) {
      query = query.orderByPriority();
      remaining.remove("orderByPriority");
    }
    if (remaining.containsKey("startAt")) {
      Map<String, Object> startAt = (Map<String, Object>) remaining.get("startAt");
      Object index = startAt.get("index");
      if (index == null || index instanceof String) {
        query = query.startAt((String) index, (String) startAt.get("name"));
      } else if (index instanceof Boolean) {
        query = query.startAt((Boolean) index, (String) startAt.get("name"));
      } else if (index instanceof Double) {
        query = query.startAt((Double) index, (String) startAt.get("name"));
      } else if (index instanceof Integer) {
        query = query.startAt((Integer) index, (String) startAt.get("name"));
      } else {
        throw new IllegalArgumentException("Unknown type for index: " + index.getClass());
      }
      remaining.remove("startAt");
    }
    if (remaining.containsKey("endAt")) {
      Map<String, Object> endAt = (Map<String, Object>) remaining.get("endAt");
      Object index = endAt.get("index");
      if (index == null || index instanceof String) {
        query = query.endAt((String) index, (String) endAt.get("name"));
      } else if (index instanceof Boolean) {
        query = query.endAt((Boolean) index, (String) endAt.get("name"));
      } else if (index instanceof Double) {
        query = query.endAt((Double) index, (String) endAt.get("name"));
      } else if (index instanceof Integer) {
        query = query.endAt((Integer) index, (String) endAt.get("name"));
      } else {
        throw new IllegalArgumentException("Unknown type for index: " + index.getClass());
      }
      remaining.remove("endAt");
    }
    if (remaining.containsKey("equalTo")) {
      Map<String, Object> equalTo = (Map<String, Object>) remaining.get("equalTo");
      Object index = equalTo.get("index");
      if (index == null || index instanceof String) {
        query = query.equalTo((String) index, (String) equalTo.get("name"));
      } else if (index instanceof Boolean) {
        query = query.equalTo((Boolean) index, (String) equalTo.get("name"));
      } else if (index instanceof Double) {
        query = query.equalTo((Double) index, (String) equalTo.get("name"));
      } else {
        throw new IllegalArgumentException("Unknown type for index: " + index.getClass());
      }
      remaining.remove("equalTo");
    }
    if (remaining.containsKey("limitToFirst")) {
      query = query.limitToFirst((Integer) remaining.get("limitToFirst"));
      remaining.remove("limitToFirst");
    }
    if (remaining.containsKey("limitToLast")) {
      query = query.limitToLast((Integer) remaining.get("limitToLast"));
      remaining.remove("limitToLast");
    }
    remaining.remove("tag");
    if (!remaining.isEmpty()) {
      throw new RuntimeException("Unsupported query parameters: " + remaining);
    }
    return query;
  }

  private static TestEvent parseEvent(
      DatabaseReference ref, Map<String, Object> eventSpec, String basePath) {
    String path = (String) eventSpec.get("path");
    Event.EventType type;
    String eventTypeStr = (String) eventSpec.get("type");
    if (eventTypeStr.equals("value")) {
      type = Event.EventType.VALUE;
    } else if (eventTypeStr.equals("child_added")) {
      type = Event.EventType.CHILD_ADDED;
    } else if (eventTypeStr.equals("child_moved")) {
      type = Event.EventType.CHILD_MOVED;
    } else if (eventTypeStr.equals("child_removed")) {
      type = Event.EventType.CHILD_REMOVED;
    } else if (eventTypeStr.equals("child_changed")) {
      type = Event.EventType.CHILD_CHANGED;
    } else {
      throw new RuntimeException("Unknown event type: " + eventTypeStr);
    }
    String childName = (String) eventSpec.get("name");
    String prevName = eventSpec.get("prevName") != null ? (String) eventSpec.get("prevName") : null;
    Object data = eventSpec.get("data");

    DatabaseReference rootRef = basePath != null ? ref.getRoot().child(basePath) : ref.getRoot();
    DatabaseReference pathRef = rootRef.child(path);
    if (childName != null) {
      pathRef = pathRef.child(childName);
    }

    Node node = NodeUtilities.NodeFromJSON(data);
    // TODO: don't use priority index by default
    DataSnapshot snapshot = InternalHelpers.createDataSnapshot(pathRef, IndexedNode.from(node));

    return new TestEvent(type, snapshot, prevName, null);
  }

  @SuppressWarnings("unchecked")
  private static List<TestEvent> testEvents(List<? extends Event> events) {
    return Collections.checkedList((List) events, TestEvent.class);
  }

  private static Tag parseTag(Object tag) {
    return new Tag((Integer) tag);
  }

  private static Map<Path, Node> parseMergePaths(Map<String, Object> merges) {
    Map<Path, Node> newMerges = new HashMap<Path, Node>();
    for (Map.Entry<String, Object> merge : merges.entrySet()) {
      newMerges.put(new Path(merge.getKey()), NodeUtilities.NodeFromJSON(merge.getValue()));
    }
    return newMerges;
  }

  @SuppressWarnings("unchecked")
  private static void runTest(Map<String, Object> testSpec, String basePath) {
    DatabaseConfig config = UnitTestHelpers.newTestConfig();
    UnitTestHelpers.setLogger(config, new DefaultLogger(Logger.Level.DEBUG, null));
    LogWrapper logger = config.getLogger("SyncPointTest");

    logger.info("Running \"" + testSpec.get("name") + '"');
    SyncTree.ListenProvider listenProvider = getNewListenProvider(logger);
    SyncTree syncTree = new SyncTree(config, new NoopPersistenceManager(), listenProvider);

    int currentWriteId = 0;

    List<Map<String, Object>> steps = (List<Map<String, Object>>) testSpec.get("steps");
    Map<Integer, EventRegistration> registrations = new HashMap<Integer, EventRegistration>();
    for (Map<String, Object> spec : steps) {
      if (spec.containsKey(".comment")) {
        logger.info(" > " + spec.get(".comment"));
      }
      String pathStr = (String) spec.get("path");
      Path path =
          pathStr != null
              ? new Path(basePath != null ? basePath : "").child(new Path(pathStr))
              : null;
      DatabaseReference reference = InternalHelpers.createReference(null, path);
      String type = (String) spec.get("type");
      List<Map<String, Object>> eventSpecs = (List<Map<String, Object>>) spec.get("events");
      List<TestEvent> expected = new ArrayList<TestEvent>();
      for (Map<String, Object> eventSpec : eventSpecs) {
        expected.add(parseEvent(reference, eventSpec, basePath));
      }
      if (type.equals("listen")) {
        Query query = reference;
        if (spec.containsKey("params")) {
          query = parseQuery(query, (Map<String, Object>) spec.get("params"));
        }
        EventRegistration eventRegistration;
        Integer callbackId = (Integer) spec.get("callbackId");
        if (callbackId != null && registrations.containsKey(callbackId)) {
          eventRegistration = registrations.get(callbackId);
        } else {
          eventRegistration = getTestEventRegistration(query.getSpec());
          if (callbackId != null) {
            registrations.put(callbackId, eventRegistration);
          }
        }
        List<TestEvent> actual = testEvents(syncTree.addEventRegistration(eventRegistration));
        assertEventExactMatch(expected, actual);
      } else if (type.equals("unlisten")) {
        EventRegistration eventRegistration = null;
        Integer callbackId = (Integer) spec.get("callbackId");
        if (callbackId == null || !registrations.containsKey(callbackId)) {
          throw new IllegalArgumentException(
              "Couldn't find previous listen will callbackId " + callbackId);
        }
        eventRegistration = registrations.get(callbackId);
        List<TestEvent> actual = testEvents(syncTree.removeEventRegistration(eventRegistration));
        assertEventExactMatch(expected, actual);
      } else if (type.equals("serverUpdate")) {
        Node update = NodeUtilities.NodeFromJSON(spec.get("data"));
        List<TestEvent> actual;
        if (spec.containsKey("tag")) {
          actual =
              testEvents(
                  syncTree.applyTaggedQueryOverwrite(path, update, parseTag(spec.get("tag"))));
        } else {
          actual = testEvents(syncTree.applyServerOverwrite(path, update));
        }
        assertEventSetsMatch(expected, actual);
      } else if (type.equals("serverMerge")) {
        Map<Path, Node> merges = parseMergePaths((Map<String, Object>) spec.get("data"));
        List<TestEvent> actual;
        if (spec.containsKey("tag")) {
          actual =
              testEvents(syncTree.applyTaggedQueryMerge(path, merges, parseTag(spec.get("tag"))));
        } else {
          actual = testEvents(syncTree.applyServerMerge(path, merges));
        }
        assertEventSetsMatch(expected, actual);
      } else if (type.equals("set")) {
        Node toSet = NodeUtilities.NodeFromJSON(spec.get("data"));
        boolean visible = spec.containsKey("visible") ? (Boolean) spec.get("visible") : true;
        boolean persist = visible; // for now, assume anything visible should be persisted.
        List<TestEvent> actual =
            testEvents(
                syncTree.applyUserOverwrite(
                    path, toSet, toSet, currentWriteId++, visible, persist));
        assertEventSetsMatch(expected, actual);
      } else if (type.equals("update")) {
        CompoundWrite merges = CompoundWrite.fromValue((Map<String, Object>) spec.get("data"));
        List<TestEvent> actual =
            testEvents(syncTree.applyUserMerge(path, merges, merges, currentWriteId++, true));
        assertEventSetsMatch(expected, actual);
      } else if (type.equals("ackUserWrite")) {
        int toClear = (Integer) spec.get("writeId");
        boolean revert = spec.containsKey("revert") ? (Boolean) spec.get("revert") : false;
        List<TestEvent> actual =
            testEvents(syncTree.ackUserWrite(toClear, revert, true, new TestClock()));
        assertEventSetsMatch(expected, actual);
      } else if (type.equals("suppressWarning")) {
        // Do nothing. This is a hack so JS's Jasmine tests don't throw warnings for "expect no
        // errors" tests.
      } else {
        throw new RuntimeException("Unknown step: " + type);
      }
    }
  }

  @After
  public void tearDown() {
    UnitTestHelpers.failOnFirstUncaughtException();
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> loadSpecs() {
    ObjectMapper mapper = new ObjectMapper();

    String pathToResource = "/syncPointSpec.json";

    InputStream stream = getClass().getResourceAsStream(pathToResource);
    if (stream == null) {
      throw new RuntimeException("Failed to find syncPointSpec.json resource.");
    }
    try {
      return mapper.readValue(stream, List.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  public void runAll() {
    List<Map<String, Object>> specs = loadSpecs();
    for (Map<String, Object> spec : specs) {
      runTest(spec, null);
      //  Run again at a deeper path
      runTest(spec, "/foo/bar/baz");
    }
  }

  public void runOne(String name) {
    List<Map<String, Object>> specs = loadSpecs();
    for (Map<String, Object> spec : specs) {
      if (name.equals(spec.get("name"))) {
        runTest(spec, null);
        // Run again at a deeper path
        runTest(spec, "/foo/bar/baz");
        return;
      }
    }
    throw new RuntimeException("Didn't find test spec with name " + name);
  }

  @Test
  public void defaultListenHandlesParentSet() {
    runOne("Default listen handles a parent set");
  }

  @Test
  public void defaultListenHandlesASetAtTheSameLevel() {
    runOne("Default listen handles a set at the same level");
  }

  @Test
  public void aQueryCanGetACompleteCacheThenAMerge() {
    runOne("A query can get a complete cache then a merge");
  }

  @Test
  public void serverMergeOnListenerWithCompleteChildren() {
    runOne("Server merge on listener with complete children");
  }

  @Test
  public void deepMergeOnListenerWithCompleteChildren() {
    runOne("Deep merge on listener with complete children");
  }

  @Test
  public void updateChildListenerTwice() {
    runOne("Update child listener twice");
  }

  @Test
  public void childOfDefaultListenThatAlreadyHasACompleteCache() {
    runOne("Update child of default listen that already has a complete cache");
  }

  @Test
  public void updateChildOfDefaultListenThatHasNoCache() {
    runOne("Update child of default listen that has no cache");
  }

  @Test
  public void updateTheChildOfACoLocatedDefaultListenerAndQuery() {
    runOne("Update (via set) the child of a co-located default listener and query");
  }

  @Test
  public void updateTheChildOfAQueryWithAFullCache() {
    runOne("Update (via set) the child of a query with a full cache");
  }

  @Test
  public void updateAChildBelowAnEmptyQuery() {
    runOne("Update (via set) a child below an empty query");
  }

  @Test
  public void updateDescendantOfDefaultListenerWithFullCache() {
    runOne("Update descendant of default listener with full cache");
  }

  @Test
  public void descendantSetBelowAnEmptyDefaultLIstenerIsIgnored() {
    runOne("Descendant set below an empty default listener is ignored");
  }

  @Test
  public void updateOfAChild() {
    runOne("Update of a child. This can happen if a child listener is added and removed");
  }

  @Test
  public void revertSetWithOnlyChildCaches() {
    runOne("Revert set with only child caches");
  }

  @Test
  public void canRevertADuplicateChildSet() {
    runOne("Can revert a duplicate child set");
  }

  @Test
  public void canRevertAChildSetAndSeeTheUnderlyingData() {
    runOne("Can revert a child set and see the underlying data");
  }

  @Test
  public void revertChildSetWithNoServerData() {
    runOne("Revert child set with no server data");
  }

  @Test
  public void revertDeepSetWithNoServerData() {
    runOne("Revert deep set with no server data");
  }

  @Test
  public void revertSetCoveredByNonvisibleTransaction() {
    runOne("Revert set covered by non-visible transaction");
  }

  @Test
  public void clearParentShadowingServerValuesSetWithServerChildren() {
    runOne("Clear parent shadowing server values set with server children");
  }

  @Test
  public void clearChildShadowingServerValuesSetWithServerChildren() {
    runOne("Clear child shadowing server values set with server children");
  }

  @Test
  public void unrelatedMergeDoesntShadowServerUpdates() {
    runOne("Unrelated merge doesn't shadow server updates");
  }

  @Test
  public void canSetAlongsideARemoteMerge() {
    runOne("Can set alongside a remote merge");
  }

  @Test
  public void setPriorityOnALocationWithNoCache() {
    runOne("setPriority on a location with no cache");
  }

  @Test
  public void deepUpdateDeletesChildFromLimitWindowAndPullsInNewChild() {
    runOne("deep update deletes child from limit window and pulls in new child");
  }

  @Test
  public void deepSetDeletesChildFromLimitWindowAndPullsInNewChild() {
    runOne("deep set deletes child from limit window and pulls in new child");
  }

  @Test
  public void edgeCaseInNewChildForChange() {
    runOne("Edge case in newChildForChange_");
  }

  @Test
  public void revertSetInQueryWindow() {
    runOne("Revert set in query window");
  }

  @Test
  public void handlesAServerValueMovingAChildOutOfAQueryWindow() {
    runOne("Handles a server value moving a child out of a query window");
  }

  @Test
  public void updateOfIndexedChildWorks() {
    runOne("Update of indexed child works");
  }

  @Test
  public void mergeAppliedToEmptyLimit() {
    runOne("Merge applied to empty limit");
  }

  @Test
  public void limitIsRefilledFromServerDataAfterMerge() {
    runOne("Limit is refilled from server data after merge");
  }

  @Test
  public void handleRepeatedListenWithMergeAsFirstUpdate() {
    runOne("Handle repeated listen with merge as first update");
  }

  @Test
  public void limitIsRefilledFromServerDataAfterSet() {
    runOne("Limit is refilled from server data after set");
  }

  @Test
  public void queryOnWeirdPath() {
    runOne("query on weird path.");
  }

  @Test
  public void runsRound2() {
    runOne("runs, round2");
  }

  @Test
  public void handlesNestedListens() {
    runOne("handles nested listens");
  }

  @Test
  public void handlesASetBelowAListen() {
    runOne("Handles a set below a listen");
  }

  @Test
  public void doesNonDefaultQueries() {
    runOne("does non-default queries");
  }

  @Test
  public void handlesCoLocatedDefaultListenerAndQuery() {
    runOne("handles a co-located default listener and query");
  }

  @Test
  public void defaultAndNonDefaultListenerAtSameLocationWithServerUpdate() {
    runOne("Default and non-default listener at same location with server update");
  }

  @Test
  public void addAParentListenerToACompleteChildListenerExpectChildEvent() {
    runOne("Add a parent listener to a complete child listener, expect child event");
  }

  @Test
  public void addListensToASetExpectCorrectEventsIncludingAChildEvent() {
    runOne("Add listens to a set, expect correct events, including a child event");
  }

  @Test
  public void serverUpdateToAChildListenerRaisesChildEventsAtParent() {
    runOne("ServerUpdate to a child listener raises child events at parent");
  }

  @Test
  public void serverUpdateToAChildListenerRaisesChildEventsAtParentQuery() {
    runOne("ServerUpdate to a child listener raises child events at parent query");
  }

  @Test
  public void multipleCompleteChildrenAreHandleProperly() {
    runOne("Multiple complete children are handled properly");
  }

  @Test
  public void writeLeafNodeOverwriteAtParentNode() {
    runOne("Write leaf node, overwrite at parent node");
  }

  @Test
  public void confirmCompleteChildrenFromTheServer() {
    runOne("Confirm complete children from the server");
  }

  @Test
  public void writeLeafOverwriteFromParent() {
    runOne("Write leaf, overwrite from parent");
  }

  @Test
  public void basicUpdateTest() {
    runOne("Basic update test");
  }

  @Test
  public void noDoubleValueEventsForUserAck() {
    runOne("No double value events for user ack");
  }

  @Test
  public void basicKeyIndexSanityCheck() {
    runOne("Basic key index sanity check");
  }

  @Test
  public void collectCorrectSubviewsToListenOn() {
    runOne("Collect correct subviews to listen on");
  }

  @Test
  public void limitToFirstOneOnOrderedQuery() {
    runOne("Limit to first one on ordered query");
  }

  @Test
  public void limitToLastOneOnOrderedQuery() {
    runOne("Limit to last one on ordered query");
  }

  @Test
  public void updateIndexedValueOnExistingChildFromLimitedQuery() {
    runOne("Update indexed value on existing child from limited query");
  }

  @Test
  public void canCreateStartAtEndAtEqualToQueriesWithBool() {
    runOne("Can create startAt, endAt, equalTo queries with bool");
  }

  @Test
  public void queryForExistingServerSnap() {
    runOne("Query with existing server snap");
  }

  @Test
  public void serverDataIsNotPurgedForNonServerIndexedQueries() {
    runOne("Server data is not purged for non-server-indexed queries");
  }

  @Test
  public void limitWithCustomOrderByIsRefilledWithCorrectItem() {
    runOne("Limit with custom orderBy is refilled with correct item");
  }

  @Test
  public void startAtEndAtDominatesLimit() {
    runOne("startAt/endAt dominates limit");
  }

  @Test
  public void updateToSingleChildThatMovesOutOfWindow() {
    runOne("Update to single child that moves out of window");
  }

  @Test
  public void limitedQueryDoesntPullInOutOfRangeChild() {
    runOne("Limited query doesn't pull in out of range child");
  }

  @Test
  public void mergerForLocationWithDefaultAndLimitedListener() {
    runOne("Merge for location with default and limited listener");
  }

  @Test
  public void userMergePullsInCorrectValues() {
    runOne("User merge pulls in correct values");
  }

  @Test
  public void userDeepSetPullsInCorrectValues() {
    runOne("User deep set pulls in correct values");
  }

  @Test
  public void queriesWithEqualToNullWork() {
    runOne("Queries with equalTo(null) work");
  }

  @Test
  public void revertedWritesUpdateQuery() {
    runOne("Reverted writes update query");
  }

  @Test
  public void deepSetForNonLocalDataDoesntRaiseEvents() {
    runOne("Deep set for non-local data doesn't raise events");
  }

  @Test
  public void userUpdateWithNewChildrenTriggersEvents() {
    runOne("User update with new children triggers events");
  }

  @Test
  public void userWriteWithDeepOverwrite() {
    runOne("User write with deep user overwrite");
  }

  @Test
  public void deepServerMerge() {
    runOne("Deep server merge");
  }

  @Test
  public void serverUpdatesPriority() {
    runOne("Server updates priority");
  }

  @Test
  public void revertFullUnderlyingWrite() {
    runOne("Revert underlying full overwrite");
  }

  @Test
  public void userChildOverwriteForNonexistentServerNode() {
    runOne("User child overwrite for non-existent server node");
  }

  @Test
  public void revertUserOverwriteOfChildOnLeafNode() {
    runOne("Revert user overwrite of child on leaf node");
  }

  @Test
  public void serverOverwriteWithDeepUserDelete() {
    runOne("Server overwrite with deep user delete");
  }

  @Test
  public void userOverwritesLeafNodeWithPriority() {
    runOne("User overwrites leaf node with priority");
  }

  @Test
  public void userOverwritesInheritPriorityValuesFromLeafNodes() {
    runOne("User overwrites inherit priority values from leaf nodes");
  }

  @Test
  public void userUpdateOnUserSetLeafNodeWithPriorityAfterServerUpdate() {
    runOne("User update on user set leaf node with priority after server update");
  }

  @Test
  public void serverDeepDeleteOnLeafNode() {
    runOne("Server deep delete on leaf node");
  }

  @Test
  public void userSetsRootPriority() {
    runOne("User sets root priority");
  }

  @Test
  public void userUpdatesPriorityOnEmptyRoot() {
    runOne("User updates priority on empty root");
  }

  @Test
  public void revertSetAtRootWithPriority() {
    runOne("Revert set at root with priority");
  }

  @Test
  public void serverUpdatesPriorityAfterUserSetsPriority() {
    runOne("Server updates priority after user sets priority");
  }

  @Test
  public void emptySetDoesntPreventServerUpdates() {
    runOne("Empty set doesn't prevent server updates");
  }

  @Test
  public void userUpdatesPriorityTwiceFirstIsReverted() {
    runOne("User updates priority twice, first is reverted");
  }

  @Test
  public void serverAcksRootPrioritySetAfterUserDeletesRootNode() {
    runOne("Server acks root priority set after user deletes root node");
  }

  @Test
  public void aDeleteInAMergeDoesntPushOutNodes() {
    runOne("A delete in a merge doesn't push out nodes");
  }

  @Test
  public void aTaggedQueryFiresEventsEventually() {
    runOne("A tagged query fires events eventually");
  }

  @Test
  public void aServerUpdateThatLeavesUserSetsUnchangedIsNotIgnored() {
    runOne("A server update that leaves user sets unchanged is not ignored");
  }

  @Test
  public void userWriteOutsideOfLimitIsIgnoredForTaggedQueries() {
    runOne("User write outside of limit is ignored for tagged queries");
  }

  @Test
  public void ackForMergeDoesntRaiseValueEventForLaterListen() {
    runOne("Ack for merge doesn't raise value event for later listen");
  }

  @Test
  public void clearParentShadowingServerValuesMergeWithServerChildren() {
    runOne("Clear parent shadowing server values merge with server children");
  }

  @Test
  public void prioritiesDontMakeMeSick() {
    runOne("Priorities don't make me sick");
  }

  @Test
  public void mergeThatMovesChildFromWindowToBoundaryDoesNotCauseChildToBeReadded() {
    runOne("Merge that moves child from window to boundary does not cause child to be readded");
  }

  @Test
  public void deepMergeAckIsHandledCorrectly() {
    runOne("Deep merge ack is handled correctly.");
  }

  @Test
  public void deepMergeAckOnIncompleteDataAndWithServerValues() {
    runOne("Deep merge ack (on incomplete data, and with server values)");
  }

  @Test
  public void limitQueryHandlesDeepServerMergeForOutOfViewItem() {
    runOne("Limit query handles deep server merge for out-of-view item.");
  }

  @Test
  public void limitQueryHandlesDeepUserMergeForOutOfViewItem() {
    runOne("Limit query handles deep user merge for out-of-view item.");
  }

  @Test
  public void limitQueryHandlesDeepUserMergeForOutOfViewItemFollowedByServerUpdate() {
    runOne("Limit query handles deep user merge for out-of-view item followed by server update.");
  }

  @Test
  public void unrelatedUntaggedUpdateIsNotCachedInTaggedListen() {
    runOne("Unrelated, untagged update is not cached in tagged listen");
  }

  @Test
  public void unrelatedAckedSetIsNotCachedInTaggedListen() {
    runOne("Unrelated, acked set is not cached in tagged listen");
  }

  @Test
  public void unrelatedAckedUpdateIsNotCachedInTaggedListen() {
    runOne("Unrelated, acked update is not cached in tagged listen");
  }

  @Test
  public void deepUpdateRaisesImmediateEventsOnlyIfHasCompleteData() {
    runOne("Deep update raises immediate events only if has complete data");
  }

  @Test
  public void deepUpdateReturnsMinimumDataRequired() {
    runOne("Deep update returns minimum data required");
  }

  @Test
  public void deepUpdateRaisesAllEvents() {
    runOne("Deep update raises all events");
  }
}
