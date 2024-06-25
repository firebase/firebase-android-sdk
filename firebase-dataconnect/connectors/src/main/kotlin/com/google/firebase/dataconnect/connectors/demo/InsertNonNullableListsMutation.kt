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

public interface InsertNonNullableListsMutation :
  GeneratedMutation<
    DemoConnector, InsertNonNullableListsMutation.Data, InsertNonNullableListsMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val strings: List<String>,
    val ints: List<Int>,
    val floats: List<Double>,
    val booleans: List<Boolean>,
    val uuids: List<java.util.UUID>,
    val int64s: List<Long>,
    val dates: List<java.util.Date>,
    val timestamps: List<com.google.firebase.Timestamp>
  ) {}

  @Serializable
  public data class Data(@SerialName("nonNullableLists_insert") val key: NonNullableListsKey) {}

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertNonNullableLists"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertNonNullableListsMutation.ref(
  strings: List<String>,
  ints: List<Int>,
  floats: List<Double>,
  booleans: List<Boolean>,
  uuids: List<java.util.UUID>,
  int64s: List<Long>,
  dates: List<java.util.Date>,
  timestamps: List<com.google.firebase.Timestamp>,
): MutationRef<InsertNonNullableListsMutation.Data, InsertNonNullableListsMutation.Variables> =
  ref(
    InsertNonNullableListsMutation.Variables(
      strings = strings,
      ints = ints,
      floats = floats,
      booleans = booleans,
      uuids = uuids,
      int64s = int64s,
      dates = dates,
      timestamps = timestamps,
    )
  )

public suspend fun InsertNonNullableListsMutation.execute(
  strings: List<String>,
  ints: List<Int>,
  floats: List<Double>,
  booleans: List<Boolean>,
  uuids: List<java.util.UUID>,
  int64s: List<Long>,
  dates: List<java.util.Date>,
  timestamps: List<com.google.firebase.Timestamp>,
): MutationResult<InsertNonNullableListsMutation.Data, InsertNonNullableListsMutation.Variables> =
  ref(
      strings = strings,
      ints = ints,
      floats = floats,
      booleans = booleans,
      uuids = uuids,
      int64s = int64s,
      dates = dates,
      timestamps = timestamps,
    )
    .execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR demo
