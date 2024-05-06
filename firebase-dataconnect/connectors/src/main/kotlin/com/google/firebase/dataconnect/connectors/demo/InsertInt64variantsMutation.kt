/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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

public interface InsertInt64variantsMutation :
  GeneratedMutation<
    DemoConnector, InsertInt64variantsMutation.Data, InsertInt64variantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullWithZeroValue: Long,
    val nonNullWithPositiveValue: Long,
    val nonNullWithNegativeValue: Long,
    val nonNullWithMaxValue: Long,
    val nonNullWithMinValue: Long,
    val nullableWithNullValue: OptionalVariable<Long?>,
    val nullableWithZeroValue: OptionalVariable<Long?>,
    val nullableWithPositiveValue: OptionalVariable<Long?>,
    val nullableWithNegativeValue: OptionalVariable<Long?>,
    val nullableWithMaxValue: OptionalVariable<Long?>,
    val nullableWithMinValue: OptionalVariable<Long?>,
    val emptyList: List<Long>,
    val nonEmptyList: List<Long>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var id: String
      public var nonNullWithZeroValue: Long
      public var nonNullWithPositiveValue: Long
      public var nonNullWithNegativeValue: Long
      public var nonNullWithMaxValue: Long
      public var nonNullWithMinValue: Long
      public var nullableWithNullValue: Long?
      public var nullableWithZeroValue: Long?
      public var nullableWithPositiveValue: Long?
      public var nullableWithNegativeValue: Long?
      public var nullableWithMaxValue: Long?
      public var nullableWithMinValue: Long?
      public var emptyList: List<Long>
      public var nonEmptyList: List<Long>
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        id: String,
        nonNullWithZeroValue: Long,
        nonNullWithPositiveValue: Long,
        nonNullWithNegativeValue: Long,
        nonNullWithMaxValue: Long,
        nonNullWithMinValue: Long,
        emptyList: List<Long>,
        nonEmptyList: List<Long>,
        block_: Builder.() -> Unit
      ): Variables {
        var id = id
        var nonNullWithZeroValue = nonNullWithZeroValue
        var nonNullWithPositiveValue = nonNullWithPositiveValue
        var nonNullWithNegativeValue = nonNullWithNegativeValue
        var nonNullWithMaxValue = nonNullWithMaxValue
        var nonNullWithMinValue = nonNullWithMinValue
        var nullableWithNullValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithZeroValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithPositiveValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithNegativeValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithMaxValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var nullableWithMinValue: OptionalVariable<Long?> = OptionalVariable.Undefined
        var emptyList = emptyList
        var nonEmptyList = nonEmptyList

        return object : Builder {
            override var id: String
              get() = id
              set(value_) {
                id = value_
              }

            override var nonNullWithZeroValue: Long
              get() = nonNullWithZeroValue
              set(value_) {
                nonNullWithZeroValue = value_
              }

            override var nonNullWithPositiveValue: Long
              get() = nonNullWithPositiveValue
              set(value_) {
                nonNullWithPositiveValue = value_
              }

            override var nonNullWithNegativeValue: Long
              get() = nonNullWithNegativeValue
              set(value_) {
                nonNullWithNegativeValue = value_
              }

            override var nonNullWithMaxValue: Long
              get() = nonNullWithMaxValue
              set(value_) {
                nonNullWithMaxValue = value_
              }

            override var nonNullWithMinValue: Long
              get() = nonNullWithMinValue
              set(value_) {
                nonNullWithMinValue = value_
              }

            override var nullableWithNullValue: Long?
              get() = nullableWithNullValue.valueOrNull()
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithZeroValue: Long?
              get() = nullableWithZeroValue.valueOrNull()
              set(value_) {
                nullableWithZeroValue = OptionalVariable.Value(value_)
              }

            override var nullableWithPositiveValue: Long?
              get() = nullableWithPositiveValue.valueOrNull()
              set(value_) {
                nullableWithPositiveValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNegativeValue: Long?
              get() = nullableWithNegativeValue.valueOrNull()
              set(value_) {
                nullableWithNegativeValue = OptionalVariable.Value(value_)
              }

            override var nullableWithMaxValue: Long?
              get() = nullableWithMaxValue.valueOrNull()
              set(value_) {
                nullableWithMaxValue = OptionalVariable.Value(value_)
              }

            override var nullableWithMinValue: Long?
              get() = nullableWithMinValue.valueOrNull()
              set(value_) {
                nullableWithMinValue = OptionalVariable.Value(value_)
              }

            override var emptyList: List<Long>
              get() = emptyList
              set(value_) {
                emptyList = value_
              }

            override var nonEmptyList: List<Long>
              get() = nonEmptyList
              set(value_) {
                nonEmptyList = value_
              }
          }
          .apply(block_)
          .let {
            Variables(
              id = id,
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
              emptyList = emptyList,
              nonEmptyList = nonEmptyList,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("int64Variants_insert") val key: Int64variantsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertInt64Variants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertInt64variantsMutation.ref(
  id: String,
  nonNullWithZeroValue: Long,
  nonNullWithPositiveValue: Long,
  nonNullWithNegativeValue: Long,
  nonNullWithMaxValue: Long,
  nonNullWithMinValue: Long,
  emptyList: List<Long>,
  nonEmptyList: List<Long>,
  block_: InsertInt64variantsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertInt64variantsMutation.Data, InsertInt64variantsMutation.Variables> =
  ref(
    InsertInt64variantsMutation.Variables.build(
      id = id,
      nonNullWithZeroValue = nonNullWithZeroValue,
      nonNullWithPositiveValue = nonNullWithPositiveValue,
      nonNullWithNegativeValue = nonNullWithNegativeValue,
      nonNullWithMaxValue = nonNullWithMaxValue,
      nonNullWithMinValue = nonNullWithMinValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
  )

public suspend fun InsertInt64variantsMutation.execute(
  id: String,
  nonNullWithZeroValue: Long,
  nonNullWithPositiveValue: Long,
  nonNullWithNegativeValue: Long,
  nonNullWithMaxValue: Long,
  nonNullWithMinValue: Long,
  emptyList: List<Long>,
  nonEmptyList: List<Long>,
  block_: InsertInt64variantsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertInt64variantsMutation.Data, InsertInt64variantsMutation.Variables> =
  ref(
      id = id,
      nonNullWithZeroValue = nonNullWithZeroValue,
      nonNullWithPositiveValue = nonNullWithPositiveValue,
      nonNullWithNegativeValue = nonNullWithNegativeValue,
      nonNullWithMaxValue = nonNullWithMaxValue,
      nonNullWithMinValue = nonNullWithMinValue,
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
