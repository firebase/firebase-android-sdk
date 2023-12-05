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

import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Encoder

class DataConnectUntypedVariables constructor(val variables: Map<String, Any?>) {
  constructor(vararg pairs: Pair<String, Any?>) : this(mapOf(*pairs))
  constructor(builderAction: MutableMap<String, Any?>.() -> Unit) : this(buildMap(builderAction))

  override fun equals(other: Any?) =
    (other as? DataConnectUntypedVariables)?.let { it.variables == variables } ?: false
  override fun hashCode() = variables.hashCode()
  override fun toString() = variables.toString()

  companion object Serializer : SerializationStrategy<DataConnectUntypedVariables> {
    override val descriptor: SerialDescriptor
      get() = unsupported()

    override fun serialize(encoder: Encoder, value: DataConnectUntypedVariables) = unsupported()

    private fun unsupported(): Nothing =
      throw UnsupportedOperationException(
        "this SerializationStrategy cannot actually be used; it is merely a placeholder"
      )
  }
}
