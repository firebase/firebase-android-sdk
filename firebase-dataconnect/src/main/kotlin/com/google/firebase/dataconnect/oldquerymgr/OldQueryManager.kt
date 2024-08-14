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

package com.google.firebase.dataconnect.oldquerymgr

import com.google.firebase.dataconnect.core.QueryRefImpl
import com.google.firebase.dataconnect.util.SequencedReference

internal class OldQueryManager(private val liveQueries: LiveQueries) {
  suspend fun <Data, Variables> execute(
    query: QueryRefImpl<Data, Variables>,
  ): SequencedReference<Result<Data>> =
    liveQueries.withLiveQuery(query) {
      it.execute(
        dataDeserializer = query.dataDeserializer,
        isFromGeneratedSdk = query.isFromGeneratedSdk,
      )
    }

  suspend fun <Data, Variables> subscribe(
    query: QueryRefImpl<Data, Variables>,
    executeQuery: Boolean,
    callback: suspend (SequencedReference<Result<Data>>) -> Unit,
  ): Nothing =
    liveQueries.withLiveQuery(query) { liveQuery ->
      liveQuery.subscribe(
        dataDeserializer = query.dataDeserializer,
        executeQuery = executeQuery,
        isFromGeneratedSdk = query.isFromGeneratedSdk,
        callback = callback,
      )
    }
}
