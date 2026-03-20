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

package com.google.firebase.dataconnect

import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadFactory
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A specialized, thread-safe collection designed to hold weak references to its values while
 * providing a coroutine-friendly interface.
 *
 * Note that [size] returns the number of entries currently in the map, including those whose values
 * have been collected but are still awaiting background cleanup.
 *
 * ### 1. Purpose: Memory-Sensitive Caching The primary goal of this class is to allow values to be
 * garbage collected (GC'd) if they are no longer referenced elsewhere in the application. It
 * achieves this by wrapping its values in [WeakReference] instances.
 *
 * ### 2. Coroutine Integration A [Mutex] is used for synchronization. All public methods are
 * `suspend` functions that acquire this mutex, ensuring that coroutines suspend non-blockingly
 * instead of blocking a platform thread.
 *
 * ### 3. Automatic Background Cleanup To prevent memory leaks of the reference objects themselves
 * after values are GC'd, it utilizes a [ReferenceQueue] that is polled by a background "cleanup"
 * thread. On the first map operation, the cleanup thread is created via the provided
 * [cleanupThreadFactory] and started. The cleanup thread then loops waiting for values to be
 * garbage collected and safely removes the corresponding entries from the map. It only removes the
 * entry if the current value matches the collected reference, preventing collision-safe race
 * conditions where a key is reused.
 *
 * ### 4. Lifecycle State Machine The class manages its lifecycle through an internal state machine:
 * - `New`: Initial state. No map or thread exists yet.
 * - `Open`: The state after the first operation. The internal map, queue, and thread are active.
 * - `Closed`: After [close] is called. The cleanup thread is interrupted, and further operations
 * will throw an [IllegalStateException]. Transitioning to this state releases the internal map and
 * queue, allowing them to be garbage collected immediately.
 *
 * ### 5. Custom ValueReference The internal map stores a custom [ValueReference] that holds the
 * key. This is critical because when the GC collects the value, the cleanup thread needs to know
 * which key to remove from the map.
 */
internal class SuspendingWeakValueHashMap<K, V : Any>(cleanupThreadFactory: ThreadFactory) {

  private val mutex = Mutex()
  private var state: State<K, V> = State.New(cleanupThreadFactory)

  /**
   * Returns the value corresponding to the given [key], or `null` if such a key is not present in
   * the map, or if the value has been garbage collected.
   *
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun get(key: K): V? = runWithLock { map[key]?.get() }

  /**
   * Associates the specified [value] with the specified [key] in the map. If the map previously
   * contained a mapping for the key, the old value is replaced by the specified value.
   *
   * @return the previous value associated with the specified key, or `null` if there was no mapping
   * for the key or if the previous value was garbage collected.
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun put(key: K, value: V): V? = runWithLock {
    val oldValueReference = map.put(key, ValueReference(key, value, referenceQueue))
    val oldValue = oldValueReference?.get()
    oldValueReference?.clear()
    return oldValue
  }

  /**
   * Removes the mapping for the specified [key] from this map if present.
   *
   * @return the previous value associated with the specified key, or `null` if there was no mapping
   * for the key or if the previous value was garbage collected.
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun remove(key: K): V? = runWithLock {
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
  suspend fun clear(): Int = runWithLock {
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
  suspend fun size(): Int = runWithLock { map.size }

  /**
   * Closes the map, releasing its resources.
   *
   * The background cleanup thread is stopped, and the internal map and reference queue are
   * released, making them eligible for immediate garbage collection.
   *
   * It is safe to call [close] multiple times. Subsequent invocations will do nothing.
   *
   * Any subsequent operations on this map will throw an [IllegalStateException].
   */
  suspend fun close(): Unit =
    mutex.withLock {
      when (val currentState = state) {
        is State.Closed<*, *> -> return
        is State.New<*, *> -> {}
        is State.Open<*, *> -> currentState.cleanupThread.interrupt()
      }
      state = State.Closed()
    }

  private suspend inline fun <T> runWithLock(block: State.Open<K, V>.() -> T): T =
    mutex.withLock {
      val openState: State.Open<K, V> =
        when (val currentState = state) {
          is State.Open<K, V> -> currentState
          is State.Closed<K, V> -> throw IllegalStateException("close() has been called")
          is State.New<K, V> -> {
            val map = mutableMapOf<K, ValueReference<K, V>>()
            val referenceQueue = ReferenceQueue<V>()
            val cleanupThread =
              currentState.cleanupThreadFactory.newThread { cleanupLoop(map, referenceQueue) }
            cleanupThread.start()
            val openState = State.Open<K, V>(map, referenceQueue, cleanupThread)
            state = openState
            openState
          }
        }
      openState.run(block)
    }

  private fun cleanupLoop(
    map: MutableMap<K, ValueReference<K, V>>,
    referenceQueue: ReferenceQueue<V>
  ) = runBlocking {
    while (true) {
      val reference: ValueReference<K, V> =
        try {
          referenceQueue.removeValueReference()
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
          break
        }

      mutex.withLock { map.remove(reference.key, reference) }
    }
  }

  private class ValueReference<K, V : Any>(
    val key: K,
    value: V,
    referenceQueue: ReferenceQueue<V>
  ) : WeakReference<V>(value, referenceQueue)

  private sealed interface State<K, V : Any> {
    class New<K, V : Any>(val cleanupThreadFactory: ThreadFactory) : State<K, V>

    class Open<K, V : Any>(
      val map: MutableMap<K, ValueReference<K, V>>,
      val referenceQueue: ReferenceQueue<V>,
      val cleanupThread: Thread,
    ) : State<K, V>

    class Closed<K, V : Any> : State<K, V>
  }

  private companion object {

    @Suppress("UNCHECKED_CAST")
    fun <K, V : Any> ReferenceQueue<V>.removeValueReference(): ValueReference<K, V> =
      remove() as ValueReference<K, V>
  }
}
