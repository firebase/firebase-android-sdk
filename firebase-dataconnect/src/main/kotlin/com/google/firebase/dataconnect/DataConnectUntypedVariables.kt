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

package com.google.firebase.dataconnect

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.encoding.Encoder

internal class DataConnectUntypedVariables(val variables: Map<String, Any?>) {

  constructor(vararg pairs: Pair<String, Any?>) : this(mapOf(*pairs))

  constructor(builderAction: MutableMap<String, Any?>.() -> Unit) : this(buildMap(builderAction))

  override fun equals(other: Any?) =
    (other is DataConnectUntypedVariables) && other.variables == variables

  override fun hashCode() = variables.hashCode()

  override fun toString() = variables.toString()

  companion object Serializer : SerializationStrategy<DataConnectUntypedVariables> {
    override val descriptor
      get() = unsupported()

    override fun serialize(encoder: Encoder, value: DataConnectUntypedVariables) = unsupported()

    private fun unsupported(): Nothing =
      throw UnsupportedOperationException(
        "The ${Serializer::class.qualifiedName} class cannot actually be used; " +
          "it is merely a placeholder"
      )
  }
}
