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

import static org.mockito.Mockito.spy;

import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
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
  static SQLiteEventStore sqliteEventStore(
      @WallTime Clock wallClock,
      @Monotonic Clock clock,
      EventStoreConfig config,
      SchemaManager schemaManager) {
    return spy(new SQLiteEventStore(wallClock, clock, config, schemaManager));
  }

  @Binds
  abstract EventStore eventStore(SQLiteEventStore store);

  @Binds
  abstract SynchronizationGuard synchronizationGuard(SQLiteEventStore store);

  @Provides
  @Named("SCHEMA_VERSION")
  static int schemaVersion() {
    return SchemaManager.SCHEMA_VERSION;
  }

  @Provides
  @Named("SQLITE_DB_NAME")
  static String dbName() {
    return SchemaManager.DB_NAME;
  }
}
