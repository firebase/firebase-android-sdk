package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.QueryRef
import kotlinx.serialization.DeserializationStrategy

fun <Data, Variables, NewData> QueryRef<Data, Variables>.withDataDeserializer(
  newDataDeserializer: DeserializationStrategy<NewData>
): QueryRef<NewData, Variables> =
  dataConnect.query(
    operationName = operationName,
    variables = variables,
    dataDeserializer = newDataDeserializer,
    variablesSerializer = variablesSerializer
  )
