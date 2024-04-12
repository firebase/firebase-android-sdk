package com.google.firebase.dataconnect.generated

import com.google.firebase.dataconnect.MutationRef

public interface GeneratedMutation<C : GeneratedConnector, Data, Variables> :
  GeneratedOperation<C, Data, Variables> {
  override fun ref(variables: Variables): MutationRef<Data, Variables> =
    connector.dataConnect.mutation(operationName, variables, dataDeserializer, variablesSerializer)
}
