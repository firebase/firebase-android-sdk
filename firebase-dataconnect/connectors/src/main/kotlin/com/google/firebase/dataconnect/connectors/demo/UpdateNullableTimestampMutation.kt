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

public interface UpdateNullableTimestampMutation :
  GeneratedMutation<
    DemoConnector, UpdateNullableTimestampMutation.Data, UpdateNullableTimestampMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val key: NullableTimestampKey,
    val value: OptionalVariable<com.google.firebase.Timestamp?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var key: NullableTimestampKey
      public var value: com.google.firebase.Timestamp?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(key: NullableTimestampKey, block_: Builder.() -> Unit): Variables {
        var key = key
        var value: OptionalVariable<com.google.firebase.Timestamp?> = OptionalVariable.Undefined

        return object : Builder {
            override var key: NullableTimestampKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                key = value_
              }

            override var value: com.google.firebase.Timestamp?
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
  public data class Data(@SerialName("nullableTimestamp_update") val key: NullableTimestampKey?) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "UpdateNullableTimestamp"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateNullableTimestampMutation.ref(
  key: NullableTimestampKey,
  block_: UpdateNullableTimestampMutation.Variables.Builder.() -> Unit
): MutationRef<UpdateNullableTimestampMutation.Data, UpdateNullableTimestampMutation.Variables> =
  ref(UpdateNullableTimestampMutation.Variables.build(key = key, block_))

public suspend fun UpdateNullableTimestampMutation.execute(
  key: NullableTimestampKey,
  block_: UpdateNullableTimestampMutation.Variables.Builder.() -> Unit
): MutationResult<UpdateNullableTimestampMutation.Data, UpdateNullableTimestampMutation.Variables> =
  ref(key = key, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
