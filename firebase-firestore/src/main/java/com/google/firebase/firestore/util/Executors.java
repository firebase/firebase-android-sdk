// Copyright 2018 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.util;

import static kotlinx.coroutines.ExecutorsKt.asExecutor;

import com.google.android.gms.tasks.TaskExecutors;
import java.util.concurrent.Executor;
import kotlinx.coroutines.Dispatchers;

/** Helper class for executors. */
public final class Executors {
  /**
   * The number of physical CPU cores available for multithreaded execution, or 2, whichever is
   * larger.
   * <p>
   * CPU-bound tasks should never use more than this number of concurrent threads as doing so will
   * almost certainly reduce throughput due to the overhead of context switching.
   */
  public static final int HARDWARE_CONCURRENCY =
      Math.max(2, Runtime.getRuntime().availableProcessors());

  /**
   * The default executor for user visible callbacks. It is an executor scheduling callbacks on
   * Android's main thread.
   */
  public static final Executor DEFAULT_CALLBACK_EXECUTOR = TaskExecutors.MAIN_THREAD;

  /** An executor that executes the provided runnable immediately on the current thread. */
  public static final Executor DIRECT_EXECUTOR = Runnable::run;

  /**
   * An executor suitable for short tasks that perform little or no blocking.
   */
  public static final Executor SHORT_WORKLOAD_EXECUTOR =
      asExecutor(
          Dispatchers.getIO()
              .limitedParallelism(HARDWARE_CONCURRENCY, "firestore.SHORT_WORKLOAD_EXECUTOR"));

  /**
   * An executor suitable for IO-bound workloads. New threads are usually created to satisfy demand,
   * and, therefore, tasks do not usually wait in a queue for execution.
   */
  public static final Executor IO_WORKLOAD_EXECUTOR = asExecutor(Dispatchers.getIO());

  /**
   * An executor suitable for CPU-bound workloads. No more tasks than available CPU cores will
   * execute concurrently, while other tasks line up and wait for a thread to become available, and
   * are scheduled in an arbitrary order.
   */
  public static final Executor CPU_WORKLOAD_EXECUTOR =
      asExecutor(
          Dispatchers.getIO()
              .limitedParallelism(HARDWARE_CONCURRENCY, "firestore.CPU_WORKLOAD_EXECUTOR"));

  /**
   * Creates and returns a new {@link Executor} that executes tasks sequentially.
   * <p>
   * The implementation guarantees that tasks are executed sequentially and that a happens-before
   * relation is established between them. This means that tasks run by this executor do _not_ need
   * to synchronize access to shared resources, such as using "synchronized" blocks or "volatile"
   * variables. See `kotlinx.coroutines.limitedParallelism` for full details.
   * <p>
   * Note that there is no guarantee that tasks will all run on the _same thread_.
   *
   * @param name a brief name to assign to the executor, for debugging purposes.
   * @return the newly-created executor.
   */
  public static Executor newSequentialExecutor(String name) {
    return asExecutor(Dispatchers.getIO().limitedParallelism(1, "firestore.seq." + name));
  }

  private Executors() {
    // Private constructor to prevent initialization
  }
}
