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

public class ImmutableSortedSet<T> implements Iterable<T> {

  private final ImmutableSortedMap<T, Void> map;

  private static class WrappedEntryIterator<T> implements Iterator<T> {
    final Iterator<Map.Entry<T, Void>> iterator;

    public WrappedEntryIterator(Iterator<Map.Entry<T, Void>> iterator) {
      this.iterator = iterator;
    }

    @Override
    public boolean hasNext() {
      return this.iterator.hasNext();
    }

    @Override
    public T next() {
      return this.iterator.next().getKey();
    }

    @Override
    public void remove() {
      this.iterator.remove();
    }
  }

  public ImmutableSortedSet(List<T> elems, Comparator<T> comparator) {
    this.map =
        ImmutableSortedMap.Builder.buildFrom(
            elems,
            Collections.<T, Void>emptyMap(),
            ImmutableSortedMap.Builder.<T>identityTranslator(),
            comparator);
  }

  private ImmutableSortedSet(ImmutableSortedMap<T, Void> map) {
    this.map = map;
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof ImmutableSortedSet)) {
      return false;
    }
    ImmutableSortedSet otherSet = (ImmutableSortedSet) other;
    return map.equals(otherSet.map);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  public boolean contains(T entry) {
    return this.map.containsKey(entry);
  }

  @SuppressWarnings("ReferenceEquality") // Detect unchanged immutable instance
  public ImmutableSortedSet<T> remove(T entry) {
    ImmutableSortedMap<T, Void> newMap = this.map.remove(entry);
    return (newMap == this.map) ? this : new ImmutableSortedSet<T>(newMap);
  }

  public ImmutableSortedSet<T> insert(T entry) {
    return new ImmutableSortedSet<T>(map.insert(entry, null));
  }

  public ImmutableSortedSet<T> unionWith(ImmutableSortedSet<T> other) {
    ImmutableSortedSet<T> result = this;

    // Make sure `result` always refers to the larger one of the two sets.
    if (result.size() < other.size()) {
      result = other;
      other = this;
    }

    for (T elem : other) {
      result = result.insert(elem);
    }

    return result;
  }

  public T getMinEntry() {
    return this.map.getMinKey();
  }

  public T getMaxEntry() {
    return this.map.getMaxKey();
  }

  public int size() {
    return this.map.size();
  }

  public boolean isEmpty() {
    return this.map.isEmpty();
  }

  @Override
  public Iterator<T> iterator() {
    return new WrappedEntryIterator<T>(this.map.iterator());
  }

  public Iterator<T> iteratorFrom(T entry) {
    return new WrappedEntryIterator<T>(this.map.iteratorFrom(entry));
  }

  public Iterator<T> reverseIteratorFrom(T entry) {
    return new WrappedEntryIterator<T>(this.map.reverseIteratorFrom(entry));
  }

  public Iterator<T> reverseIterator() {
    return new WrappedEntryIterator<T>(this.map.reverseIterator());
  }

  public T getPredecessorEntry(T entry) {
    return this.map.getPredecessorKey(entry);
  }

  public int indexOf(T entry) {
    return this.map.indexOf(entry);
  }
}
