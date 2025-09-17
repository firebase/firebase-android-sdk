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
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * A wrapper around a [HashSet] that guarantees its immutability.
 */
public final class ImmutableHashSet<T> implements ImmutableSet<T> {

  private final Set<T> hashSet;

  private ImmutableHashSet(Set<T> hashSet, boolean ignoredDiscriminator) {
    this.hashSet = hashSet;
  }

  private ImmutableHashSet(HashSet<T> hashSet) {
    this(Collections.unmodifiableSet(hashSet), true);
  }

  @Override
  public boolean contains(T element) {
    return hashSet.contains(element);
  }

  @Override
  public boolean isEmpty() {
    return hashSet.isEmpty();
  }

  @NonNull
  @Override
  public Iterator<T> iterator() {
    return hashSet.iterator();
  }

  @Override
  public int size() {
    return hashSet.size();
  }

  @NonNull
  @Override
  public Set<T> asUnmodifiableSet() {
    return hashSet;
  }

  @NonNull
  @Override
  public HashSet<T> toHashSet() {
    return new HashSet<>(hashSet);
  }

  @NonNull
  public Builder<T> toBuilder() {
    return new Builder<>(hashSet);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (object instanceof ImmutableHashSet) {
      return hashSet.equals(((ImmutableHashSet<?>) object).hashSet);
    } else {
      return hashSet.equals(object);
    }
  }

  @Override
  public int hashCode() {
    return hashSet.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return hashSet.toString();
  }

  private static final ImmutableHashSet<Void> EMPTY =
      new ImmutableHashSet<>(Collections.emptySet(), true);

  @NonNull
  public static <T> ImmutableHashSet<T> empty() {
    //noinspection unchecked
    return (ImmutableHashSet<T>) EMPTY;
  }

  @NonNull
  public static <T> ImmutableHashSet<T> adopt(HashSet<T> hashSet) {
    return new ImmutableHashSet<>(hashSet);
  }

  @SafeVarargs
  @NonNull
  public static <T> ImmutableHashSet<T> of(T... elements) {
    HashSet<T> hashSet = new HashSet<>(elements.length);
    Collections.addAll(hashSet, elements);
    return new ImmutableHashSet<>(hashSet);
  }

  @NonNull
  public static <T> ImmutableHashSet<T> copyOf(Collection<? extends T> elements) {
    return new ImmutableHashSet<>(new HashSet<>(elements));
  }

  @NonNull
  public static <T> ImmutableHashSet<T> copyOf(Iterable<? extends T> elements) {
    if (elements instanceof Collection<?>) {
      return copyOf((Collection<? extends T>) elements);
    }
    HashSet<T> hashSet = new HashSet<>();
    for (T element : elements) {
      hashSet.add(element);
    }
    return new ImmutableHashSet<>(hashSet);
  }

  public static final class Builder<T> {

    private HashSet<T> hashSet;
    private ImmutableHashSet<T> immutableHashSet;

    private Builder(HashSet<T> hashSet, boolean ignoredDiscriminator) {
      this.hashSet = hashSet;
    }

    public Builder() {
      this(new HashSet<>(), true);
    }

    public Builder(int initialCapacity) {
      this(new HashSet<>(initialCapacity), true);
    }

    public Builder(Collection<? extends T> collection) {
      this(new HashSet<>(collection), true);
    }

    public Builder(Iterable<? extends T> iterable) {
      this(new HashSet<>(hashSetFromIterable(iterable)), true);
    }

    public Builder(ImmutableCollection<T> immutableCollection) {
      this(immutableCollection.asUnmodifiableCollection());
    }

    public Builder(ImmutableHashSet<T> immutableHashSet) {
      this(immutableHashSet.toHashSet(), true);
    }

    private static <T> HashSet<T> hashSetFromIterable(Iterable<? extends T> iterable) {
      HashSet<T> hashSet = new HashSet<>();
      for (T element : iterable) {
        hashSet.add(element);
      }
      return hashSet;
    }

    public ImmutableHashSet<T> build() {
      if (immutableHashSet == null) {
        immutableHashSet = new ImmutableHashSet<>(hashSet);
        hashSet = null;
      }
      return immutableHashSet;
    }

    public Builder<T> add(T element) {
      hashSet.add(element);
      return this;
    }

    public Builder<T> addAll(Collection<? extends T> elements) {
      hashSet.addAll(elements);
      return this;
    }

    public Builder<T> addAll(Iterable<? extends T> elements) {
      if (elements instanceof Collection) {
        return addAll((Collection<? extends T>) elements);
      }
      for (T element : elements) {
        hashSet.add(element);
      }
      return this;
    }

    public Builder<T> addAll(ImmutableCollection<T> elements) {
      hashSet.addAll(elements.asUnmodifiableCollection());
      return this;
    }

    public Builder<T> remove(T element) {
      hashSet.remove(element);
      return this;
    }

    public Builder<T> removeAll(Collection<? extends T> elements) {
      hashSet.removeAll(elements);
      return this;
    }

    public Builder<T> removeAll(Iterable<? extends T> elements) {
      if (elements instanceof Collection) {
        return removeAll((Collection<? extends T>) elements);
      }
      for (T element : elements) {
        hashSet.remove(element);
      }
      return this;
    }

    public Builder<T> removeAll(ImmutableCollection<T> elements) {
      hashSet.removeAll(elements.asUnmodifiableCollection());
      return this;
    }

    public boolean contains(T element) {
      return hashSet != null ? hashSet.contains(element) : immutableHashSet.contains(element);
    }

    public boolean isEmpty() {
      return hashSet != null ? hashSet.isEmpty() : immutableHashSet.isEmpty();
    }

    public int size() {
      return hashSet != null ? hashSet.size() : immutableHashSet.size();
    }
  }
}
