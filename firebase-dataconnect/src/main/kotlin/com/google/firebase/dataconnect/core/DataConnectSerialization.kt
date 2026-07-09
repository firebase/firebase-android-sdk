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

package com.google.firebase.dataconnect.core

import com.google.firebase.dataconnect.DataConnectOperationException
import com.google.firebase.dataconnect.DataConnectPathSegment
import com.google.firebase.dataconnect.DataConnectUntypedData
import com.google.firebase.dataconnect.DataConnectUntypedVariables
import com.google.firebase.dataconnect.util.ProtoUtil.decodeFromStruct
import com.google.firebase.dataconnect.util.ProtoUtil.encodeToStruct
import com.google.firebase.dataconnect.util.ProtoUtil.toCompactString
import com.google.firebase.dataconnect.util.ProtoUtil.toMap
import com.google.firebase.dataconnect.util.ProtoUtil.toStructProto
import com.google.protobuf.ListValue
import com.google.protobuf.Struct
import com.google.protobuf.Value
import google.firebase.dataconnect.proto.GraphqlError
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.modules.SerializersModule

/**
 * Provides logic to encode variables for Data Connect operations into [Struct], and decode
 * responses from Data Connect operations into higher-level objects, all being sure to offload the
 * potentially-blocking CPU-bound work to another thread (the [cpuDispatcher] given to the
 * constructor).
 *
 * This class correctly recognizes [DataConnectUntypedData] and [DataConnectUntypedVariables] and
 * handles them specially.
 */
internal class DataConnectSerialization(private val cpuDispatcher: CoroutineDispatcher) {

  suspend fun <T> encodeVariables(
    variables: T,
    serializer: SerializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): Struct =
    withContext(cpuDispatcher) {
      if (serializer === DataConnectUntypedVariables.Serializer) {
        (variables as DataConnectUntypedVariables).variables.toStructProto()
      } else {
        encodeToStruct(variables, serializer, serializersModule)
      }
    }

  suspend fun <T> decodeData(
    data: Struct?,
    errors: List<GraphqlError>,
    deserializer: DeserializationStrategy<T>,
    serializersModule: SerializersModule?,
  ): T =
    withContext(cpuDispatcher) {
      val decodedErrors = errors.map { it.toErrorInfoImpl() }

      @Suppress("UNCHECKED_CAST")
      if (deserializer === DataConnectUntypedData) {
        return@withContext DataConnectUntypedData(data?.toMap(), decodedErrors) as T
      }

      val decodedData: Result<T>? =
        data?.let { data ->
          runCatching { decodeFromStruct(data, deserializer, serializersModule) }
        }

      if (errors.isNotEmpty()) {
        throw DataConnectOperationException(
          "operation encountered errors during execution: $decodedErrors",
          response =
            DataConnectOperationFailureResponseImpl(
              rawData = data?.toMap(),
              data = decodedData?.getOrNull(),
              errors = decodedErrors,
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

      decodedData.getOrElse { exception ->
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

  companion object {

    private fun ListValue.toPathSegment() =
      valuesList.map {
        when (it.kindCase) {
          Value.KindCase.STRING_VALUE -> DataConnectPathSegment.Field(it.stringValue)
          Value.KindCase.NUMBER_VALUE -> DataConnectPathSegment.ListIndex(it.numberValue.toInt())
          // The cases below are expected to never occur; however, implement some logic for them
          // to avoid things like throwing exceptions in those cases.
          Value.KindCase.NULL_VALUE -> DataConnectPathSegment.Field("null")
          Value.KindCase.BOOL_VALUE -> DataConnectPathSegment.Field(it.boolValue.toString())
          Value.KindCase.LIST_VALUE -> DataConnectPathSegment.Field(it.listValue.toCompactString())
          Value.KindCase.STRUCT_VALUE ->
            DataConnectPathSegment.Field(it.structValue.toCompactString())
          else -> DataConnectPathSegment.Field(it.toString())
        }
      }

    fun GraphqlError.toErrorInfoImpl() =
      DataConnectOperationFailureResponseImpl.ErrorInfoImpl(
        message = message,
        path = path.toPathSegment(),
      )
  }
}
