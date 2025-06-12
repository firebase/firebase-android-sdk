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

import java.util.Objects
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.encoding.Decoder

internal class DataConnectUntypedData(
  val data: Map<String, Any?>?,
  val errors: List<DataConnectOperationFailureResponse.ErrorInfo>
) {

  override fun equals(other: Any?) =
    (other is DataConnectUntypedData) && other.data == data && other.errors == errors

  override fun hashCode() = Objects.hash(data, errors)

  override fun toString() = "DataConnectUntypedData(data=$data, errors=$errors)"

  companion object Deserializer : DeserializationStrategy<DataConnectUntypedData> {
    override val descriptor
      get() = unsupported()

    override fun deserialize(decoder: Decoder) = unsupported()

    private fun unsupported(): Nothing =
      throw UnsupportedOperationException(
        "The ${Deserializer::class.qualifiedName} class cannot actually be used; " +
          "it is merely a placeholder"
      )
  }
}
