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

import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicReference

internal class SuspendingWeakValueHashMap<K, V : Any>: AutoCloseable {

  private val state = AtomicReference<State<K, V>>(State.New())

  /**
   * Returns the value corresponding to the given [key], or `null` if such a key is not present in
   * the map, or if the value has been garbage collected.
   *
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun get(key: K): V? = runLockedWithMapIfAvailable(resultIfMapNotAvailable = null) { map ->
    map[key]?.get()
  }

  /**
   * Removes the mapping for the specified [key] from this map if present.
   *
   * @return the previous value associated with the specified key, or `null` if there was no mapping
   * for the key or if the previous value was garbage collected.
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun remove(key: K): V? = runLockedWithMapIfAvailable(resultIfMapNotAvailable = null) { map ->
    val oldValueReference = map.remove(key)
    val oldValue = oldValueReference?.get()
    oldValueReference?.clear()
    return oldValue
  }

  /**
   * Removes all the mappings from this map. The map will be empty after this call returns. Any weak
   * references will be cleared.
   *
   * @return the number of key/value pairs removed.
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun clear(): Int = runLockedWithMapIfAvailable(resultIfMapNotAvailable = 0) { map ->
    val removeCount = map.size
    map.values.forEach(ValueReference<K, V>::clear)
    map.clear()
    return removeCount
  }

  /**
   * Returns the number of key-value mappings in this map.
   *
   * Note that this count includes entries whose values have been garbage collected but have not yet
   * been removed by the background cleanup thread.
   *
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun size(): Int = runLockedWithMapIfAvailable(resultIfMapNotAvailable = 0) { map ->
    map.size
  }

  private suspend inline fun <T> runLockedWithMapIfAvailable(resultIfMapNotAvailable: T, block: (MutableMap<K, ValueReference<K, V>>) -> T): T =
    when (val currentState = state.get()) {
      is State.New, is State.Closed -> resultIfMapNotAvailable
      is State.Open -> currentState.mutex.withLock { block(currentState.map) }
    }

  suspend fun runCleanupLoop() = coroutineScope {
    val mutex = Mutex()
    val map = mutableMapOf<K, ValueReference<K, V>>()
    val referenceQueue = ReferenceQueue<V>()

    while (true) {
      val currentState = state.get()

      val newState: State.Open<K, V> = when (currentState) {
        is State.Closed -> throw IllegalStateException("runCleanupLoop() cannot be called after close()")
        is State.Open -> throw IllegalStateException("runCleanupLoop() has already been called")
        is State.New -> State.Open<K, V>(coroutineContext.job, mutex, map, referenceQueue)
      }

      if (state.compareAndSet(currentState, newState)) {
        break
      }
    }

    while (true) {
      val reference: ValueReference<K, V> = runInterruptible {
        referenceQueue.removeValueReference()
      }
      mutex.withLock { map.remove(reference.key, reference) }
    }
  }

  suspend fun put(key: K, value: V): V? =
    when (val currentState = this.state.get()) {
      is State.Closed -> throw IllegalStateException("put() cannot be called after close()")
      is State.New -> throw IllegalStateException("runCleanupLoop() must be called before put()")
      is State.Open -> currentState.run {
        mutex.withLock {
          val oldValueReference = map.put(key, ValueReference(key, value, referenceQueue))
          val oldValue = oldValueReference?.get()
          oldValueReference?.clear()
          oldValue
        }
      }
    }

  override fun close() {
    while (true) {
      val currentState = state.get()

      when (currentState) {
        is State.Closed -> return
        is State.New -> {}
        is State.Open -> currentState.cleanupJob.cancel("SuspendingWeakValueHashMap.close() called")
      }

      if (state.compareAndSet(currentState, State.Closed())) {
        return
      }
    }
  }

  private class ValueReference<K, V : Any>(
    val key: K,
    value: V,
    referenceQueue: ReferenceQueue<V>
  ) : WeakReference<V>(value, referenceQueue)

  private sealed interface State<K, V : Any> {
    class New<K, V : Any> : State<K, V>

    class Open<K, V : Any>(
      val cleanupJob: Job,
      val mutex: Mutex,
      val map: MutableMap<K, ValueReference<K, V>>,
      val referenceQueue: ReferenceQueue<V>,
    ) : State<K, V>

    class Closed<K, V : Any> : State<K, V>
  }

  private companion object {

    @Suppress("UNCHECKED_CAST")
    fun <K, V : Any> ReferenceQueue<V>.removeValueReference(): ValueReference<K, V> =
      remove() as ValueReference<K, V>
  }
}
