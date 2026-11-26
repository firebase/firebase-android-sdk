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

package com.google.firebase.database.core;

import static com.google.firebase.database.snapshot.NodeUtilities.NodeFromJSON;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.ListBuilder;
import com.google.firebase.database.MapBuilder;
import com.google.firebase.database.ValueEventListener;
import com.google.firebase.database.connection.CompoundHash;
import com.google.firebase.database.connection.ListenHashProvider;
import com.google.firebase.database.core.persistence.NoopPersistenceManager;
import com.google.firebase.database.core.view.QuerySpec;
import com.google.firebase.database.logging.DefaultLogger;
import com.google.firebase.database.logging.Logger;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Map;
import org.junit.Test;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Tests that the hashes sent with a listen can be computed when the server cache contains data
 * that the server shouldn't send, such as a priority that is an object.
 */
@org.junit.runner.RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class SyncTreeListenHashTest {

  private static final Path ITEMS = new Path("items");

  private static final Map<String, Object> SERVER_TIMESTAMP =
      new MapBuilder().put(".sv", "timestamp").build();

  private static final ValueEventListener NOOP_LISTENER =
      new ValueEventListener() {
        @Override
        public void onDataChange(@NonNull DataSnapshot snapshot) {}

        @Override
        public void onCancelled(@NonNull DatabaseError error) {}
      };

  /** Records the hash provider that the sync tree passes when it starts a listen. */
  private static class RecordingListenProvider implements SyncTree.ListenProvider {
    private ListenHashProvider hashProvider;

    @Override
    public void startListening(
        QuerySpec query,
        Tag tag,
        ListenHashProvider hash,
        SyncTree.CompletionListener onListenComplete) {
      hashProvider = hash;
    }

    @Override
    public void stopListening(QuerySpec query, Tag tag) {}
  }

  /**
   * Listens at "items", receives enough server data for the listen to include a compound hash, and
   * then receives the given server overwrite. Returns the hash provider of the listen, which is
   * used again whenever the listen is sent, for example after every reconnect.
   */
  private static ListenHashProvider listenAndApplyServerOverwrite(Path path, Node node) {
    DatabaseConfig config = new DatabaseConfig();
    config.setLogger(new DefaultLogger(Logger.Level.DEBUG, null));
    RecordingListenProvider listenProvider = new RecordingListenProvider();
    SyncTree syncTree = new SyncTree(config, new NoopPersistenceManager(), listenProvider);
    syncTree.addEventRegistration(
        new ValueEventRegistration(
            /* repo= */ null, NOOP_LISTENER, QuerySpec.defaultQueryAtPath(ITEMS)));

    Node items = EmptyNode.Empty();
    for (int i = 0; i < 100; i++) {
      items =
          items.updateImmediateChild(ChildKey.fromString("key-" + i), NodeFromJSON("value-" + i));
    }
    syncTree.applyServerOverwrite(ITEMS, items);
    syncTree.applyServerOverwrite(path, node);

    assertNotNull(listenProvider.hashProvider);
    return listenProvider.hashProvider;
  }

  private static void assertListenHashesAreComputed(ListenHashProvider hashProvider) {
    assertNotNull(hashProvider.getSimpleHash());
    assertTrue(hashProvider.shouldIncludeCompoundHash());
    CompoundHash compoundHash = hashProvider.getCompoundHash();
    assertTrue(compoundHash.getHashes().size() > 1);
    assertEquals("", compoundHash.getHashes().get(compoundHash.getHashes().size() - 1));
  }

  @Test
  public void listenHashesToleratePriorityThatIsAnObject() {
    ListenHashProvider hashProvider =
        listenAndApplyServerOverwrite(
            new Path("items/key-1/.priority"),
            NodeFromJSON(new MapBuilder().put("k", "v").build()));

    assertListenHashesAreComputed(hashProvider);
  }

  @Test
  public void listenHashesToleratePriorityThatIsAnArray() {
    ListenHashProvider hashProvider =
        listenAndApplyServerOverwrite(
            new Path("items/key-1/.priority"),
            NodeFromJSON(new ListBuilder().put("a").put("b").build()));

    assertListenHashesAreComputed(hashProvider);
  }

  @Test
  public void listenHashesToleratePriorityThatIsAnObjectAtListenPath() {
    ListenHashProvider hashProvider =
        listenAndApplyServerOverwrite(
            new Path("items/.priority"), NodeFromJSON(new MapBuilder().put("k", "v").build()));

    assertListenHashesAreComputed(hashProvider);
  }

  @Test
  public void listenHashesTolerateServerValue() {
    ListenHashProvider hashProvider =
        listenAndApplyServerOverwrite(new Path("items/key-1"), NodeFromJSON(SERVER_TIMESTAMP));

    assertListenHashesAreComputed(hashProvider);
  }

  @Test
  public void listenHashesToleratePriorityThatIsAServerValue() {
    ListenHashProvider hashProvider =
        listenAndApplyServerOverwrite(
            new Path("items/key-1/.priority"), NodeFromJSON(SERVER_TIMESTAMP));

    assertListenHashesAreComputed(hashProvider);
  }
}
