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
