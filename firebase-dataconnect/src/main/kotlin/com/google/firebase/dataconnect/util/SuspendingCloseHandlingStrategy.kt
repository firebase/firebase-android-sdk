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

package com.google.firebase.dataconnect.util

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext

/**
 * Strategies available for run suspending "close" code in a normal, non-suspending context.
 *
 * The intended use case for [SuspendingCloseHandlingStrategy] is for "close" functions that perform
 * suspending work. Such "close" methods can provide the usual `suspend fun close()`, which is the
 * standard way to close it. It can also provide a non-suspending overload like
 * `fun close(suspendStrategy: SuspendingCloseHandlingStrategy): Deferred<Unit>` which allows close() to be
 * called from a non-suspending context.
 */
internal sealed interface SuspendingCloseHandlingStrategy {

  /**
   * Executes the code in the given suspending block.
   * @return a [Deferred] that can be used to observe the results of executing [block].
   */
  fun <T> handle(coroutineScope: CoroutineScope, block: suspend () -> T): Deferred<T>

  /**
   * Handle suspending code by blocking the calling thread.
   */
  object Block : SuspendingCloseHandlingStrategy {

    override fun <T> handle(coroutineScope: CoroutineScope, block: suspend () -> T): Deferred<T> {
      val deferred = CompletableDeferred<T>()
      try {
        deferred.complete(runBlocking { block() })
      } catch (e: Throwable) {
        deferred.completeExceptionally(e)
      }
      return deferred
    }

  }

  /**
   * Handle suspending code by running it asynchronously.
   */
  object Async : SuspendingCloseHandlingStrategy {

    override fun <T> handle(coroutineScope: CoroutineScope, block: suspend () -> T): Deferred<T> {
      return coroutineScope.async(Dispatchers.Unconfined, CoroutineStart.UNDISPATCHED) {
        withContext(NonCancellable) {
          block()
        }
      }
    }

  }
}
