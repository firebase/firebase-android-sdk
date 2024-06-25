
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

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult

import com.google.firebase.dataconnect.OptionalVariable
import com.google.firebase.dataconnect.generated.GeneratedMutation

import kotlinx.serialization.UseSerializers
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer

public interface InsertNullableTimestampMutation :
    GeneratedMutation<
      DemoConnector,
      InsertNullableTimestampMutation.Data,
      InsertNullableTimestampMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val value:
    OptionalVariable<com.google.firebase.Timestamp?>
  ) {
    
    
      
      @DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var value: com.google.firebase.Timestamp?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          
          block_: Builder.() -> Unit
        ): Variables {
          var value: OptionalVariable<com.google.firebase.Timestamp?> = OptionalVariable.Undefined
            

          return object : Builder {
            override var value: com.google.firebase.Timestamp?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { value = OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              value=value,
            )
          }
        }
      }
    
  }
  

  
    @Serializable
  public data class Data(
  @SerialName("nullableTimestamp_insert")
    val key:
    NullableTimestampKey
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertNullableTimestamp"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertNullableTimestampMutation.ref(
  
    
  
    block_: InsertNullableTimestampMutation.Variables.Builder.() -> Unit
  
): MutationRef<
    InsertNullableTimestampMutation.Data,
    InsertNullableTimestampMutation.Variables
  > =
  ref(
    
      InsertNullableTimestampMutation.Variables.build(
        
  
    block_
      )
    
  )

public suspend fun InsertNullableTimestampMutation.execute(
  
    
  
    block_: InsertNullableTimestampMutation.Variables.Builder.() -> Unit
  
  ): MutationResult<
    InsertNullableTimestampMutation.Data,
    InsertNullableTimestampMutation.Variables
  > =
  ref(
    
      
  
    block_
    
  ).execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
