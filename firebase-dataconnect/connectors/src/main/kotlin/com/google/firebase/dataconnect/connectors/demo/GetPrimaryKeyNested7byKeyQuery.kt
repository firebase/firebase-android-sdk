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

public interface GetPrimaryKeyNested7byKeyQuery :
  GeneratedQuery<
    DemoConnector, GetPrimaryKeyNested7byKeyQuery.Data, GetPrimaryKeyNested7byKeyQuery.Variables
  > {

  @Serializable public data class Variables(val key: PrimaryKeyNested7Key) {}

  @Serializable
  public data class Data(val primaryKeyNested7: PrimaryKeyNested7?) {

    @Serializable
    public data class PrimaryKeyNested7(
      val value: String,
      val nested5a: Nested5a,
      val nested5b: Nested5b,
      val nested6: Nested6
    ) {

      @Serializable
      public data class Nested5a(val value: String, val nested1: Nested1, val nested2: Nested2) {

        @Serializable public data class Nested1(val id: java.util.UUID, val value: String) {}

        @Serializable public data class Nested2(val id: java.util.UUID, val value: String) {}
      }

      @Serializable
      public data class Nested5b(val value: String, val nested1: Nested1, val nested2: Nested2) {

        @Serializable public data class Nested1(val id: java.util.UUID, val value: String) {}

        @Serializable public data class Nested2(val id: java.util.UUID, val value: String) {}
      }

      @Serializable
      public data class Nested6(val value: String, val nested3: Nested3, val nested4: Nested4) {

        @Serializable public data class Nested3(val id: java.util.UUID, val value: String) {}

        @Serializable public data class Nested4(val id: java.util.UUID, val value: String) {}
      }
    }
  }

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "GetPrimaryKeyNested7ByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetPrimaryKeyNested7byKeyQuery.ref(
  key: PrimaryKeyNested7Key,
): QueryRef<GetPrimaryKeyNested7byKeyQuery.Data, GetPrimaryKeyNested7byKeyQuery.Variables> =
  ref(
    GetPrimaryKeyNested7byKeyQuery.Variables(
      key = key,
    )
  )

public suspend fun GetPrimaryKeyNested7byKeyQuery.execute(
  key: PrimaryKeyNested7Key,
): QueryResult<GetPrimaryKeyNested7byKeyQuery.Data, GetPrimaryKeyNested7byKeyQuery.Variables> =
  ref(
      key = key,
    )
    .execute()

public fun GetPrimaryKeyNested7byKeyQuery.flow(
  key: PrimaryKeyNested7Key,
): Flow<
  QuerySubscriptionResult<
    GetPrimaryKeyNested7byKeyQuery.Data, GetPrimaryKeyNested7byKeyQuery.Variables
  >
> =
  ref(
      key = key,
    )
    .subscribe()
    .flow

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
