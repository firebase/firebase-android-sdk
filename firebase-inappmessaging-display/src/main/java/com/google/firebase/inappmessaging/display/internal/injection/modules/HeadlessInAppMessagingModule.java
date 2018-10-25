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

package com.google.firebase.inappmessaging.display.internal.injection.modules;

import com.google.firebase.inappmessaging.FirebaseInAppMessaging;
import com.google.firebase.inappmessaging.display.internal.injection.scopes.FirebaseAppScope;
import dagger.Module;
import dagger.Provides;

/** @hide */
@Module
public class HeadlessInAppMessagingModule {
  private final FirebaseInAppMessaging headless;

  public HeadlessInAppMessagingModule(FirebaseInAppMessaging headless) {
    this.headless = headless;
  }

  @Provides
  @FirebaseAppScope
  FirebaseInAppMessaging providesHeadlesssSingleton() {
    return headless;
  }
}
