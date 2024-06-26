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

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.util.SequencedReference
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

// TODO: Rename "NewQueryManager" to just "QueryManager" once "OldQueryManager" is deleted.
internal class NewQueryManager(private val activeQueries: ActiveQueries) {

  suspend fun <Data, Variables> execute(
    query: QueryRef<Data, Variables>
  ): SequencedReference<Result<Data>> =
    activeQueries.useActiveQuery(query) {
      withContext(Dispatchers.Default) { delay(1.seconds) }
      TODO("not implemented")
    }

  suspend fun <Data, Variables> subscribe(
    query: QueryRef<Data, Variables>,
    executeQuery: Boolean,
    callback: suspend (SequencedReference<Result<Data>>) -> Unit,
  ): Nothing = activeQueries.useActiveQuery(query) { TODO("not implemented") }
}
