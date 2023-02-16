// Copyright 2021 Google LLC
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

import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.UpdateTask;
import com.google.firebase.concurrent.FirebaseExecutors;
import java.util.concurrent.Executor;

class TaskUtils {
  private static final String TAG = "TaskUtils";

  /**
   * A functional interface to wrap a function that returns some result of a possibly long-running
   * operation, and could potentially throw a {@link FirebaseAppDistributionException}.
   */
  interface Operation<TResult> {
    TResult run() throws FirebaseAppDistributionException;
  }

  /**
   * A function that is called to continue execution when a {@link Task} succeeds, and returns an
   * {@link UpdateTask}.
   */
  interface UpdateTaskContinuation<TResult> {
    UpdateTask then(TResult result) throws FirebaseAppDistributionException;
  }

  /**
   * Runs a long running operation inside a {@link Task}, wrapping any errors in {@link
   * FirebaseAppDistributionException}.
   *
   * <p>This allows long running operations to be chained together using {@link Task#onSuccessTask}.
   * If the operation throws an exception, the task will fail, and the exception will be surfaced to
   * the caller as {@code FirebaseAppDistributionException}, available via {@link
   * Task#getException}.
   *
   * <p>Exceptions that are not {@code FirebaseAppDistributionException} will be wrapped in one,
   * with {@link Status#UNKNOWN}.
   *
   * @param executor the executor in which to run the long running operation
   * @param operation the long running operation
   * @param <TResult> the type of the value returned by the operation
   * @return the task encompassing the long running operation
   */
  static <TResult> Task<TResult> runAsyncInTask(Executor executor, Operation<TResult> operation) {
    TaskCompletionSource<TResult> taskCompletionSource = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          try {
            taskCompletionSource.setResult(operation.run());
          } catch (Throwable t) {
            taskCompletionSource.setException(FirebaseAppDistributionExceptions.wrap(t));
          }
        });
    return taskCompletionSource.getTask();
  }

  /**
   * Handle a {@link Task} that may fail with unexpected exceptions, wrapping them in {@link
   * FirebaseAppDistributionException}.
   *
   * <p>Chain this off of a task that may fail with unexpected exceptions, using {@link
   * Task#continueWithTask}. If the task fails, the task returned by this method will also fail,
   * with a {@code FirebaseAppDistributionException} of {@link Status#UNKNOWN} that wraps the
   * original exception.
   *
   * @param task the task that might fail with an unexpected exception
   * @param <TResult> the type of the value returned by the task
   * @return the new task that will fail with {@link FirebaseAppDistributionException}
   */
  static <TResult> Task<TResult> handleTaskFailure(Task<TResult> task) {
    if (task.isComplete() && !task.isSuccessful()) {
      Exception e = task.getException();
      LogWrapper.e(TAG, "Task failed to complete", e);
      return e instanceof FirebaseAppDistributionException
          ? task
          : Tasks.forException(FirebaseAppDistributionExceptions.wrap(e));
    }
    return task;
  }

  static void safeSetTaskException(TaskCompletionSource taskCompletionSource, Exception e) {
    if (taskCompletionSource != null && !taskCompletionSource.getTask().isComplete()) {
      taskCompletionSource.setException(e);
    }
  }

  static void safeSetTaskException(UpdateTaskImpl task, Exception e) {
    if (task != null && !task.isComplete()) {
      task.setException(e);
    }
  }

  static <TResult> void safeSetTaskResult(
      TaskCompletionSource taskCompletionSource, TResult result) {
    if (taskCompletionSource != null && !taskCompletionSource.getTask().isComplete()) {
      taskCompletionSource.setResult(result);
    }
  }

  static void safeSetTaskResult(UpdateTaskImpl task) {
    if (task != null && !task.isComplete()) {
      task.setResult();
    }
  }

  /**
   * Returns an {@link UpdateTask} that will be completed with the result of applying the specified
   * {@link UpdateTaskContinuation} to the given {@link Task} when the task completes successfully.
   *
   * <p>This is equivalent to {@link Task#onSuccessTask(Executor, SuccessContinuation)} but for a
   * continuation that returns an {@link UpdateTask}.
   */
  static <T> UpdateTask onSuccessUpdateTask(
      Task<T> task, Executor executor, UpdateTaskContinuation<T> continuation) {
    UpdateTaskImpl updateTask = new UpdateTaskImpl();
    task.addOnSuccessListener(
            executor,
            result -> {
              try {
                updateTask.shadow(continuation.then(result));
              } catch (Throwable t) {
                updateTask.setException(FirebaseAppDistributionExceptions.wrap(t));
              }
            })
        .addOnFailureListener(executor, updateTask::setException);
    return updateTask;
  }

  /** Set a {@link TaskCompletionSource} to be resolved with the result of another {@link Task}. */
  static <T> void shadowTask(TaskCompletionSource<T> taskCompletionSource, Task<T> task) {
    // Using direct executor here ensures that any handlers that were themselves added using a
    // direct executor will behave as expected: they'll be executed on the thread that sets the
    // result.
    task.addOnSuccessListener(FirebaseExecutors.directExecutor(), taskCompletionSource::setResult)
        .addOnFailureListener(
            FirebaseExecutors.directExecutor(), taskCompletionSource::setException);
  }

  private TaskUtils() {}
}
