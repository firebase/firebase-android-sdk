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

import static com.google.firebase.database.IntegrationTestHelpers.asSet;
import static com.google.firebase.database.IntegrationTestHelpers.childKeySet;
import static com.google.firebase.database.IntegrationTestHelpers.compoundWrite;
import static com.google.firebase.database.IntegrationTestHelpers.defaultQueryAt;
import static com.google.firebase.database.IntegrationTestHelpers.leafNodeOfSize;
import static com.google.firebase.database.IntegrationTestHelpers.node;
import static com.google.firebase.database.IntegrationTestHelpers.path;
import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static java.util.Arrays.asList;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.database.DatabaseException;
import com.google.firebase.database.RetryRule;
import com.google.firebase.database.android.SqlPersistenceStorageEngine;
import com.google.firebase.database.core.CompoundWrite;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.UserWriteRecord;
import com.google.firebase.database.core.utilities.NodeSizeEstimator;
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PathIndex;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class SqlPersistenceStorageEngineTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  private PersistenceStorageEngine engine;

  private PersistenceStorageEngine getCleanPersistenceCache() {
    DatabaseConfig ctx = new DatabaseConfig();
    ctx.setLogger(new DefaultLogger(Logger.Level.DEBUG, null));
    ctx.setLogLevel(com.google.firebase.database.Logger.Level.DEBUG);
    final SqlPersistenceStorageEngine engine =
        new SqlPersistenceStorageEngine(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), ctx, "test-namespace");
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.removeAllUserWrites();
            engine.purgeCache();
          }
        });
    return engine;
  }

  private static final Node SAMPLE_NODE =
      node("{ 'foo': { 'bar': true, 'baz': 'string' }, 'qux': 2, 'quu': 1.2 }");

  private static final Node ONE_MEG_NODE = leafNodeOfSize(1024 * 1024);
  private static final Node FIVE_MEG_NODE = leafNodeOfSize(5 * 1024 * 1024);
  private static final Node TEN_MEG_NODE = leafNodeOfSize(10 * 1024 * 1024);
  private static final Node TEN_MEG_PLUS_ONE_NODE = leafNodeOfSize(10 * 1024 * 1024 + 1);

  private static final QueryParams SAMPLE_QUERYPARAMS =
      QueryParams.DEFAULT_PARAMS
          .orderBy(new PathIndex(path("child")))
          .startAt(NodeFromJSON("startVal"), ChildKey.fromString("startKey"))
          .endAt(NodeFromJSON("endVal"), ChildKey.fromString("endKey"))
          .limitToLast(5);

  private static final QuerySpec SAMPLE_QUERY = new QuerySpec(path("foo"), SAMPLE_QUERYPARAMS);
  private static final QuerySpec DEFAULT_FOO_QUERY = QuerySpec.defaultQueryAtPath(path("foo"));

  private static final TrackedQuery SAMPLE_TRACKED_QUERY =
      new TrackedQuery(1, SAMPLE_QUERY, 100, false, false);

  @Before
  public void before() {
    engine = getCleanPersistenceCache();
  }

  @After
  public void after() {
    engine.close();
  }

  @Test
  public void testUserWriteIsPersisted() {
    saveUserOverwrite(engine, path("foo/bar"), SAMPLE_NODE, 1);
    assertEquals(asList(writeRecord(1, path("foo/bar"), SAMPLE_NODE)), engine.loadUserWrites());
  }

  @Test
  public void testUserMergeIsPersisted() {

    CompoundWrite merge = compoundWrite("{'foo': {'bar': 1, 'baz': 'string'}, 'quu': true}");
    saveUserMerge(engine, path("foo/bar"), merge, 1);
    assertEquals(asList(writeRecord(1, path("foo/bar"), merge)), engine.loadUserWrites());
  }

  @Test
  public void testSameWriteIdOverwritesOldWrite() {

    saveUserOverwrite(engine, path("foo/bar"), NodeFromJSON("first"), 1);
    saveUserOverwrite(engine, path("other/path"), NodeFromJSON("second"), 1);
    assertEquals(
        asList(writeRecord(1, path("other/path"), NodeFromJSON("second"))),
        engine.loadUserWrites());
  }

  @Test
  public void testHugeWritesWork() {

    saveUserOverwrite(engine, path("foo/bar"), TEN_MEG_NODE, 1);
    CompoundWrite merge = CompoundWrite.emptyWrite();
    merge.addWrite(ChildKey.fromString("update"), TEN_MEG_NODE);
    saveUserMerge(engine, path("foo/baz"), merge, 2);

    List<UserWriteRecord> expected =
        asList(
            writeRecord(1, path("foo/bar"), TEN_MEG_NODE), writeRecord(2, path("foo/baz"), merge));
    assertEquals(expected, engine.loadUserWrites());
  }

  @Test
  public void testHugeWritesCanBeDeleted() {

    saveUserOverwrite(engine, path("foo/bar"), TEN_MEG_NODE, 1);
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.removeUserWrite(1);
          }
        });

    assertTrue(engine.loadUserWrites().isEmpty());
  }

  @Test
  public void testHugeWritesCanBeInterleavedWithSmallWrites() {

    saveUserOverwrite(engine, path("foo/1"), NodeFromJSON("node-1"), 1);
    saveUserOverwrite(engine, path("foo/2"), TEN_MEG_NODE, 2);
    saveUserOverwrite(engine, path("foo/3"), NodeFromJSON("node-3"), 3);
    saveUserOverwrite(engine, path("foo/4"), TEN_MEG_PLUS_ONE_NODE, 4);

    List<UserWriteRecord> expected =
        asList(
            writeRecord(1, path("foo/1"), NodeFromJSON("node-1")),
            writeRecord(2, path("foo/2"), TEN_MEG_NODE),
            writeRecord(3, path("foo/3"), NodeFromJSON("node-3")),
            writeRecord(4, path("foo/4"), TEN_MEG_PLUS_ONE_NODE));
    assertEquals(expected, engine.loadUserWrites());
  }

  @Test
  public void testSameWriteIdOverwritesOldMultiPartWrite() {

    saveUserOverwrite(engine, path("foo/bar"), TEN_MEG_NODE, 1);
    saveUserOverwrite(engine, path("other/path"), NodeFromJSON("second"), 1);

    assertEquals(
        asList(writeRecord(1, path("other/path"), NodeFromJSON("second"))),
        engine.loadUserWrites());
  }

  @Test
  public void testWritesAreReturnedInOrder() {

    final int count = 20;
    for (int i = count - 1; i >= 0; i--) {
      saveUserOverwrite(engine, path("foo/" + i), NodeFromJSON(i), i);
    }
    List<UserWriteRecord> records = engine.loadUserWrites();
    assertEquals(count, records.size());
    for (int i = 0; i < count; i++) {
      assertEquals(writeRecord(i, path("foo/" + i), NodeFromJSON(i)), records.get(i));
    }
  }

  @Test
  public void testRemoveAllUserWrites() {

    saveUserOverwrite(engine, path("foo/1"), NodeFromJSON("node-1"), 1);
    saveUserOverwrite(engine, path("foo/2"), TEN_MEG_NODE, 2);
    CompoundWrite merge = CompoundWrite.emptyWrite();
    merge.addWrite(ChildKey.fromString("update"), TEN_MEG_NODE);
    saveUserMerge(engine, path("foo/baz"), merge, 2);

    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.removeAllUserWrites();
          }
        });
    assertEquals(Collections.<UserWriteRecord>emptyList(), engine.loadUserWrites());
  }

  @Test
  public void testCacheSavedIsReturned() {

    overwriteServerCache(engine, path("foo"), SAMPLE_NODE);
    assertEquals(SAMPLE_NODE, engine.serverCache(path("foo")));
  }

  @Test
  public void testCacheSavedIsReturnedAtRoot() {

    overwriteServerCache(engine, path(""), SAMPLE_NODE);
    assertEquals(SAMPLE_NODE, engine.serverCache(path("")));
  }

  @Test
  public void testCacheLaterWritesOverwriteOlderWrites() {

    overwriteServerCache(engine, path("foo"), SAMPLE_NODE);
    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON("later-bar"));
    // this does not affect the node
    overwriteServerCache(engine, path("unaffected"), NodeFromJSON("unaffected"));
    overwriteServerCache(engine, path("foo/later-qux"), NodeFromJSON("later-qux"));
    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON("latest-bar"));

    Node expected =
        SAMPLE_NODE
            .updateChild(path("bar"), NodeFromJSON("latest-bar"))
            .updateChild(path("later-qux"), NodeFromJSON("later-qux"));
    assertEquals(expected, engine.serverCache(path("foo")));
  }

  @Test
  public void testCacheLaterWritesOverwriterOlderDeeperWrites() {

    overwriteServerCache(engine, path("foo"), SAMPLE_NODE);
    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON("later-bar"));
    // this does not affect the node
    overwriteServerCache(engine, path("unaffected"), NodeFromJSON("unaffected"));
    overwriteServerCache(engine, path("foo/later-qux"), NodeFromJSON("later-qux"));
    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON("latest-bar"));
    overwriteServerCache(engine, path("foo"), NodeFromJSON("latest-foo"));

    assertEquals(NodeFromJSON("latest-foo"), engine.serverCache(path("foo")));
  }

  @Test
  public void testCacheLaterWritesDontAffectEarlierWritesAtUnaffectedPath() {

    overwriteServerCache(engine, path("foo"), SAMPLE_NODE);
    // this does not affect the node
    overwriteServerCache(engine, path("unaffected"), NodeFromJSON("unaffected"));
    overwriteServerCache(engine, path("foo"), NodeFromJSON("latest-foo"));

    assertEquals(NodeFromJSON("unaffected"), engine.serverCache(path("unaffected")));
  }

  @Test
  public void testMergeOnEmptyCacheGivesResults() {

    CompoundWrite merge = compoundWrite("{'foo': 'foo-value', 'bar': 'bar-value'}");
    mergeIntoServerCache(engine, path("foo"), merge);
    Node expected = node("{'foo': 'foo-value', 'bar': 'bar-value'}");
    assertEquals(expected, engine.serverCache(path("foo")));
  }

  @Test
  public void testMergePartlyOverwritingPreviousWrite() {

    Node existingNode = node("{'foo': 'foo-value', 'bar': 'bar-value'}");
    overwriteServerCache(engine, path("foo"), existingNode);

    CompoundWrite merge = compoundWrite("{'foo': 'new-foo-value', 'baz':'baz-value'}");
    mergeIntoServerCache(engine, path("foo"), merge);

    Node expected = node("{'foo': 'new-foo-value', 'bar': 'bar-value', 'baz': 'baz-value'}");
    assertEquals(expected, engine.serverCache(path("foo")));
  }

  @Test
  public void testMergePartlyOverwritingPreviousMerge() {

    CompoundWrite merge1 = compoundWrite("{'foo': 'foo-value', 'bar':'bar-value'}");
    mergeIntoServerCache(engine, path("foo"), merge1);

    CompoundWrite merge2 = compoundWrite("{'foo': 'new-foo-value', 'baz':'baz-value'}");
    mergeIntoServerCache(engine, path("foo"), merge2);

    Node expected = node("{'foo': 'new-foo-value', 'bar': 'bar-value', 'baz': 'baz-value'}");
    assertEquals(expected, engine.serverCache(path("foo")));
  }

  @Test
  public void testOverwriteRemovesPreviousMerges() {

    Node initial = node("{'foo': 'foo-value', 'bar': 'bar-value'}");
    overwriteServerCache(engine, path("foo"), initial);

    CompoundWrite merge = compoundWrite("{'foo': 'new-foo-value', 'baz': 'baz-value'}");
    mergeIntoServerCache(engine, path("foo"), merge);

    Node replacingNode = node("{'qux': 'qux-value', 'quu': 'quu-value'}");
    overwriteServerCache(engine, path("foo"), replacingNode);

    assertEquals(replacingNode, engine.serverCache(path("foo")));
  }

  @Test
  public void testEmptyOverwriteDeletesNodeFromHigherWrite() {

    Node initial = node("{'foo': 'foo-value', 'bar': 'bar-value'}");
    overwriteServerCache(engine, path("foo"), initial);

    // delete bar
    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON(null));

    Node expected = node("{'foo': 'foo-value'}");
    assertEquals(expected, engine.serverCache(path("foo")));
  }

  @Test
  public void testDeeperReadFromHigherSet() {

    Node initial = node("{'foo': 'foo-value', 'bar': 'bar-value'}");
    overwriteServerCache(engine, path("foo"), initial);

    assertEquals(NodeFromJSON("bar-value"), engine.serverCache(path("foo/bar")));
  }

  @Test
  public void testHugeNodeWithSplit() {

    Node outer = EmptyNode.Empty();
    // This structure ensures splits at various depths
    for (int i = 0; i < 100; i++) { // Outer
      Node inner = EmptyNode.Empty();
      for (int j = 0; j < i; j++) { // Inner
        Node innerMost = EmptyNode.Empty();
        for (int k = 0; k < j; k++) {
          innerMost =
              innerMost.updateImmediateChild(
                  ChildKey.fromString("key-" + k), NodeFromJSON("leaf-" + k));
        }
        inner = inner.updateImmediateChild(ChildKey.fromString("inner-" + j), innerMost);
      }
      outer = outer.updateImmediateChild(ChildKey.fromString("outer-" + i), inner);
    }
    overwriteServerCache(engine, path("foo"), outer);

    assertEquals(outer, engine.serverCache(path("foo")));
  }

  @Test
  public void testManyLargeLeafNodes() {

    Node outer = EmptyNode.Empty();
    for (int i = 0; i < 30; i++) { // Outer
      outer = outer.updateImmediateChild(ChildKey.fromString("key-" + i), ONE_MEG_NODE);
    }

    overwriteServerCache(engine, path("foo"), outer);

    assertEquals(outer, engine.serverCache(path("foo")));
  }

  @Test
  public void testDeepOverwriteWithNestedLargeNode() {

    Node first = node("{'a': { 'aa': 1 } }");
    Node newA = EmptyNode.Empty().updateChild(path("ab"), ONE_MEG_NODE);
    overwriteServerCache(engine, path(""), first);
    overwriteServerCache(engine, path("a"), newA);

    assertEquals(first.updateChild(path("a"), newA), engine.serverCache(path("")));
  }

  @Test
  public void testAllowedSessionIdCharacters() {
    DatabaseConfig cfg = new DatabaseConfig();
    cfg.setLogger(new DefaultLogger(Logger.Level.DEBUG, null));
    String id = ".?!@$%^&*()\\/-~{}π∞٩(-̮̮̃-̃)۶ ٩(●̮̮̃•̃)۶ ٩(͡๏̯͡๏)۶";
    PersistenceStorageEngine engine =
        new SqlPersistenceStorageEngine(
            InstrumentationRegistry.getInstrumentation().getTargetContext(), cfg, id);
  }

  @Test
  public void testPriorityWorks() {

    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON("bar-value"));
    overwriteServerCache(engine, path("foo/.priority"), NodeFromJSON("prio-value"));

    Node expected = node("{'bar': 'bar-value', '.priority': 'prio-value'}");
    assertEquals(expected, engine.serverCache(path("foo")));
  }

  @Test
  public void testSimilarSiblingsAreNotLoaded() {

    overwriteServerCache(engine, path("foo/123"), NodeFromJSON("value"));
    overwriteServerCache(engine, path("foo/1230"), NodeFromJSON("sibling-value"));

    assertEquals(NodeFromJSON("value"), engine.serverCache(path("foo/123")));
  }

  // TODO: this test fails, but it is a rare edge case around priorities which would require a bunch
  // of code.
  // Fix whenever we have too much time on our hands
  @Ignore
  public void priorityIsCleared() {

    Map<String, Object> node = new HashMap<String, Object>();
    node.put("bar", "bar-value");
    overwriteServerCache(engine, path("foo"), NodeFromJSON(node));
    overwriteServerCache(engine, path("foo/.priority"), NodeFromJSON("prio-value"));
    overwriteServerCache(engine, path("foo/bar"), NodeFromJSON(null));
    overwriteServerCache(engine, path("foo/baz"), NodeFromJSON("baz-value"));

    // Priority should have been cleaned out
    Node expected =
        EmptyNode.Empty()
            .updateImmediateChild(ChildKey.fromString("baz"), NodeFromJSON("baz-value"));

    Node actual = engine.serverCache(path("foo"));
    assertEquals(expected, actual);
  }

  @Test
  public void testHugeLeafNode() {

    overwriteServerCache(engine, path("foo"), TEN_MEG_NODE);

    assertEquals(TEN_MEG_NODE, engine.serverCache(path("foo")));
  }

  @Test
  public void testHugeLeafNodeSiblings() {

    overwriteServerCache(engine, path("foo/one"), TEN_MEG_NODE);
    overwriteServerCache(engine, path("foo/two"), TEN_MEG_PLUS_ONE_NODE);

    Node node = engine.serverCache(path("foo"));
    assertEquals(TEN_MEG_NODE, node.getChild(path("one")));
    assertEquals(TEN_MEG_PLUS_ONE_NODE, node.getChild(path("two")));
  }

  @Test
  public void testHugeLeafNodeAndThenTinyLeafNode() {

    overwriteServerCache(engine, path("foo"), TEN_MEG_NODE);

    Node newLeafNode = leafNodeOfSize(1024);
    overwriteServerCache(engine, path("foo"), newLeafNode);

    assertEquals(newLeafNode, engine.serverCache(path("foo")));
  }

  @Test
  public void testHugeLeafNodeAndThenSmallerLeafNode() {

    overwriteServerCache(engine, path("foo"), TEN_MEG_NODE);
    overwriteServerCache(engine, path("foo"), FIVE_MEG_NODE);

    Node node = engine.serverCache(path("foo"));
    assertEquals(FIVE_MEG_NODE, node);
  }

  @Test
  public void testHugeLeafNodeAndThenDeeperSet() {

    overwriteServerCache(engine, path("foo"), TEN_MEG_NODE);
    overwriteServerCache(engine, path("foo/deep"), NodeFromJSON("deep-value"));

    assertEquals(node("{'deep': 'deep-value'}"), engine.serverCache(path("foo")));
  }

  @Test
  public void testEstimateServerSizeForNodes() {

    long sampleNodeSize = NodeSizeEstimator.estimateSerializedNodeSize(SAMPLE_NODE);
    overwriteServerCache(engine, path("foo"), SAMPLE_NODE);

    long estimatedServerCacheSize = engine.serverCacheEstimatedSizeInBytes();
    long expectedSize = sampleNodeSize + 4; // add path

    // not off more than 20 bytes
    assertTrue(Math.abs(estimatedServerCacheSize - expectedSize) < 20);
  }

  @Test
  public void testEstimateServerSizeAccountsForPaths() {

    long sampleNodeSize = NodeSizeEstimator.estimateSerializedNodeSize(SAMPLE_NODE);
    String path =
        "super/duper/long/mega/deep/but/also/super/insightful/but/not/quite/max/path/depth/length";
    overwriteServerCache(engine, path(path), SAMPLE_NODE);

    long estimatedServerCacheSize = engine.serverCacheEstimatedSizeInBytes();
    long expectedSize = sampleNodeSize + path.length();

    // not off more than 20 bytes
    assertTrue(Math.abs(estimatedServerCacheSize - expectedSize) < 20);
  }

  @Test
  public void testEstimateServerSizeForHundredsOfNodes() {

    long sampleNodeSize = NodeSizeEstimator.estimateSerializedNodeSize(SAMPLE_NODE);
    long totalExpectedSize = 0;
    int numNodes = 200;
    for (int i = 0; i < numNodes; i++) {
      String path = "super/duper/long/path/" + i;
      overwriteServerCache(engine, path(path), SAMPLE_NODE);
      totalExpectedSize += sampleNodeSize + path.length();
    }

    long estimatedServerCacheSize = engine.serverCacheEstimatedSizeInBytes();

    // not off more than 20*nodes bytes
    assertTrue(Math.abs(estimatedServerCacheSize - totalExpectedSize) < 20 * numNodes);
  }

  @Test
  public void testSaveAndLoadTrackedQueries() {

    List<TrackedQuery> queries = new ArrayList<TrackedQuery>();
    queries.add(new TrackedQuery(1, SAMPLE_QUERY, 100, false, false));
    queries.add(new TrackedQuery(2, defaultQueryAt("a"), 200, true, false));
    queries.add(new TrackedQuery(3, defaultQueryAt("b"), 300, false, false));
    queries.add(new TrackedQuery(4, QuerySpec.defaultQueryAtPath(path("foo")), 400, false, false));

    for (TrackedQuery q : queries) {
      saveTrackedQuery(engine, q);
    }

    assertEquals(queries, engine.loadTrackedQueries());
  }

  @Test
  public void testOverwriteTrackedQueryById() {

    saveTrackedQuery(engine, new TrackedQuery(1, SAMPLE_QUERY, 100, false, false));
    TrackedQuery replaced = new TrackedQuery(1, DEFAULT_FOO_QUERY, 200, true, true);
    saveTrackedQuery(engine, replaced);

    assertEquals(asList(replaced), engine.loadTrackedQueries());
  }

  @Test
  public void testDeleteTrackedQuery() {

    TrackedQuery query1 = new TrackedQuery(1, defaultQueryAt("a"), 200, false, false);
    TrackedQuery query2 = new TrackedQuery(2, defaultQueryAt("b"), 300, true, false);
    TrackedQuery query3 = new TrackedQuery(3, defaultQueryAt("c"), 400, false, true);
    saveTrackedQuery(engine, query1);
    saveTrackedQuery(engine, query2);
    saveTrackedQuery(engine, query3);

    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.deleteTrackedQuery(2);
          }
        });

    assertEquals(asList(query1, query3), engine.loadTrackedQueries());
  }

  @Test
  public void testResetPreviouslyActiveTrackedQueries() {

    TrackedQuery inactive1 =
        new TrackedQuery(1, defaultQueryAt("a"), 200, false, /*active=*/ false);
    TrackedQuery active1 = new TrackedQuery(2, defaultQueryAt("b"), 300, false, /*active=*/ true);
    TrackedQuery inactive2 =
        new TrackedQuery(3, defaultQueryAt("c"), 400, false, /*active=*/ false);
    TrackedQuery active2 = new TrackedQuery(4, defaultQueryAt("d"), 500, false, /*active=*/ true);
    saveTrackedQuery(engine, inactive1);
    saveTrackedQuery(engine, active1);
    saveTrackedQuery(engine, inactive2);
    saveTrackedQuery(engine, active2);

    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.resetPreviouslyActiveTrackedQueries(234);
          }
        });

    List<TrackedQuery> expected =
        asList(
            inactive1,
            active1.setActiveState(false).updateLastUse(234),
            inactive2,
            active2.setActiveState(false).updateLastUse(234));
    assertEquals(expected, engine.loadTrackedQueries());
  }

  @Test
  public void testSaveAndLoadTrackedQueryKeys() {

    Set<ChildKey> keys = childKeySet("foo", "☁", "10");
    saveTrackedQueryKeys(engine, 1, keys);
    saveTrackedQueryKeys(engine, 2, childKeySet("not", "included"));

    assertEquals(keys, engine.loadTrackedQueryKeys(1));
  }

  @Test
  public void testSaveOverwritesTrackedQueryKeys() {

    saveTrackedQueryKeys(engine, 1, childKeySet("a", "b", "c"));
    saveTrackedQueryKeys(engine, 1, childKeySet("c", "d", "e"));

    assertEquals(childKeySet("c", "d", "e"), engine.loadTrackedQueryKeys(1));
  }

  @Test
  public void testLoadTrackedQueryKeysBySet() {

    saveTrackedQueryKeys(engine, 1, childKeySet("a", "b", "c"));
    saveTrackedQueryKeys(engine, 2, childKeySet("c", "d", "e"));
    saveTrackedQueryKeys(engine, 3, childKeySet("not", "included"));

    Set<ChildKey> expected = childKeySet("a", "b", "c", "d", "e");
    assertEquals(expected, engine.loadTrackedQueryKeys(asSet(1L, 2L)));
  }

  @Test
  public void testUpdateTrackedQueryKeys() {

    saveTrackedQueryKeys(engine, 1, childKeySet("a", "b", "c"));

    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.updateTrackedQueryKeys(
                1, /*added=*/ childKeySet("d", "e"), /*removed=*/ childKeySet("a", "b"));
          }
        });

    assertEquals(childKeySet("c", "d", "e"), engine.loadTrackedQueryKeys(1));
  }

  @Test
  public void testRemoveTrackedQueryRemovesTrackedQueryKeys() {

    TrackedQuery query1 = new TrackedQuery(1, defaultQueryAt("a"), 200, false, true);
    TrackedQuery query2 = new TrackedQuery(2, defaultQueryAt("b"), 300, false, true);
    saveTrackedQuery(engine, query1);
    saveTrackedQuery(engine, query2);
    saveTrackedQueryKeys(engine, query1.id, childKeySet("a", "b"));
    saveTrackedQueryKeys(engine, query2.id, childKeySet("b", "c"));

    assertEquals(asList(query1, query2), engine.loadTrackedQueries());

    assertEquals(childKeySet("a", "b"), engine.loadTrackedQueryKeys(1));
    assertEquals(childKeySet("b", "c"), engine.loadTrackedQueryKeys(2));

    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.deleteTrackedQuery(1);
          }
        });

    assertEquals(asList(query2), engine.loadTrackedQueries());

    assertEquals(Collections.<ChildKey>emptySet(), engine.loadTrackedQueryKeys(1));
    assertEquals(childKeySet("b", "c"), engine.loadTrackedQueryKeys(2));
  }

  @Test
  public void testConcurrentUsageFails() {
    try {
      getCleanPersistenceCache();
      fail("We should have failed to access the database again.");
    } catch (DatabaseException e) {
      // expected.
    }
  }

  private UserWriteRecord writeRecord(long writeId, Path path, Node node) {
    return new UserWriteRecord(writeId, path, node, /*visible=*/ true);
  }

  private UserWriteRecord writeRecord(long writeId, Path path, CompoundWrite compoundWrite) {
    return new UserWriteRecord(writeId, path, compoundWrite);
  }

  private void runInTransaction(PersistenceStorageEngine engine, Runnable r) {
    try {
      engine.beginTransaction();
      r.run();
      engine.setTransactionSuccessful();
    } finally {
      engine.endTransaction();
    }
  }

  private void overwriteServerCache(
      final PersistenceStorageEngine engine, final Path path, final Node node) {
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.overwriteServerCache(path, node);
          }
        });
  }

  private void mergeIntoServerCache(
      final PersistenceStorageEngine engine, final Path path, final CompoundWrite merge) {
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.mergeIntoServerCache(path, merge);
          }
        });
  }

  private void saveUserOverwrite(
      final PersistenceStorageEngine engine, final Path path, final Node node, final long id) {
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.saveUserOverwrite(path, node, id);
          }
        });
  }

  private void saveUserMerge(
      final PersistenceStorageEngine engine,
      final Path path,
      final CompoundWrite children,
      final long id) {
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.saveUserMerge(path, children, id);
          }
        });
  }

  private void saveTrackedQuery(final PersistenceStorageEngine engine, final TrackedQuery query) {
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.saveTrackedQuery(query);
          }
        });
  }

  private void saveTrackedQueryKeys(
      final PersistenceStorageEngine engine, final long id, final Set<ChildKey> childKeys) {
    runInTransaction(
        engine,
        new Runnable() {
          @Override
          public void run() {
            engine.saveTrackedQueryKeys(id, childKeys);
          }
        });
  }
}
