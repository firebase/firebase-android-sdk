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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public final class ImmutableMaps {

  private ImmutableMaps() {}

  public static <K, V> ImmutableMap<K, V> adopt(Map<? extends K, ? extends V> map) {
    return new DelegatingImmutableMap<K, V>(Collections.unmodifiableMap(map)) {};
  }

  public static <K, V> ImmutableMap<K, V> empty() {
    //noinspection unchecked
    return (ImmutableMap<K, V>) EmptyImmutableMap.INSTANCE;
  }

  public static <K, V> ImmutableMap<K, V> of(K key, V value) {
    return new SingletonImmutableMap<>(key, value);
  }

  private abstract static class DelegatingImmutableMap<K, V> implements ImmutableMap<K, V> {

    private final Map<K, V> delegate;

    public DelegatingImmutableMap(Map<K, V> delegate) {
      this.delegate = delegate;
    }

    @Override
    public V get(K key) {
      return delegate.get(key);
    }

    @Override
    public boolean containsKey(K key) {
      return delegate.containsKey(key);
    }

    @Override
    public boolean containsValue(V value) {
      return delegate.containsValue(value);
    }

    @NonNull
    @Override
    public ImmutableSet<K> keySet() {
      return ImmutableSets.adopt(delegate.keySet());
    }

    @NonNull
    @Override
    public ImmutableCollection<V> values() {
      return ImmutableCollections.adopt(delegate.values());
    }

    @NonNull
    @Override
    public ImmutableSet<Map.Entry<K, V>> entrySet() {
      return ImmutableSets.adopt(delegate.entrySet());
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
    public Map<K, V> asUnmodifiableMap() {
      return delegate;
    }

    @NonNull
    @Override
    public HashMap<K, V> toHashMap() {
      return new HashMap<>(delegate);
    }

    @Override
    public boolean equals(@Nullable Object other) {
      if (other == this) {
        return true;
      } else if (other instanceof ImmutableMap) {
        return ((ImmutableMap<?, ?>) other).asUnmodifiableMap().equals(this.delegate);
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

  private static final class EmptyImmutableMap extends DelegatingImmutableMap<Void, Void> {

    static final EmptyImmutableMap INSTANCE = new EmptyImmutableMap();

    private EmptyImmutableMap() {
      super(Collections.emptyMap());
    }
  }

  private static final class SingletonImmutableMap<K, V> extends DelegatingImmutableMap<K, V> {

    SingletonImmutableMap(K key, V value) {
      super(Collections.singletonMap(key, value));
    }
  }
}
