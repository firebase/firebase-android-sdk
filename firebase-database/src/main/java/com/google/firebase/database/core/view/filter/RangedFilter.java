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
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PriorityUtilities;

/** Filters nodes by range and uses an IndexFilter to track any changes after filtering the node */
public class RangedFilter implements NodeFilter {
  private final IndexedFilter indexedFilter;
  private final Index index;
  private final NamedNode startPost;
  private final NamedNode endPost;

  public RangedFilter(QueryParams params) {
    this.indexedFilter = new IndexedFilter(params.getIndex());
    this.index = params.getIndex();
    this.startPost = getStartPost(params);
    this.endPost = getEndPost(params);
  }

  public NamedNode getStartPost() {
    return this.startPost;
  }

  public NamedNode getEndPost() {
    return this.endPost;
  }

  private static NamedNode getStartPost(QueryParams params) {
    if (params.hasStart()) {
      ChildKey startName = params.getIndexStartName();
      return params.getIndex().makePost(startName, params.getIndexStartValue());
    } else {
      return params.getIndex().minPost();
    }
  }

  private static NamedNode getEndPost(QueryParams params) {
    if (params.hasEnd()) {
      ChildKey endName = params.getIndexEndName();
      return params.getIndex().makePost(endName, params.getIndexEndValue());
    } else {
      return params.getIndex().maxPost();
    }
  }

  public boolean matches(NamedNode node) {
    if (this.index.compare(this.getStartPost(), node) <= 0
        && this.index.compare(node, this.getEndPost()) <= 0) {
      return true;
    } else {
      return false;
    }
  }

  @Override
  public IndexedNode updateChild(
      IndexedNode snap,
      ChildKey key,
      Node newChild,
      Path affectedPath,
      CompleteChildSource source,
      ChildChangeAccumulator optChangeAccumulator) {
    if (!matches(new NamedNode(key, newChild))) {
      newChild = EmptyNode.Empty();
    }
    return indexedFilter.updateChild(
        snap, key, newChild, affectedPath, source, optChangeAccumulator);
  }

  @Override
  public IndexedNode updateFullNode(
      IndexedNode oldSnap, IndexedNode newSnap, ChildChangeAccumulator optChangeAccumulator) {
    IndexedNode filtered;
    if (newSnap.getNode().isLeafNode()) {
      // Make sure we have a children node with the correct index, not an empty or leaf node;
      filtered = IndexedNode.from(EmptyNode.Empty(), this.index);
    } else {
      // Don't support priorities on queries
      filtered = newSnap.updatePriority(PriorityUtilities.NullPriority());
      for (NamedNode child : newSnap) {
        if (!matches(child)) {
          filtered = filtered.updateChild(child.getName(), EmptyNode.Empty());
        }
      }
    }
    return indexedFilter.updateFullNode(oldSnap, filtered, optChangeAccumulator);
  }

  @Override
  public IndexedNode updatePriority(IndexedNode oldSnap, Node newPriority) {
    // Don't support priorities on queries
    return oldSnap;
  }

  @Override
  public NodeFilter getIndexedFilter() {
    return this.indexedFilter;
  }

  @Override
  public Index getIndex() {
    return this.index;
  }

  @Override
  public boolean filtersNodes() {
    return true;
  }
}
