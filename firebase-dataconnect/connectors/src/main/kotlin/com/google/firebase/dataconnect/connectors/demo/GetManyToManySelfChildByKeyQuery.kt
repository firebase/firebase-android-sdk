
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

public interface GetManyToManySelfChildByKeyQuery :
    GeneratedQuery<
      DemoConnector,
      GetManyToManySelfChildByKeyQuery.Data,
      GetManyToManySelfChildByKeyQuery.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val key:
    ManyToManySelfChildKey
  ) {
    
    
  }
  

  
    @Serializable
  public data class Data(
  
    val manyToManySelfChild:
    ManyToManySelfChild?
  ) {
    
      
        @Serializable
  public data class ManyToManySelfChild(
  
    val manyToManySelfChildren_via_ManyToManySelfParent_on_child1:
    List<ManyToManySelfChildrenViaManyToManySelfParentOnChild1Item>,
    val manyToManySelfChildren_via_ManyToManySelfParent_on_child2:
    List<ManyToManySelfChildrenViaManyToManySelfParentOnChild2Item>
  ) {
    
      
        @Serializable
  public data class ManyToManySelfChildrenViaManyToManySelfParentOnChild1Item(
  
    val id:
    java.util.UUID
  ) {
    
    
  }
      
        @Serializable
  public data class ManyToManySelfChildrenViaManyToManySelfParentOnChild2Item(
  
    val id:
    java.util.UUID
  ) {
    
    
  }
      
    
    
  }
      
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "GetManyToManySelfChildByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun GetManyToManySelfChildByKeyQuery.ref(
  
    key: ManyToManySelfChildKey,
  
  
): QueryRef<
    GetManyToManySelfChildByKeyQuery.Data,
    GetManyToManySelfChildByKeyQuery.Variables
  > =
  ref(
    
      GetManyToManySelfChildByKeyQuery.Variables(
        key=key,
  
      )
    
  )

public suspend fun GetManyToManySelfChildByKeyQuery.execute(
  
    key: ManyToManySelfChildKey,
  
  
  ): QueryResult<
    GetManyToManySelfChildByKeyQuery.Data,
    GetManyToManySelfChildByKeyQuery.Variables
  > =
  ref(
    
      key=key,
  
    
  ).execute()


  public fun GetManyToManySelfChildByKeyQuery.flow(
    
      key: ManyToManySelfChildKey,
  
    
    ): Flow<GetManyToManySelfChildByKeyQuery.Data> =
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
