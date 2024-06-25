
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

public interface InsertStringVariantsWithHardcodedDefaultsMutation :
    GeneratedMutation<
      DemoConnector,
      InsertStringVariantsWithHardcodedDefaultsMutation.Data,
      InsertStringVariantsWithHardcodedDefaultsMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val nonNullWithNonEmptyValue:
    OptionalVariable<String>,
    val nonNullWithEmptyValue:
    OptionalVariable<String>,
    val nullableWithNullValue:
    OptionalVariable<String?>,
    val nullableWithNonNullValue:
    OptionalVariable<String?>,
    val nullableWithEmptyValue:
    OptionalVariable<String?>
  ) {
    
    
      
      @DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var nonNullWithNonEmptyValue: String
        public var nonNullWithEmptyValue: String
        public var nullableWithNullValue: String?
        public var nullableWithNonNullValue: String?
        public var nullableWithEmptyValue: String?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          
          block_: Builder.() -> Unit
        ): Variables {
          var nonNullWithNonEmptyValue: OptionalVariable<String> = OptionalVariable.Undefined
            var nonNullWithEmptyValue: OptionalVariable<String> = OptionalVariable.Undefined
            var nullableWithNullValue: OptionalVariable<String?> = OptionalVariable.Undefined
            var nullableWithNonNullValue: OptionalVariable<String?> = OptionalVariable.Undefined
            var nullableWithEmptyValue: OptionalVariable<String?> = OptionalVariable.Undefined
            

          return object : Builder {
            override var nonNullWithNonEmptyValue: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithNonEmptyValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithEmptyValue: String
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithEmptyValue = OptionalVariable.Value(value_) }
              
            override var nullableWithNullValue: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNullValue = OptionalVariable.Value(value_) }
              
            override var nullableWithNonNullValue: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNonNullValue = OptionalVariable.Value(value_) }
              
            override var nullableWithEmptyValue: String?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithEmptyValue = OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              nonNullWithNonEmptyValue=nonNullWithNonEmptyValue,nonNullWithEmptyValue=nonNullWithEmptyValue,nullableWithNullValue=nullableWithNullValue,nullableWithNonNullValue=nullableWithNonNullValue,nullableWithEmptyValue=nullableWithEmptyValue,
            )
          }
        }
      }
    
  }
  

  
    @Serializable
  public data class Data(
  @SerialName("stringVariants_insert")
    val key:
    StringVariantsKey
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertStringVariantsWithHardcodedDefaults"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertStringVariantsWithHardcodedDefaultsMutation.ref(
  
    
  
    block_: InsertStringVariantsWithHardcodedDefaultsMutation.Variables.Builder.() -> Unit
  
): MutationRef<
    InsertStringVariantsWithHardcodedDefaultsMutation.Data,
    InsertStringVariantsWithHardcodedDefaultsMutation.Variables
  > =
  ref(
    
      InsertStringVariantsWithHardcodedDefaultsMutation.Variables.build(
        
  
    block_
      )
    
  )

public suspend fun InsertStringVariantsWithHardcodedDefaultsMutation.execute(
  
    
  
    block_: InsertStringVariantsWithHardcodedDefaultsMutation.Variables.Builder.() -> Unit
  
  ): MutationResult<
    InsertStringVariantsWithHardcodedDefaultsMutation.Data,
    InsertStringVariantsWithHardcodedDefaultsMutation.Variables
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
