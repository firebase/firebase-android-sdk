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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * An abstract channel for sending test results across threads.
 *
 * <p>This enables test code to run on the main thread and signal the test thread when to stop
 * blocking. Tests may send multiple errors that will then be thrown on the test thread. However, a
 * test may only send success once. After this is done, nothing else can be sent.
 */
public abstract class AbstractChannel<T> {

  private final CountDownLatch latch = new CountDownLatch(1);
  private final ReentrantLock lock = new ReentrantLock();

  private Exception error = null;
  private T value = null;

  /**
   * Sends a failure back to the testing thread.
   *
   * <p>This method will always send an exception to the testing thread. If an exception has already
   * been sent, this method will chain the new exception to the previous. If a successful value has
   * already been sent, this method will override it with the failure. Note, it is recommended to
   * send only one value through the channel. This method is safe to invoke from any thread.
   */
  protected void fail(Exception err) {
    // This is explicitly synchronized in case multiple threads are trying to send values.
    try {
      lock.lock();

      if (error == null) {
        error = err;
      } else {
        error.addSuppressed(err);
      }

      latch.countDown();
    } finally {
      lock.unlock();
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
    // This is explicitly synchronized in case multiple threads are trying to send values.
    try {
      lock.lock();

      if (latch.getCount() == 0) {
        // Only a single, successful value is supported. This throws the exception on both the
        // testing thread and the main thread.
        IllegalStateException error = new IllegalStateException("Result already completed");
        fail(error);
        throw error;
      }

      value = val;
      latch.countDown();
    } finally {
      lock.unlock();
    }
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
    boolean completed = latch.await(duration, unit);

    if (!completed) {
      String message = String.format("Test did not complete within %s %s", duration, unit);
      throw new AssertionError(message, error);
    }

    if (error != null) {
      throw new AssertionError("Test completed with errors", error);
    }

    return value;
  }
}
