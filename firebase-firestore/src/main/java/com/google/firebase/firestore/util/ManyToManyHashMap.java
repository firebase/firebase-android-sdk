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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;

public final class ManyToManyHashMap<K, V> {

  private final HashMap<K, HashSet<V>> valuesByKey;
  private final HashMap<V, HashSet<K>> keysByValue;
  private int keyValuePairCount;

  public ManyToManyHashMap() {
    valuesByKey = new HashMap<>();
    keysByValue = new HashMap<>();
  }

  public ManyToManyHashMap(int initialCapacity) {
    valuesByKey = new HashMap<>(initialCapacity);
    keysByValue = new HashMap<>(initialCapacity);
  }

  public ManyToManyHashMap(int initialCapacity, float loadFactor) {
    valuesByKey = new HashMap<>(initialCapacity, loadFactor);
    keysByValue = new HashMap<>(initialCapacity, loadFactor);
  }

  public ManyToManyHashMap(ManyToManyHashMap<K, V> initialValues) {
    this();
    for (Entry<K, HashSet<V>> entry : initialValues.valuesByKey.entrySet()) {
      valuesByKey.put(entry.getKey(), new HashSet<>(entry.getValue()));
      keyValuePairCount += entry.getValue().size();
    }
    for (Entry<V, HashSet<K>> entry : initialValues.keysByValue.entrySet()) {
      keysByValue.put(entry.getKey(), new HashSet<>(entry.getValue()));
    }
  }

  public boolean isEmpty() {
    return valuesByKey.isEmpty();
  }

  public int size() {
    return keyValuePairCount;
  }

  public void clear() {
    this.valuesByKey.clear();
    this.keysByValue.clear();
    keyValuePairCount = 0;
  }

  public boolean containsKey(K key) {
    return valuesByKey.containsKey(key);
  }

  public boolean containsValue(V value) {
    return keysByValue.containsKey(value);
  }

  public void put(K key, V value) {
    put(valuesByKey, key, value);
    put(keysByValue, value, key);
    keyValuePairCount++;
  }

  public void putKeysWithValue(Iterable<? extends K> keys, V value) {
    for (K key : keys) {
      put(key, value);
    }
  }

  public void putValuesForKey(K key, Iterable<? extends V> values) {
    for (V value : values) {
      put(key, value);
    }
  }

  public void remove(K key, V value) {
    boolean keyValuePairRemoved =
        remove(valuesByKey, key, value) && remove(keysByValue, value, key);
    if (keyValuePairRemoved) {
      keyValuePairCount--;
    }
  }

  public void removeKey(K key) {
    int keyValuePairRemoveCount = removeAllValuesForKey(valuesByKey, keysByValue, key);
    keyValuePairCount -= keyValuePairRemoveCount;
  }

  public void removeValue(V value) {
    int keyValuePairRemoveCount = removeAllValuesForKey(keysByValue, valuesByKey, value);
    keyValuePairCount -= keyValuePairRemoveCount;
  }

  public void removeKeysWithValue(Iterable<? extends K> keys, V value) {
    for (K key : keys) {
      remove(key, value);
    }
  }

  public void removeValuesForKey(K key, Iterable<? extends V> values) {
    for (V value : values) {
      remove(key, value);
    }
  }

  public HashSet<V> getValuesForKey(K key) {
    return getValuesForKey(valuesByKey, key);
  }

  public HashSet<K> getKeysForValue(V value) {
    return getValuesForKey(keysByValue, value);
  }

  public Set<K> keySet() {
    return valuesByKey.keySet();
  }

  public Set<V> valueSet() {
    return keysByValue.keySet();
  }

  private static <K, V> void put(HashMap<K, HashSet<V>> map, K key, V value) {
    HashSet<V> values = map.get(key);
    if (values == null) {
      values = new HashSet<>();
      map.put(key, values);
    }
    values.add(value);
  }

  private static <K, V> HashSet<V> getValuesForKey(HashMap<K, HashSet<V>> map, K key) {
    HashSet<V> values = map.get(key);
    return (values == null) ? new HashSet<>(0) : new HashSet<>(values);
  }

  private static <K, V> boolean remove(HashMap<K, HashSet<V>> valuesByKey, K key, V value) {
    HashSet<V> values = valuesByKey.get(key);
    if (values == null) {
      return false;
    }
    boolean valueRemoved = values.remove(value);
    if (!valueRemoved) {
      return false;
    }
    if (values.isEmpty()) {
      valuesByKey.remove(key);
    }
    return true;
  }

  private static <K, V> int removeAllValuesForKey(
      HashMap<K, HashSet<V>> valuesByKey, HashMap<V, HashSet<K>> keysByValue, K key) {
    HashSet<V> values = valuesByKey.remove(key);
    if (values == null) {
      return 0; // key is not present; nothing to do.
    }

    for (V value : values) {
      HashSet<K> keys = keysByValue.get(value);
      if (keys == null) {
        throw new IllegalStateException(
            "internal state corruption detected: " + "keysByValue mapping is missing");
      }
      boolean removed = keys.remove(key);
      if (!removed) {
        throw new IllegalStateException(
            "internal state corruption detected: "
                + "keysByValue mapping is present, but missing the given key");
      }
      if (keys.isEmpty()) {
        keysByValue.remove(value);
      }
    }

    return values.size();
  }
}
