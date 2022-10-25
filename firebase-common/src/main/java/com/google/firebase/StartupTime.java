// Copyright 2022 Google LLC
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

package com.google.firebase;

import com.google.auto.value.AutoValue;

/**
 * Represents the time at which Firebase began initialization, both in unix time/epoch milliseconds
 * and in nanoseconds since the startup of the JVM. The absence of a StartupTime indicates an
 * unreliable or misleading time, such as a launch in direct boot mode. Because of this, StartupTime
 * cannot be guarenteed to be present, and instead should be optionally depended on, and its absence
 * handled.
 */
@AutoValue
public abstract class StartupTime {

  /** @return The epoch time that Firebase began initializing, in milliseconds */
  public abstract long getEpochMillis();

  /**
   * @return The number of nanoseconds from the start of the program to when Firebase began
   *     initializing, measured by the JVM
   */
  public abstract long getStartupNanos();

  /**
   * @param epochMillis Time in milliseconds since epoch
   * @param startupNanos Time in nanoseconds since JVM start
   */
  public static StartupTime create(long epochMillis, long startupNanos) {
    return new AutoValue_StartupTime(epochMillis, startupNanos);
  }

  /** @return A StartupTime represented by the current epoch time and JVM nano time */
  public static StartupTime now() {
    return create(System.currentTimeMillis(), System.nanoTime());
  }
}
