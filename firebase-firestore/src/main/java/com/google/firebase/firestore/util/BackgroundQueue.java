// Copyright 2019 Google LLC
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

import java.util.concurrent.Executor;
import java.util.concurrent.Semaphore;

/**
 * A simple queue that executes tasks in parallel on the Android's AsyncTask.THREAD_POOL_EXECUTOR
 * and supports blocking on their completion.
 *
 * <p>This class is not thread-safe. In particular, `execute()` and `drain()` should not be called
 * from parallel threads.
 */
public class BackgroundQueue implements Executor {
  private Semaphore completedTasks = new Semaphore(0);
  private int pendingTaskCount = 0;

  /** Enqueue a task on Android's THREAD_POOL_EXECUTOR. */
  @Override
  public void execute(Runnable task) {
    ++pendingTaskCount;
    Executors.BACKGROUND_EXECUTOR.execute(
        () -> {
          task.run();
          completedTasks.release();
        });
  }

  /** Wait for all currently scheduled tasks to complete. */
  public void drain() throws InterruptedException {
    completedTasks.acquire(pendingTaskCount);
    pendingTaskCount = 0;
  }
}
