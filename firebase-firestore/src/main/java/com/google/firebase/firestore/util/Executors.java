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

import android.os.AsyncTask;
import com.google.android.gms.tasks.TaskExecutors;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Helper class for executors. */
public final class Executors {
  /**
   * The maximum number of tasks we submit to AsyncTask.THREAD_POOL_EXECUTOR.
   *
   * <p>The limit is based on the number of core threads spun by THREAD_POOL_EXECUTOR and addresses
   * its queue size limit of 120 pending tasks.
   */
  private static final int MAXIMUM_CONCURRENT_BACKGROUND_TASKS = 4;

  /**
   * The default executor for user visible callbacks. It is an executor scheduling callbacks on
   * Android's main thread.
   */
  public static final Executor DEFAULT_CALLBACK_EXECUTOR = TaskExecutors.MAIN_THREAD;

  /** An executor that executes the provided runnable immediately on the current thread. */
  public static final Executor DIRECT_EXECUTOR = Runnable::run;

  /**
   * An executor that runs tasks in parallel on Android's AsyncTask.THREAD_POOL_EXECUTOR.
   *
   * <p>Unlike the main THREAD_POOL_EXECUTOR, this executor manages its own queue of tasks and can
   * handle an unbounded number of pending tasks.
   */
  public static final Executor BACKGROUND_EXECUTOR =
      new Executor() {
        AtomicInteger activeRunnerCount = new AtomicInteger(0);
        Queue<Runnable> pendingTasks = new ConcurrentLinkedQueue<>();

        @Override
        public void execute(Runnable command) {
          pendingTasks.add(command);

          if (activeRunnerCount.get() < MAXIMUM_CONCURRENT_BACKGROUND_TASKS) {
            // Note that the runner count could temporarily exceed
            // MAXIMUM_CONCURRENT_BACKGROUND_TASKS if this is code path was executed in parallel.
            // While undesired, this would merely queue another task on THREAD_POOL_EXECUTOR,
            // and we are unlikely to hit the 120 pending task limit.
            activeRunnerCount.incrementAndGet();
            AsyncTask.THREAD_POOL_EXECUTOR.execute(
                () -> {
                  Runnable r;
                  while ((r = pendingTasks.poll()) != null) {
                    r.run();
                  }
                  activeRunnerCount.decrementAndGet();
                });
          }
        }
      };

  private Executors() {
    // Private constructor to prevent initialization
  }
}
