@file:Suppress("SpellCheckingInspection", "LocalVariableName")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class, TimestampSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.OptionalVariable
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
    val nullableWithNullValue: OptionalVariable<com.google.firebase.Timestamp?>,
    val nullableWithNonNullValue: OptionalVariable<com.google.firebase.Timestamp?>,
    val minValue: com.google.firebase.Timestamp,
    val maxValue: com.google.firebase.Timestamp,
    val emptyList: List<com.google.firebase.Timestamp>,
    val nonEmptyList: List<com.google.firebase.Timestamp>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var id: String
      public var nonNullValue: com.google.firebase.Timestamp
      public var nullableWithNullValue: com.google.firebase.Timestamp?
      public var nullableWithNonNullValue: com.google.firebase.Timestamp?
      public var minValue: com.google.firebase.Timestamp
      public var maxValue: com.google.firebase.Timestamp
      public var emptyList: List<com.google.firebase.Timestamp>
      public var nonEmptyList: List<com.google.firebase.Timestamp>
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        id: String,
        nonNullValue: com.google.firebase.Timestamp,
        minValue: com.google.firebase.Timestamp,
        maxValue: com.google.firebase.Timestamp,
        emptyList: List<com.google.firebase.Timestamp>,
        nonEmptyList: List<com.google.firebase.Timestamp>,
        block_: Builder.() -> Unit
      ): Variables {
        var id = id
        var nonNullValue = nonNullValue
        var nullableWithNullValue: OptionalVariable<com.google.firebase.Timestamp?> =
          OptionalVariable.Undefined
        var nullableWithNonNullValue: OptionalVariable<com.google.firebase.Timestamp?> =
          OptionalVariable.Undefined
        var minValue = minValue
        var maxValue = maxValue
        var emptyList = emptyList
        var nonEmptyList = nonEmptyList

        return object : Builder {
            override var id: String
              get() = id
              set(value_) {
                id = value_
              }

            override var nonNullValue: com.google.firebase.Timestamp
              get() = nonNullValue
              set(value_) {
                nonNullValue = value_
              }

            override var nullableWithNullValue: com.google.firebase.Timestamp?
              get() = nullableWithNullValue.valueOrNull()
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNonNullValue: com.google.firebase.Timestamp?
              get() = nullableWithNonNullValue.valueOrNull()
              set(value_) {
                nullableWithNonNullValue = OptionalVariable.Value(value_)
              }

            override var minValue: com.google.firebase.Timestamp
              get() = minValue
              set(value_) {
                minValue = value_
              }

            override var maxValue: com.google.firebase.Timestamp
              get() = maxValue
              set(value_) {
                maxValue = value_
              }

            override var emptyList: List<com.google.firebase.Timestamp>
              get() = emptyList
              set(value_) {
                emptyList = value_
              }

            override var nonEmptyList: List<com.google.firebase.Timestamp>
              get() = nonEmptyList
              set(value_) {
                nonEmptyList = value_
              }
          }
          .apply(block_)
          .let {
            Variables(
              id = id,
              nonNullValue = nonNullValue,
              nullableWithNullValue = nullableWithNullValue,
              nullableWithNonNullValue = nullableWithNonNullValue,
              minValue = minValue,
              maxValue = maxValue,
              emptyList = emptyList,
              nonEmptyList = nonEmptyList,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("timestampVariants_insert") val key: TimestampVariantsKey) {}

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
  minValue: com.google.firebase.Timestamp,
  maxValue: com.google.firebase.Timestamp,
  emptyList: List<com.google.firebase.Timestamp>,
  nonEmptyList: List<com.google.firebase.Timestamp>,
  block_: InsertTimestampVariantsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertTimestampVariantsMutation.Data, InsertTimestampVariantsMutation.Variables> =
  ref(
    InsertTimestampVariantsMutation.Variables.build(
      id = id,
      nonNullValue = nonNullValue,
      minValue = minValue,
      maxValue = maxValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
  )

public suspend fun InsertTimestampVariantsMutation.execute(
  id: String,
  nonNullValue: com.google.firebase.Timestamp,
  minValue: com.google.firebase.Timestamp,
  maxValue: com.google.firebase.Timestamp,
  emptyList: List<com.google.firebase.Timestamp>,
  nonEmptyList: List<com.google.firebase.Timestamp>,
  block_: InsertTimestampVariantsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertTimestampVariantsMutation.Data, InsertTimestampVariantsMutation.Variables> =
  ref(
      id = id,
      nonNullValue = nonNullValue,
      minValue = minValue,
      maxValue = maxValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
