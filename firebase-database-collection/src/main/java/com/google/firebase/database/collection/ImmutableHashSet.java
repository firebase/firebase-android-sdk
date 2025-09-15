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

package com.google.firebase.database.collection;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/**
 * Encapsulates a {@link HashSet}, guaranteeing immutability.
 * <p>
 * This class is safe to use concurrently from multiple threads without any external
 * synchronization.
 */
public final class ImmutableHashSet<T> implements Iterable<T> {

  @NonNull private final Set<T> set;

  private ImmutableHashSet(@NonNull HashSet<T> delegate) {
    set = Collections.unmodifiableSet(delegate);
  }

  public ImmutableHashSet(@NonNull Collection<? extends T> elements) {
    this(new HashSet<>(elements));
  }

  @NonNull
  @Override
  public Iterator<T> iterator() {
    return set.iterator();
  }

  public boolean isEmpty() {
    return set.isEmpty();
  }

  public int size() {
    return set.size();
  }

  public boolean contains(T element) {
    return set.contains(element);
  }

  @NonNull
  public HashSet<T> toHashSet() {
    return new HashSet<>(set);
  }

  @NonNull
  public Set<T> asSet() {
    return set;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof ImmutableHashSet) {
      ImmutableHashSet<?> other = (ImmutableHashSet<?>) obj;
      return set.equals(other.set);
    }

    return set.equals(obj);
  }

  @Override
  public int hashCode() {
    return set.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return set.toString();
  }

  private static final ImmutableHashSet<Void> EMPTY =
      new ImmutableHashSet<>(Collections.emptyList());

  /**
   * Returns an empty {@link ImmutableHashSet}.
   * @return the same instance on each invocation.
   */
  @NonNull
  public static <T> ImmutableHashSet<T> emptySet() {
    //noinspection unchecked
    return (ImmutableHashSet<T>) EMPTY;
  }

  /**
   * Returns a new {@link ImmutableHashSet} of size 1, populated with the given elements.
   */
  @SafeVarargs
  @NonNull
  public static <T> ImmutableHashSet<T> of(T... values) {
    HashSet<T> set = new HashSet<>(values.length);
    Collections.addAll(set, values);
    return withDelegateSet(set);
  }

  /**
   * Creates and returns a new {@link ImmutableHashSet} that uses the given {@link HashSet} as its
   * underlying set.
   * <p>
   * The caller MUST never make any changes to the given set or else the contract of
   * {@link ImmutableHashSet} is broken and the behavior of the returned object becomes undefined.
   * Ideally, the caller should not retain any references to the given set after calling this
   * method.
   * <p>
   * The advantage of this method compared to the {@link ImmutableHashSet} constructor is
   * performance. Namely, this method has O(1) runtime complexity and incurs no cost of copying the
   * elements into a new {@link HashSet}, whereas the constructor is θ(n) due to the cost of copying
   * the given elements into a new {@link HashSet}.
   */
  @NonNull
  public static <T> ImmutableHashSet<T> withDelegateSet(HashSet<T> set) {
    return new ImmutableHashSet<>(set);
  }
}
