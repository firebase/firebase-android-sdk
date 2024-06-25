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

public interface UpdateInt64variantsByKeyMutation :
  GeneratedMutation<
    DemoConnector, UpdateInt64variantsByKeyMutation.Data, UpdateInt64variantsByKeyMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val key: Int64variantsKey,
    val nonNullWithZeroValue: OptionalVariable<Long?>,
    val nonNullWithPositiveValue: OptionalVariable<Long?>,
    val nonNullWithNegativeValue: OptionalVariable<Long?>,
    val nonNullWithMaxValue: OptionalVariable<Long?>,
    val nonNullWithMinValue: OptionalVariable<Long?>,
    val nullableWithNullValue: OptionalVariable<Long?>,
    val nullableWithZeroValue: OptionalVariable<Long?>,
    val nullableWithPositiveValue: OptionalVariable<Long?>,
    val nullableWithNegativeValue: OptionalVariable<Long?>,
    val nullableWithMaxValue: OptionalVariable<Long?>,
    val nullableWithMinValue: OptionalVariable<Long?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var key: Int64variantsKey
      public var nonNullWithZeroValue: Long?
      public var nonNullWithPositiveValue: Long?
      public var nonNullWithNegativeValue: Long?
      public var nonNullWithMaxValue: Long?
      public var nonNullWithMinValue: Long?
      public var nullableWithNullValue: Long?
      public var nullableWithZeroValue: Long?
      public var nullableWithPositiveValue: Long?
      public var nullableWithNegativeValue: Long?
      public var nullableWithMaxValue: Long?
      public var nullableWithMinValue: Long?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(key: Int64variantsKey, block_: Builder.() -> Unit): Variables {
        var key = key
        var nonNullWithZeroValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nonNullWithPositiveValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nonNullWithNegativeValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nonNullWithMaxValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nonNullWithMinValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithNullValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithZeroValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithPositiveValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithNegativeValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithMaxValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithMinValue: OptionalVariable<Long?> = OptionalVariable.Undefined

        return object : Builder {
            override var key: Int64variantsKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                key = value_
              }

            override var nonNullWithZeroValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithZeroValue = OptionalVariable.Value(value_)
              }

            override var nonNullWithPositiveValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithPositiveValue = OptionalVariable.Value(value_)
              }

            override var nonNullWithNegativeValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithNegativeValue = OptionalVariable.Value(value_)
              }

            override var nonNullWithMaxValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithMaxValue = OptionalVariable.Value(value_)
              }

            override var nonNullWithMinValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithMinValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNullValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithZeroValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithZeroValue = OptionalVariable.Value(value_)
              }

            override var nullableWithPositiveValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithPositiveValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNegativeValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNegativeValue = OptionalVariable.Value(value_)
              }

            override var nullableWithMaxValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithMaxValue = OptionalVariable.Value(value_)
              }

            override var nullableWithMinValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithMinValue = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              key = key,
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

  @Serializable
  public data class Data(@SerialName("int64Variants_update") val key: Int64variantsKey?) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "UpdateInt64VariantsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateInt64variantsByKeyMutation.ref(
  key: Int64variantsKey,
  block_: UpdateInt64variantsByKeyMutation.Variables.Builder.() -> Unit
): MutationRef<UpdateInt64variantsByKeyMutation.Data, UpdateInt64variantsByKeyMutation.Variables> =
  ref(UpdateInt64variantsByKeyMutation.Variables.build(key = key, block_))

public suspend fun UpdateInt64variantsByKeyMutation.execute(
  key: Int64variantsKey,
  block_: UpdateInt64variantsByKeyMutation.Variables.Builder.() -> Unit
): MutationResult<
  UpdateInt64variantsByKeyMutation.Data, UpdateInt64variantsByKeyMutation.Variables
> = ref(key = key, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
