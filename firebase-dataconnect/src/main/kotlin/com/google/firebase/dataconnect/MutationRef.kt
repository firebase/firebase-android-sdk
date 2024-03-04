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
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
) :
  OperationRef<Response, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  ) {
  override suspend fun execute(
    variables: Variables
  ): DataConnectMutationResult<Response, Variables> = dataConnect.executeMutation(this, variables)

  override fun hashCode(): Int = Objects.hash("Mutation", super.hashCode())

  override fun equals(other: Any?): Boolean = (other is MutationRef<*, *>) && super.equals(other)

  override fun toString(): String =
    "MutationRef(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "responseDeserializer=$responseDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"

  public fun <NewResponse> withResponseDeserializer(
    newResponseDeserializer: DeserializationStrategy<NewResponse>
  ): MutationRef<NewResponse, Variables> =
    MutationRef(
      dataConnect = dataConnect,
      operationName = operationName,
      responseDeserializer = newResponseDeserializer,
      variablesSerializer = variablesSerializer,
    )

  public fun <NewVariables> withVariablesSerializer(
    newVariablesSerializer: SerializationStrategy<NewVariables>
  ): MutationRef<Response, NewVariables> =
    MutationRef(
      dataConnect = dataConnect,
      operationName = operationName,
      responseDeserializer = responseDeserializer,
      variablesSerializer = newVariablesSerializer,
    )
}

public suspend fun <Response> MutationRef<Response, Unit>.execute():
  DataConnectMutationResult<Response, Unit> = execute(Unit)
