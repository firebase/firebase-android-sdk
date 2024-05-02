@file:Suppress("SpellCheckingInspection", "LocalVariableName")
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

public interface InsertNested2Mutation :
  GeneratedMutation<DemoConnector, InsertNested2Mutation.Data, InsertNested2Mutation.Variables> {

  @Serializable
  public data class Variables(
    val nested3: Nested3Key,
    val nested3NullableNonNull: OptionalVariable<Nested3Key?>,
    val nested3NullableNull: OptionalVariable<Nested3Key?>,
    val value: String
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var nested3: Nested3Key
      public var nested3NullableNonNull: Nested3Key?
      public var nested3NullableNull: Nested3Key?
      public var value: String
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(nested3: Nested3Key, value: String, block_: Builder.() -> Unit): Variables {
        var nested3 = nested3
        var nested3NullableNonNull: OptionalVariable<Nested3Key?> = OptionalVariable.Undefined
        var nested3NullableNull: OptionalVariable<Nested3Key?> = OptionalVariable.Undefined
        var value = value

        return object : Builder {
            override var nested3: Nested3Key
              get() = nested3
              set(value_) {
                nested3 = value_
              }

            override var nested3NullableNonNull: Nested3Key?
              get() = nested3NullableNonNull.valueOrNull()
              set(value_) {
                nested3NullableNonNull = OptionalVariable.Value(value_)
              }

            override var nested3NullableNull: Nested3Key?
              get() = nested3NullableNull.valueOrNull()
              set(value_) {
                nested3NullableNull = OptionalVariable.Value(value_)
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
              nested3 = nested3,
              nested3NullableNonNull = nested3NullableNonNull,
              nested3NullableNull = nested3NullableNull,
              value = value,
            )
          }
      }
    }
  }

  @Serializable public data class Data(@SerialName("nested2_insert") val key: Nested2Key) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertNested2"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertNested2Mutation.ref(
  nested3: Nested3Key,
  value: String,
  block_: InsertNested2Mutation.Variables.Builder.() -> Unit
): MutationRef<InsertNested2Mutation.Data, InsertNested2Mutation.Variables> =
  ref(InsertNested2Mutation.Variables.build(nested3 = nested3, value = value, block_))

public suspend fun InsertNested2Mutation.execute(
  nested3: Nested3Key,
  value: String,
  block_: InsertNested2Mutation.Variables.Builder.() -> Unit
): MutationResult<InsertNested2Mutation.Data, InsertNested2Mutation.Variables> =
  ref(nested3 = nested3, value = value, block_).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
