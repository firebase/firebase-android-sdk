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

import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder

public class DataConnectUntypedResponse
internal constructor(
  public val data: Map<String, Any?>?,
  public val errors: List<DataConnectError>
) {

  override fun equals(other: Any?): Boolean =
    (other as? DataConnectUntypedResponse)?.let { it.data == data && it.errors == errors } ?: false
  override fun hashCode(): Int = (data?.hashCode() ?: 0) + (31 * errors.hashCode())
  override fun toString(): String = "DataConnectUntypedResponse(data=$data, errors=$errors)"

  public companion object Deserializer : DeserializationStrategy<DataConnectUntypedResponse> {
    override val descriptor: SerialDescriptor
      get() = unsupported()

    override fun deserialize(decoder: Decoder): DataConnectUntypedResponse = unsupported()

    private fun unsupported(): Nothing =
      throw UnsupportedOperationException(
        "this DeserializationStrategy cannot actually be used; it is merely a placeholder"
      )
  }
}
