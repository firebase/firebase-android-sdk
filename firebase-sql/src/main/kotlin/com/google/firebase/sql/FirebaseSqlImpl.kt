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

package com.google.firebase.sql

import com.google.firebase.FirebaseApp
import com.google.firebase.sql.builders.FilterBuilder
import com.google.firebase.sql.builders.OperationBuilderImpl
import com.google.firebase.sql.builders.QueryBuilder
import com.google.firebase.sql.core.CLOSED_MESSAGE
import com.google.firebase.sql.core.InstanceSettings
import com.google.firebase.sql.core.OperationsExecutor
import com.google.firebase.sql.core.defaultJson
import com.google.firebase.sql.core.requireNotReservedHeader
import kotlinx.serialization.SerializationStrategy
import kotlinx.serialization.json.JsonNull

/**
 * The implementation of [FirebaseSql].
 *
 * `from()` and `rpc()` return a new [OperationBuilderImpl] that sends its request with the shared
 * [OperationsExecutor], which keeps the state of the instance. After [terminate], `from()`,
 * `rpc()`, and the `execute()` of every builder that this instance created throw an
 * [IllegalStateException], and the requests in flight are cancelled.
 *
 * The setters, such as [setHeaders], change the [settings], which the executor reads when it sends
 * a request, until the first call of `from()`, `rpc()`, [terminate], or [suspendingTerminate]
 * freezes them.
 */
internal class FirebaseSqlImpl(
  private val component: FirebaseSqlComponent,
  override val app: FirebaseApp,
  override val config: SqlServiceConfig,
  private val settings: InstanceSettings,
  private val executor: OperationsExecutor
) : FirebaseSql {

  override val isMutable: Boolean
    get() = settings.isMutable

  override fun setHeaders(headers: Map<String, String>) {
    // A reserved header is an invalid argument, whether or not the settings can still change.
    headers.keys.forEach { requireNotReservedHeader(it, "setHeaders()") }
    settings.setHeaders(headers)
  }

  override fun from(table: String): QueryBuilder {
    checkNotTerminated()
    // Frozen only once the builder exists, so that a call with invalid arguments changes nothing.
    return OperationBuilderImpl(table, executor).also { settings.freeze() }
  }

  override fun rpc(
    functionName: String,
    params: Map<String, Any?>,
    method: RpcMethod,
    count: Count?
  ): FilterBuilder = rpcBuilder(functionName, params, method, count)

  override fun <T> rpc(
    functionName: String,
    params: T,
    serializer: SerializationStrategy<T>,
    method: RpcMethod,
    count: Count?
  ): FilterBuilder {
    checkNotTerminated()
    // A null object calls the function without arguments, like an empty map.
    val arguments =
      defaultJson.encodeToJsonElement(serializer, params).takeUnless { it is JsonNull }
    return rpcBuilder(functionName, arguments, method, count)
  }

  /** Returns the builder of a call with [params]: `null`, a `Map`, or a `JsonElement`. */
  private fun rpcBuilder(
    functionName: String,
    params: Any?,
    method: RpcMethod,
    count: Count?
  ): FilterBuilder {
    checkNotTerminated()
    return OperationBuilderImpl.rpc(functionName, params, method, count, executor).also {
      settings.freeze()
    }
  }

  override fun terminate() {
    // Removed from the cache first, so that getInstance() never returns a terminated instance.
    component.remove(this)
    settings.freeze()
    executor.close()
  }

  override suspend fun suspendingTerminate() {
    component.remove(this)
    settings.freeze()
    executor.suspendingClose()
  }

  private fun checkNotTerminated() {
    check(!executor.isClosed) { CLOSED_MESSAGE }
  }
}
