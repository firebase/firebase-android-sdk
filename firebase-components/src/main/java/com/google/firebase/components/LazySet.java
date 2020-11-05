// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.components;

import com.google.firebase.inject.Provider;
import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lazy mutable thread-safe {@link Provider} for {@link Set}s.
 *
 * <p>The actual set is materialized only when first requested via {@link #get()}.
 *
 * <p>As new values are added to the set via {@link #add(Provider)}, the underlying set is updated
 * with the new value.
 */
class LazySet<T> implements Provider<Set<T>> {

  private volatile Set<Provider<T>> providers;
  private volatile Set<T> actualSet = null;

  LazySet(Collection<Provider<T>> providers) {
    this.providers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    this.providers.addAll(providers);
  }

  static LazySet<?> fromCollection(Collection<Provider<?>> providers) {
    @SuppressWarnings("unchecked")
    Collection<Provider<Object>> casted = (Collection<Provider<Object>>) (Set<?>) providers;
    return new LazySet<>(casted);
  }

  @Override
  public Set<T> get() {
    if (actualSet == null) {
      synchronized (this) {
        if (actualSet == null) {
          actualSet = Collections.newSetFromMap(new ConcurrentHashMap<>());
          updateSet();
        }
      }
    }
    return Collections.unmodifiableSet(actualSet);
  }

  synchronized void add(Provider<T> newProvider) {
    if (actualSet == null) {
      providers.add(newProvider);
    } else {
      actualSet.add(newProvider.get());
    }
  }

  private synchronized void updateSet() {
    for (Provider<T> provider : providers) {
      actualSet.add(provider.get());
    }
    providers = null;
  }
}
