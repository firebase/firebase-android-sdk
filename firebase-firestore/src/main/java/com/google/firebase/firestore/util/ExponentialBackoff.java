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

package com.google.firebase.firestore.util;

import com.google.firebase.firestore.util.AsyncQueue.DelayedTask;
import com.google.firebase.firestore.util.AsyncQueue.TimerId;
import java.util.Date;

/** Helper to implement exponential backoff. */
public class ExponentialBackoff {

  /**
   * Initial backoff time in milliseconds after an error. Set to 1s according to
   * https://cloud.google.com/apis/design/errors.
   */
  public static final long DEFAULT_BACKOFF_INITIAL_DELAY_MS = 1000;

  public static final double DEFAULT_BACKOFF_FACTOR = 1.5;

  public static final long DEFAULT_BACKOFF_MAX_DELAY_MS = 60 * 1000;

  private final AsyncQueue queue;
  private final TimerId timerId;
  private final long initialDelayMs;
  private final double backoffFactor;

  /** The maximum backoff time in milliseconds. */
  private final long maxDelayMs;

  /**
   * The maximum backoff time used when calculating the next backoff. This value can be changed for
   * a single backoffAndRun call, after which it resets to maxDelayMs.
   */
  private long nextMaxDelayMs;

  private long currentBaseMs;
  private long lastAttemptTime;
  private DelayedTask timerTask;

  /**
   * Creates and returns a helper for running delayed tasks following an exponential backoff curve
   * between attempts.
   *
   * <p>Each delay is made up of a "base" delay which follows the exponential backoff curve, and a
   * +/- 50% "jitter" that is calculated and added to the base delay. This prevents clients from
   * accidentally synchronizing their delays causing spikes of load to the backend.
   *
   * @param queue The async queue to run tasks on.
   * @param timerId A TimerId to use when queuing backoff tasks in the AsyncQueue.
   * @param initialDelayMs The initial delay (used as the base delay on the first retry attempt).
   *     Note that jitter will still be applied, so the actual delay could be as little as
   *     0.5*initialDelayMs.
   * @param backoffFactor The multiplier to use to determine the extended base delay after each
   *     attempt.
   * @param maxDelayMs The maximum base delay after which no further backoff is performed. Note that
   *     jitter will still be applied, so the actual delay could be as much as 1.5*maxDelayMs.
   */
  public ExponentialBackoff(
      AsyncQueue queue,
      AsyncQueue.TimerId timerId,
      long initialDelayMs,
      double backoffFactor,
      long maxDelayMs) {
    this.queue = queue;
    this.timerId = timerId;
    this.initialDelayMs = initialDelayMs;
    this.backoffFactor = backoffFactor;
    this.maxDelayMs = maxDelayMs;
    this.nextMaxDelayMs = maxDelayMs;
    this.lastAttemptTime = new Date().getTime();

    reset();
  }

  public ExponentialBackoff(AsyncQueue queue, AsyncQueue.TimerId timerId) {
    this(
        queue,
        timerId,
        DEFAULT_BACKOFF_INITIAL_DELAY_MS,
        DEFAULT_BACKOFF_FACTOR,
        DEFAULT_BACKOFF_MAX_DELAY_MS);
  }

  /**
   * Resets the backoff delay.
   *
   * <p>The very next backoffAndRun() will have no delay. If it is called again (i.e. due to an
   * error), initialDelayMs (plus jitter) will be used, and subsequent ones will increase according
   * to the backoffFactor.
   */
  public void reset() {
    currentBaseMs = 0;
  }

  /**
   * Resets the backoff delay to the maximum delay (e.g. for use after a RESOURCE_EXHAUSTED error).
   */
  public void resetToMax() {
    currentBaseMs = nextMaxDelayMs;
  }

  /**
   * Set the backoff's maximum delay for only the next call to backoffAndRun, after which the delay
   * will be reset to maxDelayMs.
   *
   * @param newMax The temporary maximum delay to set.
   */
  public void setTemporaryMaxDelay(long newMax) {
    nextMaxDelayMs = newMax;
  }

  /**
   * Waits for currentDelayMs, increases the delay and runs the specified task. If there was a
   * pending backoff task waiting to run already, it will be canceled.
   *
   * @param task The task to run.
   */
  public void backoffAndRun(Runnable task) {
    // Cancel any pending backoff operation.
    cancel();

    // First schedule using the current base (which may be 0 and should be honored as such).
    long desiredDelayWithJitterMs = currentBaseMs + jitterDelayMs();

    // Guard against lastAttemptTime being in the future due to a clock change.
    long delaySoFarMs = Math.max(0, new Date().getTime() - lastAttemptTime);

    // Guard against the backoff delay already being past.
    long remainingDelayMs = Math.max(0, desiredDelayWithJitterMs - delaySoFarMs);

    if (currentBaseMs > 0) {
      Logger.debug(
          getClass().getSimpleName(),
          "Backing off for %d ms ("
              + "base delay: %d ms, "
              + "delay with jitter: %d ms, "
              + "last attempt: %d ms ago)",
          remainingDelayMs,
          currentBaseMs,
          desiredDelayWithJitterMs,
          delaySoFarMs);
    }

    timerTask =
        queue.enqueueAfterDelay(
            this.timerId,
            remainingDelayMs,
            () -> {
              lastAttemptTime = new Date().getTime();
              task.run();
            });

    // Apply backoff factor to determine next delay and ensure it is within bounds.
    currentBaseMs = (long) (currentBaseMs * backoffFactor);
    if (currentBaseMs < initialDelayMs) {
      currentBaseMs = initialDelayMs;
    } else if (currentBaseMs > nextMaxDelayMs) {
      currentBaseMs = nextMaxDelayMs;
    }

    // Reset max delay to the default.
    nextMaxDelayMs = maxDelayMs;
  }

  public void cancel() {
    if (timerTask != null) {
      timerTask.cancel();
      timerTask = null;
    }
  }

  /** Returns a random value in the range [-currentBaseMs/2, currentBaseMs/2] */
  private long jitterDelayMs() {
    return (long) ((Math.random() - 0.5) * currentBaseMs);
  }
}
