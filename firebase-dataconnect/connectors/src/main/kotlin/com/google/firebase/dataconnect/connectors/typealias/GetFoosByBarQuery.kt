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

package com.google.firebase.dataconnect.connectors.`typealias`

import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.generated.GeneratedQuery
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface GetFoosByBarQuery :
  GeneratedQuery<KeywordsConnector, GetFoosByBarQuery.Data, GetFoosByBarQuery.Variables> {

  @Serializable
  public data class Variables(val `as`: OptionalVariable<String?>) {

    @DslMarker public annotation class BuilderDsl

    @BuilderDsl
    public interface Builder {
      public var `as`: String?
    }

    public companion object {
      @Suppress("NAME_SHADOWING")
      public fun build(block_: Builder.() -> Unit): Variables {
        var `as`: OptionalVariable<String?> = OptionalVariable.Undefined

        return object : Builder {
            override var `as`: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) {
                `as` = OptionalVariable.Value(value_)
              }
          }
          .apply(block_)
          .let {
            Variables(
              `as` = `as`,
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
): Flow<GetFoosByBarQuery.Data> =
  ref(block_)
    .subscribe()
    .flow
    .filter { it.result.isSuccess }
    .map { querySubscriptionResult -> querySubscriptionResult.result.getOrThrow().data }

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR keywords
