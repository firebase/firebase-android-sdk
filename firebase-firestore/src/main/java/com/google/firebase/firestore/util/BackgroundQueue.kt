/*
 * Copyright 2025 Google LLC
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
package com.google.firebase.firestore.util

import java.util.concurrent.Executor
import java.util.concurrent.Semaphore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asExecutor

/**
 * Manages CPU-bound work on background threads to enable parallel processing.
 *
 * Instances of this class are _not_ thread-safe. All methods of an instance of this class must be
 * called from the same thread. The behavior of an instance is undefined if any of the methods are
 * called from multiple threads.
 */
internal class BackgroundQueue<K, V> {

  private var state: State<K, V> = State.Submitting()

  /** Overload for convenience of being called from Java code. */
  fun submit(consumer: Consumer<HashMap<K, V>>) = this.submit { consumer.accept(it) }

  /**
   * Submit a task for asynchronous execution on the executor of the owning [BackgroundQueue].
   *
   * @throws IllegalStateException if [drain] has been called.
   */
  fun submit(runnable: (results: HashMap<K, V>) -> Unit) {
    val submittingState = this.state
    check(submittingState is State.Submitting) {
      "submit() may not be called after drain() or drainInto()"
    }

    submittingState.run {
      taskCount++
      executor.execute {
        try {
          runnable(threadLocalResults.get()!!)
        } finally {
          completedTasks.release()
        }
      }
    }
  }

  /**
   * Blocks until all tasks submitted via calls to [submit] have completed.
   *
   * The results produced by each thread are merged into a new [HashMap] and returned.
   *
   * @throws IllegalStateException if [drain] or [drainInto] has already been called.
   */
  fun drain(): HashMap<K, V> = HashMap<K, V>().also { drainInto(it) }

  /**
   * Blocks until all tasks submitted via calls to [submit] have completed.
   *
   * The results produced by each thread are merged into the given map.
   *
   * @throws IllegalStateException if [drain] or [drainInto] has already been called.
   */
  fun drainInto(results: MutableMap<K, V>) {
    val submittingState = this.state
    check(submittingState is State.Submitting) { "drain() or drainInto() has already been called" }
    this.state = State.Draining()
    return submittingState.run {
      completedTasks.acquire(taskCount)
      threadLocalResults.mergeResultsInto(results)
    }
  }

  private class ThreadLocalResults<K, V> : ThreadLocal<HashMap<K, V>>() {

    private val results = mutableListOf<HashMap<K, V>>()

    override fun initialValue(): HashMap<K, V> {
      synchronized(results) {
        val result = HashMap<K, V>()
        results.add(result)
        return result
      }
    }

    fun mergeResultsInto(mergedResults: MutableMap<K, V>) {
      synchronized(results) { results.forEach { mergedResults.putAll(it) } }
    }
  }

  private sealed interface State<K, V> {
    class Submitting<K, V> : State<K, V> {
      val completedTasks = Semaphore(0)
      val threadLocalResults = ThreadLocalResults<K, V>()
      var taskCount: Int = 0
    }
    class Draining<K, V> : State<K, V>
  }

  companion object {

    /**
     * The maximum amount of parallelism shared by all instances of this class.
     *
     * This is equal to the number of processor cores available, or 2, whichever is larger.
     */
    val maxParallelism = Runtime.getRuntime().availableProcessors().coerceAtLeast(2)

    private val executor: Executor =
      Dispatchers.IO.limitedParallelism(maxParallelism, "firestore.BackgroundQueue").asExecutor()
  }
}
