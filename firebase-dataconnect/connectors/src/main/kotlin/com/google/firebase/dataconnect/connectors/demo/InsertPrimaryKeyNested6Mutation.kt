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

public interface InsertPrimaryKeyNested6Mutation :
  GeneratedMutation<
    DemoConnector, InsertPrimaryKeyNested6Mutation.Data, InsertPrimaryKeyNested6Mutation.Variables
  > {

  @Serializable
  public data class Variables(
    val value: String,
    val nested3: PrimaryKeyNested3Key,
    val nested4: PrimaryKeyNested4Key
  ) {}

  @Serializable
  public data class Data(@SerialName("primaryKeyNested6_insert") val key: PrimaryKeyNested6Key) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertPrimaryKeyNested6"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertPrimaryKeyNested6Mutation.ref(
  value: String,
  nested3: PrimaryKeyNested3Key,
  nested4: PrimaryKeyNested4Key,
): MutationRef<InsertPrimaryKeyNested6Mutation.Data, InsertPrimaryKeyNested6Mutation.Variables> =
  ref(
    InsertPrimaryKeyNested6Mutation.Variables(
      value = value,
      nested3 = nested3,
      nested4 = nested4,
    )
  )

public suspend fun InsertPrimaryKeyNested6Mutation.execute(
  value: String,
  nested3: PrimaryKeyNested3Key,
  nested4: PrimaryKeyNested4Key,
): MutationResult<InsertPrimaryKeyNested6Mutation.Data, InsertPrimaryKeyNested6Mutation.Variables> =
  ref(
      value = value,
      nested3 = nested3,
      nested4 = nested4,
    )
    .execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).


// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
