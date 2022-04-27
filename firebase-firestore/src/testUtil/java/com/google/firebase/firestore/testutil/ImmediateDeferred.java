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

/** An implementation of {@link Deferred} whose provider is always available. */
public final class ImmediateDeferred<T> implements Deferred<T> {

  private final Provider<T> provider;

  public ImmediateDeferred(@NonNull T instance) {
    provider = () -> instance;
  }

  @Override
  public void whenAvailable(@NonNull DeferredHandler<T> handler) {
    handler.handle(provider);
  }
}
