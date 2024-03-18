// Copyright 2024 Google LLC
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
package com.google.firebase.dataconnect.querymgr

import com.google.firebase.dataconnect.QueryRef
import com.google.firebase.dataconnect.core.FirebaseDataConnectImpl
import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.core.debug
import com.google.firebase.dataconnect.util.SequencedReference
import com.google.firebase.dataconnect.util.withAcquiredValue

// TODO: Rename "NewQueryManager" to just "QueryManager" once "OldQueryManager" is deleted.
internal class NewQueryManager(val dataConnect: FirebaseDataConnectImpl) {

  private val logger =
    Logger("NewQueryManager").apply { debug { "Created by ${dataConnect.logger.nameWithId}" } }

  private val activeQueries = ActiveQueries(dataConnect, parentLogger = logger)

  suspend fun <Data, Variables> execute(
    query: QueryRef<Data, Variables>
  ): SequencedReference<Result<Data>> =
    withActiveQuery(query) { execute(query.dataDeserializer) }.toSequencedDataResult()

  suspend fun <Data, Variables> subscribe(
    query: QueryRef<Data, Variables>,
    executeQuery: Boolean,
    callback: suspend (SequencedReference<Result<Data>>) -> Unit,
  ): Nothing =
    withActiveQuery(query) {
      subscribe(query.dataDeserializer, executeQuery) { activeQueryResult ->
        callback(activeQueryResult.toSequencedDataResult())
      }
    }

  private suspend fun <Data, Variables, ReturnType> withActiveQuery(
    query: QueryRef<Data, Variables>,
    callback: suspend ActiveQuery.() -> ReturnType
  ): ReturnType {
    require(query.dataConnect === dataConnect) {
      "The given query belongs to a different FirebaseDataConnect; " +
        "query belongs to ${query.dataConnect}, but expected ${dataConnect}"
    }
    val key = ActiveQueryKey.forQueryRef(query)
    return activeQueries.withAcquiredValue(key) { callback(it) }
  }
}
