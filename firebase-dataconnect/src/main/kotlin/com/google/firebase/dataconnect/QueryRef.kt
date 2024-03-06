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

public class QueryRef<Response, Variables>
internal constructor(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  variables: Variables,
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
) :
  OperationRef<Response, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  ) {
  override suspend fun execute(): DataConnectQueryResult<Response, Variables> =
    dataConnect.lazyQueryManager.get().execute(this)

  public fun subscribe(): QuerySubscription<Response, Variables> = QuerySubscription(this)

  override fun hashCode(): Int = Objects.hash("QueryRef", super.hashCode())

  override fun equals(other: Any?): Boolean = (other is QueryRef<*, *>) && super.equals(other)

  override fun toString(): String =
    "QueryRef(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "responseDeserializer=$responseDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"
}

internal fun <NewResponse, Variables> QueryRef<*, Variables>.withResponseDeserializer(
  deserializer: DeserializationStrategy<NewResponse>
): QueryRef<NewResponse, Variables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = deserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Response, Variables> QueryRef<Response, Variables>.withVariablesSerializer(
  serializer: SerializationStrategy<Variables>
): QueryRef<Response, Variables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = serializer
  )

internal fun <Response, Variables> QueryRef<Response, Variables>.withVariables(
  variables: Variables
): QueryRef<Response, Variables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Response, NewVariables> QueryRef<Response, *>.withVariables(
  variables: NewVariables,
  serializer: SerializationStrategy<NewVariables>
): QueryRef<Response, NewVariables> =
  QueryRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = serializer
  )

internal fun <Response> QueryRef<Response, *>.withVariables(
  variables: DataConnectUntypedVariables
): MutationRef<Response, DataConnectUntypedVariables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = DataConnectUntypedVariables
  )
