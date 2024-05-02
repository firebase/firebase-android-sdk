@file:Suppress("SpellCheckingInspection", "LocalVariableName")
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

public interface InsertPrimaryKeyNested5Mutation :
  GeneratedMutation<
    DemoConnector, InsertPrimaryKeyNested5Mutation.Data, InsertPrimaryKeyNested5Mutation.Variables
  > {

  @Serializable
  public data class Variables(
    val value: String,
    val nested1: PrimaryKeyNested1Key,
    val nested2: PrimaryKeyNested2Key
  ) {}

  @Serializable
  public data class Data(@SerialName("primaryKeyNested5_insert") val key: PrimaryKeyNested5Key) {}

  public companion object {
    @Suppress("ConstPropertyName")
    public const val operationName: String = "InsertPrimaryKeyNested5"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertPrimaryKeyNested5Mutation.ref(
  value: String,
  nested1: PrimaryKeyNested1Key,
  nested2: PrimaryKeyNested2Key,
): MutationRef<InsertPrimaryKeyNested5Mutation.Data, InsertPrimaryKeyNested5Mutation.Variables> =
  ref(
    InsertPrimaryKeyNested5Mutation.Variables(
      value = value,
      nested1 = nested1,
      nested2 = nested2,
    )
  )

public suspend fun InsertPrimaryKeyNested5Mutation.execute(
  value: String,
  nested1: PrimaryKeyNested1Key,
  nested2: PrimaryKeyNested2Key,
): MutationResult<InsertPrimaryKeyNested5Mutation.Data, InsertPrimaryKeyNested5Mutation.Variables> =
  ref(
      value = value,
      nested1 = nested1,
      nested2 = nested2,
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
