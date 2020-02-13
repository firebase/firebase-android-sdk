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
import static com.google.firebase.database.IntegrationTestHelpers.leafNodeOfSize;
import static com.google.firebase.database.IntegrationTestHelpers.node;
import static com.google.firebase.database.IntegrationTestHelpers.path;
import static org.junit.Assert.assertEquals;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import com.google.firebase.database.RetryRule;
import com.google.firebase.database.android.SqlPersistenceStorageEngine;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Set;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

@org.junit.runner.RunWith(AndroidJUnit4.class)
public class PruningTest {
  @Rule public RetryRule retryRule = new RetryRule(3);

  private static final Node EMPTY_NODE = EmptyNode.Empty();
  private static final Node LARGE_NODE = leafNodeOfSize(5 * 1024 * 1024);
  private static final Node ABC_NODE = node("{ 'a': { 'aa': 1.1, 'ab': 1.2 }, 'b': 2, 'c': 3 }");
  private static final Node A_NODE =
      EMPTY_NODE.updateChild(path("a"), ABC_NODE.getChild(path("a")));
  private static final Node BC_NODE = ABC_NODE.updateChild(path("a"), EMPTY_NODE);
  private static final Node DEF_NODE = node("{ 'd': 4, 'e': 5, 'f': 6 }");
  private static final Node D_NODE =
      EMPTY_NODE.updateChild(path("d"), DEF_NODE.getChild(path("d")));
  private static final PruneForestBuilder PF = new PruneForestBuilder();
  private TestEnv env;

  private static class TestEnv {
    public final SqlPersistenceStorageEngine engine;

    public TestEnv() {
      engine = getCleanStorageEngine();
    }

    public void writeToCache(final String path, final Node node) {
      runInTransaction(
          engine,
          new Runnable() {
            @Override
            public void run() {
              engine.overwriteServerCache(new Path(path), node);
            }
          });
    }

    public void pruneCache(final String locationToPrune, final PruneForestBuilder pruneForest) {
      runInTransaction(
          engine,
          new Runnable() {
            @Override
            public void run() {
              engine.pruneCache(new Path(locationToPrune), pruneForest.get());
            }
          });
    }

    public Node cache(String pathString) {
      return engine.serverCache(new Path(pathString));
    }

    public void close() {
      engine.close();
    }

    private SqlPersistenceStorageEngine getCleanStorageEngine() {
      DatabaseConfig ctx = new DatabaseConfig();
      ctx.setLogger(new DefaultLogger(Logger.Level.DEBUG, null));
      ctx.setLogLevel(com.google.firebase.database.Logger.Level.DEBUG);
      final SqlPersistenceStorageEngine engine =
          new SqlPersistenceStorageEngine(
              InstrumentationRegistry.getInstrumentation().getTargetContext(),
              ctx,
              "test-namespace");
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

    private void runInTransaction(PersistenceStorageEngine engine, Runnable r) {
      try {
        engine.beginTransaction();
        r.run();
        engine.setTransactionSuccessful();
      } finally {
        engine.endTransaction();
      }
    }
  }

  private static class PruneForestBuilder {
    private final PruneForest pruneForest;

    public PruneForestBuilder() {
      this(new PruneForest());
    }

    public PruneForestBuilder(PruneForest pruneForest) {
      this.pruneForest = pruneForest;
    }

    public PruneForestBuilder prune(String prunePathString) {
      return new PruneForestBuilder(pruneForest.prune(new Path(prunePathString)));
    }

    public PruneForestBuilder pruneExcept(String prunePathString, Set<String> keepPathStrings) {
      Path prunePath = new Path(prunePathString);
      PruneForest newPruneForest = pruneForest;
      newPruneForest = newPruneForest.prune(prunePath);
      for (String keepPathString : keepPathStrings) {
        newPruneForest = newPruneForest.keep(prunePath.child(new Path(keepPathString)));
      }
      return new PruneForestBuilder(newPruneForest);
    }

    public PruneForest get() {
      return pruneForest;
    }
  }

  @Before
  public void before() {
    env = new TestEnv();
  }

  @After
  public void after() {
    env.close();
  }

  // Write document at root, prune it.
  @Test
  public void test010() {
    env.writeToCache("", ABC_NODE);
    env.pruneCache("", PF.prune(""));
    assertEquals(EMPTY_NODE, env.cache(""));
  }

  // Write document at /x, prune it via PruneForest for /x, at root.
  @Test
  public void test020() {
    env.writeToCache("x", ABC_NODE);
    env.pruneCache("", PF.prune("x"));
    assertEquals(EMPTY_NODE, env.cache("x"));
  }

  // Write document at /x, prune it via PruneForest for root, at /x.
  @Test
  public void test030() {
    env.writeToCache("x", ABC_NODE);
    env.pruneCache("x", PF.prune(""));
    assertEquals(EMPTY_NODE, env.cache("x"));
  }

  // Write document at /x, prune it via PruneForest for root, at root
  @Test
  public void test040() {
    env.writeToCache("x", ABC_NODE);
    env.pruneCache("", PF.prune(""));
    assertEquals(EMPTY_NODE, env.cache("x"));
  }

  // Write document at /x/y, prune it via PruneForest for /y, at /x
  @Test
  public void test050() {
    env.writeToCache("x/y", ABC_NODE);
    env.pruneCache("x", PF.prune("y"));
    assertEquals(EMPTY_NODE, env.cache("x/y"));
  }

  // Write abc at /x/y, prune /x/y except b,c via PruneForest for /x/y -b,c, at root
  @Test
  public void test060() {
    env.writeToCache("x/y", ABC_NODE);
    env.pruneCache("", PF.pruneExcept("x/y", asSet("b", "c")));
    assertEquals(BC_NODE, env.cache("x/y"));
  }

  // Write abc at /x/y, prune /x/y except b,c via PruneForest for /y -b,c, at /x
  @Test
  public void test070() {
    env.writeToCache("x/y", ABC_NODE);
    env.pruneCache("x", PF.pruneExcept("y", asSet("b", "c")));
    assertEquals(BC_NODE, env.cache("x/y"));
  }

  // Write abc at /x/y, prune /x/y except not-there via PruneForest for /x/y -d, at root
  @Test
  public void test080() {
    env.writeToCache("x/y", ABC_NODE);
    env.pruneCache("", PF.pruneExcept("x/y", asSet("not-there")));
    assertEquals(EMPTY_NODE, env.cache("x/y"));
  }

  // Write abc at / and def at /a, prune all via PruneForest for / at root
  @Test
  public void test090() {
    env.writeToCache("", ABC_NODE);
    env.writeToCache("a", DEF_NODE);
    env.pruneCache("", PF.prune(""));
    assertEquals(EMPTY_NODE, env.cache(""));
  }

  // Write abc at / and def at /a, prune all except b,c via PruneForest for root -b,c, at root
  @Test
  public void test100() {
    env.writeToCache("", ABC_NODE);
    env.writeToCache("a", DEF_NODE);
    env.pruneCache("", PF.pruneExcept("", asSet("b", "c")));
    assertEquals(BC_NODE, env.cache(""));
  }

  // Write abc at /x and def at /x/a, prune /x except b,c via PruneForest for /x -b,c, at root
  @Test
  public void test110() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", DEF_NODE);
    env.pruneCache("", PF.pruneExcept("x", asSet("b", "c")));
    assertEquals(BC_NODE, env.cache("x"));
  }

  // Write abc at /x and def at /x/a, prune /x except b,c via PruneForest for root -b,c, at /x
  @Test
  public void test120() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", DEF_NODE);
    env.pruneCache("x", PF.pruneExcept("", asSet("b", "c")));
    assertEquals(BC_NODE, env.cache("x"));
  }

  // Write abc at /x and def at /x/a, prune /x except a via PruneForest for /x -a, at root
  @Test
  public void test130() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", DEF_NODE);
    assertEquals(ABC_NODE.updateChild(path("a"), DEF_NODE), env.cache("x"));
    env.pruneCache("", PF.pruneExcept("x", asSet("a")));
    assertEquals(EMPTY_NODE.updateChild(path("a"), DEF_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a, prune /x except a via PruneForest for root -a, at /x
  @Test
  public void test140() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", DEF_NODE);
    env.pruneCache("x", PF.pruneExcept("", asSet("a")));
    assertEquals(EMPTY_NODE.updateChild(path("a"), DEF_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a, prune /x except a/d via PruneForest for /x -a/d, at root
  @Test
  public void test150() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", DEF_NODE);
    env.pruneCache("", PF.pruneExcept("x", asSet("a/d")));
    assertEquals(EMPTY_NODE.updateChild(path("a"), D_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a, prune /x except a/d via PruneForest for / -a/d, at /x
  @Test
  public void test160() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", DEF_NODE);
    env.pruneCache("x", PF.pruneExcept("", asSet("a/d")));
    assertEquals(EMPTY_NODE.updateChild(path("a"), D_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a/aa, prune /x except a via PruneForest for /x -a, at root
  @Test
  public void test170() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a/aa", DEF_NODE);
    env.pruneCache("", PF.pruneExcept("x", asSet("a")));
    assertEquals(A_NODE.updateChild(path("a/aa"), DEF_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a/aa, prune /x except a via PruneForest for / -a, at /x
  @Test
  public void test180() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a/aa", DEF_NODE);
    env.pruneCache("x", PF.pruneExcept("", asSet("a")));
    assertEquals(A_NODE.updateChild(path("a/aa"), DEF_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a/aa, prune /x except a/aa via PruneForest for /x -a/aa, at root
  @Test
  public void test190() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a/aa", DEF_NODE);
    env.pruneCache("", PF.pruneExcept("x", asSet("a/aa")));
    assertEquals(EMPTY_NODE.updateChild(path("a/aa"), DEF_NODE), env.cache("x"));
  }

  // Write abc at /x and def at /x/a/aa, prune /x except a/aa via PruneForest for / -a/aa, at /x
  @Test
  public void test200() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a/aa", DEF_NODE);
    env.pruneCache("x", PF.pruneExcept("", asSet("a/aa")));
    assertEquals(EMPTY_NODE.updateChild(path("a/aa"), DEF_NODE), env.cache("x"));
  }

  // Write large node at /x, prune x via PruneForest for x at root
  @Test
  public void test210() {
    env.writeToCache("x", LARGE_NODE);
    env.pruneCache("", PF.prune("x"));
    assertEquals(EMPTY_NODE, env.cache("x"));
  }

  // Write abc at x and large node at /x/a, prune x except a via PruneForest for / -a, at x
  @Test
  public void test220() {
    env.writeToCache("x", ABC_NODE);
    env.writeToCache("x/a", LARGE_NODE);
    env.pruneCache("x", PF.pruneExcept("", asSet("a")));
    assertEquals(EMPTY_NODE.updateChild(path("a"), LARGE_NODE), env.cache("x"));
  }

  // This isn't an expected situation so we ignore documents above a prune location.
  // Write abc at /, prune /a via PruneForest for root, at /a
  @Test
  public void testPruningBelowDocuments1() {
    env.writeToCache("", ABC_NODE);
    env.pruneCache("a", PF.prune(""));
    assertEquals(ABC_NODE, env.cache(""));
  }

  // This isn't an expected situation so we currently ignore documents above a prune location.
  // Write abc at / and def at /a, prune /a via PruneForest for root, at /a
  @Test
  public void testPruningBelowDocuments2() {
    env.writeToCache("", ABC_NODE);
    env.writeToCache("a", DEF_NODE);
    env.pruneCache("a", PF.prune(""));
    // DEF gets pruned, but not ABC.
    assertEquals(ABC_NODE, env.cache(""));
  }
}
