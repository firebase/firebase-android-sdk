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
 * A cache for a {@link TaskCompletionSource}, for use in cases where we only ever want one active
 * task at a time for a particular operation.
 *
 * <p>This is equivalent to {@link UpdateTaskCache} but for Tasks.
 */
class TaskCompletionSourceCache<T> {

  /** A functional interface for a producer of a new {@link TaskCompletionSource}. */
  @FunctionalInterface
  interface TaskCompletionSourceProducer<T> {

    /** Produce a new {@link TaskCompletionSource}. */
    TaskCompletionSource<T> produce();
  }

  private TaskCompletionSource<T> cachedTaskCompletionSource;
  private final Executor sequentialExecutor;

  /**
   * Constructor for a {@link TaskCompletionSourceCache} that controls access using its own
   * sequential executor backed by the given base executor.
   *
   * @param baseExecutor Executor to back the sequential executor. This can be a {@link Lightweight}
   *     executor unless the {@link TaskCompletionSourceProducer} passed to {@link
   *     #getOrCreateTaskFromCompletionSource} needs to be executed on a different executor.
   */
  TaskCompletionSourceCache(Executor baseExecutor) {
    sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
  }

  /**
   * Gets a cached {@link TaskCompletionSource}, if there is one and it is not completed, or else
   * calls the given {@code producer} and caches the return value.
   */
  Task<T> getOrCreateTaskFromCompletionSource(TaskCompletionSourceProducer<T> producer) {
    TaskCompletionSource<T> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          if (cachedTaskCompletionSource == null
              || cachedTaskCompletionSource.getTask().isComplete()) {
            cachedTaskCompletionSource = producer.produce();
          }
          TaskUtils.shadowTask(taskCompletionSource, cachedTaskCompletionSource.getTask());
        });
    return taskCompletionSource.getTask();
  }

  /**
   * Sets the result on the cached {@link TaskCompletionSource}.
   *
   * <p>The task must be created using {@link #getOrCreateTaskFromCompletionSource} before calling
   * this method.
   *
   * @return A task that resolves when the change is applied, or fails with {@link
   *     IllegalStateException} if the task was not created yet or was already completed.
   */
  Task<Void> setResult(T result) {
    return executeIfOngoing(() -> cachedTaskCompletionSource.setResult(result));
  }

  /**
   * Sets the exception on the cached {@link TaskCompletionSource}, if there is one and it is not
   * completed.
   *
   * <p>The task must be created using {@link #getOrCreateTaskFromCompletionSource} before calling
   * this method.
   *
   * @return A task that resolves when the change is applied, or fails with {@link
   *     IllegalStateException} if the task was not created yet or was already completed.
   */
  Task<Void> setException(Exception e) {
    return executeIfOngoing(() -> cachedTaskCompletionSource.setException(e));
  }

  /**
   * Returns a task indicating whether the cached {@link TaskCompletionSource} has been initialized
   * and is not yet completed.
   */
  Task<Boolean> isOngoing() {
    TaskCompletionSource<Boolean> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () ->
            taskCompletionSource.setResult(
                cachedTaskCompletionSource != null
                    && !cachedTaskCompletionSource.getTask().isComplete()));
    return taskCompletionSource.getTask();
  }

  private Task<Void> executeIfOngoing(Runnable r) {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          if (cachedTaskCompletionSource == null) {
            taskCompletionSource.setException(
                new IllegalStateException(
                    "Tried to set exception before calling getOrCreateTaskFromCompletionSource()"));
          } else if (cachedTaskCompletionSource.getTask().isComplete()) {
            taskCompletionSource.setException(
                new IllegalStateException("Tried to set exception on a completed task"));
          } else {
            r.run();
            taskCompletionSource.setResult(null);
          }
        });
    return taskCompletionSource.getTask();
  }
}
