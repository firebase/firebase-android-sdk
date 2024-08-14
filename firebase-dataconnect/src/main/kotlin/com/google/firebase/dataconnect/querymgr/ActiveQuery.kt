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

import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.util.withAcquiredValue
import com.google.protobuf.Struct
import kotlinx.serialization.DeserializationStrategy

internal class ActiveQuery(
  val dataConnect: FirebaseDataConnectImpl,
  val operationName: String,
  val variables: Struct,
  parentLogger: Logger
) {
  val logger = Logger("ActiveQuery").apply { debug { "Created by ${parentLogger.nameWithId}" } }

  val queryExecutor = QueryExecutor(dataConnect, operationName, variables)

  private val typedActiveQueries = TypedActiveQueries(this, logger)

  suspend fun <Data> execute(
    dataDeserializer: DeserializationStrategy<Data>,
    isFromGeneratedSdk: Boolean,
  ): ActiveQueryResult<Data> =
    withTypedActiveQuery(dataDeserializer) { it.execute(isFromGeneratedSdk = isFromGeneratedSdk) }

  suspend fun <Data> subscribe(
    dataDeserializer: DeserializationStrategy<Data>,
    executeQuery: Boolean,
    isFromGeneratedSdk: Boolean,
    callback: suspend (ActiveQueryResult<Data>) -> Unit
  ): Nothing =
    withTypedActiveQuery(dataDeserializer) {
      it.subscribe(
        executeQuery = executeQuery,
        isFromGeneratedSdk = isFromGeneratedSdk,
        callback = callback,
      )
    }

  private suspend fun <Data, ReturnType> withTypedActiveQuery(
    dataDeserializer: DeserializationStrategy<Data>,
    callback: suspend (TypedActiveQuery<Data>) -> ReturnType,
  ): ReturnType {
    val key = TypedActiveQueryKey(dataDeserializer)
    return typedActiveQueries.withAcquiredValue(key) {
      @Suppress("UNCHECKED_CAST") callback(it as TypedActiveQuery<Data>)
    }
  }
}
