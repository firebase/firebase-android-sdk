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
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.UpdateProgress;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

/**
 * A cache for an {@link UpdateTaskImpl}, for use in cases where we only ever want one active task
 * at a time for a particular operation.
 *
 * <p>This is equivalent to {@link TaskCompletionSourceCache} but for UpdateTasks.
 */
class UpdateTaskCache {

  /** A functional interface for a producer of a new {@link UpdateTaskImpl}. */
  @FunctionalInterface
  interface UpdateTaskProducer {

    /** Produce a new {@link UpdateTaskImpl}. */
    UpdateTaskImpl produce();
  }

  private UpdateTaskImpl cachedUpdateTaskImpl;
  private final Executor sequentialExecutor;

  /**
   * Constructor for a {@link UpdateTaskCache} that controls access using its own sequential
   * executor backed by the given base executor.
   *
   * @param baseExecutor Executor to back the sequential executor, including running any {@link
   *     UpdateTaskProducer} passed to {@link #getOrCreateUpdateTask}.
   */
  UpdateTaskCache(Executor baseExecutor) {
    sequentialExecutor = FirebaseExecutors.newSequentialExecutor(baseExecutor);
  }

  /**
   * Gets a cached {@link UpdateTask}, if there is an ongoing one, or else calls the given {@code
   * producer} to produce a new {@link UpdateTaskImpl} and caches the return value.
   *
   * <p>The producer's {@link UpdateTaskProducer#produce} method will be called on the sequential
   * executor, to prevent the task from being updated while it's producing the new one.
   */
  UpdateTask getOrCreateUpdateTask(UpdateTaskProducer producer) {
    UpdateTaskImpl impl = new UpdateTaskImpl();
    sequentialExecutor.execute(
        () -> {
          if (cachedUpdateTaskImpl == null || cachedUpdateTaskImpl.isComplete()) {
            cachedUpdateTaskImpl = producer.produce();
          }
          impl.shadow(cachedUpdateTaskImpl);
        });
    return impl;
  }

  /**
   * Sets the progress on the cached {@link UpdateTaskImpl}, if there is one and it is not
   * completed.
   *
   * <p>The task must be created using {@link #getOrCreateUpdateTask} before calling this method.
   *
   * @return A task that resolves when the change is applied, or fails with {@link
   *     IllegalStateException} if the task was not created yet or was already completed.
   */
  Task<Void> setProgress(UpdateProgress progress) {
    return executeIfOngoing(() -> cachedUpdateTaskImpl.updateProgress(progress));
  }

  /**
   * Sets the result on the cached {@link UpdateTaskImpl}, if there is one and it is not completed.
   *
   * <p>The task must be created using {@link #getOrCreateUpdateTask} before calling this method.
   *
   * @return A task that resolves when the change is applied, or fails with {@link
   *     IllegalStateException} if the task was not created yet or was already completed.
   */
  Task<Void> setResult() {
    return executeIfOngoing(() -> cachedUpdateTaskImpl.setResult());
  }

  /**
   * Sets the exception on the cached {@link UpdateTaskImpl}, if there is one and it is not
   * completed.
   *
   * <p>The task must be created using {@link #getOrCreateUpdateTask} before calling this method.
   *
   * @return A task that resolves when the change is applied, or fails with {@link
   *     IllegalStateException} if the task was not created yet or was already completed.
   */
  Task<Void> setException(FirebaseAppDistributionException e) {
    return executeIfOngoing(() -> cachedUpdateTaskImpl.setException(e));
  }

  private Task<Void> executeIfOngoing(Runnable r) {
    TaskCompletionSource<Void> taskCompletionSource = new TaskCompletionSource<>();
    sequentialExecutor.execute(
        () -> {
          if (cachedUpdateTaskImpl == null) {
            taskCompletionSource.setException(
                new IllegalStateException(
                    "Tried to set exception before calling getOrCreateUpdateTask()"));
          } else if (cachedUpdateTaskImpl.isComplete()) {
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
