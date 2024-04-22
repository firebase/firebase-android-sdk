@file:Suppress("SpellCheckingInspection")
@file:UseSerializers(DateSerializer::class, UUIDSerializer::class, TimestampSerializer::class)

package com.google.firebase.dataconnect.connectors.`typealias`

import com.google.firebase.dataconnect.MutationRef
import com.google.firebase.dataconnect.MutationResult
import com.google.firebase.dataconnect.generated.GeneratedMutation
import com.google.firebase.dataconnect.serializers.DateSerializer
import com.google.firebase.dataconnect.serializers.TimestampSerializer
import com.google.firebase.dataconnect.serializers.UUIDSerializer
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.UseSerializers
import kotlinx.serialization.serializer

public interface InsertTwoFoosMutation :
  GeneratedMutation<
    KeywordsConnector, InsertTwoFoosMutation.Data, InsertTwoFoosMutation.Variables
  > {

  @Serializable
  public data class Variables(
    val id1: String,
    val id2: String,
    val bar1: String?,
    val bar2: String?
  )

  @Serializable public data class Data(val `val`: FooKey, val `var`: FooKey)

  public companion object {
    @Suppress("ConstPropertyName") public const val operationName: String = "InsertTwoFoos"
    public val dataDeserializer: DeserializationStrategy<Data> = serializer()
    public val variablesSerializer: SerializationStrategy<Variables> = serializer()
  }
}

public fun InsertTwoFoosMutation.ref(
  id1: String,
  id2: String,
  bar1: String?,
  bar2: String?
): MutationRef<InsertTwoFoosMutation.Data, InsertTwoFoosMutation.Variables> =
  ref(InsertTwoFoosMutation.Variables(id1 = id1, id2 = id2, bar1 = bar1, bar2 = bar2))

public suspend fun InsertTwoFoosMutation.execute(
  id1: String,
  id2: String,
  bar1: String?,
  bar2: String?
): MutationResult<InsertTwoFoosMutation.Data, InsertTwoFoosMutation.Variables> =
  ref(id1 = id1, id2 = id2, bar1 = bar1, bar2 = bar2).execute()

// The lines below are used by the code generator to ensure that this file is deleted if it is no
// longer needed. Any files in this directory that contain the lines below will be deleted by the
// code generator if the file is no longer needed. If, for some reason, you do _not_ want the code
// generator to delete this file, then remove the line below (and this comment too, if you want).

// FIREBASE_DATA_CONNECT_GENERATED_FILE MARKER 42da5e14-69b3-401b-a9f1-e407bee89a78
// FIREBASE_DATA_CONNECT_GENERATED_FILE CONNECTOR keywords
