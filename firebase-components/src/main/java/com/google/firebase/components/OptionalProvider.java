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

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;

/**
 * Mutable thread-safe {@link Provider}.
 *
 * <p>Can be updated to a new value with the {@link #set(Provider)} method.
 *
 * <p>The intent of this class is to be used in place of missing {@link Component} dependencies so
 * that they can be updated if dependencies are loaded later.
 */
class OptionalProvider<T> implements Provider<T>, Deferred<T> {
  private static final DeferredHandler<Object> NOOP_HANDLER = p -> {};
  private static final Provider<Object> EMPTY_PROVIDER = () -> null;

  @GuardedBy("this")
  private DeferredHandler<T> handler;

  private volatile Provider<T> delegate;

  private OptionalProvider(DeferredHandler<T> handler, Provider<T> provider) {
    this.handler = handler;
    this.delegate = provider;
  }

  @SuppressWarnings("unchecked")
  static <T> OptionalProvider<T> empty() {
    return new OptionalProvider<>((DeferredHandler<T>) NOOP_HANDLER, (Provider<T>) EMPTY_PROVIDER);
  }

  static <T> OptionalProvider<T> of(Provider<T> provider) {
    return new OptionalProvider<>(null, provider);
  }

  @Override
  public T get() {
    return delegate.get();
  }

  @SuppressWarnings("InvalidDeferredApiUse")
  void set(Provider<T> provider) {
    if (this.delegate != EMPTY_PROVIDER) {
      throw new IllegalStateException("provide() can be called only once.");
    }
    DeferredHandler<T> localHandler;
    synchronized (this) {
      localHandler = handler;
      handler = null;
      this.delegate = provider;
    }
    localHandler.handle(provider);
  }

  @Override
  @SuppressWarnings("InvalidDeferredApiUse")
  public void whenAvailable(@NonNull DeferredHandler<T> handler) {
    Provider<T> provider = this.delegate;
    if (provider != EMPTY_PROVIDER) {
      handler.handle(provider);
      return;
    }
    Provider<T> toRun = null;
    synchronized (this) {
      provider = this.delegate;
      if (provider != EMPTY_PROVIDER) {
        toRun = provider;
      } else {
        DeferredHandler<T> existingHandler = this.handler;
        this.handler =
            p -> {
              existingHandler.handle(p);
              handler.handle(p);
            };
      }
    }
    if (toRun != null) {
      handler.handle(provider);
    }
  }
}
