// Copyright 2023 Google LLC
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
package com.google.firebase.dataconnect

import java.util.concurrent.atomic.AtomicReference

abstract class BaseRef<VariablesType, ResultType>(
  val dataConnect: FirebaseDataConnect,
  internal val operationName: String,
  internal val operationSet: String,
  internal val revision: String,
  variables: VariablesType
) {
  private val _variables = AtomicReference(variables)
  val variables: VariablesType
    get() = _variables.get()

  abstract suspend fun execute(): ResultType

  fun update(newVariables: VariablesType) {
    _variables.set(newVariables)
    onUpdate()
  }

  protected open fun onUpdate() {}

  protected interface Codec<VariablesType, ResultType> {
    fun encodeVariables(variables: VariablesType): Map<String, Any?>
    fun decodeResult(map: Map<String, Any?>): ResultType
  }

  protected abstract val codec: Codec<VariablesType, ResultType>

  internal val variablesAsMap: Map<String, Any?>
    get() = codec.encodeVariables(variables)

  internal fun resultFromMap(map: Map<String, Any?>) = codec.decodeResult(map)
}
