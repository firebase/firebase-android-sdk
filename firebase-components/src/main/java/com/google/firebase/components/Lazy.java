// Copyright 2019 Google LLC
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

import androidx.annotation.VisibleForTesting;
import com.google.firebase.inject.Provider;

/**
 * Represents a lazily initialized value.
 *
 * <p>The implementation is taken from <a
 * href="https://github.com/google/dagger/blob/master/java/dagger/internal/DoubleCheck.java">Dagger2</a>,
 * simplified to our needs.
 *
 * <p>Note: the class is thread-safe.
 */
public class Lazy<T> implements Provider<T> {

  private static final Object UNINITIALIZED = new Object();

  private volatile Object instance = UNINITIALIZED;
  private volatile Provider<T> provider;

  /** Creates a {@link Lazy} with a fully initialized value. */
  Lazy(T instance) {
    this.instance = instance;
  }

  public Lazy(Provider<T> provider) {
    this.provider = provider;
  }

  /** Returns the initialized value. */
  @Override
  public T get() {
    Object result = instance;
    if (result == UNINITIALIZED) {
      synchronized (this) {
        result = instance;
        if (result == UNINITIALIZED) {
          result = provider.get();
          instance = result;
          /* Null out the reference to the provider. We are never going to need it again, so we
           * can make it eligible for GC. */
          provider = null;
        }
      }
    }

    @SuppressWarnings("unchecked")
    T tResult = (T) result;
    return tResult;
  }

  @VisibleForTesting
  boolean isInitialized() {
    return instance != UNINITIALIZED;
  }
}
