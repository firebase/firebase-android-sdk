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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface MutationRef<Data, Variables> : OperationRef<Data, Variables> {
  override suspend fun execute(): MutationResult<Data, Variables>
}

public interface MutationResult<Data, Variables> : OperationResult<Data, Variables> {
  override val ref: MutationRef<Data, Variables>
}

internal fun <NewData, Variables> MutationRef<*, Variables>.withDataDeserializer(
  deserializer: DeserializationStrategy<NewData>
): MutationRef<NewData, Variables> =
  dataConnect.mutation(
    operationName = operationName,
    variables = variables,
    dataDeserializer = deserializer,
    variablesSerializer = variablesSerializer
  )

internal fun <Data, NewVariables> MutationRef<Data, *>.withVariables(
  variables: NewVariables,
  serializer: SerializationStrategy<NewVariables>
): MutationRef<Data, NewVariables> =
  dataConnect.mutation(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer
  )

internal fun <Data> MutationRef<Data, *>.withVariables(
  variables: DataConnectUntypedVariables
): MutationRef<Data, DataConnectUntypedVariables> =
  dataConnect.mutation(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = DataConnectUntypedVariables
  )
