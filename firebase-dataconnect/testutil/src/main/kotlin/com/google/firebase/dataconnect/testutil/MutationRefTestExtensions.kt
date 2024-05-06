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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.*
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

fun <NewData, Variables> MutationRef<*, Variables>.withDataDeserializer(
  deserializer: DeserializationStrategy<NewData>
): MutationRef<NewData, Variables> =
  dataConnect.mutation(
    operationName = operationName,
    variables = variables,
    dataDeserializer = deserializer,
    variablesSerializer = variablesSerializer
  )

fun <Data, NewVariables> MutationRef<Data, *>.withVariables(
  variables: NewVariables,
  serializer: SerializationStrategy<NewVariables>
): MutationRef<Data, NewVariables> =
  dataConnect.mutation(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer
  )

inline fun <Data, reified NewVariables> MutationRef<Data, *>.withVariables(
  variables: NewVariables
): MutationRef<Data, NewVariables> = withVariables(variables, serializer())
