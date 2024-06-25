
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

public interface GetNullableTimestampsWithDefaultsByKeyQuery :
    GeneratedQuery<
      DemoConnector,
      GetNullableTimestampsWithDefaultsByKeyQuery.Data,
      GetNullableTimestampsWithDefaultsByKeyQuery.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val key:
    NullableTimestampsWithDefaultsKey
  ) {
    
    
  }
  

  
    @Serializable
  public data class Data(
  
    val nullableTimestampsWithDefaults:
    NullableTimestampsWithDefaults?
  ) {
    
      
        @Serializable
  public data class NullableTimestampsWithDefaults(
  
    val valueWithVariableDefault:
    com.google.firebase.Timestamp?,
    val valueWithSchemaDefault:
    com.google.firebase.Timestamp?,
    val epoch:
    com.google.firebase.Timestamp?,
    val requestTime1:
    com.google.firebase.Timestamp?,
    val requestTime2:
    com.google.firebase.Timestamp?
  ) {
    
    
  }
      
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "GetNullableTimestampsWithDefaultsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetNullableTimestampsWithDefaultsByKeyQuery.ref(
  
    key: NullableTimestampsWithDefaultsKey,
  
  
): QueryRef<
    GetNullableTimestampsWithDefaultsByKeyQuery.Data,
    GetNullableTimestampsWithDefaultsByKeyQuery.Variables
  > =
  ref(
    
      GetNullableTimestampsWithDefaultsByKeyQuery.Variables(
        key=key,
  
      )
    
  )

public suspend fun GetNullableTimestampsWithDefaultsByKeyQuery.execute(
  
    key: NullableTimestampsWithDefaultsKey,
  
  
  ): QueryResult<
    GetNullableTimestampsWithDefaultsByKeyQuery.Data,
    GetNullableTimestampsWithDefaultsByKeyQuery.Variables
  > =
  ref(
    
      key=key,
  
    
  ).execute()


  public fun GetNullableTimestampsWithDefaultsByKeyQuery.flow(
    
      key: NullableTimestampsWithDefaultsKey,
  
    
    ): Flow<GetNullableTimestampsWithDefaultsByKeyQuery.Data> =
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
