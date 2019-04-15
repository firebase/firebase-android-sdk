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

package com.google.android.datatransport.runtime.scheduling.persistence;

import com.google.auto.value.AutoValue;

@AutoValue
abstract class EventStoreConfig {
  abstract long getMaxStorageSizeInBytes();

  abstract int getLoadBatchSize();

  static EventStoreConfig create(long maxStorageSizeInBytes, int loadBatchSize) {
    if (maxStorageSizeInBytes < 0) {
      throw new IllegalArgumentException(
          "Cannot set max storage size to a negative number of bytes");
    }
    if (loadBatchSize < 0) {
      throw new IllegalArgumentException(
          "Cannot set load batch size to a negative number of bytes");
    }
    return new AutoValue_EventStoreConfig(maxStorageSizeInBytes, loadBatchSize);
  }

  EventStoreConfig withMaxStorageSizeInBytes(long newVaue) {
    return create(newVaue, getLoadBatchSize());
  }
}
