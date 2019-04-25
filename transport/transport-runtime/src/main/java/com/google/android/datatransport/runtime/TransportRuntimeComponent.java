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
import com.google.android.datatransport.runtime.backends.BackendRegistryModule;
import com.google.android.datatransport.runtime.scheduling.SchedulingConfigModule;
import com.google.android.datatransport.runtime.scheduling.SchedulingModule;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule;
import com.google.android.datatransport.runtime.time.TimeModule;
import dagger.BindsInstance;
import dagger.Component;
import java.io.Closeable;
import java.io.IOException;
import javax.inject.Singleton;

@Component(
    modules = {
      BackendRegistryModule.class,
      EventStoreModule.class,
      ExecutionModule.class,
      SchedulingModule.class,
      SchedulingConfigModule.class,
      TimeModule.class,
    })
@Singleton
abstract class TransportRuntimeComponent implements Closeable {
  abstract TransportRuntime getTransportRuntime();

  abstract EventStore getEventStore();

  @Override
  public void close() throws IOException {
    getEventStore().close();
  }

  @Component.Builder
  interface Builder {
    @BindsInstance
    Builder setApplicationContext(Context applicationContext);

    TransportRuntimeComponent build();
  }
}
