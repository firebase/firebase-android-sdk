// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.google.firebase.dataconnect

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable(with = OptionalVariableSerializer::class)
public sealed interface OptionalVariable<out T> {

  public fun valueOrNull(): T?
  public fun valueOrThrow(): T

  public object Undefined : OptionalVariable<Nothing> {
    override fun valueOrNull(): Nothing? = null
    override fun valueOrThrow(): Nothing = throw UndefinedValueException()

    override fun toString(): String = "undefined"

    private class UndefinedValueException :
      IllegalStateException("Undefined does not have a value")
  }

  public class Value<T>(public val value: T) : OptionalVariable<T> {
    override fun valueOrNull(): T = value
    override fun valueOrThrow(): T = value

    override fun hashCode(): Int = value.hashCode()
    override fun equals(other: Any?): Boolean = other is Value<*> && value == other.value
    override fun toString(): String = value.toString()
  }
}

public class OptionalVariableSerializer<T>(private val elementSerializer: KSerializer<T>) :
  KSerializer<OptionalVariable<T>> {

  override val descriptor: SerialDescriptor = elementSerializer.descriptor

  override fun deserialize(decoder: Decoder): OptionalVariable<T> =
    throw UnsupportedOperationException("OptionalVariableSerializer does not support decoding")

  override fun serialize(encoder: Encoder, value: OptionalVariable<T>) {
    when (value) {
      is OptionalVariable.Undefined -> {
        /* nothing to do */
      }
      is OptionalVariable.Value<T> -> elementSerializer.serialize(encoder, value.value)
    }
  }
}
