/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.crashlytics.internal.concurrency;

import androidx.annotation.Discouraged;
import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.SuccessContinuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Worker for executing tasks sequentially on the given executor service.
 *
 * <p>Work on the queue may suspend, or it may return a Task, such that the underlying thread may be
 * re-used while the worker queue is suspended.
 *
 * <p>Work enqueued on this worker will be run serially, regardless of the underlying executor.
 * Therefore, workers on the queue should not add new work to the queue and then block on it, as
 * that would create a deadlock. In such a case, the worker can return a Task that depends on the
 * future work, and run the future work on the executor's thread, but not put it in the queue as its
 * own worker.
 *
 * @hide
 */
public class CrashlyticsWorker implements Executor {
  private final ExecutorService executor;

  private final Object tailLock = new Object();
  private Task<?> tail = Tasks.forResult(null);

  CrashlyticsWorker(ExecutorService executor) {
    this.executor = executor;
  }

  /** Returns the executor used by this worker. */
  public ExecutorService getExecutor() {
    return executor;
  }

  /**
   * Submits a <code>Callable</code> task for asynchronous execution on the executor.
   *
   * <p>A blocking callable will block an underlying thread.
   *
   * <p>Returns a <code>Task</code> which will be resolved upon successful completion of the
   * callable, or throws an <code>ExecutionException</code> if the callable throws an exception.
   */
  @CanIgnoreReturnValue
  public <T> Task<T> submit(Callable<T> callable) {
    synchronized (tailLock) {
      // Chain the new callable onto the queue's tail.
      Task<T> result = tail.continueWithTask(executor, task -> Tasks.forResult(callable.call()));
      tail = result;
      return result;
    }
  }

  /**
   * Submits a <code>Runnable</code> task for asynchronous execution on the executor.
   *
   * <p>A blocking runnable will block an underlying thread.
   *
   * <p>Returns a <code>Task</code> which will be resolved with null upon successful completion of
   * the runnable, or throws an <code>ExecutionException</code> if the runnable throws an exception.
   */
  @CanIgnoreReturnValue
  public Task<Void> submit(Runnable runnable) {
    synchronized (tailLock) {
      // Chain the new runnable onto the queue's tail.
      Task<Void> result =
          tail.continueWithTask(
              executor,
              task -> {
                runnable.run();
                return Tasks.forResult(null);
              });
      tail = result;
      return result;
    }
  }

  /**
   * Submits a <code>Callable Task</code> for asynchronous execution on the executor.
   *
   * <p>This is useful for making the worker suspend on an asynchronous operation, while letting the
   * underlying threads be re-used.
   *
   * <p>Returns a <code>Task</code> which will be resolved upon successful completion of the Task
   * returned by the callable, throws an <code>ExecutionException</code> if the callable throws an
   * exception, or throws a <code>CancellationException</code> if the task is cancelled.
   */
  @CanIgnoreReturnValue
  public <T> Task<T> submitTask(Callable<Task<T>> callable) {
    synchronized (tailLock) {
      // Chain the new callable task onto the queue's tail.
      Task<T> result = tail.continueWithTask(executor, task -> callable.call());
      tail = result;
      return result;
    }
  }

  /**
   * Submits a <code>Callable Task</code> followed by a <code>Continuation</code> for asynchronous
   * execution on the executor.
   *
   * <p>This is useful for submitting a task that must be immediately followed by another task,
   * regardless of more tasks being submitted in parallel. For example, settings.
   *
   * <p>Returns a <code>Task</code> which will be resolved upon successful completion of the Task
   * returned by the callable and continued by the continuation, throws an <code>ExecutionException
   * </code> if either task throws an exception, or throws a <code>CancellationException</code> if
   * either task is cancelled.
   */
  @CanIgnoreReturnValue
  public <T, R> Task<R> submitTask(
      Callable<Task<T>> callable, Continuation<T, Task<R>> continuation) {
    synchronized (tailLock) {
      // Chain the new callable task and continuation onto the queue's tail.
      Task<R> result =
          tail.continueWithTask(executor, task -> callable.call())
              .continueWithTask(executor, continuation);
      tail = result;
      return result;
    }
  }

  /**
   * Submits a <code>Callable Task</code> followed by a <code>SuccessContinuation</code> for
   * asynchronous execution on the executor.
   *
   * <p>This is useful for submitting a task that must be immediately followed by another task, only
   * if it was successful, but regardless of more tasks being submitted in parallel.
   *
   * <p>Returns a <code>Task</code> which will be resolved upon successful completion of the Task
   * returned by the callable and continued by the continuation, throws an <code>ExecutionException
   * </code> if either task throws an exception, or throws a <code>CancellationException</code> if
   * the task is cancelled.
   */
  @CanIgnoreReturnValue
  public <T, R> Task<R> submitTaskOnSuccess(
      Callable<Task<T>> callable, SuccessContinuation<T, R> successContinuation) {
    synchronized (tailLock) {
      // Chain the new callable task and success continuation onto the queue's tail.
      Task<R> result =
          tail.continueWithTask(executor, task -> callable.call())
              .continueWithTask(
                  executor,
                  task -> {
                    if (task.isSuccessful()) {
                      return successContinuation.then(task.getResult());
                    } else if (task.getException() != null) {
                      return Tasks.forException(task.getException());
                    } else {
                      return Tasks.forCanceled();
                    }
                  });
      tail = result;
      return result;
    }
  }

  /**
   * Forwards a <code>Runnable</code> to the underlying executor.
   *
   * <p>This is useful for passing the worker as the executor to task continuations.
   *
   * <p>This is different than {@link #submit(Runnable)}. This will not submit the runnable to the
   * worker to execute in order, this will forward the runnable to the underlying executor. If you
   * are calling this directly from your code, you probably want {@link #submit(Runnable)}.
   */
  @Override
  @Discouraged(message = "This is probably not that you want. Use {@link #submit(Runnable)}.")
  public void execute(Runnable runnable) {
    executor.execute(runnable);
  }

  /**
   * Blocks until all current pending tasks have completed, up to 30 seconds. Only for testing.
   *
   * <p>This is not a shutdown, this does not stop new tasks from being submitted to the queue.
   */
  @VisibleForTesting
  public void await() throws ExecutionException, InterruptedException, TimeoutException {
    // Submit an empty runnable, and await on it for 30 sec so deadlocked tests fail faster.
    Tasks.await(submit(() -> {}), 30, TimeUnit.SECONDS);

    // Sleep for a bit here, instead of de-flaking individual test cases.
    Thread.sleep(1);
  }
}
