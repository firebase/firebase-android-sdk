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

public interface InsertUuidvariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertUuidvariantsMutation.Data, InsertUuidvariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullValue: java.util.UUID,
    val nullableWithNullValue: OptionalVariable<java.util.UUID?>,
    val nullableWithNonNullValue: OptionalVariable<java.util.UUID?>,
    val emptyList: List<java.util.UUID>,
    val nonEmptyList: List<java.util.UUID>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var id: String
      public var nonNullValue: java.util.UUID
      public var nullableWithNullValue: java.util.UUID?
      public var nullableWithNonNullValue: java.util.UUID?
      public var emptyList: List<java.util.UUID>
      public var nonEmptyList: List<java.util.UUID>
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        id: String,
        nonNullValue: java.util.UUID,
        emptyList: List<java.util.UUID>,
        nonEmptyList: List<java.util.UUID>,
        block_: Builder.() -> Unit
      ): Variables {
        var id = id
        var nonNullValue = nonNullValue
        var nullableWithNullValue: OptionalVariable<java.util.UUID?> = OptionalVariable.Undefined
        var nullableWithNonNullValue: OptionalVariable<java.util.UUID?> = OptionalVariable.Undefined
        var emptyList = emptyList
        var nonEmptyList = nonEmptyList

        return object : Builder {
            override var id: String
              get() = id
              set(value_) {
                id = value_
              }

            override var nonNullValue: java.util.UUID
              get() = nonNullValue
              set(value_) {
                nonNullValue = value_
              }

            override var nullableWithNullValue: java.util.UUID?
              get() = nullableWithNullValue.valueOrNull()
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNonNullValue: java.util.UUID?
              get() = nullableWithNonNullValue.valueOrNull()
              set(value_) {
                nullableWithNonNullValue = OptionalVariable.Value(value_)
              }

            override var emptyList: List<java.util.UUID>
              get() = emptyList
              set(value_) {
                emptyList = value_
              }

            override var nonEmptyList: List<java.util.UUID>
              get() = nonEmptyList
              set(value_) {
                nonEmptyList = value_
              }
          }
          .apply(block_)
          .let {
            Variables(
              id = id,
              nonNullValue = nonNullValue,
              nullableWithNullValue = nullableWithNullValue,
              nullableWithNonNullValue = nullableWithNonNullValue,
              emptyList = emptyList,
              nonEmptyList = nonEmptyList,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("uUIDVariants_insert") val key: UuidvariantsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertUUIDVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertUuidvariantsMutation.ref(
  id: String,
  nonNullValue: java.util.UUID,
  emptyList: List<java.util.UUID>,
  nonEmptyList: List<java.util.UUID>,
  block_: InsertUuidvariantsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertUuidvariantsMutation.Data, InsertUuidvariantsMutation.Variables> =
  ref(
    InsertUuidvariantsMutation.Variables.build(
      id = id,
      nonNullValue = nonNullValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
  )

public suspend fun InsertUuidvariantsMutation.execute(
  id: String,
  nonNullValue: java.util.UUID,
  emptyList: List<java.util.UUID>,
  nonEmptyList: List<java.util.UUID>,
  block_: InsertUuidvariantsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertUuidvariantsMutation.Data, InsertUuidvariantsMutation.Variables> =
  ref(
      id = id,
      nonNullValue = nonNullValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
