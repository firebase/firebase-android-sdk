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
package com.google.firebase.functions

import androidx.annotation.GuardedBy
import dagger.assisted.Assisted
import dagger.assisted.AssistedFactory
import javax.inject.Inject
import javax.inject.Singleton

/** Multi-resource container for Functions. */
@Singleton
internal class FunctionsMultiResourceComponent
@Inject
constructor(private val functionsFactory: FirebaseFunctionsFactory) {
  /**
   * A static map from instance key to FirebaseFunctions instances. Instance keys region names.
   *
   * To ensure thread safety it should only be accessed when it is being synchronized on.
   */
  @GuardedBy("this") private val instances: MutableMap<String, FirebaseFunctions?> = HashMap()

  @Synchronized
  operator fun get(regionOrCustomDomain: String): FirebaseFunctions? {
    var functions = instances[regionOrCustomDomain]
    if (functions == null) {
      functions = functionsFactory.create(regionOrCustomDomain)
      instances[regionOrCustomDomain] = functions
    }
    return functions
  }

  @AssistedFactory
  internal interface FirebaseFunctionsFactory {
    fun create(@Assisted regionOrCustomDomain: String?): FirebaseFunctions?
  }
}
