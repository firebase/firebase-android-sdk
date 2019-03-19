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

import com.google.android.datatransport.runtime.scheduling.ImmediateScheduler;
import com.google.android.datatransport.runtime.scheduling.Scheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Uptime;
import com.google.android.datatransport.runtime.time.UptimeClock;
import com.google.android.datatransport.runtime.time.WallTime;
import com.google.android.datatransport.runtime.time.WallTimeClock;
import dagger.Module;
import dagger.Provides;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import javax.inject.Singleton;

@Module
class TransportRuntimeModule {
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
  @Uptime
  static Clock uptimeClock() {
    return new UptimeClock();
  }

  @Provides
  static Scheduler scheduler(Executor executor, BackendRegistry registry) {
    return new ImmediateScheduler(executor, registry);
  }

  @Provides
  @Singleton
  static EventStore eventStore() {
    return new InMemoryEventStore();
  }
}
