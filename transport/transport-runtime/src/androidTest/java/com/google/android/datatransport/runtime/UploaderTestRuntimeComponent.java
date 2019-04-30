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

package com.google.android.datatransport.runtime;

import android.content.Context;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.SpyEventStoreModule;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

@Component(
    modules = {
      SpyEventStoreModule.class,
      TestExecutionModule.class,
      TestSchedulingModule.class,
    })
@Singleton
abstract class UploaderTestRuntimeComponent extends TransportRuntimeComponent {

  abstract TransportRuntime getTransportRuntime();

  abstract EventStore getEventStore();

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder setApplicationContext(Context applicationContext);

    @BindsInstance
    Builder setWorkScheduler(WorkScheduler workScheduler);

    @BindsInstance
    Builder setBackendRegistry(BackendRegistry registry);

    @BindsInstance
    Builder setUptimeClock(@Monotonic Clock clock);

    @BindsInstance
    Builder setEventClock(@WallTime Clock clock);

    UploaderTestRuntimeComponent build();
  }
}
