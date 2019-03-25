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

import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * An abstract channel for sending test results across threads.
 *
 * <p>This enables test code to run on the main thread and signal the test thread when to stop
 * blocking. Tests may send multiple errors that will then be thrown on the test thread. However, a
 * test may only send success once. After this is done, nothing else can be sent.
 */
public abstract class AbstractChannel<T> {

  private final TaskCompletionSource<T> implementation = new TaskCompletionSource<>();

  /** Runs the test target on the main thread, trapping any exception into the channel. */
  protected static <U extends AbstractChannel<R>, R> U runTarget(Target<U> target, U channel) {
    MainThread.run(
        () -> {
          try {
            target.run(channel);
          } catch (Exception ex) {
            channel.fail(ex);
          }
        });

    return channel;
  }

  /**
   * Sends a failure back to the testing thread.
   *
   * <p>This method will always send an exception to the testing thread. If an exception has already
   * been sent, this method will chain the new exception to the previous. If a successful value has
   * already been sent, this method will override it with the failure. Note, it is recommended to
   * send only one value through the channel. This method is safe to invoke from any thread.
   */
  protected void fail(Exception err) {
    boolean isSet = implementation.trySetException(err);

    if (!isSet) {
      implementation.getTask().getException().addSuppressed(err);
    }
  }

  /**
   * Sends a successful value back to the testing thread.
   *
   * <p>This method will only send the value if no value has been sent. It is an error to invoke
   * this method multiple times or after invoking {@link #fail}. This method is safe to invoke from
   * any thread.
   */
  protected void succeed(T val) {
    implementation.setResult(val);
  }

  /** Waits 30 seconds to receive the successful value. */
  public T waitForSuccess() throws InterruptedException {
    return waitForSuccess(30, TimeUnit.SECONDS);
  }

  /**
   * Waits for up to the request time for the sending thread to send a successful value.
   *
   * <p>If the sender does not send success within the specified time, this method throws an {@link
   * AssertionError} and chains any received errors to it. This method is safe to invoke from any
   * thread.
   */
  public T waitForSuccess(long duration, TimeUnit unit) throws InterruptedException {
    try {
      return Tasks.await(implementation.getTask(), duration, unit);
    } catch (ExecutionException ex) {
      throw new AssertionError("Test completed with errors", ex.getCause());
    } catch (TimeoutException ex) {
      String message = String.format("Test did not complete within %s %s", duration, unit);
      throw new AssertionError(message);
    }
  }
}
