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

package com.google.firebase.database.collection;

import java.util.*;

public class ImmutableSortedMapIterator<K, V> implements Iterator<Map.Entry<K, V>> {

  private final ArrayDeque<LLRBValueNode<K, V>> nodeStack;

  private final boolean isReverse;

  ImmutableSortedMapIterator(
      LLRBNode<K, V> root, K startKey, Comparator<K> comparator, boolean isReverse) {
    this.nodeStack = new ArrayDeque<>();
    this.isReverse = isReverse;

    LLRBNode<K, V> node = root;
    while (!node.isEmpty()) {
      int cmp;
      if (startKey != null) {
        cmp =
            isReverse
                ? comparator.compare(startKey, node.getKey())
                : comparator.compare(node.getKey(), startKey);
      } else {
        cmp = 1;
      }
      if (cmp < 0) {
        // This node is less than our start key. ignore it
        if (isReverse) {
          node = node.getLeft();
        } else {
          node = node.getRight();
        }
      } else if (cmp == 0) {
        // This node is exactly equal to our start key. Push it on the stack, but stop iterating;
        this.nodeStack.push((LLRBValueNode<K, V>) node);
        break;
      } else {
        this.nodeStack.push((LLRBValueNode<K, V>) node);
        if (isReverse) {
          node = node.getRight();
        } else {
          node = node.getLeft();
        }
      }
    }
  }

  @Override
  public boolean hasNext() {
    return nodeStack.size() > 0;
  }

  @Override
  public Map.Entry<K, V> next() {
    try {
      final LLRBValueNode<K, V> node = nodeStack.pop();
      Map.Entry<K, V> entry = new AbstractMap.SimpleEntry<>(node.getKey(), node.getValue());
      if (this.isReverse) {
        LLRBNode<K, V> next = node.getLeft();
        while (!next.isEmpty()) {
          this.nodeStack.push((LLRBValueNode<K, V>) next);
          next = next.getRight();
        }
      } else {
        LLRBNode<K, V> next = node.getRight();
        while (!next.isEmpty()) {
          this.nodeStack.push((LLRBValueNode<K, V>) next);
          next = next.getLeft();
        }
      }
      return entry;
    } catch (EmptyStackException e) {
      // No more children
      throw new NoSuchElementException();
    }
  }

  @Override
  public void remove() {
    throw new UnsupportedOperationException("remove called on immutable collection");
  }
}
