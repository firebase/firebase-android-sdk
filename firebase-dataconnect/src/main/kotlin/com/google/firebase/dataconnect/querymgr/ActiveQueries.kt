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

package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.LockFreeConcurrentMap
import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.util.buildStructProto
import com.google.firebase.dataconnect.util.calculateSha512
import com.google.firebase.dataconnect.util.encodeToStruct
import com.google.firebase.dataconnect.util.toAlphaNumericString
import com.google.protobuf.Struct
import kotlinx.serialization.SerializationStrategy

internal class ActiveQueries {

  private val activeQueryByVariablesHash = LockFreeConcurrentMap<ActiveQuery>()

  suspend fun <R> useActiveQuery(
    operationName: String,
    variables: Struct,
    block: suspend (ActiveQuery) -> R
  ): R {
    val keyStruct = buildStructProto {
      put("operationName", operationName)
      put("variables", variables)
    }
    val key = keyStruct.calculateSha512().toAlphaNumericString()
    val activeQuery = activeQueryByVariablesHash.acquire(key) { ActiveQuery() }
    try {
      return block(activeQuery)
    } finally {
      activeQueryByVariablesHash.release(key)
    }
  }
}

internal suspend fun <V, R> ActiveQueries.useActiveQuery(
  operationName: String,
  variables: V,
  variablesSerializer: SerializationStrategy<V>,
  block: suspend (ActiveQuery) -> R
): R {
  val serializedVariables = encodeToStruct(variablesSerializer, variables)
  return useActiveQuery(operationName, serializedVariables, block)
}

internal suspend fun <V, R> ActiveQueries.useActiveQuery(
  queryRef: QueryRef<*, V>,
  block: suspend (ActiveQuery) -> R
): R {
  return useActiveQuery(
    queryRef.operationName,
    queryRef.variables,
    queryRef.variablesSerializer,
    block
  )
}
