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

package com.google.firebase.inappmessaging.internal.injection.modules;

import com.google.firebase.abt.FirebaseABTesting;
import com.google.firebase.inappmessaging.internal.AbtIntegrationHelper;
import dagger.Module;
import dagger.Provides;

/**
 * Provider for ABTesting Module
 *
 * @hide
 */
@Module
public class AbTestingModule {
  private final FirebaseABTesting abTesting;

  public AbTestingModule(FirebaseABTesting abTesting) {
    this.abTesting = abTesting;
  }

  @Provides
  AbtIntegrationHelper providesAbtIntegrationHelper() {
    return new AbtIntegrationHelper(abTesting);
  }
}
