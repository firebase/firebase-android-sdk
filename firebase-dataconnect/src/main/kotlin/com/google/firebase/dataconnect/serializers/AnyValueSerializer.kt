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

package com.google.firebase.dataconnect.serializers

import com.google.firebase.dataconnect.AnyValue
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

/**
 * An implementation of [KSerializer] for serializing and deserializing [AnyValue] objects.
 *
 * Note that this is _not_ a generic serializer, but is only useful in the Data Connect SDK.
 */
public object AnyValueSerializer : KSerializer<AnyValue> {

  override val descriptor: SerialDescriptor =
    buildClassSerialDescriptor("com.google.firebase.dataconnect.AnyValue") {}

  override fun serialize(encoder: Encoder, value: AnyValue): Unit = unsupported()

  override fun deserialize(decoder: Decoder): AnyValue = unsupported()

  private fun unsupported(): Nothing =
    throw UnsupportedOperationException(
      "The AnyValueSerializer class cannot actually be used;" +
        " it is merely a sentinel that gets special treatment during Data Connect serialization"
    )
}
