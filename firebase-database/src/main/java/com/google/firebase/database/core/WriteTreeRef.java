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

import com.google.firebase.database.core.view.CacheNode;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import java.util.Collections;
import java.util.List;

/**
 * A WriteTreeRef wraps a WriteTree and a path, for convenient access to a particular subtree. All
 * of the methods just proxy to the underlying WriteTree.
 */
public class WriteTreeRef {

  /**
   * The path to this particular write tree ref. Used for calling methods on writeTree_ while
   * exposing a simpler interface to callers.
   */
  private final Path treePath;

  /**
   * A reference to the actual tree of write data. All methods are pass-through to the tree, but
   * with the appropriate path prefixed.
   *
   * <p>This lets us make cheap references to points in the tree for sync points without having to
   * copy and maintain all of the data.
   */
  private final WriteTree writeTree;

  public WriteTreeRef(Path path, WriteTree writeTree) {
    this.treePath = path;
    this.writeTree = writeTree;
  }

  /**
   * If possible, returns a complete event cache, using the underlying server data if possible. In
   * addition, can be used to get a cache that includes hidden writes, and excludes arbitrary
   * writes. Note that customizing the returned node can lead to a more expensive calculation.
   */
  public Node calcCompleteEventCache(Node completeServerCache) {
    return this.calcCompleteEventCache(completeServerCache, Collections.<Long>emptyList());
  }

  public Node calcCompleteEventCache(Node completeServerCache, List<Long> writeIdsToExclude) {
    return this.calcCompleteEventCache(completeServerCache, writeIdsToExclude, false);
  }

  public Node calcCompleteEventCache(
      Node completeServerCache, List<Long> writeIdsToExclude, boolean includeHiddenWrites) {
    return this.writeTree.calcCompleteEventCache(
        this.treePath, completeServerCache, writeIdsToExclude, includeHiddenWrites);
  }

  /**
   * If possible, returns a children node containing all of the complete children we have data for.
   * The returned data is a mix of the given server data and write data.
   */
  public Node calcCompleteEventChildren(Node completeServerChildren) {
    return this.writeTree.calcCompleteEventChildren(this.treePath, completeServerChildren);
  }

  /**
   * Given that either the underlying server data has updated or the outstanding writes have
   * updated, determine what, if anything, needs to be applied to the event cache.
   *
   * <p>Possibilities:
   *
   * <p>1. No writes are shadowing. Events should be raised, the snap to be applied comes from the
   * server data
   *
   * <p>2. Some write is completely shadowing. No events to be raised
   *
   * <p>3. Is partially shadowed. Events should be raised
   *
   * <p>Either existingEventSnap or existingServerSnap must exist, this is validated via an assert
   */
  public Node calcEventCacheAfterServerOverwrite(
      Path path, Node existingEventSnap, Node existingServerSnap) {
    return this.writeTree.calcEventCacheAfterServerOverwrite(
        this.treePath, path, existingEventSnap, existingServerSnap);
  }

  /**
   * Returns a node if there is a complete overwrite for this path. More specifically, if there is a
   * write at a higher path, this will return the child of that write relative to the write and this
   * path. Returns null if there is no write at this path.
   */
  public Node shadowingWrite(Path path) {
    return this.writeTree.shadowingWrite(this.treePath.child(path));
  }

  /**
   * This method is used when processing child remove events on a query. If we can, we pull in
   * children that were outside the window, but may now be in the window
   */
  public NamedNode calcNextNodeAfterPost(
      Node completeServerData, NamedNode startPost, boolean reverse, Index index) {
    return this.writeTree.calcNextNodeAfterPost(
        this.treePath, completeServerData, startPost, reverse, index);
  }

  /**
   * Returns a complete child for a given server snap after applying all user writes or null if
   * there is no complete child for this ChildKey.
   */
  public Node calcCompleteChild(ChildKey childKey, CacheNode existingServerCache) {
    return this.writeTree.calcCompleteChild(this.treePath, childKey, existingServerCache);
  }

  /** Return a WriteTreeRef for a child. */
  public WriteTreeRef child(ChildKey childKey) {
    return new WriteTreeRef(this.treePath.child(childKey), this.writeTree);
  }
}
