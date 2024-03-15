package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.*
import com.google.firebase.dataconnect.DataConnectUntypedVariables

internal fun <Data> MutationRef<Data, *>.withVariables(
  variables: DataConnectUntypedVariables
): MutationRef<Data, DataConnectUntypedVariables> =
  dataConnect.mutation(
    operationName = operationName,
    variables = variables,
    dataDeserializer = dataDeserializer,
    variablesSerializer = DataConnectUntypedVariables
  )
