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

public interface InsertIntVariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertIntVariantsMutation.Data, InsertIntVariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val nonNullWithZeroValue: Int,
    val nonNullWithPositiveValue: Int,
    val nonNullWithNegativeValue: Int,
    val nonNullWithMaxValue: Int,
    val nonNullWithMinValue: Int,
    val nullableWithNullValue: OptionalVariable<Int?>,
    val nullableWithZeroValue: OptionalVariable<Int?>,
    val nullableWithPositiveValue: OptionalVariable<Int?>,
    val nullableWithNegativeValue: OptionalVariable<Int?>,
    val nullableWithMaxValue: OptionalVariable<Int?>,
    val nullableWithMinValue: OptionalVariable<Int?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var nonNullWithZeroValue: Int
      public var nonNullWithPositiveValue: Int
      public var nonNullWithNegativeValue: Int
      public var nonNullWithMaxValue: Int
      public var nonNullWithMinValue: Int
      public var nullableWithNullValue: Int?
      public var nullableWithZeroValue: Int?
      public var nullableWithPositiveValue: Int?
      public var nullableWithNegativeValue: Int?
      public var nullableWithMaxValue: Int?
      public var nullableWithMinValue: Int?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        nonNullWithZeroValue: Int,
        nonNullWithPositiveValue: Int,
        nonNullWithNegativeValue: Int,
        nonNullWithMaxValue: Int,
        nonNullWithMinValue: Int,
        block_: Builder.() -> Unit
      ): Variables {
        var nonNullWithZeroValue = nonNullWithZeroValue
        var nonNullWithPositiveValue = nonNullWithPositiveValue
        var nonNullWithNegativeValue = nonNullWithNegativeValue
        var nonNullWithMaxValue = nonNullWithMaxValue
        var nonNullWithMinValue = nonNullWithMinValue
        var nullableWithNullValue: OptionalVariable<Int?> = OptionalVariable.Undefined
        var nullableWithZeroValue: OptionalVariable<Int?> = OptionalVariable.Undefined
        var nullableWithPositiveValue: OptionalVariable<Int?> = OptionalVariable.Undefined
        var nullableWithNegativeValue: OptionalVariable<Int?> = OptionalVariable.Undefined
        var nullableWithMaxValue: OptionalVariable<Int?> = OptionalVariable.Undefined
        var nullableWithMinValue: OptionalVariable<Int?> = OptionalVariable.Undefined

        return object : Builder {
            override var nonNullWithZeroValue: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithZeroValue = value_
              }

            override var nonNullWithPositiveValue: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithPositiveValue = value_
              }

            override var nonNullWithNegativeValue: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithNegativeValue = value_
              }

            override var nonNullWithMaxValue: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithMaxValue = value_
              }

            override var nonNullWithMinValue: Int
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithMinValue = value_
              }

            override var nullableWithNullValue: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithZeroValue: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithZeroValue = OptionalVariable.Value(value_)
              }

            override var nullableWithPositiveValue: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithPositiveValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNegativeValue: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNegativeValue = OptionalVariable.Value(value_)
              }

            override var nullableWithMaxValue: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithMaxValue = OptionalVariable.Value(value_)
              }

            override var nullableWithMinValue: Int?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithMinValue = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
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
            )
          }
      }
    }
  }

  @Serializable public data class Data(@SerialName("intVariants_insert") val key: IntVariantsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertIntVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertIntVariantsMutation.ref(
  nonNullWithZeroValue: Int,
  nonNullWithPositiveValue: Int,
  nonNullWithNegativeValue: Int,
  nonNullWithMaxValue: Int,
  nonNullWithMinValue: Int,
  block_: InsertIntVariantsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertIntVariantsMutation.Data, InsertIntVariantsMutation.Variables> =
  ref(
    InsertIntVariantsMutation.Variables.build(
      nonNullWithZeroValue = nonNullWithZeroValue,
      nonNullWithPositiveValue = nonNullWithPositiveValue,
      nonNullWithNegativeValue = nonNullWithNegativeValue,
      nonNullWithMaxValue = nonNullWithMaxValue,
      nonNullWithMinValue = nonNullWithMinValue,
      block_
    )
  )

public suspend fun InsertIntVariantsMutation.execute(
  nonNullWithZeroValue: Int,
  nonNullWithPositiveValue: Int,
  nonNullWithNegativeValue: Int,
  nonNullWithMaxValue: Int,
  nonNullWithMinValue: Int,
  block_: InsertIntVariantsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertIntVariantsMutation.Data, InsertIntVariantsMutation.Variables> =
  ref(
      nonNullWithZeroValue = nonNullWithZeroValue,
      nonNullWithPositiveValue = nonNullWithPositiveValue,
      nonNullWithNegativeValue = nonNullWithNegativeValue,
      nonNullWithMaxValue = nonNullWithMaxValue,
      nonNullWithMinValue = nonNullWithMinValue,
      block_
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
