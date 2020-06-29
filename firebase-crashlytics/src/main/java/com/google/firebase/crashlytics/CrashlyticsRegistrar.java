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

package com.google.firebase.crashlytics;

import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;

/** @hide */
public class CrashlyticsRegistrar implements ComponentRegistrar {
  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseCrashlytics.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(FirebaseInstallationsApi.class))
            .add(Dependency.optional(AnalyticsConnector.class))
            .add(Dependency.optional(CrashlyticsNativeComponent.class))
            .factory(this::buildCrashlytics)
            .eagerInDefaultApp()
            .build(),
        LibraryVersionComponent.create("fire-cls", BuildConfig.VERSION_NAME));
  }

  private FirebaseCrashlytics buildCrashlytics(ComponentContainer container) {
    FirebaseApp app = container.get(FirebaseApp.class);

    CrashlyticsNativeComponent nativeComponent = container.get(CrashlyticsNativeComponent.class);

    AnalyticsConnector analyticsConnector = container.get(AnalyticsConnector.class);

    FirebaseInstallationsApi firebaseInstallations = container.get(FirebaseInstallationsApi.class);

    return FirebaseCrashlytics.init(
        app, firebaseInstallations, nativeComponent, analyticsConnector);
  }
}
