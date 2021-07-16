// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.testing.fireperf;

import android.util.Log;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;

/** A helper class that generates all of the traces and network requests. */
public class FireperfUtils {

  private static final String LOG_TAG = FireperfUtils.class.getSimpleName();
  private static final int MILLIS_IN_SECONDS = 1000;
  private static final int MAX_SEMAPHORE_PERMITS = 2;
  private static final Semaphore SEMAPHORE = new Semaphore(MAX_SEMAPHORE_PERMITS);

  /** Creates all the traces and network requests. */
  static void startEvents(final int iterations) {
    if (SEMAPHORE.availablePermits() < MAX_SEMAPHORE_PERMITS) {
      return;
    }

    final TraceGenerator traceGenerator = new TraceGenerator(SEMAPHORE);
    final NetworkRequestGenerator networkRequestGenerator = new NetworkRequestGenerator(SEMAPHORE);

    @SuppressWarnings("FutureReturnValueIgnored")
    Future<?> unusedFuture =
        Executors.newSingleThreadExecutor()
            .submit(
                () -> {
                  try {
                    traceGenerator.launchTraces(/* totalTraces= */ 32, iterations).get();
                    networkRequestGenerator
                        .launchRequests(/* totalRequests= */ 32, iterations)
                        .get();
                  } catch (Exception e) {
                    Log.e(LOG_TAG, e.getMessage(), e);
                  }
                });
  }

  static void startTraces(int iterations) {
    if (SEMAPHORE.availablePermits() < 1) {
      return;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    Future<?> unusedTraceFuture =
        new TraceGenerator(SEMAPHORE).launchTraces(/* totalTraces= */ 32, iterations);
  }

  static void startNetworkRequests(int iterations) {
    if (SEMAPHORE.availablePermits() < 1) {
      return;
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    Future<?> unusedNetworkFuture =
        new NetworkRequestGenerator(SEMAPHORE).launchRequests(/* totalRequests= */ 32, iterations);
  }

  /**
   * Converts seconds to milliseconds, rounding to the nearest whole number.
   *
   * @param duration The time to be converted.
   * @return time in integer milliseconds form.
   */
  static int normalizeTime(float duration) {
    float newDuration = duration * MILLIS_IN_SECONDS;
    return Math.round(newDuration);
  }

  /**
   * Generates a random value following Gaussian distribution based on the Box-Mueller transform
   * defined by this reference: https://en.wikipedia.org/wiki/Box%E2%80%93Muller_transform with U1
   * being the mean and U2 being the standard deviation.
   *
   * @param mean Mean value to be used as U1.
   * @param deviation Standard deviation to be used as U2.
   */
  static float randomGaussianValueWithMean(float mean, float deviation) {
    float randomValue1 = (float) Math.random();
    float randomValue2 = (float) Math.random();
    float gaussianValue =
        (float) (Math.sqrt(-2 * Math.log(randomValue1)) * Math.cos(2 * Math.PI * randomValue2));

    return mean + (deviation * gaussianValue);
  }

  /**
   * Tries to acquire all allocated semaphore permits, and blocks the thread until they are all
   * available.
   */
  public static void blockUntilAllEventsSent() throws InterruptedException {
    SEMAPHORE.acquire(MAX_SEMAPHORE_PERMITS);
    SEMAPHORE.release(MAX_SEMAPHORE_PERMITS);
  }
}
