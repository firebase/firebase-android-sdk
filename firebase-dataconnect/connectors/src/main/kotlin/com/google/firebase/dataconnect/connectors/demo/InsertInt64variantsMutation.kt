
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

public interface InsertInt64variantsMutation :
    GeneratedMutation<
      DemoConnector,
      InsertInt64variantsMutation.Data,
      InsertInt64variantsMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
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
    OptionalVariable<Long?>,
    val nullableWithZeroValue:
    OptionalVariable<Long?>,
    val nullableWithPositiveValue:
    OptionalVariable<Long?>,
    val nullableWithNegativeValue:
    OptionalVariable<Long?>,
    val nullableWithMaxValue:
    OptionalVariable<Long?>,
    val nullableWithMinValue:
    OptionalVariable<Long?>
  ) {
    
    
      
      @DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var nonNullWithZeroValue: Long
        public var nonNullWithPositiveValue: Long
        public var nonNullWithNegativeValue: Long
        public var nonNullWithMaxValue: Long
        public var nonNullWithMinValue: Long
        public var nullableWithNullValue: Long?
        public var nullableWithZeroValue: Long?
        public var nullableWithPositiveValue: Long?
        public var nullableWithNegativeValue: Long?
        public var nullableWithMaxValue: Long?
        public var nullableWithMinValue: Long?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          nonNullWithZeroValue: Long,nonNullWithPositiveValue: Long,nonNullWithNegativeValue: Long,nonNullWithMaxValue: Long,nonNullWithMinValue: Long,
          block_: Builder.() -> Unit
        ): Variables {
          var nonNullWithZeroValue= nonNullWithZeroValue
            var nonNullWithPositiveValue= nonNullWithPositiveValue
            var nonNullWithNegativeValue= nonNullWithNegativeValue
            var nonNullWithMaxValue= nonNullWithMaxValue
            var nonNullWithMinValue= nonNullWithMinValue
            var nullableWithNullValue: OptionalVariable<Long?> = OptionalVariable.Undefined
            var nullableWithZeroValue: OptionalVariable<Long?> = OptionalVariable.Undefined
            var nullableWithPositiveValue: OptionalVariable<Long?> = OptionalVariable.Undefined
            var nullableWithNegativeValue: OptionalVariable<Long?> = OptionalVariable.Undefined
            var nullableWithMaxValue: OptionalVariable<Long?> = OptionalVariable.Undefined
            var nullableWithMinValue: OptionalVariable<Long?> = OptionalVariable.Undefined
            

          return object : Builder {
            override var nonNullWithZeroValue: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithZeroValue = value_ }
              
            override var nonNullWithPositiveValue: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithPositiveValue = value_ }
              
            override var nonNullWithNegativeValue: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithNegativeValue = value_ }
              
            override var nonNullWithMaxValue: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithMaxValue = value_ }
              
            override var nonNullWithMinValue: Long
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithMinValue = value_ }
              
            override var nullableWithNullValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNullValue = OptionalVariable.Value(value_) }
              
            override var nullableWithZeroValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithZeroValue = OptionalVariable.Value(value_) }
              
            override var nullableWithPositiveValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithPositiveValue = OptionalVariable.Value(value_) }
              
            override var nullableWithNegativeValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNegativeValue = OptionalVariable.Value(value_) }
              
            override var nullableWithMaxValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithMaxValue = OptionalVariable.Value(value_) }
              
            override var nullableWithMinValue: Long?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithMinValue = OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              nonNullWithZeroValue=nonNullWithZeroValue,nonNullWithPositiveValue=nonNullWithPositiveValue,nonNullWithNegativeValue=nonNullWithNegativeValue,nonNullWithMaxValue=nonNullWithMaxValue,nonNullWithMinValue=nonNullWithMinValue,nullableWithNullValue=nullableWithNullValue,nullableWithZeroValue=nullableWithZeroValue,nullableWithPositiveValue=nullableWithPositiveValue,nullableWithNegativeValue=nullableWithNegativeValue,nullableWithMaxValue=nullableWithMaxValue,nullableWithMinValue=nullableWithMinValue,
            )
          }
        }
      }
    
  }
  

  
    @Serializable
  public data class Data(
  @SerialName("int64Variants_insert")
    val key:
    Int64variantsKey
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertInt64Variants"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertInt64variantsMutation.ref(
  
    nonNullWithZeroValue: Long,nonNullWithPositiveValue: Long,nonNullWithNegativeValue: Long,nonNullWithMaxValue: Long,nonNullWithMinValue: Long,
  
    block_: InsertInt64variantsMutation.Variables.Builder.() -> Unit
  
): MutationRef<
    InsertInt64variantsMutation.Data,
    InsertInt64variantsMutation.Variables
  > =
  ref(
    
      InsertInt64variantsMutation.Variables.build(
        nonNullWithZeroValue=nonNullWithZeroValue,nonNullWithPositiveValue=nonNullWithPositiveValue,nonNullWithNegativeValue=nonNullWithNegativeValue,nonNullWithMaxValue=nonNullWithMaxValue,nonNullWithMinValue=nonNullWithMinValue,
  
    block_
      )
    
  )

public suspend fun InsertInt64variantsMutation.execute(
  
    nonNullWithZeroValue: Long,nonNullWithPositiveValue: Long,nonNullWithNegativeValue: Long,nonNullWithMaxValue: Long,nonNullWithMinValue: Long,
  
    block_: InsertInt64variantsMutation.Variables.Builder.() -> Unit
  
  ): MutationResult<
    InsertInt64variantsMutation.Data,
    InsertInt64variantsMutation.Variables
  > =
  ref(
    
      nonNullWithZeroValue=nonNullWithZeroValue,nonNullWithPositiveValue=nonNullWithPositiveValue,nonNullWithNegativeValue=nonNullWithNegativeValue,nonNullWithMaxValue=nonNullWithMaxValue,nonNullWithMinValue=nonNullWithMinValue,
  
    block_
    
  ).execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
