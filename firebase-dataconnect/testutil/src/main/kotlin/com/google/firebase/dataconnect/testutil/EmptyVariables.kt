/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.ExperimentalFirebaseDataConnect
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

@ExperimentalFirebaseDataConnect
suspend fun <Data> GeneratedOperation<*, Data, *>.executeWithEmptyVariables() =
  withVariablesSerializer(serializer<EmptyVariables>()).ref(EmptyVariables).execute()
