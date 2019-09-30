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

import android.util.SparseArray;

public enum Priority {
  /** Quality of the service is within an hour. */
  DEFAULT(0),

  /** Events delivered at most daily, on unmetered network. */
  VERY_LOW(1),

  /** Events delivered as soon as possible. */
  HIGHEST(2),
  ;

  private static final SparseArray<Priority> PRIORITY_MAP = new SparseArray<>();

  static {
    for (Priority p : Priority.values()) {
      PRIORITY_MAP.append(p.getValue(), p);
    }
  }

  private final int value;

  Priority(int value) {
    this.value = value;
  }

  public int getValue() {
    return value;
  }

  public static Priority forValue(int value) {
    Priority priority = PRIORITY_MAP.get(value);
    if (priority == null) {
      throw new IllegalArgumentException("Unknown Priority for value " + value);
    }
    return priority;
  }
}
