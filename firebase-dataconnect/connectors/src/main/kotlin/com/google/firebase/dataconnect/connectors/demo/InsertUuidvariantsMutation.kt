@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface InsertUuidvariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertUuidvariantsMutation.Data, InsertUuidvariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullValue: java.util.UUID,
    val nullableWithNullValue: java.util.UUID?,
    val nullableWithNonNullValue: java.util.UUID?,
    val emptyList: List<java.util.UUID>,
    val nonEmptyList: List<java.util.UUID>
  )

  @Serializable public data class Data(@SerialName("uUIDVariants_insert") val key: UuidvariantsKey)

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertUUIDVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertUuidvariantsMutation.ref(
  id: String,
  nonNullValue: java.util.UUID,
  nullableWithNullValue: java.util.UUID?,
  nullableWithNonNullValue: java.util.UUID?,
  emptyList: List<java.util.UUID>,
  nonEmptyList: List<java.util.UUID>
): MutationRef<InsertUuidvariantsMutation.Data, InsertUuidvariantsMutation.Variables> =
  ref(
    InsertUuidvariantsMutation.Variables(
      id = id,
      nonNullValue = nonNullValue,
      nullableWithNullValue = nullableWithNullValue,
      nullableWithNonNullValue = nullableWithNonNullValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList
    )
  )

public suspend fun InsertUuidvariantsMutation.execute(
  id: String,
  nonNullValue: java.util.UUID,
  nullableWithNullValue: java.util.UUID?,
  nullableWithNonNullValue: java.util.UUID?,
  emptyList: List<java.util.UUID>,
  nonEmptyList: List<java.util.UUID>
): MutationResult<InsertUuidvariantsMutation.Data, InsertUuidvariantsMutation.Variables> =
  ref(
      id = id,
      nonNullValue = nonNullValue,
      nullableWithNullValue = nullableWithNullValue,
      nullableWithNonNullValue = nullableWithNonNullValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
