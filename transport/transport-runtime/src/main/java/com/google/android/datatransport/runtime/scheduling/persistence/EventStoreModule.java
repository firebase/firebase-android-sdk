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
import dagger.Binds;
import dagger.Module;
import dagger.Provides;

@Module
public abstract class EventStoreModule {
  private static final long MAX_DB_STORAGE_SIZE_IN_BYTES = 10 * 1024 * 1024;
  private static final int LOAD_BATCH_SIZE = 200;

  @Provides
  static EventStoreConfig storeConfig() {
    return EventStoreConfig.create(MAX_DB_STORAGE_SIZE_IN_BYTES, LOAD_BATCH_SIZE);
  }

  @Binds
  abstract EventStore eventStore(SQLiteEventStore store);

  @Binds
  abstract SynchronizationGuard synchronizationGuard(SQLiteEventStore store);
}
