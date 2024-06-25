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

public interface GetNullableListsByKeyQuery :
  GeneratedQuery<
    DemoConnector, GetNullableListsByKeyQuery.Data, GetNullableListsByKeyQuery.Variables
  > {

  @Serializable public data class Variables(val key: NullableListsKey) {}

  @Serializable
  public data class Data(val nullableLists: NullableLists?) {

    @Serializable
    public data class NullableLists(
      val strings: List<String>?,
      val ints: List<Int>?,
      val floats: List<Double>?,
      val booleans: List<Boolean>?,
      val uuids: List<java.util.UUID>?,
      val int64s: List<Long>?,
      val dates: List<java.util.Date>?,
      val timestamps: List<com.google.firebase.Timestamp>?
    ) {}
  }

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "GetNullableListsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetNullableListsByKeyQuery.ref(
  key: NullableListsKey,
): QueryRef<GetNullableListsByKeyQuery.Data, GetNullableListsByKeyQuery.Variables> =
  ref(
    GetNullableListsByKeyQuery.Variables(
      key = key,
    )
  )

public suspend fun GetNullableListsByKeyQuery.execute(
  key: NullableListsKey,
): QueryResult<GetNullableListsByKeyQuery.Data, GetNullableListsByKeyQuery.Variables> =
  ref(
      key = key,
    )
    .execute()

public fun GetNullableListsByKeyQuery.flow(
  key: NullableListsKey,
): Flow<GetNullableListsByKeyQuery.Data> =
  ref(
      key = key,
    )
    .subscribe()
    .flow
    .filter { it.result.isSuccess }
    .map { querySubscriptionResult -> querySubscriptionResult.result.getOrThrow().data }

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
