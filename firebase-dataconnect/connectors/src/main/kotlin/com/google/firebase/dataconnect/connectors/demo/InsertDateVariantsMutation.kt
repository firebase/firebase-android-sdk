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

public interface InsertDateVariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertDateVariantsMutation.Data, InsertDateVariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullValue: java.util.Date,
    val nullableWithNullValue: java.util.Date?,
    val nullableWithNonNullValue: java.util.Date?,
    val minValue: java.util.Date,
    val maxValue: java.util.Date,
    val emptyList: List<java.util.Date>,
    val nonEmptyList: List<java.util.Date>
  )

  @Serializable public data class Data(@SerialName("dateVariants_insert") val key: DateVariantsKey)

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertDateVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertDateVariantsMutation.ref(
  id: String,
  nonNullValue: java.util.Date,
  nullableWithNullValue: java.util.Date?,
  nullableWithNonNullValue: java.util.Date?,
  minValue: java.util.Date,
  maxValue: java.util.Date,
  emptyList: List<java.util.Date>,
  nonEmptyList: List<java.util.Date>
): MutationRef<InsertDateVariantsMutation.Data, InsertDateVariantsMutation.Variables> =
  ref(
    InsertDateVariantsMutation.Variables(
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

public suspend fun InsertDateVariantsMutation.execute(
  id: String,
  nonNullValue: java.util.Date,
  nullableWithNullValue: java.util.Date?,
  nullableWithNonNullValue: java.util.Date?,
  minValue: java.util.Date,
  maxValue: java.util.Date,
  emptyList: List<java.util.Date>,
  nonEmptyList: List<java.util.Date>
): MutationResult<InsertDateVariantsMutation.Data, InsertDateVariantsMutation.Variables> =
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
