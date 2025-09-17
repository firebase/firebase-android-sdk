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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

public final class ImmutableLists {

  private ImmutableLists() {}

  public static <T> ImmutableList<T> adopt(List<T> collection) {
    return new DelegatingImmutableList<>(Collections.unmodifiableList(collection));
  }

  public static <T> ImmutableList<T> empty() {
    //noinspection unchecked
    return (ImmutableList<T>) EmptyImmutableList.INSTANCE;
  }

  public static <T> ImmutableList<T> of() {
    return empty();
  }

  public static <T> ImmutableList<T> of(T element) {
    return new SingletonImmutableList<>(element);
  }

  @SafeVarargs
  public static <T> ImmutableList<T> of(T... elements) {
    if (elements.length == 0) {
      return empty();
    } else if (elements.length == 1) {
      return new SingletonImmutableList<>(elements[0]);
    } else {
      return new ArrayImmutableList<>(elements);
    }
  }

  private static class DelegatingImmutableList<T> implements ImmutableList<T> {

    private final List<T> delegate;

    public DelegatingImmutableList(List<T> delegate) {
      this.delegate = delegate;
    }

    @Override
    public T get(int index) {
      return delegate.get(index);
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

    @Override
    public ArrayList<T> toArrayList() {
      return new ArrayList<>(delegate);
    }

    @NonNull
    @Override
    public List<T> asUnmodifiableList() {
      return delegate;
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
      } else if (other instanceof ImmutableList) {
        return ((ImmutableList<?>) other).asUnmodifiableList().equals(this.delegate);
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

  private static final class EmptyImmutableList extends DelegatingImmutableList<Void> {

    static final EmptyImmutableList INSTANCE = new EmptyImmutableList();

    private EmptyImmutableList() {
      super(Collections.emptyList());
    }
  }

  private static final class SingletonImmutableList<T> extends DelegatingImmutableList<T> {

    SingletonImmutableList(T element) {
      super(Collections.singletonList(element));
    }
  }

  private static final class ArrayImmutableList<T> extends DelegatingImmutableList<T> {
    ArrayImmutableList(T[] elements) {
      super(Collections.unmodifiableList(Arrays.asList(elements)));
    }
  }
}
