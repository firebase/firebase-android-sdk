@file:Suppress("SpellCheckingInspection")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

public interface InsertOneNullableStringFieldMutation {
  public val connector: DemoConnector

  public fun ref(variables: Variables): MutationRef<Data, Variables> =
    connector.dataConnect.mutation(operationName, variables, dataDeserializer, variablesSerializer)

  @Serializable public data class Variables(val id: String, val value: String?)

  @Serializable
  public data class Data(
    @SerialName("oneNullableStringField_insert") val key: OneNullableStringFieldKey
  )

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertOneNullableStringField"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertOneNullableStringFieldMutation.ref(
  id: String,
  value: String?
): MutationRef<
  InsertOneNullableStringFieldMutation.Data, InsertOneNullableStringFieldMutation.Variables
> = ref(InsertOneNullableStringFieldMutation.Variables(id = id, value = value))

public suspend fun InsertOneNullableStringFieldMutation.execute(
  id: String,
  value: String?
): MutationResult<
  InsertOneNullableStringFieldMutation.Data, InsertOneNullableStringFieldMutation.Variables
> = ref(id = id, value = value).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
