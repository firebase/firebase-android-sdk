// Copyright 2026 Google LLC
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

package com.google.firebase.database.android;

import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.database.ListBuilder;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.core.DatabaseConfig;
import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.utilities.NodeSizeEstimator;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.CompoundHash;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Map;
import java.util.UUID;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests that server data that the server shouldn't send, such as a priority that is an object, can
 * be saved to and loaded from the persistence cache, and that the hashes sent with a listen can be
 * computed from the loaded data. The cache is loaded again at every launch.
 */
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SqlPersistenceInvalidPriorityTest {

  private static final Map<String, Object> SERVER_TIMESTAMP =
      new MapBuilder().put(".sv", "timestamp").build();

  private SqlPersistenceStorageEngine engine;

  @Before
  public void setUp() {
    DatabaseConfig config = new DatabaseConfig();
    config.setLogger(new DefaultLogger(Logger.Level.DEBUG, null));
    engine =
        new SqlPersistenceStorageEngine(
            ApplicationProvider.getApplicationContext(), config, "test-" + UUID.randomUUID());
  }

  @After
  public void tearDown() {
    engine.close();
  }

  private void overwriteServerCache(Path path, Node node) {
    engine.beginTransaction();
    try {
      engine.overwriteServerCache(path, node);
      engine.setTransactionSuccessful();
    } finally {
      engine.endTransaction();
    }
  }

  private Node loadServerCache(Path path) {
    engine.beginTransaction();
    try {
      Node node = engine.serverCache(path);
      engine.setTransactionSuccessful();
      return node;
    } finally {
      engine.endTransaction();
    }
  }

  /**
   * Saves enough server data at "items" for a listen at "items" to include a compound hash, then
   * saves the given server overwrite and loads "items" again. Returns the loaded node after
   * checking that it matches the server data.
   */
  private Node saveAndLoadItemsWithServerOverwrite(Path path, Node node) {
    Path itemsPath = new Path("items");
    Node items = EmptyNode.Empty();
    for (int i = 0; i < 100; i++) {
      items =
          items.updateImmediateChild(ChildKey.fromString("key-" + i), NodeFromJSON("value-" + i));
    }
    overwriteServerCache(itemsPath, items);
    overwriteServerCache(path, node);

    Node loaded = loadServerCache(itemsPath);

    assertEquals(items.updateChild(Path.getRelative(itemsPath, path), node), loaded);
    return loaded;
  }

  private static void assertListenHashesAreComputed(Node node) {
    assertTrue(NodeSizeEstimator.estimateSerializedNodeSize(node) > 1024);
    assertNotNull(node.getHash());
    CompoundHash compoundHash = CompoundHash.fromNode(node);
    assertTrue(compoundHash.getHashes().size() > 1);
  }

  @Test
  public void priorityThatIsAnObjectIsSavedAndLoaded() {
    Node loaded =
        saveAndLoadItemsWithServerOverwrite(
            new Path("items/key-1/.priority"),
            NodeFromJSON(new MapBuilder().put("k", "v").build()));

    assertListenHashesAreComputed(loaded);
  }

  @Test
  public void priorityThatIsAnArrayIsSavedAndLoaded() {
    Node loaded =
        saveAndLoadItemsWithServerOverwrite(
            new Path("items/key-1/.priority"),
            NodeFromJSON(new ListBuilder().put("a").put("b").build()));

    assertListenHashesAreComputed(loaded);
  }

  @Test
  public void serverValueIsSavedAndLoaded() {
    Node loaded =
        saveAndLoadItemsWithServerOverwrite(
            new Path("items/key-1"), NodeFromJSON(SERVER_TIMESTAMP));

    assertListenHashesAreComputed(loaded);
  }

  @Test
  public void priorityThatIsAServerValueIsSavedAndLoaded() {
    Node loaded =
        saveAndLoadItemsWithServerOverwrite(
            new Path("items/key-1/.priority"), NodeFromJSON(SERVER_TIMESTAMP));

    assertListenHashesAreComputed(loaded);
  }
}
