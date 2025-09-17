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
import java.util.Iterator;

public final class ImmutableCollections {

  private ImmutableCollections() {}

  public static <T> ImmutableCollection<T> adopt(Collection<T> collection) {
    return new DelegatingImmutableCollection<>(Collections.unmodifiableCollection(collection));
  }

  public static <T> ImmutableCollection<T> empty() {
    //noinspection unchecked
    return (ImmutableCollection<T>) EmptyImmutableCollection.INSTANCE;
  }

  public static <T> ImmutableCollection<T> of() {
    return empty();
  }

  public static <T> ImmutableCollection<T> of(T element) {
    return new SingletonImmutableCollection<>(element);
  }

  @SafeVarargs
  public static <T> ImmutableCollection<T> of(T... elements) {
    if (elements.length == 0) {
      return empty();
    } else if (elements.length == 1) {
      return new SingletonImmutableCollection<>(elements[0]);
    } else {
      return new ArrayImmutableCollection<>(elements);
    }
  }

  private static class DelegatingImmutableCollection<T> implements ImmutableCollection<T> {

    private final Collection<T> delegate;

    public DelegatingImmutableCollection(Collection<T> delegate) {
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
    public Collection<T> asUnmodifiableCollection() {
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
      } else if (other instanceof ImmutableCollection) {
        return ((ImmutableCollection<?>) other).asUnmodifiableCollection().equals(this.delegate);
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

  private static final class EmptyImmutableCollection extends DelegatingImmutableCollection<Void> {

    static final EmptyImmutableCollection INSTANCE = new EmptyImmutableCollection();

    private EmptyImmutableCollection() {
      super(Collections.emptyList());
    }
  }

  private static final class SingletonImmutableCollection<T>
      extends DelegatingImmutableCollection<T> {

    SingletonImmutableCollection(T element) {
      super(Collections.singletonList(element));
    }
  }

  private static final class ArrayImmutableCollection<T> extends DelegatingImmutableCollection<T> {
    ArrayImmutableCollection(T[] elements) {
      super(Collections.unmodifiableCollection(Arrays.asList(elements)));
    }
  }
}
