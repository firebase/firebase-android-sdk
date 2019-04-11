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
import com.google.android.datatransport.runtime.scheduling.persistence.EventStoreModule;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.TimeModule;
import dagger.BindsInstance;
import dagger.Component;
import javax.inject.Singleton;

@Component(modules = {EventStoreModule.class, TimeModule.class})
@Singleton
public abstract class SynchronizationComponent {
  private static SynchronizationGuard INSTANCE;

  abstract SynchronizationGuard getGuard();

  public static SynchronizationGuard getGuard(Context applicationContext) {
    synchronized (SynchronizationComponent.class) {
      if (INSTANCE == null) {
        INSTANCE = DaggerSynchronizationComponent.factory().create(applicationContext).getGuard();
      }
      return INSTANCE;
    }
  }

  @Component.Factory
  protected interface Factory {
    SynchronizationComponent create(@BindsInstance Context applicationContext);
  }
}
