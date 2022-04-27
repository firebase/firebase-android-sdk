// Copyright 2021 Google LLC
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

package com.google.firebase.firestore.testutil;

import androidx.annotation.NonNull;
import com.google.firebase.inject.Deferred;
import com.google.firebase.inject.Provider;
import java.util.ArrayList;
import java.util.List;

/**
 * An implementation of {@link Deferred} whose provider is initially unavailable, then becomes
 * available when {@link #setInstance} is invoked.
 */
public final class DelayedDeferred<T> implements Deferred<T> {

  private final List<DeferredHandler<T>> handlers = new ArrayList<>();
  private Provider<T> provider;

  @Override
  public synchronized void whenAvailable(@NonNull DeferredHandler<T> handler) {
    if (provider != null) {
      handler.handle(provider);
    } else {
      handlers.add(handler);
    }
  }

  public synchronized void setInstance(@NonNull T instance) {
    if (provider != null) {
      throw new IllegalStateException("setInstance() has already been invoked");
    }
    provider = () -> instance;
    for (DeferredHandler<T> handler : handlers) {
      handler.handle(provider);
    }
  }
}
