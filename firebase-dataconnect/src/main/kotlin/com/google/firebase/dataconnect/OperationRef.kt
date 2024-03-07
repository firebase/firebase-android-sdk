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
import kotlinx.serialization.serializer

public abstract class OperationRef<Data, Variables>
internal constructor(
  public val dataConnect: FirebaseDataConnect,
  public val operationName: String,
  public val variables: Variables,
  public val dataDeserializer: DeserializationStrategy<Data>,
  public val variablesSerializer: SerializationStrategy<Variables>,
) {
  public abstract suspend fun execute(): DataConnectResult<Data, Variables>

  override fun hashCode(): Int =
    Objects.hash(dataConnect, operationName, variables, dataDeserializer, variablesSerializer)

  override fun equals(other: Any?): Boolean =
    (other as? OperationRef<*, *>)?.let {
      it.dataConnect == dataConnect &&
        it.operationName == operationName &&
        it.variables == variables &&
        it.dataDeserializer == dataDeserializer &&
        it.variablesSerializer == variablesSerializer
    }
      ?: false

  override fun toString(): String =
    "OperationRef(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"
}

internal inline fun <Data, reified NewVariables> MutationRef<Data, *>.withVariables(
  variables: NewVariables
): MutationRef<Data, NewVariables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer()
  )

internal inline fun <Data, reified NewVariables> QueryRef<Data, *>.withVariables(
  variables: NewVariables
): QueryRef<Data, NewVariables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer()
  )
