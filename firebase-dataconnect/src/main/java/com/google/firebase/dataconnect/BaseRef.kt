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

abstract class BaseRef<VariablesType, ResultType>
internal constructor(
  val dataConnect: FirebaseDataConnect,
  internal val operationName: String,
  internal val operationSet: String,
  internal val revision: String
) {
  abstract suspend fun execute(variables: VariablesType): ResultType
  protected abstract fun encodeVariables(variables: VariablesType): Map<String, Any?>
  protected abstract fun decodeResult(map: Map<String, Any?>): ResultType

  internal fun mapFromVariables(variables: VariablesType) = encodeVariables(variables)
  internal fun resultFromMap(map: Map<String, Any?>) = decodeResult(map)
}
