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

import com.google.firebase.dataconnect.core.LoggerGlobals.Logger
import java.lang.ref.ReferenceQueue
import java.lang.ref.WeakReference
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * A thread-safe, coroutine-aware map that holds weak references to its values.
 *
 * Entries in this map are eventually removed when their values are garbage collected. This
 * automatic removal requires a background cleanup job to run in a blocking fashion, hence the
 * requirement of a [CoroutineDispatcher] constructor parameter. This background cleanup job is
 * stopped by calling [close]. Therefore, it is extremely important to call [close] when the object
 * is no longer needed.
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
 * @param V the type of mapped values.
 * @param blockingDispatcher the [CoroutineDispatcher] to use for the blocking operations in the
 * background cleanup job. Long-running blocking operations will be done on the dispatcher, so it is
 * strongly recommended to use an "elastic" dispatcher, like [kotlinx.coroutines.Dispatchers.IO].
 */
internal class SuspendingWeakValueHashMap<K, V : Any>(
  nonBlockingDispatcher: CoroutineDispatcher,
  blockingDispatcher: CoroutineDispatcher,
) : AutoCloseable {

  private val logger = Logger("SuspendingWeakValueHashMap")

  private val _garbageCollectedKeys =
    MutableSharedFlow<K>(
      replay = 0,
      extraBufferCapacity = Int.MAX_VALUE,
      onBufferOverflow = BufferOverflow.SUSPEND,
    )

  /**
   * A [Flow] that emits the keys of entries whose values have been garbage collected.
   *
   * Note that this [Flow] only emits events for keys that were reclaimed by the garbage collector
   * automatically; keys/value pairs that were explicitly removed, such as by calling [remove] or
   * [clear], do _not_ result in events being produced by this [Flow].
   *
   * This flow is useful for being notified when entries are automatically removed from the map.
   * Note that the emission of a key from this flow happens _after_ the corresponding entry has been
   * removed from the map's internal storage.
   *
   * This flow is thread-safe and supports multiple concurrent collectors.
   */
  val garbageCollectedKeys: SharedFlow<K> = _garbageCollectedKeys.asSharedFlow()

  private class LifecycleResource<K, V : Any>(
    val cleanupJob: Job,
    val mutex: Mutex,
    val map: MutableMap<K, ValueReference<K, V>>,
    val referenceQueue: ReferenceQueue<V>,
  )

  private val lifecycle =
    ObjectLifecycleManager<LifecycleResource<K, V>, Unit>(
      coroutineDispatcher = nonBlockingDispatcher,
      logger = logger,
    ) {
      val mutex = Mutex()
      val map = mutableMapOf<K, ValueReference<K, V>>()
      val referenceQueue = ReferenceQueue<V>()

      val cleanupJob =
        lifetimeScope.launch(
          blockingDispatcher + CoroutineName("${logger.nameWithId} cleanup"),
          start = CoroutineStart.LAZY,
        ) {
          suspend fun remove(ref: ValueReference<K, V>) {
            mutex.withLock { map.remove(ref.key, ref) }

            _garbageCollectedKeys.tryEmit(ref.key).also {
              check(it) {
                "internal error hawykvtjbr: _garbageCollectedKeys.tryEmit returned $it " +
                  "(this must mean that something is configured incorrectly because this " +
                  "should never happen when extraBufferCapacity=Int.MAX_VALUE)"
              }
            }
          }

          while (true) {
            remove(runInterruptible { referenceQueue.removeValueReference() })
            while (true) {
              remove(referenceQueue.pollValueReference() ?: break)
            }
          }
        }

      LifecycleResource(cleanupJob, mutex, map, referenceQueue)
    }

  /**
   * Returns the value corresponding to the given key.
   *
   * @param key the key whose associated value is to be returned.
   * @return the value to which the specified key is mapped, or `null` if this map contains no
   * mapping for the key, the value has been garbage collected, or [close] has been called.
   */
  suspend fun get(key: K): V? =
    runLockedWithMapIfAvailable(resultIfMapNotAvailable = null) { map -> map[key]?.get() }

  /**
   * Removes the mapping for the given key, if present.
   *
   * @param key the key whose mapping to remove.
   * @return the previous value associated with the given key, or `null` if there was no mapping for
   * the key, the previous value was garbage collected, or [close] has been called.
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
   * garbage collected but have not yet been removed by the background cleanup job.
   *
   * @return the number of key/value pairs removed, or `0` if [close] has been called.
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
   * been removed by the background cleanup job.
   *
   * @return the number of key-value mappings in this map, or `0` if [close] has been called.
   */
  suspend fun size(): Int =
    runLockedWithMapIfAvailable(resultIfMapNotAvailable = 0) { map -> map.size }

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
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun put(key: K, value: V): V? =
    lifecycle.open().run {
      cleanupJob.start()
      mutex.withLock { map.put(key, value, referenceQueue) }
    }

  /**
   * Associates the given value with the given key if the key is not already associated with a
   * value.
   *
   * @param key the key whose value to return, or with which the given value is to be associated.
   * @param value the value to be associated with the given key if there is no value currently
   * associated with it.
   * @return the current value associated with the given key, or the given value if there was no
   * value associated with the given key, or if the previous value was garbage collected.
   * @throws IllegalStateException if [close] has been called.
   */
  suspend fun putIfAbsent(key: K, value: V): V =
    lifecycle.open().run {
      cleanupJob.start()
      mutex.withLock {
        val currentValue = map[key]?.get()
        if (currentValue !== null) {
          currentValue
        } else {
          map.put(key, value, referenceQueue)
          value
        }
      }
    }

  private fun MutableMap<K, ValueReference<K, V>>.put(
    key: K,
    value: V,
    referenceQueue: ReferenceQueue<V>
  ): V? {
    val oldValueReference = put(key, ValueReference(key, value, referenceQueue))
    val oldValue = oldValueReference?.get()
    oldValueReference?.clear()
    return oldValue
  }

  /** Closes this map and cancels the background cleanup loop. */
  override fun close() {
    // Closing the lifecycle in the background is fine because the "only" work that it does is
    // cancel a CoroutineScope, which is quick and cheap and never fails.
    @Suppress("DeferredResultUnused") lifecycle.close(SuspendingCloseHandlingStrategy.Async)
  }

  private suspend inline fun <T> runLockedWithMapIfAvailable(
    resultIfMapNotAvailable: T,
    block: (MutableMap<K, ValueReference<K, V>>) -> T
  ): T {
    val resources = lifecycle.poll() ?: return resultIfMapNotAvailable
    resources.mutex.withLock {
      return block(resources.map)
    }
  }

  private class ValueReference<K, V : Any>(
    val key: K,
    value: V,
    referenceQueue: ReferenceQueue<V>
  ) : WeakReference<V>(value, referenceQueue)

  private sealed interface State<K, V : Any> {
    class Uninitialized<K, V : Any>(val coroutineScope: CoroutineScope) : State<K, V>

    class Open<K, V : Any>(
      val coroutineScope: CoroutineScope,
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

    @Suppress("UNCHECKED_CAST")
    fun <K, V : Any> ReferenceQueue<V>.pollValueReference(): ValueReference<K, V>? =
      poll() as ValueReference<K, V>?
  }
}

/**
 * Returns the value associated with the given [key], populating the value using the given
 * [defaultValue] function if it is not present.
 *
 * Note that there is a possibility that [defaultValue] is called but its value discarded. This can
 * happen if some other thread or coroutine concurrently associates a value with the same key.
 *
 * @param key the key whose associated value to return, and with which to associate the new value if
 * there was no value associated with it.
 * @param defaultValue the function to call to create the value if it's not present; its invocation
 * satisfies [kotlin.contracts.CallsInPlace] and [kotlin.contracts.InvocationKind.AT_MOST_ONCE].
 * @return the value that was associated with the key or became associated with the given key by a
 * concurrent thread or coroutine, or the value returned by the [defaultValue] function if there was
 * no value associated with the key.
 */
internal suspend inline fun <K, V : Any> SuspendingWeakValueHashMap<K, V>.getOrPut(
  key: K,
  defaultValue: () -> V,
): V = get(key) ?: putIfAbsent(key, defaultValue())
