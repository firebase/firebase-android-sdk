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

public interface InsertManyToOneParentMutation :
  GeneratedMutation<
    DemoConnector, InsertManyToOneParentMutation.Data, InsertManyToOneParentMutation.Variables
  > {

  @Serializable
  public data class Variables(val child: OptionalVariable<ManyToOneChildKey?>) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var child: ManyToOneChildKey?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(block_: Builder.() -> Unit): Variables {
        var child: OptionalVariable<ManyToOneChildKey?> = OptionalVariable.Undefined

        return object : Builder {
            override var child: ManyToOneChildKey?
              get() = child.valueOrNull()
              set(value_) {
                child = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              child = child,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("manyToOneParent_insert") val key: ManyToOneParentKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertManyToOneParent"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertManyToOneParentMutation.ref(
  block_: InsertManyToOneParentMutation.Variables.Builder.() -> Unit
): MutationRef<InsertManyToOneParentMutation.Data, InsertManyToOneParentMutation.Variables> =
  ref(InsertManyToOneParentMutation.Variables.build(block_))

public suspend fun InsertManyToOneParentMutation.execute(
  block_: InsertManyToOneParentMutation.Variables.Builder.() -> Unit
): MutationResult<InsertManyToOneParentMutation.Data, InsertManyToOneParentMutation.Variables> =
  ref(block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
