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
import java.util.HashMap;
import java.util.Map;

public interface ImmutableMap<K, V> {

  V get(K key);

  boolean containsKey(K key);

  boolean containsValue(V value);

  @NonNull
  ImmutableSet<K> keySet();

  @NonNull
  ImmutableCollection<V> values();

  @NonNull
  ImmutableSet<Map.Entry<K, V>> entrySet();

  boolean isEmpty();

  int size();

  @NonNull
  Map<K, V> asUnmodifiableMap();

  @NonNull
  HashMap<K, V> toHashMap();
}
