package com.google.firebase.dataconnect.generated

import com.google.firebase.dataconnect.QueryRef

public interface GeneratedQuery<C : GeneratedConnector, Data, Variables> :
  GeneratedOperation<C, Data, Variables> {
  override fun ref(variables: Variables): QueryRef<Data, Variables> =
    connector.dataConnect.query(operationName, variables, dataDeserializer, variablesSerializer)
}
