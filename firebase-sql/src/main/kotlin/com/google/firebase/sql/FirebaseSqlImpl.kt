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
import com.google.firebase.sql.core.InstanceSettings
import com.google.firebase.sql.core.OperationsExecutor
import com.google.firebase.sql.core.requireNotReservedHeader
import com.google.firebase.sql.core.requireValidHeader

/**
 * The implementation of [FirebaseSql].
 *
 * `from()` and `rpc()` return a new `OperationBuilderImpl` that sends its request with the shared
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
  override val config: ServiceConfig,
  private val settings: InstanceSettings,
  private val executor: OperationsExecutor
) : FirebaseSql {

  override val isMutable: Boolean
    get() = settings.isMutable

  override fun setHeaders(headers: Map<String, String>) {
    // An invalid header is an invalid argument, whether or not the settings can still change.
    // Checking it here keeps OkHttp from rejecting it on every request once the settings freeze.
    for ((name, value) in headers) {
      requireNotReservedHeader(name, "setHeaders()")
      requireValidHeader(name, value)
    }
    settings.setHeaders(headers)
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
}
