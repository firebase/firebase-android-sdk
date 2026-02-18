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

package com.google.firebase.dataconnect.util

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * An adaptation of the standard library [lazy] builder that implements
 * [LazyThreadSafetyMode.SYNCHRONIZED] with a suspending function and a [Mutex] rather than a
 * blocking synchronization call.
 *
 * @param mutex the mutex to have locked when `initializer` is invoked; if null (the default) then a
 * new lock will be used.
 * @param coroutineContext the coroutine context with which to invoke `initializer`; if null (the
 * default) then the context of the coroutine that calls [get] or [getLocked] will be used.
 * @param initializer the block to invoke at most once to initialize this object's value.
 */
internal class SuspendingLazy<T : Any>(
  mutex: Mutex? = null,
  private val coroutineContext: CoroutineContext? = null,
  initializer: suspend () -> T
) {
  private val mutex = mutex ?: Mutex()
  private var initializer: (suspend () -> T)? = initializer
  @Volatile private var value: T? = null

  val initializedValueOrNull: T?
    get() = value

  suspend inline fun get(): T = value ?: mutex.withLock { getLocked() }

  // This function _must_ be called by a coroutine that has locked the mutex given to the
  // constructor; otherwise, a data race will occur, resulting in undefined behavior.
  suspend fun getLocked(): T =
    if (coroutineContext === null) {
      getLockedInContext()
    } else {
      withContext(coroutineContext) { getLockedInContext() }
    }

  private suspend inline fun getLockedInContext(): T =
    value
      ?: initializer!!().also {
        value = it
        initializer = null
      }

  override fun toString(): String =
    if (value !== null) value.toString() else "SuspendingLazy value not initialized yet."
}
