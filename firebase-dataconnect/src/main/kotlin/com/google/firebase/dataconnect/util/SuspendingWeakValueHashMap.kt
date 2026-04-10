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

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A thread-safe, coroutine-aware map that holds weak references to its values.
 *
 * Entries in this map are automatically removed when their values are garbage collected. To enable
 * this automatic removal, a background cleanup loop must be started by calling [runCleanupLoop].
 *
 * This class is useful for caching objects that should be shared as long as they are referenced
 * elsewhere, but allowed to be reclaimed by the garbage collector otherwise.
 *
 * ### Safe for concurrent use
 *
 * All methods and properties of [SuspendingWeakValueHashMap] are thread-safe and may be safely
 * called and/or accessed concurrently from multiple threads and/or coroutines.
 *
 * @param K the type of keys maintained by this map.
 * @param V the type of mapped values, which must be a reference type.
 * @param blockingDispatcher the [CoroutineDispatcher] to use for the blocking operations in the
 * [runCleanupLoop] background cleanup loop (e.g., `Dispatchers.IO`).
 */
internal class SuspendingWeakValueHashMap<K, V : Any>(
  private val blockingDispatcher: CoroutineDispatcher
) : AutoCloseable {

  private val state = MutableStateFlow<State<K, V>>(State.New())

  /**
   * Returns the value corresponding to the given key.
   *
   * @param key the key whose associated value is to be returned.
   * @return the value to which the specified key is mapped, or `null` if this map contains no
   * mapping for the key, the value has been garbage collected, [runCleanupLoop] has not been
   * called, or [close] has been called.
   */
  suspend fun get(key: K): V? =
    runLockedWithMapIfAvailable(resultIfMapNotAvailable = null) { map -> map[key]?.get() }

  /**
   * Removes the mapping for the given key, if present.
   *
   * @param key the key whose mapping to remove.
   * @return the previous value associated with the given key, or `null` if there was no mapping for
   * the key, the previous value was garbage collected, [runCleanupLoop] has not been called, or
   * [close] has been called.
   */
  suspend fun remove(key: K): V? =
    runLockedWithMapIfAvailable(resultIfMapNotAvailable = null) { map ->
      val oldValueReference = map.remove(key)
      val oldValue = oldValueReference?.get()
      oldValueReference?.clear()
      return oldValue
    }

  /**
   * Removes all mappings.
   *
   * The map will be empty after this call returns, which includes entries whose values have been
   * garbage collected but have not yet been removed by [runCleanupLoop].
   *
   * @return the number of key/value pairs removed, or `0` if [runCleanupLoop] has not been called
   * or [close] has been called.
   */
  suspend fun clear(): Int =
    runLockedWithMapIfAvailable(resultIfMapNotAvailable = 0) { map ->
      val removeCount = map.size
      map.values.forEach(ValueReference<K, V>::clear)
      map.clear()
      return removeCount
    }

  /**
   * Returns the number of key-value mappings.
   *
   * Note that this count includes entries whose values have been garbage collected but have not yet
   * been removed by [runCleanupLoop].
   *
   * @return the number of key-value mappings in this map, or `0` if [runCleanupLoop] has not been
   * called or [close] has been called.
   */
  suspend fun size(): Int =
    runLockedWithMapIfAvailable(resultIfMapNotAvailable = 0) { map -> map.size }

  /**
   * Starts the background cleanup loop that removes entries from the map when their values are
   * garbage collected.
   *
   * This method must be called exactly once before any calls to [put]. The job will be started in
   * the given [CoroutineScope] but will use a custom dispatcher that is suitable for blocking
   * operations (the [CoroutineDispatcher] given to the constructor). The job will be canceled by
   * [close].
   *
   * @throws IllegalStateException if this method is called more than once or after [close].
   */
  fun startCleanupJob(coroutineScope: CoroutineScope): Job {
    val cleanupJob = CleanupJob<K, V>(coroutineScope, blockingDispatcher)

    while (true) {
      val currentState = state.value

      val newState: State.Open<K, V> =
        when (currentState) {
          is State.Closed ->
            throw IllegalStateException("runCleanupLoop() cannot be called after close()")
          is State.Open -> throw IllegalStateException("runCleanupLoop() has already been called")
          is State.New -> cleanupJob.toOpenState()
        }

      if (state.compareAndSet(currentState, newState)) {
        break
      }
    }

    return cleanupJob.job
  }

  /**
   * Associates the given value with the given key.
   *
   * If the map previously contained a mapping for the given key, the old value is replaced and the
   * old value is returned. The value is stored using a weak reference, allowing it to be garbage
   * collected if no strong references to it remain.
   *
   * @param key the key with which the given value is to be associated.
   * @param value the value to be associated with the given key.
   * @return the previous value associated with the given key, or `null` if there was no mapping for
   * the key or if the previous value was garbage collected.
   * @throws IllegalStateException if [runCleanupLoop] has not been called or if [close] has been
   * called.
   */
  suspend fun put(key: K, value: V): V? =
    when (val currentState = this.state.value) {
      is State.Closed -> throw IllegalStateException("put() cannot be called after close()")
      is State.New -> throw IllegalStateException("runCleanupLoop() must be called before put()")
      is State.Open ->
        currentState.run {
          cleanupJob.start()
          mutex.withLock {
            val oldValueReference = map.put(key, ValueReference(key, value, referenceQueue))
            val oldValue = oldValueReference?.get()
            oldValueReference?.clear()
            oldValue
          }
        }
    }

  /** Closes this map and cancels the background cleanup loop. */
  override fun close() {
    while (true) {
      val currentState = state.value

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

  private suspend inline fun <T> runLockedWithMapIfAvailable(
    resultIfMapNotAvailable: T,
    block: (MutableMap<K, ValueReference<K, V>>) -> T
  ): T =
    when (val currentState = state.value) {
      is State.New,
      is State.Closed -> resultIfMapNotAvailable
      is State.Open -> currentState.mutex.withLock { block(currentState.map) }
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

  private class CleanupJob<K, V : Any>(
    coroutineScope: CoroutineScope,
    blockingDispatcher: CoroutineDispatcher
  ) {
    val mutex: Mutex = Mutex()
    val map: MutableMap<K, ValueReference<K, V>> = mutableMapOf()
    val referenceQueue: ReferenceQueue<V> = ReferenceQueue<V>()

    val job: Job =
      coroutineScope.launch(
        blockingDispatcher + CoroutineName("SuspendingWeakValueHashMap_CleanupJob"),
        CoroutineStart.LAZY,
      ) {
        while (true) {
          remove(runInterruptible { referenceQueue.removeValueReference() })
          while (true) {
            remove(referenceQueue.pollValueReference() ?: break)
          }
        }
      }

    private suspend fun remove(ref: ValueReference<K, V>) {
      mutex.withLock { map.remove(ref.key, ref) }
    }
  }

  private companion object {

    @Suppress("UNCHECKED_CAST")
    fun <K, V : Any> ReferenceQueue<V>.removeValueReference(): ValueReference<K, V> =
      remove() as ValueReference<K, V>

    @Suppress("UNCHECKED_CAST")
    fun <K, V : Any> ReferenceQueue<V>.pollValueReference(): ValueReference<K, V>? =
      poll() as ValueReference<K, V>?

    fun <K, V : Any> CleanupJob<K, V>.toOpenState(): State.Open<K, V> =
      State.Open(job, mutex, map, referenceQueue)
  }
}
