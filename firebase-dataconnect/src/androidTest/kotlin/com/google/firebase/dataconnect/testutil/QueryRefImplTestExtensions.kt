package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.DataConnectUntypedVariables

internal fun <Data> QueryRef<Data, *>.withVariables(
  variables: DataConnectUntypedVariables
): QueryRef<Data, DataConnectUntypedVariables> =
  dataConnect.query(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = DataConnectUntypedVariables
  )
