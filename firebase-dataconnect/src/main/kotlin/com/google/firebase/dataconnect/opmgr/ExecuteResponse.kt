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

package com.google.firebase.dataconnect.opmgr

import com.google.firebase.dataconnect.util.DeserializeUtils.deserialize
import com.google.firebase.dataconnect.util.DeserializeUtils.toErrorInfoImpl
import com.google.protobuf.Struct
import google.firebase.dataconnect.proto.GraphqlError as GraphqlErrorProto
import google.firebase.dataconnect.proto.GraphqlResponseExtensions.DataConnectProperties as DataConnectPropertiesProto
import google.firebase.dataconnect.proto.StreamResponse as StreamResponseProto
import kotlinx.serialization.DeserializationStrategy
import kotlinx.serialization.modules.SerializersModule

internal class ExecuteResponse(
  val authUid: String?,
  val data: Struct,
  val errors: List<GraphqlErrorProto>,
  val extensions: List<DataConnectPropertiesProto>,
)

internal fun <T> ExecuteResponse.deserialize(
  deserializer: DeserializationStrategy<T>,
  serializersModule: SerializersModule?,
): T = deserialize(data, errors.map { it.toErrorInfoImpl() }, deserializer, serializersModule)

internal fun StreamResponseProto.toExecuteResponse(authUid: String?): ExecuteResponse? =
  if (!hasData() && errorsCount == 0) {
    null
  } else {
    ExecuteResponse(
      authUid = authUid,
      data = if (hasData()) data else Struct.getDefaultInstance(),
      errors = if (errorsCount > 0) errorsList else emptyList(),
      extensions =
        if (hasExtensions() && extensions.dataConnectCount > 0) extensions.dataConnectList
        else emptyList(),
    )
  }
