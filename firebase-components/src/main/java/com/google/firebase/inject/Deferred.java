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

package com.google.firebase.inject;

import androidx.annotation.NonNull;
import com.google.firebase.annotations.DeferredApi;

/**
 * Represents a continuation-style dependency.
 *
 * <p>The motivation for it is to model optional dependencies that may become available in the
 * future and once they do, the depender will get notified automatically via the registered {@link
 * DeferredHandler}.
 *
 * <p>Example:
 *
 * <pre>{@code
 * class Foo {
 *   Foo(Deferred<Bar> bar) {
 *     bar.whenAvailable(barProvider -> {
 *       // automatically called when Bar becomes available
 *       use(barProvider.get());
 *     });
 *   }
 * }
 * }</pre>
 */
public interface Deferred<T> {
  /** Used by dependers to register their callbacks. */
  interface DeferredHandler<T> {
    @DeferredApi
    void handle(Provider<T> provider);
  }

  /** Register a callback that is executed once {@link T} becomes available */
  void whenAvailable(@NonNull DeferredHandler<T> handler);
}
