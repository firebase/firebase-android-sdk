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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Encapsulates a {@link HashMap}, guaranteeing immutability.
 * <p>
 * This class is safe to use concurrently from multiple threads without any external
 * synchronization.
 */
public final class ImmutableHashMap<K, V> {

  @NonNull private final Map<K, V> map;

  public ImmutableHashMap(@NonNull Map<? extends K, ? extends V> elements) {
    map = Collections.unmodifiableMap(new HashMap<>(elements));
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public int size() {
    return map.size();
  }

  public boolean containsKey(K key) {
    return map.containsKey(key);
  }

  public boolean containsValue(V value) {
    return map.containsValue(value);
  }

  public V get(K key) {
    return map.get(key);
  }

  public Set<Map.Entry<K, V>> entrySet() {
    return map.entrySet();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  public Collection<V> values() {
    return map.values();
  }

  @NonNull
  public HashMap<K, V> toHashMap() {
    return new HashMap<>(map);
  }

  @NonNull
  public Map<K, V> asMap() {
    return map;
  }

  @Override
  public boolean equals(@Nullable Object obj) {
    if (obj == this) {
      return true;
    }

    if (obj instanceof ImmutableHashMap) {
      ImmutableHashMap<?, ?> other = (ImmutableHashMap<?, ?>) obj;
      return map.equals(other.map);
    }

    return map.equals(obj);
  }

  @Override
  public int hashCode() {
    return map.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return map.toString();
  }

  private static final ImmutableHashMap<Void, Void> EMPTY =
      new ImmutableHashMap<>(Collections.emptyMap());

  /**
   * Returns an empty {@link ImmutableHashMap}.
   * @return the same instance on each invocation.
   */
  @NonNull
  public static <K, V> ImmutableHashMap<K, V> emptyMap() {
    //noinspection unchecked
    return (ImmutableHashMap<K, V>) EMPTY;
  }

  /**
   * Returns a new {@link ImmutableHashMap} of size 1, populated with the given key/value pair.
   */
  @NonNull
  public static <K, V> ImmutableHashMap<K, V> of(K key, V value) {
    HashMap<K, V> map = new HashMap<>(1);
    map.put(key, value);
    return withDelegateMap(map);
  }

  /**
   * Creates and returns a new {@link ImmutableHashMap} that uses the given {@link HashMap} as its
   * underlying map.
   * <p>
   * The caller MUST never make any changes to the given map or else the contract of
   * {@link ImmutableHashMap} is broken and the behavior of the returned object becomes undefined.
   * Ideally, the caller should not retain any references to the given map after calling this
   * method.
   * <p>
   * The advantage of this method compared to the {@link ImmutableHashMap} constructor is
   * performance. Namely, this method has O(1) runtime complexity and incurs no cost of copying the
   * key/value pairs into a new {@link HashMap}, whereas the constructor is θ(n) due to the cost of
   * copying the given key/value pairs into a new {@link HashMap}.
   */
  @NonNull
  public static <K, V> ImmutableHashMap<K, V> withDelegateMap(HashMap<K, V> map) {
    return new ImmutableHashMap<>(map);
  }
}
