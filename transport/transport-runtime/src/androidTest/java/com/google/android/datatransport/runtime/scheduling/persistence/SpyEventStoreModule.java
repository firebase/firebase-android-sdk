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

import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXTS_SQL_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_CONTEXT_BACKEND_PRIORITY_EXTRAS_INDEX_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENTS_SQL_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_BACKEND_INDEX_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.CREATE_EVENT_METADATA_SQL_V2;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.DROP_CONTEXTS_SQL;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.DROP_EVENTS_SQL;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.DROP_EVENT_METADATA_SQL;
import static com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule.MIGRATE_TO_V2;
import static org.mockito.Mockito.spy;

import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.Collections;
import javax.inject.Named;
import javax.inject.Singleton;

@Module
public abstract class SpyEventStoreModule {
  @Provides
  static EventStoreConfig storeConfig() {
    return EventStoreConfig.DEFAULT;
  }

  @Provides
  @Singleton
  static EventStore eventStore(SQLiteEventStore store) {
    return spy(store);
  }

  @Binds
  abstract SynchronizationGuard synchronizationGuard(SQLiteEventStore store);

  @Provides
  @Named("CREATE_EVENTS_SQL")
  static String createEventsSql() {
    return CREATE_EVENTS_SQL_V2;
  }

  @Provides
  @Named("CREATE_EVENT_METADATA_SQL")
  static String createEventMetadataSql() {
    return CREATE_EVENT_METADATA_SQL_V2;
  }

  @Provides
  @Named("CREATE_CONTEXTS_SQL")
  static String createContextsSql() {
    return CREATE_CONTEXTS_SQL_V2;
  }

  @Provides
  @Named("CREATE_EVENT_BACKEND_INDEX")
  static String getCreateEventBackendIndex() {
    return CREATE_EVENT_BACKEND_INDEX_V2;
  }

  @Provides
  @Named("CREATE_CONTEXT_BACKEND_PRIORITY_INDEX")
  static String createEventBackendPriorityIndex() {
    return CREATE_CONTEXT_BACKEND_PRIORITY_EXTRAS_INDEX_V2;
  }

  @Provides
  @Named("DROP_EVENTS_SQL")
  static String dropEventsSQL() {
    return DROP_EVENTS_SQL;
  }

  @Provides
  @Named("DROP_EVENT_METADATA_SQL")
  static String dropEventMetadataSql() {
    return DROP_EVENT_METADATA_SQL;
  }

  @Provides
  @Named("DROP_CONTEXTS_SQL")
  static String dropContextsSql() {
    return DROP_CONTEXTS_SQL;
  }

  @Provides
  static DatabaseMigrationClient createDatabaseMigrationClient() {
    return new DatabaseMigrationClient(Collections.singletonList(MIGRATE_TO_V2));
  }
}
