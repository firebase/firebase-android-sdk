// Copyright 2019 Google LLC
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

package com.google.android.datatransport.runtime.util;

import android.util.SparseArray;
import androidx.annotation.NonNull;
import com.google.android.datatransport.Priority;
import java.util.HashMap;

public final class PriorityMapping {
  private static SparseArray<Priority> PRIORITY_MAP = new SparseArray<>();
  private static HashMap<Priority, Integer> PRIORITY_INT_MAP = new HashMap<>();

  static {
    PRIORITY_INT_MAP.put(Priority.DEFAULT, 0);
    PRIORITY_INT_MAP.put(Priority.VERY_LOW, 1);
    PRIORITY_INT_MAP.put(Priority.HIGHEST, 2);

    for (Priority p : PRIORITY_INT_MAP.keySet()) {
      PRIORITY_MAP.append(PRIORITY_INT_MAP.get(p), p);
    }
  }

  @NonNull
  public static Priority valueOf(int value) {
    Priority priority = PRIORITY_MAP.get(value);
    if (priority == null) {
      throw new IllegalArgumentException("Unknown Priority for value " + value);
    }
    return priority;
  }

  public static int toInt(@NonNull Priority priority) {
    Integer integer = PRIORITY_INT_MAP.get(priority);
    if (integer == null) {
      throw new IllegalStateException(
          "PriorityMapping is missing known Priority value " + priority);
    }
    return integer;
  }
}
