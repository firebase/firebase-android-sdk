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

import java.util.ArrayList;
import java.util.Collection;

public interface ImmutableCollection<T> extends Iterable<T> {

  boolean contains(T element);

  boolean isEmpty();

  int size();

  Collection<T> asUnmodifiableCollection();

  default ArrayList<T> toArrayList() {
    ArrayList<T> newArrayList = new ArrayList<>(size());
    for (T element : this) {
      newArrayList.add(element);
    }
    return newArrayList;
  }

  default <U> ImmutableCollection<U> map(Function<T, U> transformer) {
    ArrayList<U> newCollection = new ArrayList<>(size());
    for (T element : this) {
      newCollection.add(transformer.apply(element));
    }
    return ImmutableCollections.adopt(newCollection);
  }
}
