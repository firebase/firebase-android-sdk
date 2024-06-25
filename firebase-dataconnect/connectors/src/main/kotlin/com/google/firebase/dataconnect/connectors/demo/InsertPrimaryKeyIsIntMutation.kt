
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

public interface InsertPrimaryKeyIsIntMutation :
    GeneratedMutation<
      DemoConnector,
      InsertPrimaryKeyIsIntMutation.Data,
      InsertPrimaryKeyIsIntMutation.Variables
    >
{
  
    @Serializable
  public data class Variables(
  
    val foo:
    Int,
    val value:
    String
  ) {
    
    
  }
  

  
    @Serializable
  public data class Data(
  @SerialName("primaryKeyIsInt_upsert")
    val key:
    PrimaryKeyIsIntKey
  ) {
    
    
  }
  

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertPrimaryKeyIsInt"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertPrimaryKeyIsIntMutation.ref(
  
    foo: Int,value: String,
  
  
): MutationRef<
    InsertPrimaryKeyIsIntMutation.Data,
    InsertPrimaryKeyIsIntMutation.Variables
  > =
  ref(
    
      InsertPrimaryKeyIsIntMutation.Variables(
        foo=foo,value=value,
  
      )
    
  )

public suspend fun InsertPrimaryKeyIsIntMutation.execute(
  
    foo: Int,value: String,
  
  
  ): MutationResult<
    InsertPrimaryKeyIsIntMutation.Data,
    InsertPrimaryKeyIsIntMutation.Variables
  > =
  ref(
    
      foo=foo,value=value,
  
    
  ).execute()



// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
