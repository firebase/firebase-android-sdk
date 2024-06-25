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

public interface UpdateBooleanVariantsByKeyMutation :
  GeneratedMutation<
    DemoConnector,
    UpdateBooleanVariantsByKeyMutation.Data,
    UpdateBooleanVariantsByKeyMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val key: BooleanVariantsKey,
    val nonNullWithTrueValue: OptionalVariable<Boolean?>,
    val nonNullWithFalseValue: OptionalVariable<Boolean?>,
    val nullableWithNullValue: OptionalVariable<Boolean?>,
    val nullableWithTrueValue: OptionalVariable<Boolean?>,
    val nullableWithFalseValue: OptionalVariable<Boolean?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var key: BooleanVariantsKey
      public var nonNullWithTrueValue: Boolean?
      public var nonNullWithFalseValue: Boolean?
      public var nullableWithNullValue: Boolean?
      public var nullableWithTrueValue: Boolean?
      public var nullableWithFalseValue: Boolean?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(key: BooleanVariantsKey, block_: Builder.() -> Unit): Variables {
        var key = key
        var nonNullWithTrueValue: OptionalVariable<Boolean?> = OptionalVariable.Undefined
        var nonNullWithFalseValue: OptionalVariable<Boolean?> = OptionalVariable.Undefined
        var nullableWithNullValue: OptionalVariable<Boolean?> = OptionalVariable.Undefined
        var nullableWithTrueValue: OptionalVariable<Boolean?> = OptionalVariable.Undefined
        var nullableWithFalseValue: OptionalVariable<Boolean?> = OptionalVariable.Undefined

        return object : Builder {
            override var key: BooleanVariantsKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                key = value_
              }

            override var nonNullWithTrueValue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithTrueValue = OptionalVariable.Value(value_)
              }

            override var nonNullWithFalseValue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullWithFalseValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNullValue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithTrueValue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithTrueValue = OptionalVariable.Value(value_)
              }

            override var nullableWithFalseValue: Boolean?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithFalseValue = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              key = key,
              nonNullWithTrueValue = nonNullWithTrueValue,
              nonNullWithFalseValue = nonNullWithFalseValue,
              nullableWithNullValue = nullableWithNullValue,
              nullableWithTrueValue = nullableWithTrueValue,
              nullableWithFalseValue = nullableWithFalseValue,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("booleanVariants_update") val key: BooleanVariantsKey?) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "UpdateBooleanVariantsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateBooleanVariantsByKeyMutation.ref(
  key: BooleanVariantsKey,
  block_: UpdateBooleanVariantsByKeyMutation.Variables.Builder.() -> Unit
): MutationRef<
  UpdateBooleanVariantsByKeyMutation.Data, UpdateBooleanVariantsByKeyMutation.Variables
> = ref(UpdateBooleanVariantsByKeyMutation.Variables.build(key = key, block_))

public suspend fun UpdateBooleanVariantsByKeyMutation.execute(
  key: BooleanVariantsKey,
  block_: UpdateBooleanVariantsByKeyMutation.Variables.Builder.() -> Unit
): MutationResult<
  UpdateBooleanVariantsByKeyMutation.Data, UpdateBooleanVariantsByKeyMutation.Variables
> = ref(key = key, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
