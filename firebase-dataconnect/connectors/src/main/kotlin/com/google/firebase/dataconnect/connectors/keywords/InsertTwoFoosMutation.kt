
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

public interface InsertTwoFoosMutation :
    GeneratedMutation<
      KeywordsConnector,
      InsertTwoFoosMutation.Data,
      InsertTwoFoosMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val id1:
    String,
    val id2:
    String,
    val bar1:
    OptionalVariable<String?>,
    val bar2:
    OptionalVariable<String?>
  ) {
    
    
      
      @DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var id1: String
        public var id2: String
        public var bar1: String?
        public var bar2: String?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          id1: String,id2: String,
          block_: Builder.() -> Unit
        ): Variables {
          var id1= id1
            var id2= id2
            var bar1: OptionalVariable<String?> = OptionalVariable.Undefined
            var bar2: OptionalVariable<String?> = OptionalVariable.Undefined
            

          return object : Builder {
            override var id1: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id1 = value_ }
              
            override var id2: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { id2 = value_ }
              
            override var bar1: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { bar1 = OptionalVariable.Value(value_) }
              
            override var bar2: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { bar2 = OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              id1=id1,id2=id2,bar1=bar1,bar2=bar2,
            )
          }
        }
      }
    
  }
  

  
    @Serializable
  public data class Data(
  
    val `val`:
    FooKey,
    val `var`:
    FooKey
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertTwoFoos"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertTwoFoosMutation.ref(
  
    id1: String,id2: String,
  
    block_: InsertTwoFoosMutation.Variables.Builder.() -> Unit
  
): MutationRef<
    InsertTwoFoosMutation.Data,
    InsertTwoFoosMutation.Variables
  > =
  ref(
    
      InsertTwoFoosMutation.Variables.build(
        id1=id1,id2=id2,
  
    block_
      )
    
  )

public suspend fun InsertTwoFoosMutation.execute(
  
    id1: String,id2: String,
  
    block_: InsertTwoFoosMutation.Variables.Builder.() -> Unit
  
  ): MutationResult<
    InsertTwoFoosMutation.Data,
    InsertTwoFoosMutation.Variables
  > =
  ref(
    
      id1=id1,id2=id2,
  
    block_
    
  ).execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR keywords
