@file:Suppress("SpellCheckingInspection")

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.QuerySubscriptionResult
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.serializer

public interface GetOneStringListFieldByIdQuery {
  public val connector: DemoConnector

  public fun ref(variables: Variables): QueryRef<Data, Variables> =
    connector.dataConnect.query(operationName, variables, dataDeserializer, variablesSerializer)

  @Serializable public data class Variables(val id: String)

  @Serializable
  public data class Data(val oneStringListField: OneStringListField?) {

    @Serializable public data class OneStringListField(val value: List<String>)
  }

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "GetOneStringListFieldById"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetOneStringListFieldByIdQuery.ref(
  id: String
): QueryRef<GetOneStringListFieldByIdQuery.Data, GetOneStringListFieldByIdQuery.Variables> =
  ref(GetOneStringListFieldByIdQuery.Variables(id = id))

public suspend fun GetOneStringListFieldByIdQuery.execute(
  id: String
): QueryResult<GetOneStringListFieldByIdQuery.Data, GetOneStringListFieldByIdQuery.Variables> =
  ref(id = id).execute()

public fun GetOneStringListFieldByIdQuery.flow(
  id: String
): Flow<
  QuerySubscriptionResult<
    GetOneStringListFieldByIdQuery.Data, GetOneStringListFieldByIdQuery.Variables
  >
> = ref(id = id).subscribe().flow

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
