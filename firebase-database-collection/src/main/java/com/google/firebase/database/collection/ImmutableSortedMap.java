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

import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

public abstract class ImmutableSortedMap<K, V> implements Iterable<Map.Entry<K, V>> {

  public abstract boolean containsKey(K key);

  public abstract V get(K key);

  public abstract ImmutableSortedMap<K, V> remove(K key);

  public abstract ImmutableSortedMap<K, V> insert(K key, V value);

  public abstract K getMinKey();

  public abstract K getMaxKey();

  public abstract int size();

  public abstract boolean isEmpty();

  public abstract void inOrderTraversal(LLRBNode.NodeVisitor<K, V> visitor);

  @Override
  public abstract Iterator<Map.Entry<K, V>> iterator();

  public abstract Iterator<Map.Entry<K, V>> iteratorFrom(K key);

  public abstract Iterator<Map.Entry<K, V>> reverseIteratorFrom(K key);

  public abstract Iterator<Map.Entry<K, V>> reverseIterator();

  public abstract K getPredecessorKey(K key);

  public abstract K getSuccessorKey(K key);

  public abstract int indexOf(K key);

  public abstract Comparator<K> getComparator();

  @Override
  @SuppressWarnings("unchecked")
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof ImmutableSortedMap)) return false;

    ImmutableSortedMap<K, V> that = (ImmutableSortedMap) o;

    if (!this.getComparator().equals(that.getComparator())) return false;
    if (this.size() != that.size()) return false;

    Iterator<Map.Entry<K, V>> thisIterator = this.iterator();
    Iterator<Map.Entry<K, V>> thatIterator = that.iterator();
    while (thisIterator.hasNext()) {
      if (!thisIterator.next().equals(thatIterator.next())) return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = this.getComparator().hashCode();
    for (Map.Entry<K, V> entry : this) {
      result = 31 * result + entry.hashCode();
    }

    return result;
  }

  @Override
  public String toString() {
    StringBuilder b = new StringBuilder();
    b.append(this.getClass().getSimpleName());
    b.append("{");
    boolean first = true;
    for (Map.Entry<K, V> entry : this) {
      if (first) first = false;
      else b.append(", ");
      b.append("(");
      b.append(entry.getKey());
      b.append("=>");
      b.append(entry.getValue());
      b.append(")");
    }
    b.append("};");
    return b.toString();
  }

  public static class Builder {
    /**
     * The size threshold where we use a tree backed sorted map instead of an array backed sorted
     * map. This is a more or less arbitrary chosen value, that was chosen to be large enough to fit
     * most of object kind of Database data, but small enough to not notice degradation in
     * performance for inserting and lookups. Feel free to empirically determine this constant, but
     * don't expect much gain in real world performance.
     */
    static final int ARRAY_TO_RB_TREE_SIZE_THRESHOLD = 25;

    public static <K, V> ImmutableSortedMap<K, V> emptyMap(Comparator<K> comparator) {
      return new ArraySortedMap<>(comparator);
    }

    public interface KeyTranslator<C, D> {
      D translate(C key);
    }

    private static final KeyTranslator IDENTITY_TRANSLATOR = key -> key;

    @SuppressWarnings("unchecked")
    public static <A> KeyTranslator<A, A> identityTranslator() {
      return IDENTITY_TRANSLATOR;
    }

    public static <A, B> ImmutableSortedMap<A, B> fromMap(
        Map<A, B> values, Comparator<A> comparator) {
      if (values.size() < ARRAY_TO_RB_TREE_SIZE_THRESHOLD) {
        return ArraySortedMap.fromMap(values, comparator);
      } else {
        return RBTreeSortedMap.fromMap(values, comparator);
      }
    }

    public static <A, B, C> ImmutableSortedMap<A, C> buildFrom(
        List<A> keys,
        Map<B, C> values,
        ImmutableSortedMap.Builder.KeyTranslator<A, B> translator,
        Comparator<A> comparator) {
      if (keys.size() < ARRAY_TO_RB_TREE_SIZE_THRESHOLD) {
        return ArraySortedMap.buildFrom(keys, values, translator, comparator);
      } else {
        return RBTreeSortedMap.buildFrom(keys, values, translator, comparator);
      }
    }
  }
}
