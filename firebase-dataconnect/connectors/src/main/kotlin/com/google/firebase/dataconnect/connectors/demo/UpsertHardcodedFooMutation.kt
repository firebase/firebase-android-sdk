@file:Suppress("SpellCheckingInspection")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

public interface UpsertHardcodedFooMutation {
  public val connector: DemoConnector

  public fun ref(variables: Unit): MutationRef<Data, Unit> =
    connector.dataConnect.mutation(operationName, variables, dataDeserializer, variablesSerializer)

  @Serializable public data class Data(@SerialName("foo_upsert") val key: FooKey)

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "UpsertHardcodedFoo"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Unit> = serializer()
  }
}

public fun UpsertHardcodedFooMutation.ref(): MutationRef<UpsertHardcodedFooMutation.Data, Unit> =
  ref(Unit)

public suspend fun UpsertHardcodedFooMutation.execute():
  MutationResult<UpsertHardcodedFooMutation.Data, Unit> = ref().execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
