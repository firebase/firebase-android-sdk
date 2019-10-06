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
import com.google.android.datatransport.runtime.scheduling.SchedulingModule;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.SchedulerConfig;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.Uploader;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule;
import com.google.android.datatransport.runtime.scheduling.persistence.SQLiteEventStore;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import dagger.BindsInstance;
import dagger.Component;
import java.io.IOException;
import javax.inject.Singleton;

@Component(
    modules = {
      EventStoreModule.class,
      TestExecutionModule.class,
      SchedulingModule.class,
    })
@Singleton
abstract class TestRuntimeComponent extends TransportRuntimeComponent {

  @Override
  abstract TransportRuntime getTransportRuntime();

  @Override
  abstract SQLiteEventStore getEventStore();

  @Override
  public void close() throws IOException {
    getEventStore().clearDb();
    super.close();
  }

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder setApplicationContext(Context applicationContext);

    @BindsInstance
    Builder setBackendRegistry(BackendRegistry registry);

    @BindsInstance
    Builder setUploader(Uploader uploader);

    @BindsInstance
    Builder setSchedulerConfig(SchedulerConfig config);

    @BindsInstance
    Builder setUptimeClock(@Monotonic Clock clock);

    @BindsInstance
    Builder setEventClock(@WallTime Clock clock);

    TestRuntimeComponent build();
  }
}
