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

import androidx.annotation.VisibleForTesting;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Worker for executing tasks sequentially on the given executor service.
 *
 * <p>Work on the queue may block, or it may return a Task, such that the underlying thread may be
 * re-used while the worker queue is still blocked.
 *
 * <p>Work enqueued on this worker will be run serially, regardless of the underlying executor.
 * Therefore, workers on the queue should not add new work to the queue and then block on it, as
 * that would create a deadlock. In such a case, the worker can return a Task that depends on the
 * future work, and run the future work on the executor's thread, but not put it in the queue as its
 * own worker.
 *
 * @hide
 */
public class CrashlyticsWorker {
  private final ExecutorService executor;

  private final Object tailLock = new Object();
  private Task<?> tail = Tasks.forResult(null);

  public CrashlyticsWorker(ExecutorService executor) {
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
      // Do not propagate a cancellation.
      if (tail.isCanceled()) {
        tail = tail.continueWithTask(executor, task -> Tasks.forResult(null));
      }
      // Chain the new callable onto the queue's tail.
      Task<T> result = tail.continueWith(executor, task -> callable.call());
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
      // Do not propagate a cancellation.
      if (tail.isCanceled()) {
        tail = tail.continueWithTask(executor, task -> Tasks.forResult(null));
      }
      // Chain the new runnable onto the queue's tail.
      Task<Void> result =
          tail.continueWith(
              executor,
              task -> {
                runnable.run();
                return null;
              });
      tail = result;
      return result;
    }
  }

  /**
   * Submits a <code>Callable</code> <code>Task</code> for asynchronous execution on the executor.
   *
   * <p>This is useful for making the worker block on an asynchronous operation, while letting the
   * underlying threads be re-used.
   *
   * <p>Returns a <code>Task</code> which will be resolved upon successful completion of the Task
   * returned by the callable, throws an <code>ExecutionException</code> if the callable throws an
   * exception, or throws a <code>CancellationException</code> if the task is cancelled.
   */
  @CanIgnoreReturnValue
  public <T> Task<T> submitTask(Callable<Task<T>> callable) {
    synchronized (tailLock) {
      // Chain the new callable task onto the queue's tail, regardless of cancellation.
      Task<T> result = tail.continueWithTask(executor, task -> callable.call());
      tail = result;
      return result;
    }
  }

  /**
   * Submits a <code>Callable</code> <code>Task</code> followed by a <code>Continuation</code> for
   * asynchronous execution on the executor.
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
   * Blocks until all current pending tasks have completed, up to 30 seconds. Useful for testing.
   *
   * <p>This is not a shutdown, this does not stop new tasks from being submitted to the queue.
   */
  @VisibleForTesting
  public void await() throws ExecutionException, InterruptedException, TimeoutException {
    // Submit an empty runnable, and await on it for 30 sec so deadlocked tests fail faster.
    Tasks.await(submit(() -> {}), 30, TimeUnit.SECONDS);
  }
}
