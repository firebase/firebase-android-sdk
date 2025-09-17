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

import java.util.concurrent.Semaphore

/**
 * Manages CPU-bound work on background threads to enable parallel processing.
 *
 * Instances of this class are _not_ thread-safe. All methods of an instance of this class must be
 * called from the same thread. The behavior of an instance is undefined if any of the methods are
 * called from multiple threads.
 */
internal class BackgroundQueue {

  private var state: State = State.Submitting()

  /**
   * Submit a task for asynchronous execution on the executor of the owning [BackgroundQueue].
   *
   * @throws IllegalStateException if [drain] has been called.
   */
  fun submit(runnable: Runnable) {
    val submittingState = this.state
    check(submittingState is State.Submitting) { "submit() may not be called after drain()" }

    submittingState.taskCount++
    Executors.CPU_WORKLOAD_EXECUTOR.execute {
      try {
        runnable.run()
      } finally {
        submittingState.completedTasks.release()
      }
    }
  }

  /**
   * Blocks until all tasks submitted via calls to [submit] have completed.
   *
   * @throws IllegalStateException if called more than once.
   */
  fun drain() {
    val submittingState = this.state
    check(submittingState is State.Submitting) { "drain() may not be called more than once" }
    this.state = State.Draining

    submittingState.completedTasks.acquire(submittingState.taskCount)
  }

  private sealed interface State {
    class Submitting : State {
      val completedTasks = Semaphore(0)
      var taskCount: Int = 0
    }
    object Draining : State
  }
}
