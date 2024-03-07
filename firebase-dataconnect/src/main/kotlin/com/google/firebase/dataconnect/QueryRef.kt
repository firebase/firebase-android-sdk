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

public class QueryRef<Data, Variables>
internal constructor(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  variables: Variables,
  dataDeserializer: DeserializationStrategy<Data>,
  variablesSerializer: SerializationStrategy<Variables>,
) :
  OperationRef<Data, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer,
  ) {
  override suspend fun execute(): DataConnectQueryResult<Data, Variables> =
    dataConnect.lazyQueryManager.get().execute(this)

  public fun subscribe(): QuerySubscription<Data, Variables> = QuerySubscription(this)

  override fun hashCode(): Int = Objects.hash("QueryRef", super.hashCode())

  override fun equals(other: Any?): Boolean = (other is QueryRef<*, *>) && super.equals(other)

  override fun toString(): String =
    "QueryRef(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "dataDeserializer=$dataDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"
}

internal fun <NewData, Variables> QueryRef<*, Variables>.withDataDeserializer(
  deserializer: DeserializationStrategy<NewData>
): QueryRef<NewData, Variables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = deserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Data, Variables> QueryRef<Data, Variables>.withVariablesSerializer(
  serializer: SerializationStrategy<Variables>
): QueryRef<Data, Variables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer
  )

internal fun <Data, Variables> QueryRef<Data, Variables>.withVariables(
  variables: Variables
): QueryRef<Data, Variables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Data, NewVariables> QueryRef<Data, *>.withVariables(
  variables: NewVariables,
  serializer: SerializationStrategy<NewVariables>
): QueryRef<Data, NewVariables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer
  )

internal fun <Data> QueryRef<Data, *>.withVariables(
  variables: DataConnectUntypedVariables
): MutationRef<Data, DataConnectUntypedVariables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = DataConnectUntypedVariables
  )
