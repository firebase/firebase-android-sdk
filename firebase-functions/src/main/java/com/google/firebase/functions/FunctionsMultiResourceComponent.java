// Copyright 2018 Google LLC
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

package com.google.firebase.functions;

import androidx.annotation.GuardedBy;
import dagger.assisted.Assisted;
import dagger.assisted.AssistedFactory;
import java.util.HashMap;
import java.util.Map;
import javax.inject.Inject;
import javax.inject.Singleton;

/** Multi-resource container for Functions. */
@Singleton
class FunctionsMultiResourceComponent {
  /**
   * A static map from instance key to FirebaseFunctions instances. Instance keys region names.
   *
   * <p>To ensure thread safety it should only be accessed when it is being synchronized on.
   */
  @GuardedBy("this")
  private final Map<String, FirebaseFunctions> instances = new HashMap<>();

  private final FirebaseFunctionsFactory functionsFactory;

  @Inject
  FunctionsMultiResourceComponent(FirebaseFunctionsFactory functionsFactory) {
    this.functionsFactory = functionsFactory;
  }

  synchronized FirebaseFunctions get(String regionOrCustomDomain) {
    FirebaseFunctions functions = instances.get(regionOrCustomDomain);
    if (functions == null) {
      functions = functionsFactory.create(regionOrCustomDomain);
      instances.put(regionOrCustomDomain, functions);
    }
    return functions;
  }

  @AssistedFactory
  interface FirebaseFunctionsFactory {
    FirebaseFunctions create(@Assisted String regionOrCustomDomain);
  }
}
