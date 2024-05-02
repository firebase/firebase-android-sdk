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

public interface InsertNested1Mutation :
  GeneratedMutation<DemoConnector, InsertNested1Mutation.Data, InsertNested1Mutation.Variables> {

  @Serializable
  public data class Variables(
    val nested1: OptionalVariable<Nested1Key?>,
    val nested2: Nested2Key,
    val nested2NullableNonNull: OptionalVariable<Nested2Key?>,
    val nested2NullableNull: OptionalVariable<Nested2Key?>,
    val value: String
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var nested1: Nested1Key?
      public var nested2: Nested2Key
      public var nested2NullableNonNull: Nested2Key?
      public var nested2NullableNull: Nested2Key?
      public var value: String
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(nested2: Nested2Key, value: String, block_: Builder.() -> Unit): Variables {
        var nested1: OptionalVariable<Nested1Key?> = OptionalVariable.Undefined
        var nested2 = nested2
        var nested2NullableNonNull: OptionalVariable<Nested2Key?> = OptionalVariable.Undefined
        var nested2NullableNull: OptionalVariable<Nested2Key?> = OptionalVariable.Undefined
        var value = value

        return object : Builder {
            override var nested1: Nested1Key?
              get() = nested1.valueOrNull()
              set(value_) {
                nested1 = OptionalVariable.Value(value_)
              }

            override var nested2: Nested2Key
              get() = nested2
              set(value_) {
                nested2 = value_
              }

            override var nested2NullableNonNull: Nested2Key?
              get() = nested2NullableNonNull.valueOrNull()
              set(value_) {
                nested2NullableNonNull = OptionalVariable.Value(value_)
              }

            override var nested2NullableNull: Nested2Key?
              get() = nested2NullableNull.valueOrNull()
              set(value_) {
                nested2NullableNull = OptionalVariable.Value(value_)
              }

            override var value: String
              get() = value
              set(value_) {
                value = value_
              }
          }
          .apply(block_)
          .let {
            Variables(
              nested1 = nested1,
              nested2 = nested2,
              nested2NullableNonNull = nested2NullableNonNull,
              nested2NullableNull = nested2NullableNull,
              value = value,
            )
          }
      }
    }
  }

  @Serializable public data class Data(@SerialName("nested1_insert") val key: Nested1Key) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertNested1"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertNested1Mutation.ref(
  nested2: Nested2Key,
  value: String,
  block_: InsertNested1Mutation.Variables.Builder.() -> Unit
): MutationRef<InsertNested1Mutation.Data, InsertNested1Mutation.Variables> =
  ref(InsertNested1Mutation.Variables.build(nested2 = nested2, value = value, block_))

public suspend fun InsertNested1Mutation.execute(
  nested2: Nested2Key,
  value: String,
  block_: InsertNested1Mutation.Variables.Builder.() -> Unit
): MutationResult<InsertNested1Mutation.Data, InsertNested1Mutation.Variables> =
  ref(nested2 = nested2, value = value, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
