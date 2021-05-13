// Copyright 2021 Google LLC
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

package com.google.firebase.perf.util;

import java.util.concurrent.TimeUnit;

/** A Rate object representing the number of tokens per total specified time unit. */
public class Rate {

  private static final long NANO_IN_SECOND = 1000 * 1000 * 1000;
  private static final long MICRO_IN_SECOND = 1000 * 1000;
  private static final long MILLI_IN_SECOND = 1000;

  private long numTokensPerTotalTimeUnit;
  private long numTimeUnits;
  private TimeUnit timeUnit;

  /**
   * Constructs a Rate object.
   *
   * <p>For example, a rate of 3 tokens per minute can be represented by new Rate(3, 1, MINUTES) or
   * new Rate(3, 60, SECONDS).
   *
   * @param numTokensPerTotalTimeUnit The number of tokens to be issued within the specified time
   * @param numTimeUnits The number of specified time unit
   * @param timeUnit The specified time unit
   */
  public Rate(long numTokensPerTotalTimeUnit, long numTimeUnits, TimeUnit timeUnit) {
    assert numTimeUnits > 0;
    this.numTokensPerTotalTimeUnit = numTokensPerTotalTimeUnit;
    this.numTimeUnits = numTimeUnits;
    this.timeUnit = timeUnit;
  }

  /**
   * Converts the rate to tokens per second.
   *
   * @return rate in tokens per second
   */
  public double getTokenPerSeconds() {
    switch (timeUnit) {
      case NANOSECONDS:
        return ((double) numTokensPerTotalTimeUnit / numTimeUnits) * NANO_IN_SECOND;
      case MICROSECONDS:
        return ((double) numTokensPerTotalTimeUnit / numTimeUnits) * MICRO_IN_SECOND;
      case MILLISECONDS:
        return ((double) numTokensPerTotalTimeUnit / numTimeUnits) * MILLI_IN_SECOND;
      default:
        return (double) numTokensPerTotalTimeUnit / timeUnit.toSeconds(numTimeUnits);
    }
  }
}
