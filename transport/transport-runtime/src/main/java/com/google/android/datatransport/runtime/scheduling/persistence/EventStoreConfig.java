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
  private static final long MAX_DB_STORAGE_SIZE_IN_BYTES = 10 * 1024 * 1024;
  private static final int LOAD_BATCH_SIZE = 200;
  private static final int LOCK_TIME_OUT_MS = 10000;
  private static final long DURATION_ONE_WEEK_MS = 7 * 24 * 60 * 60 * 1000;
  private static final int MAX_BLOB_BYTE_SIZE_PER_ROW = 80 * 1024;

  static final EventStoreConfig DEFAULT =
      EventStoreConfig.builder()
          .setMaxStorageSizeInBytes(MAX_DB_STORAGE_SIZE_IN_BYTES)
          .setLoadBatchSize(LOAD_BATCH_SIZE)
          .setCriticalSectionEnterTimeoutMs(LOCK_TIME_OUT_MS)
          .setEventCleanUpAge(DURATION_ONE_WEEK_MS)
          .setMaxBlobByteSizePerRow(MAX_BLOB_BYTE_SIZE_PER_ROW)
          .build();

  abstract long getMaxStorageSizeInBytes();

  abstract int getLoadBatchSize();

  abstract int getCriticalSectionEnterTimeoutMs();

  abstract long getEventCleanUpAge();

  abstract int getMaxBlobByteSizePerRow();

  static EventStoreConfig.Builder builder() {
    return new AutoValue_EventStoreConfig.Builder();
  }

  Builder toBuilder() {
    return builder()
        .setMaxStorageSizeInBytes(getMaxStorageSizeInBytes())
        .setLoadBatchSize(getLoadBatchSize())
        .setCriticalSectionEnterTimeoutMs(getCriticalSectionEnterTimeoutMs())
        .setEventCleanUpAge(getEventCleanUpAge())
        .setMaxBlobByteSizePerRow(getMaxBlobByteSizePerRow());
  }

  @AutoValue.Builder
  abstract static class Builder {
    abstract Builder setMaxStorageSizeInBytes(long value);

    abstract Builder setLoadBatchSize(int value);

    abstract Builder setCriticalSectionEnterTimeoutMs(int value);

    abstract Builder setEventCleanUpAge(long value);

    abstract Builder setMaxBlobByteSizePerRow(int value);

    abstract EventStoreConfig build();
  }
}
