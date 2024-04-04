@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(UUIDSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface GetFooByIdQuery {
  public val connector: DemoConnector

  public fun ref(variables: Variables): QueryRef<Data, Variables> =
    connector.dataConnect.query(operationName, variables, dataDeserializer, variablesSerializer)

  @Serializable public data class Variables(val id: String)

  @Serializable
  public data class Data(val foo: Foo?) {

    @Serializable public data class Foo(val bar: String?)
  }

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "GetFooById"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetFooByIdQuery.ref(
  id: String
): QueryRef<GetFooByIdQuery.Data, GetFooByIdQuery.Variables> =
  ref(GetFooByIdQuery.Variables(id = id))

public suspend fun GetFooByIdQuery.execute(
  id: String
): QueryResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables> = ref(id = id).execute()

public fun GetFooByIdQuery.flow(
  id: String
): Flow<QuerySubscriptionResult<GetFooByIdQuery.Data, GetFooByIdQuery.Variables>> =
  ref(id = id).subscribe().flow

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
