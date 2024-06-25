
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

public interface UpdateNullableListsByKeyMutation :
    GeneratedMutation<
      DemoConnector,
      UpdateNullableListsByKeyMutation.Data,
      UpdateNullableListsByKeyMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val key:
    NullableListsKey,
    val strings:
    OptionalVariable<List<String>?>,
    val ints:
    OptionalVariable<List<Int>?>,
    val floats:
    OptionalVariable<List<Double>?>,
    val booleans:
    OptionalVariable<List<Boolean>?>,
    val uuids:
    OptionalVariable<List<java.util.UUID>?>,
    val int64s:
    OptionalVariable<List<Long>?>,
    val dates:
    OptionalVariable<List<java.util.Date>?>,
    val timestamps:
    OptionalVariable<List<com.google.firebase.Timestamp>?>
  ) {
    
    
      
      @DslMarker public annotation class BuilderDsl

      @BuilderDsl
      public interface Builder {
        public var key: NullableListsKey
        public var strings: List<String>?
        public var ints: List<Int>?
        public var floats: List<Double>?
        public var booleans: List<Boolean>?
        public var uuids: List<java.util.UUID>?
        public var int64s: List<Long>?
        public var dates: List<java.util.Date>?
        public var timestamps: List<com.google.firebase.Timestamp>?
        
      }

      public companion object {
        @Suppress("NAME_SHADOWING")
        public fun build(
          key: NullableListsKey,
          block_: Builder.() -> Unit
        ): Variables {
          var key= key
            var strings: OptionalVariable<List<String>?> = OptionalVariable.Undefined
            var ints: OptionalVariable<List<Int>?> = OptionalVariable.Undefined
            var floats: OptionalVariable<List<Double>?> = OptionalVariable.Undefined
            var booleans: OptionalVariable<List<Boolean>?> = OptionalVariable.Undefined
            var uuids: OptionalVariable<List<java.util.UUID>?> = OptionalVariable.Undefined
            var int64s: OptionalVariable<List<Long>?> = OptionalVariable.Undefined
            var dates: OptionalVariable<List<java.util.Date>?> = OptionalVariable.Undefined
            var timestamps: OptionalVariable<List<com.google.firebase.Timestamp>?> = OptionalVariable.Undefined
            

          return object : Builder {
            override var key: NullableListsKey
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { key = value_ }
              
            override var strings: List<String>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { strings = OptionalVariable.Value(value_) }
              
            override var ints: List<Int>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { ints = OptionalVariable.Value(value_) }
              
            override var floats: List<Double>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { floats = OptionalVariable.Value(value_) }
              
            override var booleans: List<Boolean>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { booleans = OptionalVariable.Value(value_) }
              
            override var uuids: List<java.util.UUID>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { uuids = OptionalVariable.Value(value_) }
              
            override var int64s: List<Long>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { int64s = OptionalVariable.Value(value_) }
              
            override var dates: List<java.util.Date>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { dates = OptionalVariable.Value(value_) }
              
            override var timestamps: List<com.google.firebase.Timestamp>?
              get() = throw UnsupportedOperationException("getting builder values is not supported")
              set(value_) { timestamps = OptionalVariable.Value(value_) }
              
            
          }.apply(block_)
          .let {
            Variables(
              key=key,strings=strings,ints=ints,floats=floats,booleans=booleans,uuids=uuids,int64s=int64s,dates=dates,timestamps=timestamps,
            )
          }
        }
      }
    
  }
  

  
    @Serializable
  public data class Data(
  @SerialName("nullableLists_update")
    val key:
    NullableListsKey?
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "UpdateNullableListsByKey"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun UpdateNullableListsByKeyMutation.ref(
  
    key: NullableListsKey,
  
    block_: UpdateNullableListsByKeyMutation.Variables.Builder.() -> Unit
  
): MutationRef<
    UpdateNullableListsByKeyMutation.Data,
    UpdateNullableListsByKeyMutation.Variables
  > =
  ref(
    
      UpdateNullableListsByKeyMutation.Variables.build(
        key=key,
  
    block_
      )
    
  )

public suspend fun UpdateNullableListsByKeyMutation.execute(
  
    key: NullableListsKey,
  
    block_: UpdateNullableListsByKeyMutation.Variables.Builder.() -> Unit
  
  ): MutationResult<
    UpdateNullableListsByKeyMutation.Data,
    UpdateNullableListsByKeyMutation.Variables
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
