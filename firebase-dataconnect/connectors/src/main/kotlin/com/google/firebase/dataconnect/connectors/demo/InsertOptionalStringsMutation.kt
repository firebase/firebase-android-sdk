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

public interface InsertOptionalStringsMutation :
  GeneratedMutation<
    DemoConnector, InsertOptionalStringsMutation.Data, InsertOptionalStringsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val required1: String,
    val required2: String,
    val nullable1: OptionalVariable<String?>,
    val nullable2: OptionalVariable<String?>,
    val nullable3: OptionalVariable<String?>,
    val nullableWithSchemaDefault: OptionalVariable<String?>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var required1: String
      public var required2: String
      public var nullable1: String?
      public var nullable2: String?
      public var nullable3: String?
      public var nullableWithSchemaDefault: String?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        required1: String,
        required2: String,
        block_: Builder.() -> Unit
      ): Variables {
        var required1 = required1
        var required2 = required2
        var nullable1: OptionalVariable<String?> = OptionalVariable.Undefined
        var nullable2: OptionalVariable<String?> = OptionalVariable.Undefined
        var nullable3: OptionalVariable<String?> = OptionalVariable.Undefined
        var nullableWithSchemaDefault: OptionalVariable<String?> = OptionalVariable.Undefined

        return object : Builder {
            override var required1: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                required1 = value_
              }

            override var required2: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                required2 = value_
              }

            override var nullable1: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullable1 = OptionalVariable.Value(value_)
              }

            override var nullable2: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullable2 = OptionalVariable.Value(value_)
              }

            override var nullable3: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullable3 = OptionalVariable.Value(value_)
              }

            override var nullableWithSchemaDefault: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                nullableWithSchemaDefault = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              required1 = required1,
              required2 = required2,
              nullable1 = nullable1,
              nullable2 = nullable2,
              nullable3 = nullable3,
              nullableWithSchemaDefault = nullableWithSchemaDefault,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("optionalStrings_insert") val key: OptionalStringsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertOptionalStrings"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertOptionalStringsMutation.ref(
  required1: String,
  required2: String,
  block_: InsertOptionalStringsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertOptionalStringsMutation.Data, InsertOptionalStringsMutation.Variables> =
  ref(
    InsertOptionalStringsMutation.Variables.build(
      required1 = required1,
      required2 = required2,
      block_
    )
  )

public suspend fun InsertOptionalStringsMutation.execute(
  required1: String,
  required2: String,
  block_: InsertOptionalStringsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertOptionalStringsMutation.Data, InsertOptionalStringsMutation.Variables> =
  ref(required1 = required1, required2 = required2, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
