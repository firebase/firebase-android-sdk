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
import java.util.concurrent.Executor;

/** Helper class for executors. */
public final class Executors {
  /**
   * The maximum number of tasks we submit to AsyncTask.THREAD_POOL_EXECUTOR.
   *
   * <p>The limit is based on the number of core threads spun by THREAD_POOL_EXECUTOR and is well
   * below the queue size limit of 120 pending tasks. Limiting our usage of the THREAD_POOL_EXECUTOR
   * allows other users to schedule their own operations on the shared THREAD_POOL_EXECUTOR.
   */
  private static final int ASYNC_THREAD_POOL_MAXIMUM_CONCURRENCY = 4;

  /**
   * The default executor for user visible callbacks. It is an executor scheduling callbacks on
   * Android's main thread.
   */
  public static final Executor DEFAULT_CALLBACK_EXECUTOR = TaskExecutors.MAIN_THREAD;

  /** An executor that executes the provided runnable immediately on the current thread. */
  public static final Executor DIRECT_EXECUTOR = Runnable::run;

  /** An executor that runs tasks in parallel on Android's AsyncTask.THREAD_POOL_EXECUTOR. */
  public static final Executor BACKGROUND_EXECUTOR =
      new ThrottledForwardingExecutor(
          ASYNC_THREAD_POOL_MAXIMUM_CONCURRENCY, AsyncTask.THREAD_POOL_EXECUTOR);

  private Executors() {
    // Private constructor to prevent initialization
  }
}
