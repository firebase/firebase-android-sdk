/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.util.calculateSha512
import com.google.firebase.dataconnect.util.encodeToStruct
import com.google.firebase.dataconnect.util.toAlphaNumericString
import com.google.firebase.dataconnect.util.toCompactString
import com.google.protobuf.Struct
import java.util.Objects

internal class ActiveQueryKey(val operationName: String, val variables: Struct) {

  private val variablesHash: String = variables.calculateSha512().toAlphaNumericString()

  override fun equals(other: Any?) =
    other is ActiveQueryKey &&
      other.operationName == operationName &&
      other.variablesHash == variablesHash

  override fun hashCode() = Objects.hash(operationName, variablesHash)

  override fun toString() =
    "ActiveQueryKey(" +
      "operationName=$operationName, " +
      "variables=${variables.toCompactString()})"

  companion object {
    fun <Data, Variables> forQueryRef(query: QueryRef<Data, Variables>): ActiveQueryKey {
      val variablesStruct = encodeToStruct(query.variablesSerializer, query.variables)
      return ActiveQueryKey(operationName = query.operationName, variables = variablesStruct)
    }
  }
}
