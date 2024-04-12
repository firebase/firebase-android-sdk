package com.google.firebase.dataconnect.generated

import com.google.firebase.dataconnect.OperationRef
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy

public interface GeneratedOperation<C : GeneratedConnector, Data, Variables> {
  public val connector: C

  public val operationName: String

  public val dataDeserializer: DeserializationStrategy<Data>
  public val variablesSerializer: SerializationStrategy<Variables>

  public fun ref(variables: Variables): OperationRef<Data, Variables> =
    connector.dataConnect.mutation(operationName, variables, dataDeserializer, variablesSerializer)
}
