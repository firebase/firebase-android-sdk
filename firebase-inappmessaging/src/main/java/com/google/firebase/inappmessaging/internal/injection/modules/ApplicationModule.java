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

package com.google.firebase.inappmessaging.internal.injection.modules;

import android.app.Application;
import com.google.firebase.inappmessaging.internal.DeveloperListenerManager;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Bindings for {@link Application}
 *
 * @hide
 */
@Module
public class ApplicationModule {
  private final Application application;

  public ApplicationModule(Application application) {
    this.application = application;
  }

  @Provides
  @Singleton
  public Application providesApplication() {
    return application;
  }

  @Provides
  @Singleton
  public DeveloperListenerManager developerListenerManager() {
    return new DeveloperListenerManager();
  }
}
