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
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * A wrapper around a [HashMap] that guarantees its immutability.
 */
public final class ImmutableHashMap<K, V> implements ImmutableMap<K, V> {

  private final Map<K, V> hashMap;

  private ImmutableHashMap(Map<K, V> hashMap, boolean ignoredDiscriminator) {
    this.hashMap = hashMap;
  }

  private ImmutableHashMap(HashMap<K, V> hashMap) {
    this(Collections.unmodifiableMap(hashMap), true);
  }

  @Override
  public V get(K key) {
    return hashMap.get(key);
  }

  @Override
  public boolean containsKey(K key) {
    return hashMap.containsKey(key);
  }

  @Override
  public boolean containsValue(V value) {
    return hashMap.containsValue(value);
  }

  @Override
  @NonNull
  public ImmutableSet<K> keySet() {
    return ImmutableSets.adopt(hashMap.keySet());
  }

  @Override
  @NonNull
  public ImmutableCollection<V> values() {
    return ImmutableCollections.adopt(hashMap.values());
  }

  @Override
  @NonNull
  public ImmutableSet<Map.Entry<K, V>> entrySet() {
    return ImmutableSets.adopt(hashMap.entrySet());
  }

  @Override
  public boolean isEmpty() {
    return hashMap.isEmpty();
  }

  @Override
  public int size() {
    return hashMap.size();
  }

  @Override
  @NonNull
  public Map<K, V> asUnmodifiableMap() {
    return hashMap;
  }

  @Override
  @NonNull
  public HashMap<K, V> toHashMap() {
    return new HashMap<>(hashMap);
  }

  @NonNull
  public Builder<K, V> toBuilder() {
    return new Builder<>(hashMap);
  }

  @Override
  public boolean equals(@Nullable Object object) {
    if (object == this) {
      return true;
    } else if (object instanceof ImmutableHashMap) {
      return hashMap.equals(((ImmutableHashMap<?, ?>) object).hashMap);
    } else {
      return hashMap.equals(object);
    }
  }

  @Override
  public int hashCode() {
    return hashMap.hashCode();
  }

  @NonNull
  @Override
  public String toString() {
    return hashMap.toString();
  }

  private static final ImmutableHashMap<Void, Void> EMPTY =
      new ImmutableHashMap<>(Collections.emptyMap(), true);

  public static <K, V> ImmutableHashMap<K, V> empty() {
    //noinspection unchecked
    return (ImmutableHashMap<K, V>) EMPTY;
  }

  @NonNull
  public static <K, V> ImmutableHashMap<K, V> adopt(HashMap<K, V> hashMap) {
    return new ImmutableHashMap<>(hashMap);
  }

  public static <K, V> ImmutableHashMap<K, V> of(K key, V value) {
    HashMap<K, V> hashMap = new HashMap<>(1);
    hashMap.put(key, value);
    return new ImmutableHashMap<>(hashMap);
  }

  @NonNull
  public static <K, V> ImmutableHashMap<K, V> copyOf(Map<? extends K, ? extends V> map) {
    return new ImmutableHashMap<>(new HashMap<>(map));
  }

  public static final class Builder<K, V> {

    private HashMap<K, V> hashMap;
    private ImmutableHashMap<K, V> immutableHashMap;

    private Builder(HashMap<K, V> hashMap, boolean ignoredDiscriminator) {
      this.hashMap = hashMap;
    }

    public Builder() {
      this(new HashMap<>(), true);
    }

    public Builder(int initialCapacity) {
      this(new HashMap<>(initialCapacity), true);
    }

    public Builder(Map<? extends K, ? extends V> map) {
      this(new HashMap<>(map), true);
    }

    public Builder(ImmutableHashMap<K, V> immutableHashMap) {
      this(immutableHashMap.toHashMap(), true);
    }

    public ImmutableHashMap<K, V> build() {
      if (immutableHashMap == null) {
        immutableHashMap = new ImmutableHashMap<>(hashMap);
        hashMap = null;
      }
      return immutableHashMap;
    }

    public Builder<K, V> put(K key, V value) {
      hashMap.put(key, value);
      return this;
    }

    public Builder<K, V> putAll(Map<? extends K, ? extends V> map) {
      hashMap.putAll(map);
      return this;
    }

    public Builder<K, V> putAll(ImmutableHashMap<K, V> map) {
      hashMap.putAll(map.asUnmodifiableMap());
      return this;
    }

    public Builder<K, V> remove(K key) {
      hashMap.remove(key);
      return this;
    }

    public V get(K key) {
      return hashMap != null ? hashMap.get(key) : immutableHashMap.get(key);
    }

    public Set<K> keySet() {
      return hashMap != null ? hashMap.keySet() : immutableHashMap.asUnmodifiableMap().keySet();
    }

    public Collection<V> values() {
      return hashMap != null ? hashMap.values() : immutableHashMap.asUnmodifiableMap().values();
    }

    public boolean containsKey(K key) {
      return hashMap != null ? hashMap.containsKey(key) : immutableHashMap.containsKey(key);
    }

    public boolean containsValue(V value) {
      return hashMap != null ? hashMap.containsValue(value) : immutableHashMap.containsValue(value);
    }

    public boolean isEmpty() {
      return hashMap != null ? hashMap.isEmpty() : immutableHashMap.isEmpty();
    }

    public int size() {
      return hashMap != null ? hashMap.size() : immutableHashMap.size();
    }

    public static <K, V> ImmutableHashMap.Builder<K, V> adopt(HashMap<K, V> map) {
      return new ImmutableHashMap.Builder<>(map, true);
    }
  }
}
