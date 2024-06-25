
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

public interface UpdateFloatVariantsByKeyMutation :
    GeneratedMutation<
      DemoConnector,
      UpdateFloatVariantsByKeyMutation.Data,
      UpdateFloatVariantsByKeyMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val key:
    FloatVariantsKey,
    val nonNullWithZeroValue:
    OptionalVariable<Double?>,
    val nonNullWithNegativeZeroValue:
    OptionalVariable<Double?>,
    val nonNullWithPositiveValue:
    OptionalVariable<Double?>,
    val nonNullWithNegativeValue:
    OptionalVariable<Double?>,
    val nonNullWithMaxValue:
    OptionalVariable<Double?>,
    val nonNullWithMinValue:
    OptionalVariable<Double?>,
    val nonNullWithMaxSafeIntegerValue:
    OptionalVariable<Double?>,
    val nullableWithNullValue:
    OptionalVariable<Double?>,
    val nullableWithZeroValue:
    OptionalVariable<Double?>,
    val nullableWithNegativeZeroValue:
    OptionalVariable<Double?>,
    val nullableWithPositiveValue:
    OptionalVariable<Double?>,
    val nullableWithNegativeValue:
    OptionalVariable<Double?>,
    val nullableWithMaxValue:
    OptionalVariable<Double?>,
    val nullableWithMinValue:
    OptionalVariable<Double?>,
    val nullableWithMaxSafeIntegerValue:
    OptionalVariable<Double?>
  ) {
    
    
      
      @DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var key: FloatVariantsKey
        public var nonNullWithZeroValue: Double?
        public var nonNullWithNegativeZeroValue: Double?
        public var nonNullWithPositiveValue: Double?
        public var nonNullWithNegativeValue: Double?
        public var nonNullWithMaxValue: Double?
        public var nonNullWithMinValue: Double?
        public var nonNullWithMaxSafeIntegerValue: Double?
        public var nullableWithNullValue: Double?
        public var nullableWithZeroValue: Double?
        public var nullableWithNegativeZeroValue: Double?
        public var nullableWithPositiveValue: Double?
        public var nullableWithNegativeValue: Double?
        public var nullableWithMaxValue: Double?
        public var nullableWithMinValue: Double?
        public var nullableWithMaxSafeIntegerValue: Double?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          key: FloatVariantsKey,
          block_: Builder.() -> Unit
        ): Variables {
          var key= key
            var nonNullWithZeroValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nonNullWithNegativeZeroValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nonNullWithPositiveValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nonNullWithNegativeValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nonNullWithMaxValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nonNullWithMinValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nonNullWithMaxSafeIntegerValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithNullValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithZeroValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithNegativeZeroValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithPositiveValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithNegativeValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithMaxValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithMinValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            var nullableWithMaxSafeIntegerValue: OptionalVariable<Double?> = OptionalVariable.Undefined
            

          return object : Builder {
            override var key: FloatVariantsKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { key = value_ }
              
            override var nonNullWithZeroValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithZeroValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithNegativeZeroValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithNegativeZeroValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithPositiveValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithPositiveValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithNegativeValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithNegativeValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithMaxValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithMaxValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithMinValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithMinValue = OptionalVariable.Value(value_) }
              
            override var nonNullWithMaxSafeIntegerValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nonNullWithMaxSafeIntegerValue = OptionalVariable.Value(value_) }
              
            override var nullableWithNullValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNullValue = OptionalVariable.Value(value_) }
              
            override var nullableWithZeroValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithZeroValue = OptionalVariable.Value(value_) }
              
            override var nullableWithNegativeZeroValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNegativeZeroValue = OptionalVariable.Value(value_) }
              
            override var nullableWithPositiveValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithPositiveValue = OptionalVariable.Value(value_) }
              
            override var nullableWithNegativeValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithNegativeValue = OptionalVariable.Value(value_) }
              
            override var nullableWithMaxValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithMaxValue = OptionalVariable.Value(value_) }
              
            override var nullableWithMinValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithMinValue = OptionalVariable.Value(value_) }
              
            override var nullableWithMaxSafeIntegerValue: Double?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { nullableWithMaxSafeIntegerValue = OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              key=key,nonNullWithZeroValue=nonNullWithZeroValue,nonNullWithNegativeZeroValue=nonNullWithNegativeZeroValue,nonNullWithPositiveValue=nonNullWithPositiveValue,nonNullWithNegativeValue=nonNullWithNegativeValue,nonNullWithMaxValue=nonNullWithMaxValue,nonNullWithMinValue=nonNullWithMinValue,nonNullWithMaxSafeIntegerValue=nonNullWithMaxSafeIntegerValue,nullableWithNullValue=nullableWithNullValue,nullableWithZeroValue=nullableWithZeroValue,nullableWithNegativeZeroValue=nullableWithNegativeZeroValue,nullableWithPositiveValue=nullableWithPositiveValue,nullableWithNegativeValue=nullableWithNegativeValue,nullableWithMaxValue=nullableWithMaxValue,nullableWithMinValue=nullableWithMinValue,nullableWithMaxSafeIntegerValue=nullableWithMaxSafeIntegerValue,
            )
          }
        }
      }
    
  }
  

  
    @Serializable
  public data class Data(
  @SerialName("floatVariants_update")
    val key:
    FloatVariantsKey?
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "UpdateFloatVariantsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateFloatVariantsByKeyMutation.ref(
  
    key: FloatVariantsKey,
  
    block_: UpdateFloatVariantsByKeyMutation.Variables.Builder.() -> Unit
  
): MutationRef<
    UpdateFloatVariantsByKeyMutation.Data,
    UpdateFloatVariantsByKeyMutation.Variables
  > =
  ref(
    
      UpdateFloatVariantsByKeyMutation.Variables.build(
        key=key,
  
    block_
      )
    
  )

public suspend fun UpdateFloatVariantsByKeyMutation.execute(
  
    key: FloatVariantsKey,
  
    block_: UpdateFloatVariantsByKeyMutation.Variables.Builder.() -> Unit
  
  ): MutationResult<
    UpdateFloatVariantsByKeyMutation.Data,
    UpdateFloatVariantsByKeyMutation.Variables
  > =
  ref(
    
      key=key,
  
    block_
    
  ).execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
