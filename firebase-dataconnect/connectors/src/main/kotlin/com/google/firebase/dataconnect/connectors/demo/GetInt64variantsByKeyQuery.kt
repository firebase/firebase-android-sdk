
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

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.serializer

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.QueryResult

  import kotlinx.coroutines.flow.Flow
  import kotlinx.coroutines.flow.filter
  import kotlinx.coroutines.flow.map

import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.generated.GeneratedQuery

import kotlinx.serialization.UseSerializers
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer

public interface GetInt64variantsByKeyQuery :
    GeneratedQuery<
      DemoConnector,
      GetInt64variantsByKeyQuery.Data,
      GetInt64variantsByKeyQuery.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val key:
    Int64variantsKey
  ) {
    
    
  }
  

  
    @Serializable
  public data class Data(
  
    val int64Variants:
    Int64variants?
  ) {
    
      
        @Serializable
  public data class Int64variants(
  
    val nonNullWithZeroValue:
    Long,
    val nonNullWithPositiveValue:
    Long,
    val nonNullWithNegativeValue:
    Long,
    val nonNullWithMaxValue:
    Long,
    val nonNullWithMinValue:
    Long,
    val nullableWithNullValue:
    Long?,
    val nullableWithZeroValue:
    Long?,
    val nullableWithPositiveValue:
    Long?,
    val nullableWithNegativeValue:
    Long?,
    val nullableWithMaxValue:
    Long?,
    val nullableWithMinValue:
    Long?
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "GetInt64VariantsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetInt64variantsByKeyQuery.ref(
  
    key: Int64variantsKey,
  
  
): QueryRef<
    GetInt64variantsByKeyQuery.Data,
    GetInt64variantsByKeyQuery.Variables
  > =
  ref(
    
      GetInt64variantsByKeyQuery.Variables(
        key=key,
  
      )
    
  )

public suspend fun GetInt64variantsByKeyQuery.execute(
  
    key: Int64variantsKey,
  
  
  ): QueryResult<
    GetInt64variantsByKeyQuery.Data,
    GetInt64variantsByKeyQuery.Variables
  > =
  ref(
    
      key=key,
  
    
  ).execute()


  public fun GetInt64variantsByKeyQuery.flow(
    
      key: Int64variantsKey,
  
    
    ): Flow<GetInt64variantsByKeyQuery.Data> =
    ref(
        
          key=key,
  
        
      ).subscribe().flow.filter { it.result.isSuccess }.map { querySubscriptionResult ->
        querySubscriptionResult.result.getOrThrow().data
    }


// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
