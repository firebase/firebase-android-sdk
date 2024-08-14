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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.util.SequencedReference
import kotlinx.serialization.DeserializationStrategy

internal class TypedActiveQuery<Data>(
  val activeQuery: ActiveQuery,
  val dataDeserializer: DeserializationStrategy<Data>,
  val logger: Logger
) {
  suspend fun execute(isFromGeneratedSdk: Boolean): ActiveQueryResult<Data> =
    activeQuery.queryExecutor.execute(isFromGeneratedSdk = isFromGeneratedSdk).toActiveQueryResult()

  suspend fun subscribe(
    executeQuery: Boolean,
    isFromGeneratedSdk: Boolean,
    callback: suspend (ActiveQueryResult<Data>) -> Unit
  ): Nothing =
    activeQuery.queryExecutor.subscribe(
      executeQuery,
      isFromGeneratedSdk = isFromGeneratedSdk,
    ) {
      callback(it.toActiveQueryResult())
    }

  private fun SequencedReference<QueryExecutorResult>.toActiveQueryResult():
    ActiveQueryResult<Data> =
    when (ref) {
      is QueryExecutorResult.Success ->
        ActiveQueryResult.Success(activeQuery, dataDeserializer, successOrThrow(), logger)
      is QueryExecutorResult.Failure ->
        ActiveQueryResult.Failure(activeQuery, dataDeserializer, failureOrThrow())
    }
}
