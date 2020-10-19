// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.common;

import androidx.annotation.NonNull;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.Callable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;

/**
 * Helper for executing tasks on the Crashlytics background executor service.
 *
 * <p>Work on the queue may block, or it may return a Task, such that the underlying thread may be
 * re-used while the worker queue is still blocked.
 *
 * <p>Work enqueued on this worker will be run serially, regardless of the underlying executor.
 * Therefore, workers on the queue should not add new work to the queue and then block on it, as
 * that would create a deadlock. In such a case, the worker can return a Task that depends on the
 * future work, and run the future work on the executor's thread, but not put it in the queue as its
 * own worker.
 */
class CrashlyticsBackgroundWorker {
  private final ExecutorService executorService;

  private Task<Void> tail = Tasks.forResult(null);

  private final Object tailLock = new Object();

  // A thread local to keep track of which thread belongs to this executor.
  private ThreadLocal<Boolean> isExecutorThread = new ThreadLocal<>();

  public CrashlyticsBackgroundWorker(ExecutorService executorService) {
    this.executorService = executorService;
    // Queue up the first job as one that marks the thread so we can check it later.
    @SuppressWarnings("FutureReturnValueIgnored")
    Future<?> submit =
        executorService.submit(
            new Runnable() {
              @Override
              public void run() {
                isExecutorThread.set(true);
              }
            });
  }

  /** Returns the executor used by this background worker. */
  public Executor getExecutor() {
    return executorService;
  }

  /** Returns true if called on the thread owned by this background worker. */
  private boolean isRunningOnThread() {
    return Boolean.TRUE.equals(isExecutorThread.get());
  }

  /**
   * Throws an exception if called from any thread other than the background worker's. This helps
   * guarantee code is being called on the intended thread.
   */
  public void checkRunningOnThread() {
    if (!isRunningOnThread()) {
      throw new IllegalStateException("Not running on background worker thread as intended.");
    }
  }

  /**
   * Submit a <code>Runnable</code> task for asynchronous execution on the Crashlytics background
   * executor service.
   *
   * <p>If the runnable throws an exception, the task will be rejected with it.
   *
   * @return a <code>Task</code> which will be resolved with null upon successful completion of the
   *     runnable, or <code>null</code> if the runnable is rejected from the background executor
   *     service.
   */
  Task<Void> submit(final Runnable runnable) {
    return submit(
        new Callable<Void>() {
          @Override
          public Void call() throws Exception {
            runnable.run();
            return null;
          }
        });
  }

  /** Convenience method that creates a new Continuation that wraps the given callable. */
  private <T> Continuation<Void, T> newContinuation(Callable<T> callable) {
    return new Continuation<Void, T>() {
      @Override
      public T then(@NonNull Task<Void> task) throws Exception {
        // We can ignore the task passed in, which is just the queue's tail.
        return callable.call();
      }
    };
  }

  /** Convenience method that tasks a Task<T> and convert it to a Task<Void>. */
  private <T> Task<Void> ignoreResult(Task<T> task) {
    return task.continueWith(
        executorService,
        new Continuation<T, Void>() {
          @Override
          public Void then(@NonNull Task<T> task) throws Exception {
            // Ignore whether the task succeeded or failed.
            return null;
          }
        });
  }

  /**
   * Submit a <code>Callable</code> task for asynchronous execution on the Crashlytics background
   * executor service.
   *
   * <p>If the callable throws an exception, the task will be rejected with it.
   *
   * @return a <code>Task</code> which will be resolved upon successful completion of the callable.
   */
  public <T> Task<T> submit(final Callable<T> callable) {
    synchronized (tailLock) {
      // Chain the new callable onto the queue's tail.
      Task<T> toReturn = tail.continueWith(executorService, newContinuation(callable));

      // Add a new tail that swallows errors from the callable when it finishes.
      tail = ignoreResult(toReturn);
      return toReturn;
    }
  }

  /**
   * Submit a <code>Callable</code> task for asynchronous execution on the Crashlytics background
   * executor service. This method is useful for making the worker block on an asynchronous
   * operation, while letting the underlying thread be re-used.
   *
   * <p>If the callable throws an exception, the task will be rejected with it.
   *
   * @return a <code>Task</code> which will be resolved upon successful completion of the Task
   *     returns by the callable.
   */
  public <T> Task<T> submitTask(final Callable<Task<T>> callable) {
    synchronized (tailLock) {
      // Chain the new callable onto the queue's tail.
      Task<T> toReturn = tail.continueWithTask(executorService, newContinuation(callable));

      // Add a new tail that swallows errors from the callable when it finishes.
      tail = ignoreResult(toReturn);
      return toReturn;
    }
  }
}
