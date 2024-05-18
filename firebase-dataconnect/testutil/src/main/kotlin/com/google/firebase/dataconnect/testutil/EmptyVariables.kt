package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.generated.GeneratedOperation
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.serializer

/**
 * A class whose serializer writes nothing when serialized.
 *
 * This can be used to test the server behavior when required variables are absent.
 */
@Serializable(with = EmptyVariablesSerializer::class) object EmptyVariables

private class EmptyVariablesSerializer : KSerializer<EmptyVariables> {
  override val descriptor = PrimitiveSerialDescriptor("EmptyVariables", PrimitiveKind.INT)

  override fun deserialize(decoder: Decoder): EmptyVariables = throw UnsupportedOperationException()

  override fun serialize(encoder: Encoder, value: EmptyVariables) {
    // do nothing
  }
}

suspend fun <Data> GeneratedOperation<*, Data, *>.executeWithEmptyVariables() =
  withVariablesSerializer(serializer<EmptyVariables>()).ref(EmptyVariables).execute()
