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

/**
 * This is a red-black tree backed implementation of ImmutableSortedMap. This has better asymptotic
 * complexity for large collections, but performs worse in practice than an ArraySortedMap for small
 * collections. It also uses about twice as much memory.
 */
public class RBTreeSortedMap<K, V> extends ImmutableSortedMap<K, V> {

  private LLRBNode<K, V> root;
  private Comparator<K> comparator;

  RBTreeSortedMap(Comparator<K> comparator) {
    this.root = LLRBEmptyNode.getInstance();
    this.comparator = comparator;
  }

  private RBTreeSortedMap(LLRBNode<K, V> root, Comparator<K> comparator) {
    this.root = root;
    this.comparator = comparator;
  }

  // For testing purposes
  LLRBNode<K, V> getRoot() {
    return root;
  }

  private LLRBNode<K, V> getNode(K key) {
    LLRBNode<K, V> node = root;
    while (!node.isEmpty()) {
      int cmp = this.comparator.compare(key, node.getKey());
      if (cmp < 0) {
        node = node.getLeft();
      } else if (cmp == 0) {
        return node;
      } else {
        node = node.getRight();
      }
    }
    return null;
  }

  @Override
  public boolean containsKey(K key) {
    return getNode(key) != null;
  }

  @Override
  public V get(K key) {
    LLRBNode<K, V> node = getNode(key);
    return node != null ? node.getValue() : null;
  }

  @Override
  public ImmutableSortedMap<K, V> remove(K key) {
    if (!this.containsKey(key)) {
      return this;
    } else {
      LLRBNode<K, V> newRoot =
          root.remove(key, this.comparator).copy(null, null, LLRBNode.Color.BLACK, null, null);
      return new RBTreeSortedMap<>(newRoot, this.comparator);
    }
  }

  @Override
  public ImmutableSortedMap<K, V> insert(K key, V value) {
    LLRBNode<K, V> newRoot =
        root.insert(key, value, this.comparator).copy(null, null, LLRBNode.Color.BLACK, null, null);
    return new RBTreeSortedMap<>(newRoot, this.comparator);
  }

  @Override
  public K getMinKey() {
    return root.getMin().getKey();
  }

  @Override
  public K getMaxKey() {
    return root.getMax().getKey();
  }

  @Override
  public int size() {
    return root.size();
  }

  @Override
  public boolean isEmpty() {
    return root.isEmpty();
  }

  @Override
  public void inOrderTraversal(LLRBNode.NodeVisitor<K, V> visitor) {
    root.inOrderTraversal(visitor);
  }

  @Override
  public Iterator<Map.Entry<K, V>> iterator() {
    return new ImmutableSortedMapIterator<>(root, null, this.comparator, false);
  }

  @Override
  public Iterator<Map.Entry<K, V>> iteratorFrom(K key) {
    return new ImmutableSortedMapIterator<>(root, key, this.comparator, false);
  }

  @Override
  public Iterator<Map.Entry<K, V>> reverseIteratorFrom(K key) {
    return new ImmutableSortedMapIterator<>(root, key, this.comparator, true);
  }

  @Override
  public Iterator<Map.Entry<K, V>> reverseIterator() {
    return new ImmutableSortedMapIterator<>(root, null, this.comparator, true);
  }

  @Override
  public K getPredecessorKey(K key) {
    LLRBNode<K, V> node = root;
    LLRBNode<K, V> rightParent = null;
    while (!node.isEmpty()) {
      int cmp = this.comparator.compare(key, node.getKey());
      if (cmp == 0) {
        if (!node.getLeft().isEmpty()) {
          node = node.getLeft();
          while (!node.getRight().isEmpty()) {
            node = node.getRight();
          }
          return node.getKey();
        } else if (rightParent != null) {
          return rightParent.getKey();
        } else {
          return null;
        }
      } else if (cmp < 0) {
        node = node.getLeft();
      } else {
        rightParent = node;
        node = node.getRight();
      }
    }
    throw new IllegalArgumentException("Couldn't find predecessor key of non-present key: " + key);
  }

  @Override
  public K getSuccessorKey(K key) {
    LLRBNode<K, V> node = root;
    LLRBNode<K, V> leftParent = null;
    while (!node.isEmpty()) {
      int cmp = this.comparator.compare(node.getKey(), key);
      if (cmp == 0) {
        if (!node.getRight().isEmpty()) {
          node = node.getRight();
          while (!node.getLeft().isEmpty()) {
            node = node.getLeft();
          }
          return node.getKey();
        } else if (leftParent != null) {
          return leftParent.getKey();
        } else {
          return null;
        }
      } else if (cmp < 0) {
        node = node.getRight();
      } else {
        leftParent = node;
        node = node.getLeft();
      }
    }
    throw new IllegalArgumentException("Couldn't find successor key of non-present key: " + key);
  }

  @Override
  public int indexOf(K key) {
    // Number of nodes that were pruned when descending right
    int prunedNodes = 0;
    LLRBNode<K, V> node = root;
    while (!node.isEmpty()) {
      int cmp = this.comparator.compare(key, node.getKey());
      if (cmp == 0) {
        return prunedNodes + node.getLeft().size();
      } else if (cmp < 0) {
        node = node.getLeft();
      } else {
        // Count all nodes left of the node plus the node itself
        prunedNodes += node.getLeft().size() + 1;
        node = node.getRight();
      }
    }
    // Node not found
    return -1;
  }

  @Override
  public Comparator<K> getComparator() {
    return comparator;
  }

  public static <A, B, C> RBTreeSortedMap<A, C> buildFrom(
      List<A> keys,
      Map<B, C> values,
      ImmutableSortedMap.Builder.KeyTranslator<A, B> translator,
      Comparator<A> comparator) {
    return Builder.buildFrom(keys, values, translator, comparator);
  }

  public static <A, B> RBTreeSortedMap<A, B> fromMap(Map<A, B> values, Comparator<A> comparator) {
    return Builder.buildFrom(
        new ArrayList<>(values.keySet()),
        values,
        ImmutableSortedMap.Builder.identityTranslator(),
        comparator);
  }

  private static class Builder<A, B, C> {

    static class BooleanChunk {
      public boolean isOne;
      public int chunkSize;
    }

    static class Base1_2 implements Iterable<BooleanChunk> {

      private long value;
      private final int length;

      public Base1_2(int size) {
        int toCalc = size + 1;
        length = (int) Math.floor(Math.log(toCalc) / Math.log(2));
        long mask = ((long) Math.pow(2, length)) - 1;
        value = toCalc & mask;
      }

      /**
       * Iterates over booleans for whether or not a particular digit is a '1' in base {1, 2}
       *
       * @return A reverse iterator over the base {1, 2} number
       */
      @Override
      public Iterator<BooleanChunk> iterator() {
        return new Iterator<BooleanChunk>() {

          private int current = length - 1;

          @Override
          public boolean hasNext() {
            return current >= 0;
          }

          @Override
          public BooleanChunk next() {
            long result = value & ((byte) 1 << current);
            BooleanChunk next = new BooleanChunk();
            next.isOne = result == 0;
            next.chunkSize = (int) Math.pow(2, current);
            current--;
            return next;
          }

          @Override
          public void remove() {
            // No-op
          }
        };
      }
    }

    private final List<A> keys;
    private final Map<B, C> values;
    private final ImmutableSortedMap.Builder.KeyTranslator<A, B> keyTranslator;

    private LLRBValueNode<A, C> root;
    private LLRBValueNode<A, C> leaf;

    private Builder(
        List<A> keys, Map<B, C> values, ImmutableSortedMap.Builder.KeyTranslator<A, B> translator) {
      this.keys = keys;
      this.values = values;
      this.keyTranslator = translator;
    }

    private C getValue(A key) {
      return values.get(keyTranslator.translate(key));
    }

    private LLRBNode<A, C> buildBalancedTree(int start, int size) {
      if (size == 0) {
        return LLRBEmptyNode.getInstance();
      } else if (size == 1) {
        A key = this.keys.get(start);
        return new LLRBBlackValueNode<>(key, getValue(key), null, null);
      } else {
        int half = size / 2;
        int middle = start + half;
        LLRBNode<A, C> left = buildBalancedTree(start, half);
        LLRBNode<A, C> right = buildBalancedTree(middle + 1, half);
        A key = this.keys.get(middle);
        return new LLRBBlackValueNode<>(key, getValue(key), left, right);
      }
    }

    private void buildPennant(LLRBNode.Color color, int chunkSize, int start) {
      LLRBNode<A, C> treeRoot = buildBalancedTree(start + 1, chunkSize - 1);
      A key = this.keys.get(start);
      LLRBValueNode<A, C> node;
      if (color == LLRBNode.Color.RED) {
        node = new LLRBRedValueNode<>(key, getValue(key), null, treeRoot);
      } else {
        node = new LLRBBlackValueNode<>(key, getValue(key), null, treeRoot);
      }
      if (root == null) {
        root = node;
        leaf = node;
      } else {
        leaf.setLeft(node);
        leaf = node;
      }
    }

    public static <A, B, C> RBTreeSortedMap<A, C> buildFrom(
        List<A> keys,
        Map<B, C> values,
        ImmutableSortedMap.Builder.KeyTranslator<A, B> translator,
        Comparator<A> comparator) {
      Builder<A, B, C> builder = new Builder<>(keys, values, translator);
      Collections.sort(keys, comparator);
      Iterator<BooleanChunk> iter = new Base1_2(keys.size()).iterator();
      int index = keys.size();
      while (iter.hasNext()) {
        BooleanChunk next = iter.next();
        index -= next.chunkSize;
        if (next.isOne) {
          builder.buildPennant(LLRBNode.Color.BLACK, next.chunkSize, index);
        } else {
          builder.buildPennant(LLRBNode.Color.BLACK, next.chunkSize, index);
          index -= next.chunkSize;
          builder.buildPennant(LLRBNode.Color.RED, next.chunkSize, index);
        }
      }
      return new RBTreeSortedMap<>(
          builder.root == null ? LLRBEmptyNode.getInstance() : builder.root, comparator);
    }
  }
}
