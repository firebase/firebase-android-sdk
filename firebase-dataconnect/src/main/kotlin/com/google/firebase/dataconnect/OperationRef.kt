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

import java.util.Objects
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public abstract class OperationRef<Response, Variables>
internal constructor(
  public val dataConnect: FirebaseDataConnect,
  internal val operationName: String,
  internal val responseDeserializer: DeserializationStrategy<Response>,
  internal val variablesSerializer: SerializationStrategy<Variables>,
) {
  public abstract suspend fun execute(variables: Variables): DataConnectResult<Response, Variables>

  override fun hashCode(): Int =
    Objects.hash(dataConnect, operationName, responseDeserializer, variablesSerializer)

  override fun equals(other: Any?): Boolean =
    (other as? OperationRef<*, *>)?.let {
      it.dataConnect == dataConnect &&
        it.operationName == operationName &&
        it.responseDeserializer == responseDeserializer &&
        it.variablesSerializer == variablesSerializer
    }
      ?: false

  override fun toString(): String =
    "OperationRef(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "responseDeserializer=$responseDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"
}
