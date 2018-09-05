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

package com.google.firebase.database.snapshot;

import com.google.android.gms.common.internal.Objects;
import com.google.firebase.database.collection.ImmutableSortedSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

/**
 * Represents a node together with an index. The index and node are updated in unison. In the case
 * where the index does not affect the ordering (i.e. the ordering is identical to the key ordering)
 * this class uses a fallback index to save memory. Everything operating on the index must special
 * case the fallback index.
 */
public class IndexedNode implements Iterable<NamedNode> {

  /**
   * This is a sentinal value, so it's fine to just use null for the comparator as it will never be
   * invoked.
   */
  private static final ImmutableSortedSet<NamedNode> FALLBACK_INDEX =
      new ImmutableSortedSet<NamedNode>(Collections.<NamedNode>emptyList(), null);

  private final Node node;
  /**
   * The indexed set is initialized lazily for cases where we don't need to access any order
   * specific methods
   */
  private ImmutableSortedSet<NamedNode> indexed;

  private final Index index;

  private IndexedNode(Node node, Index index) {
    this.index = index;
    this.node = node;
    // Index lazily
    this.indexed = null;
  }

  private IndexedNode(Node node, Index index, ImmutableSortedSet<NamedNode> indexed) {
    this.index = index;
    this.node = node;
    this.indexed = indexed;
  }

  private void ensureIndexed() {
    if (this.indexed == null) {
      // Not indexed yet, create now
      if (this.index.equals(KeyIndex.getInstance())) {
        this.indexed = FALLBACK_INDEX;
      } else {
        List<NamedNode> children = new ArrayList<NamedNode>();
        boolean sawIndexedValue = false;
        for (NamedNode entry : node) {
          sawIndexedValue = sawIndexedValue || index.isDefinedOn(entry.getNode());
          NamedNode namedNode = new NamedNode(entry.getName(), entry.getNode());
          children.add(namedNode);
        }
        if (sawIndexedValue) {
          this.indexed = new ImmutableSortedSet<NamedNode>(children, index);
        } else {
          this.indexed = FALLBACK_INDEX;
        }
      }
    }
  }

  public static IndexedNode from(Node node) {
    return new IndexedNode(node, PriorityIndex.getInstance());
  }

  public static IndexedNode from(Node node, Index index) {
    return new IndexedNode(node, index);
  }

  public boolean hasIndex(Index index) {
    return this.index == index;
  }

  public Node getNode() {
    return this.node;
  }

  @Override
  public Iterator<NamedNode> iterator() {
    ensureIndexed();
    if (Objects.equal(this.indexed, FALLBACK_INDEX)) {
      return this.node.iterator();
    } else {
      return this.indexed.iterator();
    }
  }

  public Iterator<NamedNode> reverseIterator() {
    ensureIndexed();
    if (Objects.equal(this.indexed, FALLBACK_INDEX)) {
      return this.node.reverseIterator();
    } else {
      return this.indexed.reverseIterator();
    }
  }

  public IndexedNode updateChild(ChildKey key, Node child) {
    Node newNode = this.node.updateImmediateChild(key, child);
    if (Objects.equal(this.indexed, FALLBACK_INDEX) && !this.index.isDefinedOn(child)) {
      // doesn't affect the index, no need to create an index
      return new IndexedNode(newNode, this.index, FALLBACK_INDEX);
    } else if (this.indexed == null || Objects.equal(this.indexed, FALLBACK_INDEX)) {
      // No need to index yet, index lazily
      return new IndexedNode(newNode, this.index, null);
    } else {
      Node oldChild = this.node.getImmediateChild(key);
      ImmutableSortedSet<NamedNode> newIndexed = this.indexed.remove(new NamedNode(key, oldChild));
      if (!child.isEmpty()) {
        newIndexed = newIndexed.insert(new NamedNode(key, child));
      }
      return new IndexedNode(newNode, this.index, newIndexed);
    }
  }

  public IndexedNode updatePriority(Node priority) {
    return new IndexedNode(node.updatePriority(priority), this.index, this.indexed);
  }

  public NamedNode getFirstChild() {
    if (!(this.node instanceof ChildrenNode)) {
      return null;
    } else {
      ensureIndexed();
      if (Objects.equal(this.indexed, FALLBACK_INDEX)) {
        ChildKey firstKey = ((ChildrenNode) this.node).getFirstChildKey();
        return new NamedNode(firstKey, this.node.getImmediateChild(firstKey));
      } else {
        return this.indexed.getMinEntry();
      }
    }
  }

  public NamedNode getLastChild() {
    if (!(this.node instanceof ChildrenNode)) {
      return null;
    } else {
      ensureIndexed();
      if (Objects.equal(this.indexed, FALLBACK_INDEX)) {
        ChildKey lastKey = ((ChildrenNode) this.node).getLastChildKey();
        return new NamedNode(lastKey, this.node.getImmediateChild(lastKey));
      } else {
        return this.indexed.getMaxEntry();
      }
    }
  }

  public ChildKey getPredecessorChildName(ChildKey childKey, Node childNode, Index index) {
    if (!this.index.equals(KeyIndex.getInstance()) && !this.index.equals(index)) {
      throw new IllegalArgumentException("Index not available in IndexedNode!");
    }
    ensureIndexed();
    if (Objects.equal(this.indexed, FALLBACK_INDEX)) {
      return this.node.getPredecessorChildKey(childKey);
    } else {
      NamedNode node = this.indexed.getPredecessorEntry(new NamedNode(childKey, childNode));
      return node != null ? node.getName() : null;
    }
  }
}
