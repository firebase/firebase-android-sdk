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

import android.os.SystemClock;
import androidx.annotation.NonNull;
import com.google.auto.value.AutoValue;

/**
 * Represents Firebase's startup time in several timing methods. Represented in unix time/epoch
 * milliseconds milliseconds since boot, and uptime milliseconds. The absence of a StartupTime
 * indicates an unreliable or misleading time, such as a launch in direct boot mode. Because of
 * this, StartupTime cannot be guaranteed to be present, and instead should be optionally depended
 * on, and its absence handled.
 *
 * @hide
 */
@AutoValue
public abstract class StartupTime {

  /** @return The epoch time that Firebase began initializing, in milliseconds */
  public abstract long getEpochMillis();

  /** @return The number of milliseconds from boot to when Firebase began initializing */
  public abstract long getElapsedRealtime();

  /** @return The number of milliseconds of uptime measured by SystemClock.uptimeMillis() */
  public abstract long getUptimeMillis();

  /**
   * @param epochMillis Time in milliseconds since epoch
   * @param elapsedRealtime Time in milliseconds since boot
   */
  public static @NonNull StartupTime create(
      long epochMillis, long elapsedRealtime, long uptimeMillis) {
    return new AutoValue_StartupTime(epochMillis, elapsedRealtime, uptimeMillis);
  }

  /** @return A StartupTime represented by the current epoch time and JVM nano time */
  public static @NonNull StartupTime now() {
    return create(
        System.currentTimeMillis(), SystemClock.elapsedRealtime(), SystemClock.uptimeMillis());
  }
}
