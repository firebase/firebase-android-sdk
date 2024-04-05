@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryResult
import com.google.firebase.dataconnect.QuerySubscriptionResult
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface GetInt64variantsByIdQuery {
  public val connector: DemoConnector

  public fun ref(variables: Variables): QueryRef<Data, Variables> =
    connector.dataConnect.query(operationName, variables, dataDeserializer, variablesSerializer)

  @Serializable public data class Variables(val id: String)

  @Serializable
  public data class Data(val int64Variants: Int64variants?) {

    @Serializable
    public data class Int64variants(
      val nonNullWithZeroValue: Long,
      val nonNullWithPositiveValue: Long,
      val nonNullWithNegativeValue: Long,
      val nonNullWithMaxValue: Long,
      val nonNullWithMinValue: Long,
      val nullableWithNullValue: Long?,
      val nullableWithZeroValue: Long?,
      val nullableWithPositiveValue: Long?,
      val nullableWithNegativeValue: Long?,
      val nullableWithMaxValue: Long?,
      val nullableWithMinValue: Long?,
      val emptyList: List<Long>,
      val nonEmptyList: List<Long>
    )
  }

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "GetInt64VariantsById"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetInt64variantsByIdQuery.ref(
  id: String
): QueryRef<GetInt64variantsByIdQuery.Data, GetInt64variantsByIdQuery.Variables> =
  ref(GetInt64variantsByIdQuery.Variables(id = id))

public suspend fun GetInt64variantsByIdQuery.execute(
  id: String
): QueryResult<GetInt64variantsByIdQuery.Data, GetInt64variantsByIdQuery.Variables> =
  ref(id = id).execute()

public fun GetInt64variantsByIdQuery.flow(
  id: String
): Flow<
  QuerySubscriptionResult<GetInt64variantsByIdQuery.Data, GetInt64variantsByIdQuery.Variables>
> = ref(id = id).subscribe().flow

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
