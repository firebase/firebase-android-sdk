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

public interface UpdateUuidVariantsByKeyMutation :
  GeneratedMutation<
    DemoConnector, UpdateUuidVariantsByKeyMutation.Data, UpdateUuidVariantsByKeyMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val key: UuidVariantsKey,
    val nonNullValue: OptionalVariable<java.util.UUID?>,
    val nullableWithNullValue: OptionalVariable<java.util.UUID?>,
    val nullableWithNonNullValue: OptionalVariable<java.util.UUID?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var key: UuidVariantsKey
      public var nonNullValue: java.util.UUID?
      public var nullableWithNullValue: java.util.UUID?
      public var nullableWithNonNullValue: java.util.UUID?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(key: UuidVariantsKey, block_: Builder.() -> Unit): Variables {
        var key = key
        var nonNullValue: OptionalVariable<java.util.UUID?> = OptionalVariable.Undefined
        var nullableWithNullValue: OptionalVariable<java.util.UUID?> = OptionalVariable.Undefined
        var nullableWithNonNullValue: OptionalVariable<java.util.UUID?> = OptionalVariable.Undefined

        return object : Builder {
            override var key: UuidVariantsKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                key = value_
              }

            override var nonNullValue: java.util.UUID?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nonNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNullValue: java.util.UUID?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNonNullValue: java.util.UUID?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithNonNullValue = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              key = key,
              nonNullValue = nonNullValue,
              nullableWithNullValue = nullableWithNullValue,
              nullableWithNonNullValue = nullableWithNonNullValue,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("uUIDVariants_update") val key: UuidVariantsKey?) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "UpdateUUIDVariantsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateUuidVariantsByKeyMutation.ref(
  key: UuidVariantsKey,
  block_: UpdateUuidVariantsByKeyMutation.Variables.Builder.() -> Unit
): MutationRef<UpdateUuidVariantsByKeyMutation.Data, UpdateUuidVariantsByKeyMutation.Variables> =
  ref(UpdateUuidVariantsByKeyMutation.Variables.build(key = key, block_))

public suspend fun UpdateUuidVariantsByKeyMutation.execute(
  key: UuidVariantsKey,
  block_: UpdateUuidVariantsByKeyMutation.Variables.Builder.() -> Unit
): MutationResult<UpdateUuidVariantsByKeyMutation.Data, UpdateUuidVariantsByKeyMutation.Variables> =
  ref(key = key, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
