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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

/**
 * An executor that forwards executions to another executor, but caps the number of pending
 * operations. Tasks scheduled past the specified limit are directly invoked on the calling thread,
 * reducing the total memory consumed by pending tasks.
 */
class ThrottledForwardingExecutor implements Executor {
  private final Executor executor;
  private final Semaphore availableSlots;

  /**
   * Instantiates a new ThrottledForwardingExecutor.
   *
   * @param maximumConcurrency The maximum number of pending tasks to schedule on the provided
   *     executor.
   * @param executor The executor to forward tasks to.
   */
  ThrottledForwardingExecutor(int maximumConcurrency, Executor executor) {
    this.availableSlots = new Semaphore(maximumConcurrency);
    this.executor = executor;
  }

  /**
   * Forwards a task to the provided executor if the current number of pending tasks is less than
   * the configured limit. Otherwise, executes the task directly.
   *
   * @param command The task to run.
   */
  @Override
  public void execute(Runnable command) {
    if (availableSlots.tryAcquire()) {
      try {
        executor.execute(
            () -> {
              command.run();
              availableSlots.release();
            });
      } catch (RejectedExecutionException e) {
        command.run();
      }
    } else {
      command.run();
    }
  }
}
