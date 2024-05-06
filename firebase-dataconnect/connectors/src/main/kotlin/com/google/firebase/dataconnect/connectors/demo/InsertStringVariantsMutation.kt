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

public interface InsertStringVariantsMutation :
  GeneratedMutation<
    DemoConnector, InsertStringVariantsMutation.Data, InsertStringVariantsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id: String,
    val nonNullWithNonEmptyValue: String,
    val nonNullWithEmptyValue: String,
    val nullableWithNullValue: OptionalVariable<String?>,
    val nullableWithNonNullValue: OptionalVariable<String?>,
    val nullableWithEmptyValue: OptionalVariable<String?>,
    val emptyList: List<String>,
    val nonEmptyList: List<String>
  ) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var id: String
      public var nonNullWithNonEmptyValue: String
      public var nonNullWithEmptyValue: String
      public var nullableWithNullValue: String?
      public var nullableWithNonNullValue: String?
      public var nullableWithEmptyValue: String?
      public var emptyList: List<String>
      public var nonEmptyList: List<String>
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(
        id: String,
        nonNullWithNonEmptyValue: String,
        nonNullWithEmptyValue: String,
        emptyList: List<String>,
        nonEmptyList: List<String>,
        block_: Builder.() -> Unit
      ): Variables {
        var id = id
        var nonNullWithNonEmptyValue = nonNullWithNonEmptyValue
        var nonNullWithEmptyValue = nonNullWithEmptyValue
        var nullableWithNullValue: OptionalVariable<String?> = OptionalVariable.Undefined
        var nullableWithNonNullValue: OptionalVariable<String?> = OptionalVariable.Undefined
        var nullableWithEmptyValue: OptionalVariable<String?> = OptionalVariable.Undefined
        var emptyList = emptyList
        var nonEmptyList = nonEmptyList

        return object : Builder {
            override var id: String
              get() = id
              set(value_) {
                id = value_
              }

            override var nonNullWithNonEmptyValue: String
              get() = nonNullWithNonEmptyValue
              set(value_) {
                nonNullWithNonEmptyValue = value_
              }

            override var nonNullWithEmptyValue: String
              get() = nonNullWithEmptyValue
              set(value_) {
                nonNullWithEmptyValue = value_
              }

            override var nullableWithNullValue: String?
              get() = nullableWithNullValue.valueOrNull()
              set(value_) {
                nullableWithNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithNonNullValue: String?
              get() = nullableWithNonNullValue.valueOrNull()
              set(value_) {
                nullableWithNonNullValue = OptionalVariable.Value(value_)
              }

            override var nullableWithEmptyValue: String?
              get() = nullableWithEmptyValue.valueOrNull()
              set(value_) {
                nullableWithEmptyValue = OptionalVariable.Value(value_)
              }

            override var emptyList: List<String>
              get() = emptyList
              set(value_) {
                emptyList = value_
              }

            override var nonEmptyList: List<String>
              get() = nonEmptyList
              set(value_) {
                nonEmptyList = value_
              }
          }
          .apply(block_)
          .let {
            Variables(
              id = id,
              nonNullWithNonEmptyValue = nonNullWithNonEmptyValue,
              nonNullWithEmptyValue = nonNullWithEmptyValue,
              nullableWithNullValue = nullableWithNullValue,
              nullableWithNonNullValue = nullableWithNonNullValue,
              nullableWithEmptyValue = nullableWithEmptyValue,
              emptyList = emptyList,
              nonEmptyList = nonEmptyList,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(@SerialName("stringVariants_insert") val key: StringVariantsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertStringVariants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertStringVariantsMutation.ref(
  id: String,
  nonNullWithNonEmptyValue: String,
  nonNullWithEmptyValue: String,
  emptyList: List<String>,
  nonEmptyList: List<String>,
  block_: InsertStringVariantsMutation.Variables.Builder.() -> Unit
): MutationRef<InsertStringVariantsMutation.Data, InsertStringVariantsMutation.Variables> =
  ref(
    InsertStringVariantsMutation.Variables.build(
      id = id,
      nonNullWithNonEmptyValue = nonNullWithNonEmptyValue,
      nonNullWithEmptyValue = nonNullWithEmptyValue,
      emptyList = emptyList,
      nonEmptyList = nonEmptyList,
      block_
    )
  )

public suspend fun InsertStringVariantsMutation.execute(
  id: String,
  nonNullWithNonEmptyValue: String,
  nonNullWithEmptyValue: String,
  emptyList: List<String>,
  nonEmptyList: List<String>,
  block_: InsertStringVariantsMutation.Variables.Builder.() -> Unit
): MutationResult<InsertStringVariantsMutation.Data, InsertStringVariantsMutation.Variables> =
  ref(
      id = id,
      nonNullWithNonEmptyValue = nonNullWithNonEmptyValue,
      nonNullWithEmptyValue = nonNullWithEmptyValue,
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
