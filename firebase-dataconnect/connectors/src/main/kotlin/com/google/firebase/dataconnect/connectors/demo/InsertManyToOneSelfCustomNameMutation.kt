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

public interface InsertManyToOneSelfCustomNameMutation :
  GeneratedMutation<
    DemoConnector,
    InsertManyToOneSelfCustomNameMutation.Data,
    InsertManyToOneSelfCustomNameMutation.Variables
  > {

  @Serializable
  public data class Variables(val ref: OptionalVariable<ManyToOneSelfCustomNameKey?>) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var ref: ManyToOneSelfCustomNameKey?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(block_: Builder.() -> Unit): Variables {
        var ref: OptionalVariable<ManyToOneSelfCustomNameKey?> = OptionalVariable.Undefined

        return object : Builder {
            override var ref: ManyToOneSelfCustomNameKey?
              get() = ref.valueOrNull()
              set(value_) {
                ref = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              ref = ref,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(
    @SerialName("manyToOneSelfCustomName_insert") val key: ManyToOneSelfCustomNameKey
  ) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertManyToOneSelfCustomName"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertManyToOneSelfCustomNameMutation.ref(
  block_: InsertManyToOneSelfCustomNameMutation.Variables.Builder.() -> Unit
): MutationRef<
  InsertManyToOneSelfCustomNameMutation.Data, InsertManyToOneSelfCustomNameMutation.Variables
> = ref(InsertManyToOneSelfCustomNameMutation.Variables.build(block_))

public suspend fun InsertManyToOneSelfCustomNameMutation.execute(
  block_: InsertManyToOneSelfCustomNameMutation.Variables.Builder.() -> Unit
): MutationResult<
  InsertManyToOneSelfCustomNameMutation.Data, InsertManyToOneSelfCustomNameMutation.Variables
> = ref(block_).execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).


// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
