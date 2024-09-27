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

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.generated.GeneratedConnector
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.generated.GeneratedOperation
import com.google.firebase.dataconnect.generated.GeneratedQuery
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

fun <Data, Variables, NewData> QueryRef<Data, Variables>.withDataDeserializer(
  newDataDeserializer: DeserializationStrategy<NewData>
): QueryRef<NewData, Variables> =
  dataConnect.query(
    operationName = operationName,
    variables = variables,
    dataDeserializer = newDataDeserializer,
    variablesSerializer = variablesSerializer
  )

fun <Data, NewVariables> QueryRef<Data, *>.withVariables(
  variables: NewVariables,
  serializer: SerializationStrategy<NewVariables>
): QueryRef<Data, NewVariables> =
  dataConnect.query(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = serializer
  )

inline fun <Data, reified NewVariables> QueryRef<Data, *>.withVariables(
  variables: NewVariables
): QueryRef<Data, NewVariables> = withVariables(variables, serializer())

fun <C : GeneratedConnector, Data, NewVariables> GeneratedOperation<C, Data, *>
  .withVariablesSerializer(variablesSerializer: SerializationStrategy<NewVariables>) =
  object : GeneratedOperation<C, Data, NewVariables> {
    override val connector by this@withVariablesSerializer::connector
    override val operationName by this@withVariablesSerializer::operationName
    override val dataDeserializer by this@withVariablesSerializer::dataDeserializer
    override val variablesSerializer
      get() = variablesSerializer

    override fun toString(): String =
      this@withVariablesSerializer.toString() + " with variables serializer $variablesSerializer"
  }

fun <C : GeneratedConnector, Data, NewVariables> GeneratedQuery<C, Data, *>.withVariablesSerializer(
  variablesSerializer: SerializationStrategy<NewVariables>
) =
  object : GeneratedQuery<C, Data, NewVariables> {
    override val connector by this@withVariablesSerializer::connector
    override val operationName by this@withVariablesSerializer::operationName
    override val dataDeserializer by this@withVariablesSerializer::dataDeserializer
    override val variablesSerializer
      get() = variablesSerializer

    override fun toString(): String =
      this@withVariablesSerializer.toString() + " with variables serializer $variablesSerializer"
  }

fun <C : GeneratedConnector, Data, NewVariables> GeneratedMutation<C, Data, *>
  .withVariablesSerializer(variablesSerializer: SerializationStrategy<NewVariables>) =
  object : GeneratedMutation<C, Data, NewVariables> {
    override val connector by this@withVariablesSerializer::connector
    override val operationName by this@withVariablesSerializer::operationName
    override val dataDeserializer by this@withVariablesSerializer::dataDeserializer
    override val variablesSerializer
      get() = variablesSerializer

    override fun toString(): String =
      this@withVariablesSerializer.toString() + " with variables serializer $variablesSerializer"
  }

fun <C : GeneratedConnector, Variables, NewData> GeneratedOperation<C, *, Variables>
  .withDataDeserializer(dataDeserializer: DeserializationStrategy<NewData>) =
  object : GeneratedOperation<C, NewData, Variables> {
    override val connector by this@withDataDeserializer::connector
    override val operationName by this@withDataDeserializer::operationName
    override val dataDeserializer
      get() = dataDeserializer
    override val variablesSerializer by this@withDataDeserializer::variablesSerializer

    override fun toString(): String =
      this@withDataDeserializer.toString() + " with data deserializer $dataDeserializer"
  }

fun <C : GeneratedConnector, Variables, NewData> GeneratedQuery<C, *, Variables>
  .withDataDeserializer(dataDeserializer: DeserializationStrategy<NewData>) =
  object : GeneratedQuery<C, NewData, Variables> {
    override val connector by this@withDataDeserializer::connector
    override val operationName by this@withDataDeserializer::operationName
    override val dataDeserializer
      get() = dataDeserializer
    override val variablesSerializer by this@withDataDeserializer::variablesSerializer

    override fun toString(): String =
      this@withDataDeserializer.toString() + " with data deserializer $dataDeserializer"
  }

fun <C : GeneratedConnector, Variables, NewData> GeneratedMutation<C, *, Variables>
  .withDataDeserializer(dataDeserializer: DeserializationStrategy<NewData>) =
  object : GeneratedMutation<C, NewData, Variables> {
    override val connector by this@withDataDeserializer::connector
    override val operationName by this@withDataDeserializer::operationName
    override val dataDeserializer
      get() = dataDeserializer
    override val variablesSerializer by this@withDataDeserializer::variablesSerializer

    override fun toString(): String =
      this@withDataDeserializer.toString() + " with data deserializer $dataDeserializer"
  }
