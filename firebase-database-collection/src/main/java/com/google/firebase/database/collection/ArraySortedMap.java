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
 * This is an array backed implementation of ImmutableSortedMap. It uses arrays and linear lookups
 * to achieve good memory efficiency while maintaining good performance for small collections. To
 * avoid degrading performance with increasing collection size it will automatically convert to a
 * RBTreeSortedMap after an insert call above a certain threshold.
 */
public class ArraySortedMap<K, V> extends ImmutableSortedMap<K, V> {

  @SuppressWarnings("unchecked")
  public static <A, B, C> ArraySortedMap<A, C> buildFrom(
      List<A> keys,
      Map<B, C> values,
      Builder.KeyTranslator<A, B> translator,
      Comparator<A> comparator) {
    Collections.sort(keys, comparator);
    int size = keys.size();
    A[] keyArray = (A[]) new Object[size];
    C[] valueArray = (C[]) new Object[size];
    int pos = 0;
    for (A k : keys) {
      keyArray[pos] = k;
      C value = values.get(translator.translate(k));
      valueArray[pos] = value;
      pos++;
    }
    return new ArraySortedMap<A, C>(comparator, keyArray, valueArray);
  }

  public static <K, V> ArraySortedMap<K, V> fromMap(Map<K, V> map, Comparator<K> comparator) {
    return buildFrom(
        new ArrayList<K>(map.keySet()), map, Builder.<K>identityTranslator(), comparator);
  }

  private final K[] keys;
  private final V[] values;
  private final Comparator<K> comparator;

  @SuppressWarnings("unchecked")
  public ArraySortedMap(Comparator<K> comparator) {
    this.keys = (K[]) new Object[0];
    this.values = (V[]) new Object[0];
    this.comparator = comparator;
  }

  @SuppressWarnings("unchecked")
  private ArraySortedMap(Comparator<K> comparator, K[] keys, V[] values) {
    this.keys = keys;
    this.values = values;
    this.comparator = comparator;
  }

  @Override
  public boolean containsKey(K key) {
    return findKey(key) != -1;
  }

  @Override
  public V get(K key) {
    int pos = findKey(key);
    return pos != -1 ? this.values[pos] : null;
  }

  @Override
  public ImmutableSortedMap<K, V> remove(K key) {
    int pos = findKey(key);
    if (pos == -1) {
      return this;
    } else {
      K[] keys = removeFromArray(this.keys, pos);
      V[] values = removeFromArray(this.values, pos);
      return new ArraySortedMap<K, V>(this.comparator, keys, values);
    }
  }

  @Override
  public ImmutableSortedMap<K, V> insert(K key, V value) {
    int pos = findKey(key);
    if (pos != -1) {
      if (this.keys[pos] == key && this.values[pos] == value) {
        return this;
      } else {
        // The key and/or value might have changed, even though the comparison might still yield 0
        K[] newKeys = replaceInArray(this.keys, pos, key);
        V[] newValues = replaceInArray(this.values, pos, value);
        return new ArraySortedMap<K, V>(this.comparator, newKeys, newValues);
      }
    } else {
      if (this.keys.length > Builder.ARRAY_TO_RB_TREE_SIZE_THRESHOLD) {
        @SuppressWarnings("unchecked")
        Map<K, V> map = new HashMap<K, V>(this.keys.length + 1);
        for (int i = 0; i < this.keys.length; i++) {
          map.put(this.keys[i], this.values[i]);
        }
        map.put(key, value);
        return RBTreeSortedMap.fromMap(map, this.comparator);
      } else {
        int newPos = findKeyOrInsertPosition(key);
        K[] keys = addToArray(this.keys, newPos, key);
        V[] values = addToArray(this.values, newPos, value);
        return new ArraySortedMap<K, V>(this.comparator, keys, values);
      }
    }
  }

  @Override
  public K getMinKey() {
    return this.keys.length > 0 ? this.keys[0] : null;
  }

  @Override
  public K getMaxKey() {
    return this.keys.length > 0 ? this.keys[this.keys.length - 1] : null;
  }

  @Override
  public int size() {
    return this.keys.length;
  }

  @Override
  public boolean isEmpty() {
    return this.keys.length == 0;
  }

  @Override
  public void inOrderTraversal(LLRBNode.NodeVisitor<K, V> visitor) {
    for (int i = 0; i < this.keys.length; i++) {
      visitor.visitEntry(this.keys[i], this.values[i]);
    }
  }

  private Iterator<Map.Entry<K, V>> iterator(final int pos, final boolean reverse) {
    return new Iterator<Map.Entry<K, V>>() {
      int currentPos = pos;

      @Override
      public boolean hasNext() {
        return reverse ? currentPos >= 0 : currentPos < keys.length;
      }

      @Override
      public Map.Entry<K, V> next() {
        final K key = keys[currentPos];
        final V value = values[currentPos];
        currentPos = reverse ? currentPos - 1 : currentPos + 1;
        return new AbstractMap.SimpleImmutableEntry<K, V>(key, value);
      }

      @Override
      public void remove() {
        throw new UnsupportedOperationException("Can't remove elements from ImmutableSortedMap");
      }
    };
  }

  @Override
  public Iterator<Map.Entry<K, V>> iterator() {
    return iterator(0, false);
  }

  @Override
  public Iterator<Map.Entry<K, V>> iteratorFrom(K key) {
    int pos = findKeyOrInsertPosition(key);
    return iterator(pos, false);
  }

  @Override
  public Iterator<Map.Entry<K, V>> reverseIteratorFrom(K key) {
    int pos = findKeyOrInsertPosition(key);
    // if there's no exact match, findKeyOrInsertPosition will return the index *after* the closest
    // match, but
    // since this is a reverse iterator, we want to start just *before* the closest match.
    if (pos < this.keys.length && this.comparator.compare(this.keys[pos], key) == 0) {
      return iterator(pos, true);
    } else {
      return iterator(pos - 1, true);
    }
  }

  @Override
  public Iterator<Map.Entry<K, V>> reverseIterator() {
    return iterator(this.keys.length - 1, true);
  }

  @Override
  public K getPredecessorKey(K key) {
    int pos = findKey(key);
    if (pos == -1) {
      throw new IllegalArgumentException("Can't find predecessor of nonexistent key");
    } else {
      return (pos > 0) ? this.keys[pos - 1] : null;
    }
  }

  @Override
  public K getSuccessorKey(K key) {
    int pos = findKey(key);
    if (pos == -1) {
      throw new IllegalArgumentException("Can't find successor of nonexistent key");
    } else {
      return (pos < this.keys.length - 1) ? this.keys[pos + 1] : null;
    }
  }

  @Override
  public int indexOf(K key) {
    return findKey(key);
  }

  @Override
  public Comparator<K> getComparator() {
    return comparator;
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] removeFromArray(T[] arr, int pos) {
    int newSize = arr.length - 1;
    T[] newArray = (T[]) new Object[newSize];
    System.arraycopy(arr, 0, newArray, 0, pos);
    System.arraycopy(arr, pos + 1, newArray, pos, newSize - pos);
    return newArray;
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] addToArray(T[] arr, int pos, T value) {
    int newSize = arr.length + 1;
    T[] newArray = (T[]) new Object[newSize];
    System.arraycopy(arr, 0, newArray, 0, pos);
    newArray[pos] = value;
    System.arraycopy(arr, pos, newArray, pos + 1, newSize - pos - 1);
    return newArray;
  }

  @SuppressWarnings("unchecked")
  private static <T> T[] replaceInArray(T[] arr, int pos, T value) {
    int size = arr.length;
    T[] newArray = (T[]) new Object[size];
    System.arraycopy(arr, 0, newArray, 0, size);
    newArray[pos] = value;
    return newArray;
  }

  /**
   * This does a linear scan which is simpler than a binary search. For a small collection size this
   * still should be as fast a as binary search.
   */
  private int findKeyOrInsertPosition(K key) {
    int newPos = 0;
    while (newPos < this.keys.length && this.comparator.compare(this.keys[newPos], key) < 0) {
      newPos++;
    }
    return newPos;
  }

  /**
   * This does a linear scan which is simpler than a binary search. For a small collection size this
   * still should be as fast a as binary search.
   */
  private int findKey(K key) {
    int i = 0;
    for (K otherKey : this.keys) {
      if (this.comparator.compare(key, otherKey) == 0) {
        return i;
      }
      i++;
    }
    return -1;
  }
}
