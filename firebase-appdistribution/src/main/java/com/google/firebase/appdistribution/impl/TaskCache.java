// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

/**
 * A cache for a {@link Task}, for use in cases where we only ever want one active task at a time
 * for a particular operation.
 *
 * <p>If you need a reference to an underlying TaskCompletionSource, use {@link
 * TaskCompletionSourceCache} instead.
 */
class TaskCache<T> {

  /** A functional interface for a producer of a new {@link Task}. */
  @FunctionalInterface
  interface TaskProducer<T> {

    /** Produce a new {@link Task}. */
    Task<T> produce();
  }

  private Task<T> cachedTask;
  private final Executor sequentialExecutor;

  /**
   * Constructor for a {@link TaskCache} that controls access using its own sequential executor
   * backed by the given base executor.
   *
   * @param baseExecutor Executor (typically {@link Lightweight}) to back the sequential executor.
   */
  TaskCache(Executor baseExecutor) {
    sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
  }

  /**
   * Gets a cached {@link Task}, if there is one and it is not completed, or else calls the given
   * {@code producer} and caches the return value.
   */
  Task<T> getOrCreateTask(TaskProducer<T> producer) {
    TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          if (!isOngoing(cachedTask)) {
            cachedTask = producer.produce();
          }
          TaskUtils.shadowTask(taskCompletionSource, cachedTask);
        });
    return taskCompletionSource.getTask();
  }

  private static <T> boolean isOngoing(Task<T> task) {
    return task != null && !task.isComplete();
  }
}
