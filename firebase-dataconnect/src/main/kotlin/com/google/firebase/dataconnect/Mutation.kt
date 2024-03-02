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

public class Mutation<Response, Variables>
internal constructor(
  dataConnect: FirebaseDataConnect,
  operationName: String,
  responseDeserializer: DeserializationStrategy<Response>,
  variablesSerializer: SerializationStrategy<Variables>,
) :
  Reference<Response, Variables>(
    dataConnect = dataConnect,
    operationName = operationName,
    responseDeserializer = responseDeserializer,
    variablesSerializer = variablesSerializer,
  ) {
  override suspend fun execute(
    variables: Variables
  ): DataConnectMutationResult<Response, Variables> = dataConnect.executeMutation(this, variables)

  override fun hashCode(): Int = Objects.hash("Mutation", super.hashCode())

  override fun equals(other: Any?): Boolean = (other is Mutation<*, *>) && super.equals(other)

  override fun toString(): String =
    "Mutation(" +
      "dataConnect=$dataConnect, " +
      "operationName=$operationName, " +
      "responseDeserializer=$responseDeserializer, " +
      "variablesSerializer=$variablesSerializer" +
      ")"

  public fun <NewResponse> withResponseDeserializer(
    newResponseDeserializer: DeserializationStrategy<NewResponse>
  ): Mutation<NewResponse, Variables> =
    Mutation(
      dataConnect = dataConnect,
      operationName = operationName,
      responseDeserializer = newResponseDeserializer,
      variablesSerializer = variablesSerializer,
    )

  public fun <NewVariables> withVariablesSerializer(
    newVariablesSerializer: SerializationStrategy<NewVariables>
  ): Mutation<Response, NewVariables> =
    Mutation(
      dataConnect = dataConnect,
      operationName = operationName,
      responseDeserializer = responseDeserializer,
      variablesSerializer = newVariablesSerializer,
    )
}
