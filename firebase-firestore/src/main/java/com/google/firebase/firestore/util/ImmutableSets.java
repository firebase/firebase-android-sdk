/*
 * Copyright 2025 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.firestore.util;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public final class ImmutableSets {

  private ImmutableSets() {}

  public static <T> ImmutableSet<T> adopt(Set<? extends T> map) {
    return new ImmutableSets.DelegatingImmutableSet<T>(Collections.unmodifiableSet(map)) {};
  }

  public static <T> ImmutableSet<T> empty() {
    //noinspection unchecked
    return (ImmutableSet<T>) EmptyImmutableSet.INSTANCE;
  }

  public static <T> ImmutableSet<T> of() {
    return empty();
  }

  public static <T> ImmutableSet<T> of(T element) {
    return new SingletonImmutableSet<>(element);
  }

  @SafeVarargs
  public static <T> ImmutableSet<T> of(T... elements) {
    if (elements.length == 0) {
      return empty();
    } else if (elements.length == 1) {
      return new SingletonImmutableSet<>(elements[0]);
    } else {
      return new ArrayImmutableSet<>(elements);
    }
  }

  private static class DelegatingImmutableSet<T> implements ImmutableSet<T> {

    private final Set<T> delegate;

    public DelegatingImmutableSet(Set<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public boolean contains(T element) {
      return delegate.contains(element);
    }

    @Override
    public boolean isEmpty() {
      return delegate.isEmpty();
    }

    @Override
    public int size() {
      return delegate.size();
    }

    @NonNull
    @Override
    public Set<T> asUnmodifiableSet() {
      return delegate;
    }

    @NonNull
    @Override
    public HashSet<T> toHashSet() {
      return new HashSet<>(delegate);
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
      return delegate.iterator();
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other == this) {
        return true;
      } else if (other instanceof ImmutableSet) {
        return ((ImmutableSet<?>) other).asUnmodifiableSet().equals(this.delegate);
      } else {
        return this.delegate.equals(other);
      }
    }

    @Override
    public int hashCode() {
      return delegate.hashCode();
    }

    @NonNull
    @Override
    public String toString() {
      return delegate.toString();
    }
  }

  private static final class EmptyImmutableSet extends DelegatingImmutableSet<Void> {

    static final EmptyImmutableSet INSTANCE = new EmptyImmutableSet();

    private EmptyImmutableSet() {
      super(Collections.emptySet());
    }
  }

  private static final class SingletonImmutableSet<T> extends DelegatingImmutableSet<T> {

    SingletonImmutableSet(T element) {
      super(Collections.singleton(element));
    }
  }

  private static final class ArrayImmutableSet<T> implements ImmutableSet<T>, Set<T> {

    private final Collection<T> elements;

    ArrayImmutableSet(T[] elements) {
      this.elements = Collections.unmodifiableCollection(Arrays.asList(elements));
    }

    @Override
    public boolean isEmpty() {
      return elements.isEmpty();
    }

    @Override
    public boolean contains(@Nullable Object element) {
      return elements.contains(element);
    }

    @NonNull
    @Override
    public Object[] toArray() {
      return elements.toArray();
    }

    @NonNull
    @Override
    public <T1> T1[] toArray(@NonNull T1[] array) {
      return elements.toArray(array);
    }

    @Override
    public boolean add(T t) {
      throw new UnsupportedOperationException("ArrayImmutableSet does not support modification");
    }

    @Override
    public boolean remove(@Nullable Object o) {
      throw new UnsupportedOperationException("ArrayImmutableSet does not support modification");
    }

    @Override
    public boolean containsAll(@NonNull Collection<?> collection) {
      return elements.containsAll(collection);
    }

    @Override
    public boolean addAll(@NonNull Collection<? extends T> c) {
      throw new UnsupportedOperationException("ArrayImmutableSet does not support modification");
    }

    @Override
    public boolean retainAll(@NonNull Collection<?> c) {
      throw new UnsupportedOperationException("ArrayImmutableSet does not support modification");
    }

    @Override
    public boolean removeAll(@NonNull Collection<?> c) {
      throw new UnsupportedOperationException("ArrayImmutableSet does not support modification");
    }

    @Override
    public void clear() {
      throw new UnsupportedOperationException("ArrayImmutableSet does not support modification");
    }

    @Override
    public int size() {
      return elements.size();
    }

    @NonNull
    @Override
    public Set<T> asUnmodifiableSet() {
      return this;
    }

    @NonNull
    @Override
    public HashSet<T> toHashSet() {
      return new HashSet<>(this);
    }

    @NonNull
    @Override
    public Iterator<T> iterator() {
      return elements.iterator();
    }
  }
}
