// Copyright 2020 Google LLC
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

package com.google.firebase.installations.time;

/**
 * Implementation that uses System.currentTimeMillis() to implement {@link
 * Clock#currentTimeMillis()}.
 *
 * <p>Note: Using JodaTime would require us to a add a dependency on joda-time:joda-time library
 * which will lead to increase in FIS aar size just to fetch system time. Hence, decided to use
 * Java's inbuilt current system time.
 *
 * @hide
 */
public class SystemClock implements Clock {
  private static SystemClock singleton;

  private SystemClock() {}

  /** Factory method that always returns the same {@link SystemClock} instance. */
  public static SystemClock getInstance() {
    if (singleton == null) {
      singleton = new SystemClock();
    }
    return singleton;
  }

  @Override
  public long currentTimeMillis() {
    // Returns current system time in millis as per
    // https://docs.oracle.com/javase/7/docs/api/java/lang/System.html#currentTimeMillis().
    return System.currentTimeMillis();
  }
}
