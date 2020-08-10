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

import static com.google.firebase.database.core.utilities.Utilities.hardAssert;

import com.google.firebase.database.core.Path;
import com.google.firebase.database.core.view.Change;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;

/** Doesn't really filter nodes but applies an index to the node and keeps track of any changes */
public class IndexedFilter implements NodeFilter {
  private final Index index;

  public IndexedFilter(Index index) {
    this.index = index;
  }

  @Override
  public IndexedNode updateChild(
      IndexedNode indexedNode,
      ChildKey key,
      Node newChild,
      Path affectedPath,
      CompleteChildSource source,
      ChildChangeAccumulator optChangeAccumulator) {
    hardAssert(indexedNode.hasIndex(this.index), "The index must match the filter");
    Node snap = indexedNode.getNode();
    Node oldChild = snap.getImmediateChild(key);
    // Check if anything actually changed.
    if (oldChild.getChild(affectedPath).equals(newChild.getChild(affectedPath))) {
      // There's an edge case where a child can enter or leave the view because affectedPath was
      // set to null. In this case, affectedPath will appear null in both the old and new snapshots.
      // So we need to avoid treating these cases as "nothing changed."
      if (oldChild.isEmpty() == newChild.isEmpty()) {
        // Nothing changed.

        // This assert should be valid, but it's expensive (can dominate perf testing) so don't
        // actually do it.
        // assert oldChild.equals(newChild): "Old and new snapshots should be equal.";
        return indexedNode;
      }
    }
    if (optChangeAccumulator != null) {
      if (newChild.isEmpty()) {
        if (snap.hasChild(key)) {
          optChangeAccumulator.trackChildChange(Change.childRemovedChange(key, oldChild));
        } else {
          hardAssert(
              snap.isLeafNode(),
              "A child remove without an old child only makes sense on a leaf node");
        }
      } else if (oldChild.isEmpty()) {
        optChangeAccumulator.trackChildChange(Change.childAddedChange(key, newChild));
      } else {
        optChangeAccumulator.trackChildChange(Change.childChangedChange(key, newChild, oldChild));
      }
    }
    if (snap.isLeafNode() && newChild.isEmpty()) {
      return indexedNode;
    } else {
      // Make sure the node is indexed
      return indexedNode.updateChild(key, newChild);
    }
  }

  @Override
  public IndexedNode updateFullNode(
      IndexedNode oldSnap, IndexedNode newSnap, ChildChangeAccumulator optChangeAccumulator) {
    hardAssert(
        newSnap.hasIndex(this.index), "Can't use IndexedNode that doesn't have filter's index");
    if (optChangeAccumulator != null) {
      for (NamedNode child : oldSnap.getNode()) {
        if (!newSnap.getNode().hasChild(child.getName())) {
          optChangeAccumulator.trackChildChange(
              Change.childRemovedChange(child.getName(), child.getNode()));
        }
      }
      if (!newSnap.getNode().isLeafNode()) {
        for (NamedNode child : newSnap.getNode()) {
          if (oldSnap.getNode().hasChild(child.getName())) {
            Node oldChild = oldSnap.getNode().getImmediateChild(child.getName());
            if (!oldChild.equals(child.getNode())) {
              optChangeAccumulator.trackChildChange(
                  Change.childChangedChange(child.getName(), child.getNode(), oldChild));
            }
          } else {
            optChangeAccumulator.trackChildChange(
                Change.childAddedChange(child.getName(), child.getNode()));
          }
        }
      }
    }
    return newSnap;
  }

  @Override
  public IndexedNode updatePriority(IndexedNode oldSnap, Node newPriority) {
    if (oldSnap.getNode().isEmpty()) {
      return oldSnap;
    } else {
      return oldSnap.updatePriority(newPriority);
    }
  }

  @Override
  public NodeFilter getIndexedFilter() {
    return this;
  }

  @Override
  public Index getIndex() {
    return this.index;
  }

  @Override
  public boolean filtersNodes() {
    return false;
  }
}
