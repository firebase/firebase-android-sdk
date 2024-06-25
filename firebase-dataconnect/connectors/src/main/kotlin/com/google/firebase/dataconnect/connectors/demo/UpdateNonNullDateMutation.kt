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

public interface UpdateNonNullDateMutation :
  GeneratedMutation<
    DemoConnector, UpdateNonNullDateMutation.Data, UpdateNonNullDateMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val key: NonNullDateKey,
    val value: OptionalVariable<java.util.Date?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var key: NonNullDateKey
      public var value: java.util.Date?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(key: NonNullDateKey, block_: Builder.() -> Unit): Variables {
        var key = key
        var value: OptionalVariable<java.util.Date?> = OptionalVariable.Undefined

        return object : Builder {
            override var key: NonNullDateKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                key = value_
              }

            override var value: java.util.Date?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                value = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              key = key,
              value = value,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("nonNullDate_update") val key: NonNullDateKey?) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "UpdateNonNullDate"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateNonNullDateMutation.ref(
  key: NonNullDateKey,
  block_: UpdateNonNullDateMutation.Variables.Builder.() -> Unit
): MutationRef<UpdateNonNullDateMutation.Data, UpdateNonNullDateMutation.Variables> =
  ref(UpdateNonNullDateMutation.Variables.build(key = key, block_))

public suspend fun UpdateNonNullDateMutation.execute(
  key: NonNullDateKey,
  block_: UpdateNonNullDateMutation.Variables.Builder.() -> Unit
): MutationResult<UpdateNonNullDateMutation.Data, UpdateNonNullDateMutation.Variables> =
  ref(key = key, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
