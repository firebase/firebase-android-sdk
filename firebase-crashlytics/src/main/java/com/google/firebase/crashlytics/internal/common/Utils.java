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

import android.annotation.SuppressLint;
import android.os.Looper;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/** Utils */
@SuppressWarnings({"UnusedReturnValue"})
public final class Utils {
  private static final int TIMEOUT_MILLIS = 4_000;
  private static final int MAIN_HANDLER_TIMEOUT_MILLIS = 2_750;

  /**
   * @return A tasks that is resolved when either of the given tasks is resolved.
   */
  // TODO(b/261014167): Use an explicit executor in continuations.
  @SuppressLint("TaskMainThread")
  public static <T> Task<T> race(Task<T> t1, Task<T> t2) {
    final TaskCompletionSource<T> result = new TaskCompletionSource<>();
    Continuation<T, Void> continuation =
        task -> {
          if (task.isSuccessful()) {
            result.trySetResult(task.getResult());
          } else if (task.getException() != null) {
            result.trySetException(task.getException());
          }
          return null;
        };
    t1.continueWith(continuation);
    t2.continueWith(continuation);
    return result.getTask();
  }

  /**
   * @return A tasks that is resolved when either of the given tasks is resolved.
   */
  public static <T> Task<T> race(Executor executor, Task<T> t1, Task<T> t2) {
    final TaskCompletionSource<T> result = new TaskCompletionSource<>();
    Continuation<T, Void> continuation =
        task -> {
          if (task.isSuccessful()) {
            result.trySetResult(task.getResult());
          } else if (task.getException() != null) {
            result.trySetException(task.getException());
          }
          return null;
        };
    t1.continueWith(executor, continuation);
    t2.continueWith(executor, continuation);
    return result.getTask();
  }

  /** Similar to Tasks.call, but takes a Callable that returns a Task. */
  public static <T> Task<T> callTask(Executor executor, Callable<Task<T>> callable) {
    final TaskCompletionSource<T> result = new TaskCompletionSource<>();
    executor.execute(
        () -> {
          try {
            callable
                .call()
                .continueWith(
                    executor,
                    task -> {
                      if (task.isSuccessful()) {
                        result.setResult(task.getResult());
                      } else if (task.getException() != null) {
                        result.setException(task.getException());
                      }
                      return null;
                    });
          } catch (Exception e) {
            result.setException(e);
          }
        });
    return result.getTask();
  }

  /**
   * Blocks until the given Task completes, and then returns the value the Task was resolved with,
   * if successful. If the Task fails, an exception will be thrown, wrapping the Exception of the
   * Task. Blocking on Tasks is generally a bad idea, and you definitely should not block the main
   * thread waiting on one. But there are a couple of weird spots in our SDK where we really have no
   * choice. You should not use this method for any new code. And if you really do have to use it,
   * you should feel slightly bad about it.
   *
   * @param task the task to block on
   * @return the Task's result
   * @throws ExecutionException if the Task fails. {@code getCause} will return the original
   *     exception.
   * @throws InterruptedException if an interrupt occurs while waiting for the Task to complete
   * @throws TimeoutException if the specified timeout is reached before the Task completes
   */
  public static <T> T awaitEvenIfOnMainThread(Task<T> task)
      throws ExecutionException, InterruptedException, TimeoutException {
    if (Looper.getMainLooper() == Looper.myLooper()) {
      return Tasks.await(task, MAIN_HANDLER_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    } else {
      return Tasks.await(task, TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
    }
  }

  /** Invokes latch.await(timeout, unit) uninterruptibly. */
  @CanIgnoreReturnValue
  public static boolean awaitUninterruptibly(CountDownLatch latch, long timeout, TimeUnit unit) {
    boolean interrupted = false;
    try {
      long remainingNanos = unit.toNanos(timeout);
      long end = System.nanoTime() + remainingNanos;

      while (true) {
        try {
          // CountDownLatch treats negative timeouts just like zero.
          return latch.await(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (InterruptedException e) {
          interrupted = true;
          remainingNanos = end - System.nanoTime();
        }
      }
    } finally {
      if (interrupted) {
        Thread.currentThread().interrupt();
      }
    }
  }

  private Utils() {}
}
