@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class, TimestampSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface InsertTimestampVariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertTimestampVariantsMutation.Data, InsertTimestampVariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullValue: com.google.firebase.Timestamp,
    val nullableWithNullValue: com.google.firebase.Timestamp?,
    val nullableWithNonNullValue: com.google.firebase.Timestamp?,
    val minValue: com.google.firebase.Timestamp,
    val maxValue: com.google.firebase.Timestamp,
    val emptyList: List<com.google.firebase.Timestamp>,
    val nonEmptyList: List<com.google.firebase.Timestamp>
  )

  @Serializable
  public data class Data(@SerialName("timestampVariants_insert") val key: TimestampVariantsKey)

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertTimestampVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertTimestampVariantsMutation.ref(
  id: String,
  nonNullValue: com.google.firebase.Timestamp,
  nullableWithNullValue: com.google.firebase.Timestamp?,
  nullableWithNonNullValue: com.google.firebase.Timestamp?,
  minValue: com.google.firebase.Timestamp,
  maxValue: com.google.firebase.Timestamp,
  emptyList: List<com.google.firebase.Timestamp>,
  nonEmptyList: List<com.google.firebase.Timestamp>
): MutationRef<InsertTimestampVariantsMutation.Data, InsertTimestampVariantsMutation.Variables> =
  ref(
    InsertTimestampVariantsMutation.Variables(
      id = id,
      nonNullValue = nonNullValue,
      nullableWithNullValue = nullableWithNullValue,
      nullableWithNonNullValue = nullableWithNonNullValue,
      minValue = minValue,
      maxValue = maxValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList
    )
  )

public suspend fun InsertTimestampVariantsMutation.execute(
  id: String,
  nonNullValue: com.google.firebase.Timestamp,
  nullableWithNullValue: com.google.firebase.Timestamp?,
  nullableWithNonNullValue: com.google.firebase.Timestamp?,
  minValue: com.google.firebase.Timestamp,
  maxValue: com.google.firebase.Timestamp,
  emptyList: List<com.google.firebase.Timestamp>,
  nonEmptyList: List<com.google.firebase.Timestamp>
): MutationResult<InsertTimestampVariantsMutation.Data, InsertTimestampVariantsMutation.Variables> =
  ref(
      id = id,
      nonNullValue = nonNullValue,
      nullableWithNullValue = nullableWithNullValue,
      nullableWithNonNullValue = nullableWithNonNullValue,
      minValue = minValue,
      maxValue = maxValue,
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
