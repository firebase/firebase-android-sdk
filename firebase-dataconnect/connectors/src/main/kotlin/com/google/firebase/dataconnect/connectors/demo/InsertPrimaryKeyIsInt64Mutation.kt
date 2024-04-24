@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class, TimestampSerializer::class)

package com.google.firebase.dataconnect.connectors.demo

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface InsertPrimaryKeyIsInt64Mutation :
  GeneratedMutation<
    DemoConnector, InsertPrimaryKeyIsInt64Mutation.Data, InsertPrimaryKeyIsInt64Mutation.Variables
  > {

  @Serializable public data class Variables(val foo: Long, val value: String)

  @Serializable
  public data class Data(@SerialName("primaryKeyIsInt64_upsert") val key: PrimaryKeyIsInt64Key)

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertPrimaryKeyIsInt64"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertPrimaryKeyIsInt64Mutation.ref(
  foo: Long,
  value: String
): MutationRef<InsertPrimaryKeyIsInt64Mutation.Data, InsertPrimaryKeyIsInt64Mutation.Variables> =
  ref(InsertPrimaryKeyIsInt64Mutation.Variables(foo = foo, value = value))

public suspend fun InsertPrimaryKeyIsInt64Mutation.execute(
  foo: Long,
  value: String
): MutationResult<InsertPrimaryKeyIsInt64Mutation.Data, InsertPrimaryKeyIsInt64Mutation.Variables> =
  ref(foo = foo, value = value).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
