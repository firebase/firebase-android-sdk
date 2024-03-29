@file:Suppress("SpellCheckingInspection")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

public interface InsertInt64variantsMutation {
  public val connector: DemoConnector

  public fun ref(variables: Variables): MutationRef<Data, Variables> =
    connector.dataConnect.mutation(operationName, variables, dataDeserializer, variablesSerializer)

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullWithZeroValue: Long,
    val nonNullWithPositiveValue: Long,
    val nonNullWithNegativeValue: Long,
    val nonNullWithMaxValue: Long,
    val nonNullWithMinValue: Long,
    val nullableWithNullValue: Long?,
    val nullableWithZeroValue: Long?,
    val nullableWithPositiveValue: Long?,
    val nullableWithNegativeValue: Long?,
    val nullableWithMaxValue: Long?,
    val nullableWithMinValue: Long?,
    val emptyList: List<Long>,
    val nonEmptyList: List<Long>
  )

  @Serializable
  public data class Data(@SerialName("int64Variants_insert") val key: Int64variantsKey)

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertInt64Variants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertInt64variantsMutation.ref(
  id: String,
  nonNullWithZeroValue: Long,
  nonNullWithPositiveValue: Long,
  nonNullWithNegativeValue: Long,
  nonNullWithMaxValue: Long,
  nonNullWithMinValue: Long,
  nullableWithNullValue: Long?,
  nullableWithZeroValue: Long?,
  nullableWithPositiveValue: Long?,
  nullableWithNegativeValue: Long?,
  nullableWithMaxValue: Long?,
  nullableWithMinValue: Long?,
  emptyList: List<Long>,
  nonEmptyList: List<Long>
): MutationRef<InsertInt64variantsMutation.Data, InsertInt64variantsMutation.Variables> =
  ref(
    InsertInt64variantsMutation.Variables(
      id = id,
      nonNullWithZeroValue = nonNullWithZeroValue,
      nonNullWithPositiveValue = nonNullWithPositiveValue,
      nonNullWithNegativeValue = nonNullWithNegativeValue,
      nonNullWithMaxValue = nonNullWithMaxValue,
      nonNullWithMinValue = nonNullWithMinValue,
      nullableWithNullValue = nullableWithNullValue,
      nullableWithZeroValue = nullableWithZeroValue,
      nullableWithPositiveValue = nullableWithPositiveValue,
      nullableWithNegativeValue = nullableWithNegativeValue,
      nullableWithMaxValue = nullableWithMaxValue,
      nullableWithMinValue = nullableWithMinValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList
    )
  )

public suspend fun InsertInt64variantsMutation.execute(
  id: String,
  nonNullWithZeroValue: Long,
  nonNullWithPositiveValue: Long,
  nonNullWithNegativeValue: Long,
  nonNullWithMaxValue: Long,
  nonNullWithMinValue: Long,
  nullableWithNullValue: Long?,
  nullableWithZeroValue: Long?,
  nullableWithPositiveValue: Long?,
  nullableWithNegativeValue: Long?,
  nullableWithMaxValue: Long?,
  nullableWithMinValue: Long?,
  emptyList: List<Long>,
  nonEmptyList: List<Long>
): MutationResult<InsertInt64variantsMutation.Data, InsertInt64variantsMutation.Variables> =
  ref(
      id = id,
      nonNullWithZeroValue = nonNullWithZeroValue,
      nonNullWithPositiveValue = nonNullWithPositiveValue,
      nonNullWithNegativeValue = nonNullWithNegativeValue,
      nonNullWithMaxValue = nonNullWithMaxValue,
      nonNullWithMinValue = nonNullWithMinValue,
      nullableWithNullValue = nullableWithNullValue,
      nullableWithZeroValue = nullableWithZeroValue,
      nullableWithPositiveValue = nullableWithPositiveValue,
      nullableWithNegativeValue = nullableWithNegativeValue,
      nullableWithMaxValue = nullableWithMaxValue,
      nullableWithMinValue = nullableWithMinValue,
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
