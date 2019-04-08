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

package com.google.android.datatransport.runtime;

import android.content.Context;
import android.os.Build;
import com.google.android.datatransport.runtime.backends.CreationContext;
import com.google.android.datatransport.runtime.scheduling.ImmediateScheduler;
import com.google.android.datatransport.runtime.scheduling.Scheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.AlarmManagerScheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoScheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.MaxStorageSize;
import com.google.android.datatransport.runtime.scheduling.persistence.SQLiteEventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.UptimeClock;
import com.google.android.datatransport.runtime.time.WallTime;
import com.google.android.datatransport.runtime.time.WallTimeClock;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

@Module
abstract class TransportRuntimeModule {

  private static final long MAX_DB_STORAGE_SIZE_IN_BYTES = 10 * 1024 * 1024;

  @Provides
  @MaxStorageSize
  static long maxStorageSize() {
    return MAX_DB_STORAGE_SIZE_IN_BYTES;
  }

  @Singleton
  @Provides
  static Executor executor() {
    return Executors.newSingleThreadExecutor();
  }

  @Provides
  @WallTime
  static Clock eventClock() {
    return new WallTimeClock();
  }

  @Provides
  @Monotonic
  static Clock uptimeClock() {
    return new UptimeClock();
  }

  @Provides
  static WorkScheduler workScheduler(
      Context context, EventStore eventStore, @WallTime Clock eventClock) {
    if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
      return new JobInfoScheduler(context, eventStore, eventClock);
    } else {
      return new AlarmManagerScheduler(context, eventStore, eventClock);
    }
  }

  @Binds
  abstract Scheduler scheduler(ImmediateScheduler scheduler);

  @Binds
  abstract EventStore eventStore(SQLiteEventStore store);

  @Binds
  abstract SynchronizationGuard synchronizationGuard(SQLiteEventStore store);

  @Provides
  @Singleton
  static CreationContext creationContext(
      @WallTime Clock wallClock, @Monotonic Clock monotonicClock) {
    return CreationContext.create(wallClock, monotonicClock);
  }
}
