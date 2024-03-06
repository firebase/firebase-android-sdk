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

public class MutationRef<Response, Variables>
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
  override suspend fun execute(): DataConnectMutationResult<Response, Variables> =
    dataConnect.executeMutation(this)

  override fun hashCode(): Int = Objects.hash("MutationRef", super.hashCode())

  override fun equals(other: Any?): Boolean = (other is MutationRef<*, *>) && super.equals(other)

  override fun toString(): String =
    "MutationRef(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "variables=$variables, " +
      "responseDeserializer=$responseDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"
}

internal fun <NewResponse, Variables> MutationRef<*, Variables>.withResponseDeserializer(
  deserializer: DeserializationStrategy<NewResponse>
): MutationRef<NewResponse, Variables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = deserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Response, Variables> MutationRef<Response, Variables>.withVariablesSerializer(
  serializer: SerializationStrategy<Variables>
): MutationRef<Response, Variables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = serializer
  )

internal fun <Response, Variables> MutationRef<Response, Variables>.withVariables(
  variables: Variables
): MutationRef<Response, Variables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Response, NewVariables> MutationRef<Response, *>.withVariables(
  variables: NewVariables,
  serializer: SerializationStrategy<NewVariables>
): MutationRef<Response, NewVariables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = serializer
  )

internal fun <Response> MutationRef<Response, *>.withVariables(
  variables: DataConnectUntypedVariables
): MutationRef<Response, DataConnectUntypedVariables> =
  MutationRef(
    dataConnect = dataConnect,
    operationName = operationName,
    variables = variables,
    responseDeserializer = responseDeserializer,
    variablesSerializer = DataConnectUntypedVariables
  )
