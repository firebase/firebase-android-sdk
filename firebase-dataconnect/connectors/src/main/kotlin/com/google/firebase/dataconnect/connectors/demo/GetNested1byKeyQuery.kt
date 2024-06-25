
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

public interface GetNested1byKeyQuery :
    GeneratedQuery<
      DemoConnector,
      GetNested1byKeyQuery.Data,
      GetNested1byKeyQuery.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val key:
    Nested1Key
  ) {
    
    
  }
  

  
    @Serializable
  public data class Data(
  
    val nested1:
    Nested1?
  ) {
    
      
        @Serializable
  public data class Nested1(
  
    val id:
    java.util.UUID,
    val nested1:
    Nested1?,
    val nested2:
    Nested2,
    val nested2NullableNonNull:
    Nested2nullableNonNull,
    val nested2NullableNull:
    Nested2nullableNull?
  ) {
    
      
        @Serializable
  public data class Nested1(
  
    val id:
    java.util.UUID,
    val nested1:
    Nested1?,
    val nested2:
    Nested2,
    val nested2NullableNull:
    Nested2nullableNull?,
    val nested2NullableNonNull:
    Nested2nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested1(
  
    val id:
    java.util.UUID
  ) {
    
    
  }
      
        @Serializable
  public data class Nested2(
  
    val id:
    java.util.UUID,
    val value:
    String,
    val nested3:
    Nested3,
    val nested3NullableNull:
    Nested3nullableNull?,
    val nested3NullableNonNull:
    Nested3nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested3(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
    
    
  }
      
        @Serializable
  public data class Nested2nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String,
    val nested3:
    Nested3,
    val nested3NullableNull:
    Nested3nullableNull?,
    val nested3NullableNonNull:
    Nested3nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested3(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
    
    
  }
      
        @Serializable
  public data class Nested2nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String,
    val nested3:
    Nested3,
    val nested3NullableNull:
    Nested3nullableNull?,
    val nested3NullableNonNull:
    Nested3nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested3(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
      
        @Serializable
  public data class Nested2(
  
    val id:
    java.util.UUID,
    val value:
    String,
    val nested3:
    Nested3,
    val nested3NullableNull:
    Nested3nullableNull?,
    val nested3NullableNonNull:
    Nested3nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested3(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
    
    
  }
      
        @Serializable
  public data class Nested2nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String,
    val nested3:
    Nested3,
    val nested3NullableNull:
    Nested3nullableNull?,
    val nested3NullableNonNull:
    Nested3nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested3(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
    
    
  }
      
        @Serializable
  public data class Nested2nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String,
    val nested3:
    Nested3,
    val nested3NullableNull:
    Nested3nullableNull?,
    val nested3NullableNonNull:
    Nested3nullableNonNull
  ) {
    
      
        @Serializable
  public data class Nested3(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
        @Serializable
  public data class Nested3nullableNonNull(
  
    val id:
    java.util.UUID,
    val value:
    String
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "GetNested1ByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetNested1byKeyQuery.ref(
  
    key: Nested1Key,
  
  
): QueryRef<
    GetNested1byKeyQuery.Data,
    GetNested1byKeyQuery.Variables
  > =
  ref(
    
      GetNested1byKeyQuery.Variables(
        key=key,
  
      )
    
  )

public suspend fun GetNested1byKeyQuery.execute(
  
    key: Nested1Key,
  
  
  ): QueryResult<
    GetNested1byKeyQuery.Data,
    GetNested1byKeyQuery.Variables
  > =
  ref(
    
      key=key,
  
    
  ).execute()


  public fun GetNested1byKeyQuery.flow(
    
      key: Nested1Key,
  
    
    ): Flow<GetNested1byKeyQuery.Data> =
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
