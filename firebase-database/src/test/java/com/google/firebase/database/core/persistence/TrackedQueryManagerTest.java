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

import static com.google.firebase.database.UnitTestHelpers.asSet;
import static com.google.firebase.database.UnitTestHelpers.ck;
import static com.google.firebase.database.UnitTestHelpers.defaultQueryAt;
import static com.google.firebase.database.UnitTestHelpers.path;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.Clock;
import com.google.firebase.database.core.utilities.TestClock;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.LogWrapper;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.PathIndex;
import java.util.Collections;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class TrackedQueryManagerTest {

  private static final QueryParams SAMPLE_QUERYPARAMS =
      QueryParams.DEFAULT_PARAMS
          .orderBy(new PathIndex(new Path("child")))
          .startAt(NodeFromJSON("startVal"), ChildKey.fromString("startKey"))
          .endAt(NodeFromJSON("endVal"), ChildKey.fromString("endKey"))
          .limitToLast(5);

  private static final QuerySpec SAMPLE_FOO_QUERY = new QuerySpec(path("foo"), SAMPLE_QUERYPARAMS);
  private static final QuerySpec DEFAULT_FOO_QUERY = defaultQueryAt("foo");
  private static final QuerySpec DEFAULT_BAR_QUERY = defaultQueryAt("bar");

  private TrackedQueryManager newManager() {
    return newManager(null, null);
  }

  private TrackedQueryManager newManager(PersistenceStorageEngine engine, Clock clock) {
    if (clock == null) {
      clock = new TestClock();
    }
    if (engine == null) {
      MockPersistenceStorageEngine e = new MockPersistenceStorageEngine();
      e.disableTransactionCheck = true;
      engine = e;
    }
    LogWrapper logWrapper =
        new LogWrapper(new DefaultLogger(Logger.Level.DEBUG, null), "TrackedQueryManagerTest");
    return new TrackedQueryManager(engine, logWrapper, clock);
  }

  @Test
  public void findTrackedQuery() {
    TrackedQueryManager manager = newManager();
    assertNull(manager.findTrackedQuery(SAMPLE_FOO_QUERY));
    manager.setQueryActive(SAMPLE_FOO_QUERY);
    assertNotNull(manager.findTrackedQuery(SAMPLE_FOO_QUERY));
  }

  @Test
  public void removeTrackedQuery() {
    TrackedQueryManager manager = newManager();
    manager.setQueryActive(SAMPLE_FOO_QUERY);
    assertNotNull(manager.findTrackedQuery(SAMPLE_FOO_QUERY));
    manager.removeTrackedQuery(SAMPLE_FOO_QUERY);
    assertNull(manager.findTrackedQuery(SAMPLE_FOO_QUERY));
    manager.verifyCache();
  }

  @Test
  public void setQueryActiveAndInactive() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    manager.setQueryActive(SAMPLE_FOO_QUERY);
    TrackedQuery q = manager.findTrackedQuery(SAMPLE_FOO_QUERY);
    assertTrue(q.active);
    assertEquals(clock.millis(), q.lastUse);
    manager.verifyCache();

    clock.tick();
    manager.setQueryInactive(SAMPLE_FOO_QUERY);
    q = manager.findTrackedQuery(SAMPLE_FOO_QUERY);
    assertFalse(q.active);
    assertEquals(clock.millis(), q.lastUse);
    manager.verifyCache();
  }

  @Test
  public void setQueryComplete() {
    TrackedQueryManager manager = newManager();
    manager.setQueryActive(SAMPLE_FOO_QUERY);
    manager.setQueryCompleteIfExists(SAMPLE_FOO_QUERY);
    assertTrue(manager.findTrackedQuery(SAMPLE_FOO_QUERY).complete);
    manager.verifyCache();
  }

  @Test
  public void setQueriesComplete() {
    TrackedQueryManager manager = newManager();
    manager.setQueryActive(defaultQueryAt("foo"));
    manager.setQueryActive(defaultQueryAt("foo/bar"));
    manager.setQueryActive(defaultQueryAt("elsewhere"));
    manager.setQueryActive(new QuerySpec(path("foo"), SAMPLE_QUERYPARAMS));
    manager.setQueryActive(new QuerySpec(path("foo/baz"), SAMPLE_QUERYPARAMS));
    manager.setQueryActive(new QuerySpec(path("elsewhere"), SAMPLE_QUERYPARAMS));

    manager.setQueriesComplete(path("foo"));

    assertTrue(manager.findTrackedQuery(defaultQueryAt("foo")).complete);
    assertTrue(manager.findTrackedQuery(defaultQueryAt("foo/bar")).complete);
    assertTrue(manager.findTrackedQuery(new QuerySpec(path("foo"), SAMPLE_QUERYPARAMS)).complete);
    assertTrue(
        manager.findTrackedQuery(new QuerySpec(path("foo/baz"), SAMPLE_QUERYPARAMS)).complete);
    assertFalse(manager.findTrackedQuery(defaultQueryAt("elsewhere")).complete);
    assertFalse(
        manager.findTrackedQuery(new QuerySpec(path("elsewhere"), SAMPLE_QUERYPARAMS)).complete);
    manager.verifyCache();
  }

  @Test
  public void isQueryComplete() {
    TrackedQueryManager manager = newManager();

    manager.setQueryActive(SAMPLE_FOO_QUERY);
    manager.setQueryCompleteIfExists(SAMPLE_FOO_QUERY);

    manager.setQueryActive(DEFAULT_BAR_QUERY);

    manager.setQueryActive(defaultQueryAt("baz"));
    manager.setQueryCompleteIfExists(defaultQueryAt("baz"));

    assertTrue(manager.isQueryComplete(SAMPLE_FOO_QUERY));
    assertFalse(manager.isQueryComplete(DEFAULT_BAR_QUERY));

    assertFalse(manager.isQueryComplete(defaultQueryAt("")));
    assertTrue(manager.isQueryComplete(defaultQueryAt("baz")));
    assertTrue(manager.isQueryComplete(defaultQueryAt("baz/quu")));
  }

  @Test
  public void pruneOldQueries() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    manager.setQueryActive(defaultQueryAt("active1"));
    manager.setQueryActive(defaultQueryAt("active2"));
    manager.setQueryActive(defaultQueryAt("inactive1"));
    manager.setQueryInactive(defaultQueryAt("inactive1"));
    clock.tick();
    manager.setQueryActive(defaultQueryAt("inactive2"));
    manager.setQueryInactive(defaultQueryAt("inactive2"));
    clock.tick();
    manager.setQueryActive(defaultQueryAt("inactive3"));
    manager.setQueryInactive(defaultQueryAt("inactive3"));
    clock.tick();
    manager.setQueryActive(defaultQueryAt("inactive4"));
    manager.setQueryInactive(defaultQueryAt("inactive4"));

    // Should prune the first two inactive queries.
    PruneForest forest = manager.pruneOldQueries(new TestCachePolicy(0.5f, Long.MAX_VALUE));
    PruneForest expected =
        new PruneForest()
            .prune(path("inactive1"))
            .prune(path("inactive2"))
            .keep(path("active1"))
            .keep(path("active2"))
            .keep(path("inactive3"))
            .keep(path("inactive4"));
    assertEquals(expected, forest);

    // Should prune the other two inactive queries.
    forest = manager.pruneOldQueries(new TestCachePolicy(1.0f, Long.MAX_VALUE));
    expected =
        new PruneForest()
            .prune(path("inactive3"))
            .prune(path("inactive4"))
            .keep(path("active1"))
            .keep(path("active2"));
    assertEquals(expected, forest);

    forest = manager.pruneOldQueries(new TestCachePolicy(1.0f, Long.MAX_VALUE));
    assertFalse(forest.prunesAnything());

    manager.verifyCache();
  }

  @Test
  public void pruneQueriesOverMaxSize() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    // Create a bunch of inactive queries.
    for (int i = 0; i < 15; i++) {
      manager.setQueryActive(defaultQueryAt("" + i));
      manager.setQueryInactive(defaultQueryAt("" + i));
      clock.tick();
    }

    PruneForest forest =
        manager.pruneOldQueries(
            new TestCachePolicy(/* percentToPruneAtOnce= */ 0.2f, /* maxNumberToKeep= */ 10));

    // Should prune down to the max of 10, so 5 pruned.
    PruneForest expected = new PruneForest();
    for (int i = 0; i < 15; i++) {
      if (i < 5) {
        expected = expected.prune(path("" + i));
      } else {
        expected = expected.keep(path("" + i));
      }
    }
    assertEquals(expected, forest);

    manager.verifyCache();
  }

  @Test
  public void pruneDefaultWithDeeperQueries() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    manager.setQueryActive(defaultQueryAt("foo"));
    manager.setQueryActive(new QuerySpec(path("foo/a"), SAMPLE_QUERYPARAMS));
    manager.setQueryActive(new QuerySpec(path("foo/b"), SAMPLE_QUERYPARAMS));
    manager.setQueryInactive(defaultQueryAt("foo"));

    // prune foo, but keep foo/a and foo/b
    PruneForest forest = manager.pruneOldQueries(new TestCachePolicy(1.0f, Long.MAX_VALUE));
    PruneForest expected =
        new PruneForest().prune(path("foo")).keep(path("foo/a")).keep(path("foo/b"));
    assertEquals(expected, forest);
    manager.verifyCache();
  }

  @Test
  public void pruneQueriesWithDefaultQueryOnParent() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    manager.setQueryActive(defaultQueryAt("foo"));
    manager.setQueryActive(new QuerySpec(path("foo/a"), SAMPLE_QUERYPARAMS));
    manager.setQueryActive(new QuerySpec(path("foo/b"), SAMPLE_QUERYPARAMS));
    manager.setQueryInactive(new QuerySpec(path("foo/a"), SAMPLE_QUERYPARAMS));
    manager.setQueryInactive(new QuerySpec(path("foo/b"), SAMPLE_QUERYPARAMS));

    // prune foo/a and foo/b, but keep foo
    PruneForest forest = manager.pruneOldQueries(new TestCachePolicy(1.0f, Long.MAX_VALUE));
    PruneForest expected =
        new PruneForest().prune(path("foo/a")).prune(path("foo/b")).keep(path("foo"));
    assertEquals(expected, forest);
    manager.verifyCache();
  }

  @Test
  public void getKnownCompleteChildren() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    engine.disableTransactionCheck = true;

    TrackedQueryManager manager = newManager(engine, null);

    assertEquals(Collections.<ChildKey>emptySet(), manager.getKnownCompleteChildren(path("foo")));

    manager.setQueryActive(defaultQueryAt("foo/a"));
    manager.setQueryCompleteIfExists(defaultQueryAt("foo/a"));
    manager.setQueryActive(defaultQueryAt("foo/not-included"));
    manager.setQueryActive(defaultQueryAt("foo/deep/not-included"));
    manager.setQueryCompleteIfExists(defaultQueryAt("foo/deep/not-included"));

    manager.setQueryActive(SAMPLE_FOO_QUERY);
    TrackedQuery trackedQuery = manager.findTrackedQuery(SAMPLE_FOO_QUERY);
    engine.saveTrackedQueryKeys(trackedQuery.id, asSet(ck("d"), ck("e")));

    assertEquals(asSet(ck("a"), ck("d"), ck("e")), manager.getKnownCompleteChildren(path("foo")));
    assertEquals(Collections.<ChildKey>emptySet(), manager.getKnownCompleteChildren(path("")));
    assertEquals(
        Collections.<ChildKey>emptySet(), manager.getKnownCompleteChildren(path("foo/baz")));
  }

  @Test
  public void ensureTrackedQueryForNewQuery() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    manager.ensureCompleteTrackedQuery(path("foo"));
    TrackedQuery query = manager.findTrackedQuery(DEFAULT_FOO_QUERY);
    assertTrue(query.complete);
    assertEquals(clock.millis(), query.lastUse);
  }

  @Test
  public void ensureTrackedQueryForAlreadyTrackedQuery() {
    TestClock clock = new TestClock();
    TrackedQueryManager manager = newManager(null, clock);

    manager.setQueryActive(DEFAULT_FOO_QUERY);

    long lastTick = clock.millis();
    clock.tick();
    manager.ensureCompleteTrackedQuery(path("foo"));
    assertEquals(lastTick, manager.findTrackedQuery(DEFAULT_FOO_QUERY).lastUse);
  }

  @Test
  public void hasActiveDefaultQuery() {
    TrackedQueryManager manager = newManager();

    manager.setQueryActive(SAMPLE_FOO_QUERY);

    manager.setQueryActive(DEFAULT_BAR_QUERY);

    assertFalse(manager.hasActiveDefaultQuery(path("foo")));
    assertFalse(manager.hasActiveDefaultQuery(path("")));
    assertTrue(manager.hasActiveDefaultQuery(path("bar")));
    assertTrue(manager.hasActiveDefaultQuery(path("bar/baz")));
  }

  @Test
  public void cacheSanityCheck() {
    MockPersistenceStorageEngine engine = new MockPersistenceStorageEngine();
    engine.disableTransactionCheck = true;
    TrackedQueryManager manager = newManager(engine, null);

    manager.setQueryActive(SAMPLE_FOO_QUERY);
    manager.setQueryActive(DEFAULT_FOO_QUERY);
    manager.verifyCache();

    manager.setQueryCompleteIfExists(SAMPLE_FOO_QUERY);
    manager.verifyCache();

    manager.setQueryInactive(DEFAULT_FOO_QUERY);
    manager.verifyCache();

    TrackedQueryManager manager2 = newManager(engine, null);
    manager2.verifyCache();
  }
}
