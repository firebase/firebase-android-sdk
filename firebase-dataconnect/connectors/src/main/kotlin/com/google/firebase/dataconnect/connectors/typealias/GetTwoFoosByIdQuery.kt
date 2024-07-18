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

public interface GetTwoFoosByIdQuery :
  GeneratedQuery<KeywordsConnector, GetTwoFoosByIdQuery.Data, GetTwoFoosByIdQuery.Variables> {

  @Serializable public data class Variables(val id1: String, val id2: String) {}

  @Serializable
  public data class Data(val `super`: Super?, val `this`: This?) {

    @Serializable public data class Super(val id: String, val bar: String?) {}

    @Serializable public data class This(val id: String, val bar: String?) {}
  }

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "GetTwoFoosById"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetTwoFoosByIdQuery.ref(
  id1: String,
  id2: String,
): QueryRef<GetTwoFoosByIdQuery.Data, GetTwoFoosByIdQuery.Variables> =
  ref(
    GetTwoFoosByIdQuery.Variables(
      id1 = id1,
      id2 = id2,
    )
  )

public suspend fun GetTwoFoosByIdQuery.execute(
  id1: String,
  id2: String,
): QueryResult<GetTwoFoosByIdQuery.Data, GetTwoFoosByIdQuery.Variables> =
  ref(
      id1 = id1,
      id2 = id2,
    )
    .execute()

public fun GetTwoFoosByIdQuery.flow(
  id1: String,
  id2: String,
): Flow<GetTwoFoosByIdQuery.Data> =
  ref(
      id1 = id1,
      id2 = id2,
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
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR keywords
