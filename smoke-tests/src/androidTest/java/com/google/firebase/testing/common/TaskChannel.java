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

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;

/**
 * A channel for sending test results across threads.
 *
 * <p>This channel adds specializations for {@link Task}. The methods in this class are intended to
 * be executed from the main thread, but they are safe to execute from any thread.
 */
public class TaskChannel<T> extends AbstractChannel<T> {

  /**
   * Sends the outcome of a task over the channel.
   *
   * <p>This is a terminal operation. As long as the task completes, some value will be sent over
   * the channel. As such, the task must produce an instance of {@code T} on success. This value or
   * the exception will be sent to the testing thread.
   *
   * <p>Invoking this method multiple times in a single test case is usually an error (unless there
   * are multiple channels).
   */
  public void sendOutcome(Task<? extends T> task) {
    task.addOnCompleteListener(
        t -> {
          if (t.isSuccessful()) {
            succeed(t.getResult());
          } else {
            fail(t.getException());
          }
        });
  }

  /**
   * Returns a fluent API for trapping task failures.
   *
   * <p>Asynchronous tasks may fail, and tests may need to execute multiple asynchronous tasks in
   * series to produce a result. This API automatically captures any failures and sends them to the
   * testing thread.
   *
   * <p>Note, this method does nothing itself. You must invoke a method on the returned {@link
   * FailureTrap} to do anything meaningful.
   */
  public <R> FailureTrap<R> trapFailure(Task<R> task) {
    return new FailureTrap<>(task);
  }

  /** A fluent API for trapping task failures. */
  public final class FailureTrap<R> {
    private final Task<R> task;

    FailureTrap(Task<R> task) {
      this.task = task;
    }

    /**
     * Traps the task's failure or ignores its result.
     *
     * <p>This method ignores any successful result from the task. Generally, this is useful only if
     * no other logic needs to be executed in series. If the task fails, the exception will be
     * trapped and sent to the testing thread.
     */
    public void andIgnoreResult() {
      task.addOnFailureListener(error -> fail(error));
    }

    /**
     * Traps the task's failure or passes the successful result to a listener.
     *
     * <p>If the task succeeds, this method will pass the result to the supplied listener.
     * Otherwise, the exception will be trapped and sent to the testing thread. The listener is
     * usually supplied as a lambda. Any exceptions raised by the listener will be caught and sent
     * to the testing thread.
     */
    public void andThen(OnSuccessListener<? super R> listener) {
      task.addOnCompleteListener(
          t -> {
            if (t.isSuccessful()) {
              try {
                listener.onSuccess(t.getResult());
              } catch (RuntimeException ex) {
                fail(ex);
              }
            } else {
              fail(t.getException());
            }
          });
    }
  }
}
