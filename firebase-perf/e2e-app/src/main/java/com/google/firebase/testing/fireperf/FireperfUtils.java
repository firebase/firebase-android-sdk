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

import com.google.android.datatransport.Priority;
import com.google.android.datatransport.cct.CCTDestination;
import com.google.android.datatransport.runtime.TransportRuntimeTesting;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Future;

/** A helper class that generates all of the traces and network requests. */
public class FireperfUtils {
  private static final int MILLIS_IN_SECONDS = 1000;
  private static final int TRACES_PER_ITERATION = 32;
  private static final int REQUESTS_PER_ITERATION = 32;

  /** Creates all the traces and network requests. */
  static List<Future<?>> generateEvents(final int iterations) {
    List<Future<?>> futures = new ArrayList<>();
    futures.add(generateTraces(iterations));
    futures.add(generateNetworkRequests(iterations));
    return futures;
  }

  static Future<?> generateTraces(int iterations) {
    return new TraceGenerator().generateTraces(/* totalTraces= */ TRACES_PER_ITERATION, iterations);
  }

  static Future<?> generateNetworkRequests(int iterations) {
    return new NetworkRequestGenerator()
        .generateRequests(/* totalRequests= */ REQUESTS_PER_ITERATION, iterations);
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
   * Blocks calling thread until all of Fireperf's persisted events are sent by Firelog. Must be
   * called after events have persisted.
   */
  static void flgForceUploadSync() {
    TransportRuntimeTesting.forceUpload(CCTDestination.LEGACY_INSTANCE, Priority.DEFAULT);
  }
}
