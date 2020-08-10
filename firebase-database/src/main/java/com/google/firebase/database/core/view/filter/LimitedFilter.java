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
import com.google.firebase.database.core.view.QueryParams;
import com.google.firebase.database.snapshot.ChildKey;
import com.google.firebase.database.snapshot.EmptyNode;
import com.google.firebase.database.snapshot.Index;
import com.google.firebase.database.snapshot.IndexedNode;
import com.google.firebase.database.snapshot.NamedNode;
import com.google.firebase.database.snapshot.Node;
import com.google.firebase.database.snapshot.PriorityUtilities;
import java.util.Iterator;

/**
 * Applies a limit and a range to a node and uses RangedFilter to do the heavy lifting where
 * possible
 */
public class LimitedFilter implements NodeFilter {
  private final RangedFilter rangedFilter;
  private final Index index;
  private final int limit;
  private final boolean reverse;

  public LimitedFilter(QueryParams params) {
    this.rangedFilter = new RangedFilter(params);
    this.index = params.getIndex();
    this.limit = params.getLimit();
    this.reverse = !params.isViewFromLeft();
  }

  @Override
  public IndexedNode updateChild(
      IndexedNode snap,
      ChildKey key,
      Node newChild,
      Path affectedPath,
      CompleteChildSource source,
      ChildChangeAccumulator optChangeAccumulator) {
    if (!rangedFilter.matches(new NamedNode(key, newChild))) {
      newChild = EmptyNode.Empty();
    }
    if (snap.getNode().getImmediateChild(key).equals(newChild)) {
      // No change
      return snap;
    } else if (snap.getNode().getChildCount() < this.limit) {
      return rangedFilter
          .getIndexedFilter()
          .updateChild(snap, key, newChild, affectedPath, source, optChangeAccumulator);
    } else {
      return fullLimitUpdateChild(snap, key, newChild, source, optChangeAccumulator);
    }
  }

  private IndexedNode fullLimitUpdateChild(
      IndexedNode oldIndexed,
      ChildKey childKey,
      Node childSnap,
      CompleteChildSource source,
      ChildChangeAccumulator optChangeAccumulator) {
    // TODO: rename all cache stuff etc to general snap terminology
    hardAssert(oldIndexed.getNode().getChildCount() == this.limit);
    NamedNode newChildNamedNode = new NamedNode(childKey, childSnap);
    NamedNode windowBoundary =
        this.reverse ? oldIndexed.getFirstChild() : oldIndexed.getLastChild();
    boolean inRange = rangedFilter.matches(newChildNamedNode);
    if (oldIndexed.getNode().hasChild(childKey)) {
      Node oldChildSnap = oldIndexed.getNode().getImmediateChild(childKey);
      NamedNode nextChild = source.getChildAfterChild(this.index, windowBoundary, this.reverse);
      while (nextChild != null
          && (nextChild.getName().equals(childKey)
              || oldIndexed.getNode().hasChild(nextChild.getName()))) {
        // There is a weird edge case where a node is updated as part of a merge in the write tree,
        // but hasn't been applied to the limited filter yet. Ignore this next child which will be
        // updated later in the limited filter...
        nextChild = source.getChildAfterChild(this.index, nextChild, this.reverse);
      }
      int compareNext =
          nextChild == null ? 1 : index.compare(nextChild, newChildNamedNode, this.reverse);
      boolean remainsInWindow = inRange && !childSnap.isEmpty() && compareNext >= 0;
      if (remainsInWindow) {
        if (optChangeAccumulator != null) {
          optChangeAccumulator.trackChildChange(
              Change.childChangedChange(childKey, childSnap, oldChildSnap));
        }
        return oldIndexed.updateChild(childKey, childSnap);
      } else {
        if (optChangeAccumulator != null) {
          optChangeAccumulator.trackChildChange(Change.childRemovedChange(childKey, oldChildSnap));
        }
        IndexedNode newIndexed = oldIndexed.updateChild(childKey, EmptyNode.Empty());
        boolean nextChildInRange = nextChild != null && rangedFilter.matches(nextChild);
        if (nextChildInRange) {
          if (optChangeAccumulator != null) {
            optChangeAccumulator.trackChildChange(
                Change.childAddedChange(nextChild.getName(), nextChild.getNode()));
          }
          return newIndexed.updateChild(nextChild.getName(), nextChild.getNode());
        } else {
          return newIndexed;
        }
      }
    } else if (childSnap.isEmpty()) {
      // we're deleting a node, but it was not in the window, so ignore it
      return oldIndexed;
    } else if (inRange) {
      if (this.index.compare(windowBoundary, newChildNamedNode, this.reverse) >= 0) {
        if (optChangeAccumulator != null) {
          optChangeAccumulator.trackChildChange(
              Change.childRemovedChange(windowBoundary.getName(), windowBoundary.getNode()));
          optChangeAccumulator.trackChildChange(Change.childAddedChange(childKey, childSnap));
        }
        return oldIndexed
            .updateChild(childKey, childSnap)
            .updateChild(windowBoundary.getName(), EmptyNode.Empty());
      } else {
        return oldIndexed;
      }
    } else {
      return oldIndexed;
    }
  }

  @Override
  public IndexedNode updateFullNode(
      IndexedNode oldSnap, IndexedNode newSnap, ChildChangeAccumulator optChangeAccumulator) {
    IndexedNode filtered;
    if (newSnap.getNode().isLeafNode() || newSnap.getNode().isEmpty()) {
      // Make sure we have a children node with the correct index, not an empty or leaf node;
      filtered = IndexedNode.from(EmptyNode.Empty(), this.index);
    } else {
      filtered = newSnap;
      // Don't support priorities on queries
      filtered = filtered.updatePriority(PriorityUtilities.NullPriority());
      NamedNode startPost;
      NamedNode endPost;
      Iterator<NamedNode> iterator;
      int sign;
      if (this.reverse) {
        iterator = newSnap.reverseIterator();
        startPost = rangedFilter.getEndPost();
        endPost = rangedFilter.getStartPost();
        sign = -1;
      } else {
        iterator = newSnap.iterator();
        startPost = rangedFilter.getStartPost();
        endPost = rangedFilter.getEndPost();
        sign = 1;
      }

      int count = 0;
      boolean foundStartPost = false;
      while (iterator.hasNext()) {
        NamedNode next = iterator.next();
        if (!foundStartPost && index.compare(startPost, next) * sign <= 0) {
          // start adding
          foundStartPost = true;
        }
        boolean inRange =
            foundStartPost && count < this.limit && index.compare(next, endPost) * sign <= 0;
        if (inRange) {
          count++;
        } else {
          filtered = filtered.updateChild(next.getName(), EmptyNode.Empty());
        }
      }
    }
    return rangedFilter.getIndexedFilter().updateFullNode(oldSnap, filtered, optChangeAccumulator);
  }

  @Override
  public IndexedNode updatePriority(IndexedNode oldSnap, Node newPriority) {
    // Don't support priorities on queries
    return oldSnap;
  }

  @Override
  public NodeFilter getIndexedFilter() {
    return rangedFilter.getIndexedFilter();
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
