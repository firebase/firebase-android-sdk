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

package com.google.firebase.firestore.local;

import java.util.HashMap;
import java.util.Map;

/* A test-only collector of operation counts from the persistence layer. */
class AccumulatingStatsCollector extends StatsCollector {

  private final Map<String, Integer> rowsRead = new HashMap<>();
  private final Map<String, Integer> rowsDeleted = new HashMap<>();
  private final Map<String, Integer> rowsWritten = new HashMap<>();

  @Override
  void recordRowsRead(String tag, int count) {
    Integer currentValue = rowsRead.get(tag);
    rowsRead.put(tag, currentValue != null ? currentValue + count : count);
  }

  @Override
  void recordRowsDeleted(String tag, int count) {
    Integer currentValue = rowsDeleted.get(tag);
    rowsDeleted.put(tag, currentValue != null ? currentValue + count : count);
  }

  @Override
  void recordRowsWritten(String tag, int count) {
    Integer currentValue = rowsWritten.get(tag);
    rowsWritten.put(tag, currentValue != null ? currentValue + count : count);
  }

  /** Reset all operation counts */
  void reset() {
    rowsRead.clear();
    rowsDeleted.clear();
    rowsWritten.clear();
  }

  /** Returns the number of rows read for the given tag since the last call to `reset()`. */
  int getRowsRead(String tag) {
    return rowsRead.containsKey(tag) ? rowsRead.get(tag) : 0;
  }

  /** Returns the number of rows written for the given tag since the last call to `reset()`. */
  int getRowsWritten(String tag) {
    return rowsWritten.containsKey(tag) ? rowsWritten.get(tag) : 0;
  }

  /** Returns the number of rows deleted for the given tag since the last call to `reset()`. */
  int getRowsDeleted(String tag) {
    return rowsDeleted.containsKey(tag) ? rowsDeleted.get(tag) : 0;
  }
}
