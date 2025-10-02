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

import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.Continuation;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Crashlytics specific utilities for dealing with Tasks.
 *
 * @hide
 */
public final class CrashlyticsTasks {
  /** An Executor that runs on the calling thread. */
  private static final Executor DIRECT = Runnable::run;

  /**
   * Returns a task that is resolved when either of the given tasks is resolved.
   *
   * <p>If both tasks are cancelled, the returned task will be cancelled.
   */
  public static <T> Task<T> race(Task<T> task1, Task<T> task2) {
    CancellationTokenSource cancellation = new CancellationTokenSource();
    TaskCompletionSource<T> result = new TaskCompletionSource<>(cancellation.getToken());

    AtomicBoolean otherTaskCancelled = new AtomicBoolean(false);

    Continuation<T, Task<Void>> continuation =
        task -> {
          if (task.isSuccessful()) {
            // Task is complete and successful.
            result.trySetResult(task.getResult());
          } else if (task.getException() != null) {
            // Task is complete but unsuccessful.
            result.trySetException(task.getException());
          } else if (otherTaskCancelled.getAndSet(true)) {
            // Both tasks are cancelled.
            cancellation.cancel();
          }
          return Tasks.forResult(null);
        };

    task1.continueWithTask(DIRECT, continuation);
    task2.continueWithTask(DIRECT, continuation);

    return result.getTask();
  }

  private CrashlyticsTasks() {}
}
