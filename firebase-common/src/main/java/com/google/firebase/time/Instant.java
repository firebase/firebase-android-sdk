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

package com.google.firebase.time;

import com.google.auto.value.AutoValue;
import java.util.concurrent.TimeUnit;

/**
 * Stores information about an instant in time.
 *
 * <p>Currently used to record various timestamps as part of Firebase initialization for the
 * purposes of measuring how long various initialization stages take.
 */
@AutoValue
public abstract class Instant {
  /** UTC Timestamp in micro seconds. */
  public abstract long getMicros();

  /**
   * Nano seconds since Java VM start, only useful to calculate durations between {@link Instant}s
   * and does not represent wall clock time.
   */
  public abstract long getNanos();

  /** A valid timestamp has non-negative attributes. */
  public boolean isValid() {
    return getMicros() >= 0 && getNanos() >= 0;
  }

  /** Returns a timestamp based on current wall clock time. */
  public static Instant now() {
    return create(TimeUnit.MILLISECONDS.toMicros(System.currentTimeMillis()), System.nanoTime());
  }

  private static Instant create(long micros, long nanos) {
    return new AutoValue_Instant(micros, nanos);
  }

  /** An invalid "marker" instant useful for representing invalid/missing instants. */
  public static Instant NEVER = create(-1, -1);
}
