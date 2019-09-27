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

package com.google.android.datatransport;

import java.util.Arrays;

public enum Priority {
  /** Quality of the service is within an hour. */
  DEFAULT(0),

  /** Events delivered at most daily, on unmetered network. */
  VERY_LOW(1),

  /** Events delivered as soon as possible. */
  HIGHEST(2),
  ;

  private static final Priority[] PRIORITY_MAP;

  static {
    // the order of this array must match the values of the enums.
    PRIORITY_MAP = Priority.values();
    Arrays.sort(PRIORITY_MAP, (p1, p2) -> p1.value - p2.value);
  }

  private final int value;

  Priority(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  public static Priority forValue(int value) {
    if (value < 0 || value >= PRIORITY_MAP.length) {
      throw new IllegalArgumentException("Unknown Priority for value " + value);
    }
    return PRIORITY_MAP[value];
  }
}
