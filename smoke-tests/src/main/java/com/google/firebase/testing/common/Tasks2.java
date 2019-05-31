// Copyright 2018 Google LLC
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

package com.google.firebase.testing.common;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.TimeUnit;

/** Test utilities for asynchronous tasks. */
public final class Tasks2 {

  private static final long BEST_EFFORT_DURATION = 10;
  private static final long WAIT_DURATION = 40;
  private static final TimeUnit WAIT_UNIT = TimeUnit.SECONDS;

  private Tasks2() {}

  /**
   * Waits for the task to complete.
   *
   * <p>The primary use case for this method is to perform test clean-up in a {@code finally} block.
   * Clean-up is inherently a best-effort task, because it might not succeed when the test is
   * broken.
   *
   * <p>This method will block the current thread for a short period of time. Unlike the other
   * methods in this class, this method is not biased towards success or failure. This method does
   * not throw any exceptions.
   */
  public static void waitBestEffort(Task<?> task) {
    try {
      Tasks.await(task, BEST_EFFORT_DURATION, WAIT_UNIT);
    } catch (InterruptedException ex) {
      Thread.currentThread().interrupt();
    } catch (ExecutionException | TimeoutException ex) {
      // Ignore.
    }
  }

  /**
   * Waits for the task to complete with a failure.
   *
   * <p>This method will block the current thread and return the resulting exception. An assertion
   * failure will be thrown if the task does not fail.
   */
  public static Throwable waitForFailure(Task<?> task) throws Exception {
    try {
      Tasks.await(task, WAIT_DURATION, WAIT_UNIT);
    } catch (ExecutionException ex) {
      return ex.getCause();
    }

    throw new AssertionError("Task did not fail");
  }

  /**
   * Waits for the task to complete successfully.
   *
   * <p>This method will block the current thread and return the result of the task. It will rethrow
   * any expection thrown by the task without wrapping.
   */
  public static <T> T waitForSuccess(Task<T> task) throws Exception {
    try {
      return Tasks.await(task, WAIT_DURATION, WAIT_UNIT);
    } catch (ExecutionException ex) {
      Throwable t = ex.getCause();
      if (t instanceof Exception) {
        throw (Exception) t;
      } else if (t instanceof Error) {
        throw (Error) t;
      }

      throw new IllegalStateException("Task threw unexpected Throwable", t);
    }
  }
}
