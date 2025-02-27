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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.inject.Deferred
import com.google.firebase.inject.Provider
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * An implementation of [Deferred] whose provider is initially unavailable, then becomes available
 * when [makeAvailable] is invoked.
 *
 * The callback registered with [whenAvailable] is _always_ called back asynchronously, even if the
 * instance has already been registered.
 */
@OptIn(DelicateCoroutinesApi::class)
class DelayedDeferred<T>(instance: T) : Deferred<T> {
  private val provider = Provider { instance }
  private val mutex = Mutex()
  private var provided = false
  private val handlers = mutableListOf<Deferred.DeferredHandler<T>>()

  override fun whenAvailable(handler: Deferred.DeferredHandler<T>) {
    GlobalScope.launch(Dispatchers.Default) {
      val notifyHandler =
        mutex.withLock {
          if (provided) {
            true
          } else {
            handlers.add(handler)
            false
          }
        }
      if (notifyHandler) {
        handler.handle(provider)
      }
    }
  }

  suspend fun makeAvailable() {
    val capturedHandlers =
      mutex.withLock {
        provided = true
        val capturedHandlers = handlers.toList()
        handlers.clear()
        capturedHandlers
      }
    GlobalScope.launch(Dispatchers.Default) { capturedHandlers.forEach { it.handle(provider) } }
  }
}
