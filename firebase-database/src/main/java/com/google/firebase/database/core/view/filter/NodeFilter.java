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

package com.google.firebase.database.core.view.filter;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;

/**
 * NodeFilter is used to update nodes and complete children of nodes while applying queries on the
 * fly and keeping track of any child changes. This class does not track value changes as value
 * changes depend on more than just the node itself. Different kind of queries require different
 * kind of implementations of this interface.
 */
public interface NodeFilter {
  /**
   * Update a single complete child in the snap. If the child equals the old child in the snap, this
   * is a no-op. The method expects an indexed snap.
   */
  public IndexedNode updateChild(
      IndexedNode node,
      ChildKey key,
      Node newChild,
      Path affectedPath,
      CompleteChildSource source,
      ChildChangeAccumulator optChangeAccumulator);

  /** Update a node in full and output any resulting change from this complete update. */
  public IndexedNode updateFullNode(
      IndexedNode oldSnap, IndexedNode newSnap, ChildChangeAccumulator optChangeAccumulator);

  /** Update the priority of the root node */
  public IndexedNode updatePriority(IndexedNode oldSnap, Node newPriority);

  /** Returns true if children might be filtered due to query criteria */
  public boolean filtersNodes();

  /**
   * Returns the index filter that this filter uses to get a NodeFilter that doesn't filter any
   * children.
   */
  public NodeFilter getIndexedFilter();

  /** Returns the index that this filter uses */
  public Index getIndex();

  /**
   * Since updates to filtered nodes might require nodes to be pulled in from "outside" the node,
   * this interface can help to get complete children that can be pulled in. A class implementing
   * this interface takes potentially multiple sources (e.g. user writes, server data from other
   * views etc.) to try it's best to get a complete child that might be useful in pulling into the
   * view.
   */
  public static interface CompleteChildSource {
    public Node getCompleteChild(ChildKey childKey);

    public NamedNode getChildAfterChild(Index index, NamedNode child, boolean reverse);
  }
}
