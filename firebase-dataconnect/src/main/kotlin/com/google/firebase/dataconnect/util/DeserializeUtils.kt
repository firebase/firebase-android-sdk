/*
 * Copyright 2026 Google LLC
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

@file:OptIn(ExperimentalSerializationApi::class)

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.core.DataConnectOperationFailureResponseImpl
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.ExecuteMutationResponse
import google.firebase.dataconnect.proto.ExecuteQueryResponse
import google.firebase.dataconnect.proto.GraphqlError
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.modules.SerializersModule

internal object DeserializeUtils {

  fun <T> deserialize(
    data: Struct?,
    errors: List<DataConnectOperationFailureResponseImpl.ErrorInfoImpl>,
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): T {
    if (deserializer === DataConnectUntypedData) {
      @Suppress("UNCHECKED_CAST") return DataConnectUntypedData(data?.toMap(), errors) as T
    }

    val decodedData: Result<T>? =
      data?.let { data -> runCatching { decodeFromStruct(data, deserializer, serializersModule) } }

    if (errors.isNotEmpty()) {
      throw DataConnectOperationException(
        "operation encountered errors during execution: $errors",
        response =
          DataConnectOperationFailureResponseImpl(
            rawData = data?.toMap(),
            data = decodedData?.getOrNull(),
            errors = errors,
          )
      )
    }

    if (decodedData == null) {
      throw DataConnectOperationException(
        "no data was included in the response from the server",
        response =
          DataConnectOperationFailureResponseImpl(
            rawData = null,
            data = null,
            errors = emptyList(),
          )
      )
    }

    return decodedData.getOrElse { exception ->
      throw DataConnectOperationException(
        "decoding data from the server's response failed: ${exception.message}",
        cause = exception,
        response =
          DataConnectOperationFailureResponseImpl(
            rawData = data.toMap(),
            data = null,
            errors = emptyList(),
          )
      )
    }
  }

  fun <T> ExecuteQueryResponse.deserialize(
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): T =
    deserialize(
      ::hasData,
      ::getData,
      errorsList,
      deserializer,
      serializersModule,
    )

  fun <T> ExecuteMutationResponse.deserialize(
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): T =
    deserialize(
      ::hasData,
      ::getData,
      errorsList,
      deserializer,
      serializersModule,
    )

  private inline fun <T> deserialize(
    hasData: () -> Boolean,
    getData: () -> Struct,
    errorsList: List<GraphqlError>,
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): T {
    val data = if (hasData()) getData() else null
    val errors = errorsList.map { it.toErrorInfoImpl() }
    return deserialize(data, errors, deserializer, serializersModule)
  }

  fun GraphqlError.toErrorInfoImpl() =
    DataConnectOperationFailureResponseImpl.ErrorInfoImpl(
      message = message,
      path = path.toPathSegment(),
    )

  private fun ListValue.toPathSegment() =
    valuesList.map {
      when (it.kindCase) {
        Value.KindCase.STRING_VALUE -> DataConnectPathSegment.Field(it.stringValue)
        Value.KindCase.NUMBER_VALUE -> DataConnectPathSegment.ListIndex(it.numberValue.toInt())
        // The other cases are expected to never occur; however, implement some logic for them
        // to avoid things like throwing exceptions in those cases.
        else -> DataConnectPathSegment.Field(it.toCompactString())
      }
    }
}
