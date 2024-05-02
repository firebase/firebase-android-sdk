@file:Suppress(
  "KotlinRedundantDiagnosticSuppress",
  "LocalVariableName",
  "RedundantVisibilityModifier",
  "RemoveEmptyClassBody",
  "SpellCheckingInspection",
  "LocalVariableName",
  "unused",
)
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

public interface InsertDateVariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertDateVariantsMutation.Data, InsertDateVariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullValue: java.util.Date,
    val nullableWithNullValue: OptionalVariable<java.util.Date?>,
    val nullableWithNonNullValue: OptionalVariable<java.util.Date?>,
    val minValue: java.util.Date,
    val maxValue: java.util.Date,
    val nonZeroTime: java.util.Date,
    val emptyList: List<java.util.Date>,
    val nonEmptyList: List<java.util.Date>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var id: String
      public var nonNullValue: java.util.Date
      public var nullableWithNullValue: java.util.Date?
      public var nullableWithNonNullValue: java.util.Date?
      public var minValue: java.util.Date
      public var maxValue: java.util.Date
      public var nonZeroTime: java.util.Date
      public var emptyList: List<java.util.Date>
      public var nonEmptyList: List<java.util.Date>
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        id: String,
        nonNullValue: java.util.Date,
        minValue: java.util.Date,
        maxValue: java.util.Date,
        nonZeroTime: java.util.Date,
        emptyList: List<java.util.Date>,
        nonEmptyList: List<java.util.Date>,
        block_: Builder.() -> Unit
      ): Variables {
        var id = id
        var nonNullValue = nonNullValue
        var nullableWithNullValue: OptionalVariable<java.util.Date?> = OptionalVariable.Undefined
        var nullableWithNonNullValue: OptionalVariable<java.util.Date?> = OptionalVariable.Undefined
        var minValue = minValue
        var maxValue = maxValue
        var nonZeroTime = nonZeroTime
        var emptyList = emptyList
        var nonEmptyList = nonEmptyList

        return object : Builder {
            override var id: String
              get() = id
              set(value_) {
                id = value_
              }

            override var nonNullValue: java.util.Date
              get() = nonNullValue
              set(value_) {
                nonNullValue = value_
              }

            override var nullableWithNullValue: java.util.Date?
              get() = nullableWithNullValue.valueOrNull()
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNonNullValue: java.util.Date?
              get() = nullableWithNonNullValue.valueOrNull()
              set(value_) {
                nullableWithNonNullValue = OptionalVariable.Value(value_)
              }

            override var minValue: java.util.Date
              get() = minValue
              set(value_) {
                minValue = value_
              }

            override var maxValue: java.util.Date
              get() = maxValue
              set(value_) {
                maxValue = value_
              }

            override var nonZeroTime: java.util.Date
              get() = nonZeroTime
              set(value_) {
                nonZeroTime = value_
              }

            override var emptyList: List<java.util.Date>
              get() = emptyList
              set(value_) {
                emptyList = value_
              }

            override var nonEmptyList: List<java.util.Date>
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
              nonZeroTime = nonZeroTime,
              emptyList = emptyList,
              nonEmptyList = nonEmptyList,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("dateVariants_insert") val key: DateVariantsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertDateVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertDateVariantsMutation.ref(
  id: String,
  nonNullValue: java.util.Date,
  minValue: java.util.Date,
  maxValue: java.util.Date,
  nonZeroTime: java.util.Date,
  emptyList: List<java.util.Date>,
  nonEmptyList: List<java.util.Date>,
  block_: InsertDateVariantsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertDateVariantsMutation.Data, InsertDateVariantsMutation.Variables> =
  ref(
    InsertDateVariantsMutation.Variables.build(
      id = id,
      nonNullValue = nonNullValue,
      minValue = minValue,
      maxValue = maxValue,
      nonZeroTime = nonZeroTime,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
  )

public suspend fun InsertDateVariantsMutation.execute(
  id: String,
  nonNullValue: java.util.Date,
  minValue: java.util.Date,
  maxValue: java.util.Date,
  nonZeroTime: java.util.Date,
  emptyList: List<java.util.Date>,
  nonEmptyList: List<java.util.Date>,
  block_: InsertDateVariantsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertDateVariantsMutation.Data, InsertDateVariantsMutation.Variables> =
  ref(
      id = id,
      nonNullValue = nonNullValue,
      minValue = minValue,
      maxValue = maxValue,
      nonZeroTime = nonZeroTime,
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
