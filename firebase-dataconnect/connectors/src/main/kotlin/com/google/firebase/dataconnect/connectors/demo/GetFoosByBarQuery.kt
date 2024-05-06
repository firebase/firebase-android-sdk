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

import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface GetFoosByBarQuery :
  GeneratedQuery<DemoConnector, GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables> {

  @Serializable
  public data class Variables(val bar: OptionalVariable<String?>) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var bar: String?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(block_: Builder.() -> Unit): Variables {
        var bar: OptionalVariable<String?> = OptionalVariable.Undefined

        return object : Builder {
            override var bar: String?
              get() = bar.valueOrNull()
              set(value_) {
                bar = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              bar = bar,
            )
          }
      }
    }
  }

  @Serializable
  public data class Data(val foos: List<FoosItem>) {

    @Serializable public data class FoosItem(val id: String) {}
  }

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "GetFoosByBar"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetFoosByBarQuery.ref(
  block_: GetFoosByBarQuery.Variables.Builder.() -> Unit
): QueryRef<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables> =
  ref(GetFoosByBarQuery.Variables.build(block_))

public suspend fun GetFoosByBarQuery.execute(
  block_: GetFoosByBarQuery.Variables.Builder.() -> Unit
): QueryResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables> = ref(block_).execute()

public fun GetFoosByBarQuery.flow(
  block_: GetFoosByBarQuery.Variables.Builder.() -> Unit
): Flow<QuerySubscriptionResult<GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables>> =
  ref(block_).subscribe().flow


// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).


// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
