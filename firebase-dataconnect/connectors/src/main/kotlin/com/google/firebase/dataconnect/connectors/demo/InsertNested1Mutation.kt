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

public interface InsertNested1Mutation :
  GeneratedMutation<DemoConnector, InsertNested1Mutation.Data, InsertNested1Mutation.Variables> {

  @Serializable
  public data class Variables(
    val nested1: Nested1Key?,
    val nested2: Nested2Key,
    val nested2NullableNonNull: Nested2Key?,
    val nested2NullableNull: Nested2Key?,
    val value: String
  )

  @Serializable public data class Data(@SerialName("nested1_insert") val key: Nested1Key)

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertNested1"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertNested1Mutation.ref(
  nested1: Nested1Key?,
  nested2: Nested2Key,
  nested2NullableNonNull: Nested2Key?,
  nested2NullableNull: Nested2Key?,
  value: String
): MutationRef<InsertNested1Mutation.Data, InsertNested1Mutation.Variables> =
  ref(
    InsertNested1Mutation.Variables(
      nested1 = nested1,
      nested2 = nested2,
      nested2NullableNonNull = nested2NullableNonNull,
      nested2NullableNull = nested2NullableNull,
      value = value
    )
  )

public suspend fun InsertNested1Mutation.execute(
  nested1: Nested1Key?,
  nested2: Nested2Key,
  nested2NullableNonNull: Nested2Key?,
  nested2NullableNull: Nested2Key?,
  value: String
): MutationResult<InsertNested1Mutation.Data, InsertNested1Mutation.Variables> =
  ref(
      nested1 = nested1,
      nested2 = nested2,
      nested2NullableNonNull = nested2NullableNonNull,
      nested2NullableNull = nested2NullableNull,
      value = value
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
