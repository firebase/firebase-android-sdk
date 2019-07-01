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

import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;

import javax.inject.Named;

import dagger.Binds;
import dagger.Module;
import dagger.Provides;

import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXTS_SQL_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENTS_SQL_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_BACKEND_INDEX_V1;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_METADATA_SQL_V1;

@Module
public abstract class TestEventStoreModule {
  private static final long MAX_DB_STORAGE_SIZE_IN_BYTES = 10 * 1024 * 1024;
  private static final int LOAD_BATCH_SIZE = 200;
  private static final int LOCK_TIME_OUT_MS = 0;

  @Provides
  static EventStoreConfig storeConfig() {
    return EventStoreConfig.builder()
        .setMaxStorageSizeInBytes(MAX_DB_STORAGE_SIZE_IN_BYTES)
        .setLoadBatchSize(LOAD_BATCH_SIZE)
        .setCriticalSectionEnterTimeoutMs(LOCK_TIME_OUT_MS)
        .setEventCleanUpAge(60 * 1000)
        .build();
  }

  @Binds
  abstract EventStore eventStore(SQLiteEventStore store);

  @Binds
  abstract SynchronizationGuard synchronizationGuard(SQLiteEventStore store);

  @Provides
  @Named("CREATE_EVENTS_SQL")
  static String createEventsSql() {
    return CREATE_EVENTS_SQL_V1;
  }

  @Provides
  @Named("CREATE_EVENT_METADATA_SQL")
  static String createEventMetadataSql() {
    return CREATE_EVENT_METADATA_SQL_V1;
  }

  @Provides
  @Named("CREATE_CONTEXTS_SQL")
  static String createContextsSql() {
    return CREATE_CONTEXTS_SQL_V1;
  }

  @Provides
  @Named("CREATE_EVENT_BACKEND_INDEX")
  static String getCreateEventBackendIndex() {
    return CREATE_EVENT_BACKEND_INDEX_V1;
  }

  @Provides
  @Named("CREATE_CONTEXT_BACKEND_PRIORITY_INDEX")
  static String createEventBackendPriorityIndex() {
    return CREATE_CONTEXT_BACKEND_PRIORITY_INDEX_V1;
  }
}
