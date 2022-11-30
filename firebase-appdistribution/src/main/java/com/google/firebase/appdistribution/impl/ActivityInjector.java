// Copyright 2022 Google LLC
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

package com.google.firebase.appdistribution.impl;

import androidx.annotation.Nullable;
import com.google.firebase.FirebaseApp;
import dagger.MembersInjector;
import javax.inject.Inject;

/** Firebase Component used by activities to request dependency injection into them. */
class ActivityInjector {
  private final MembersInjector<InstallActivity> installActivityInjector;

  @Inject
  ActivityInjector(MembersInjector<InstallActivity> installActivityInjector) {
    this.installActivityInjector = installActivityInjector;
  }

  static void injectForApp(@Nullable String firebaseAppName, InstallActivity activity) {
    getApp(firebaseAppName)
        .get(ActivityInjector.class)
        .installActivityInjector
        .injectMembers(activity);
  }

  private static FirebaseApp getApp(@Nullable String name) {
    if (name == null) {
      return FirebaseApp.getInstance();
    }
    return FirebaseApp.getInstance(name);
  }
}
