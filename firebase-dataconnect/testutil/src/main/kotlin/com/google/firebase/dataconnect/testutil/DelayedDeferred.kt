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

/**
 * An implementation of {@link Deferred} whose provider is initially unavailable, then becomes
 * available when {@link #setInstance} is invoked.
 */
class DelayedDeferred<T> : Deferred<T> {

  @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
  private val singleThreadDispatcher = newSingleThreadContext("UnavailableDeferredThread")

  private val handlers: MutableList<Deferred.DeferredHandler<T>> = mutableListOf()

  private lateinit var provider: Provider<T>

  override fun whenAvailable(handler: Deferred.DeferredHandler<T>) {
    runBlocking {
      withContext(singleThreadDispatcher) {
        if (this@DelayedDeferred::provider.isInitialized) {
          handler.handle(provider)
        } else {
          handlers.add(handler)
        }
      }
    }
  }

  fun setInstance(instance: T) {
    runBlocking {
      withContext(singleThreadDispatcher) {
        if (this@DelayedDeferred::provider.isInitialized) {
          throw IllegalStateException("setInstance() has already been invoked")
        }

        provider = Provider { instance }
        handlers.forEach { it.handle(provider) }
      }
    }
  }
}
