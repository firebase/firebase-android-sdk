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
import com.google.firebase.inject.Deferred;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.remoteconfig.interop.FirebaseRemoteConfigInterop;
import com.google.firebase.sessions.api.FirebaseSessionsDependencies;
import com.google.firebase.sessions.api.SessionSubscriber;
import java.util.Arrays;
import java.util.List;

/** @hide */
public class CrashlyticsRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-cls";

  static {
    // Add Crashlytics as a dependency of Sessions when this class is loaded into memory.
    FirebaseSessionsDependencies.addDependency(SessionSubscriber.Name.CRASHLYTICS);
  }

  @Override
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebaseCrashlytics.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.required(FirebaseInstallationsApi.class))
            .add(Dependency.deferred(CrashlyticsNativeComponent.class))
            .add(Dependency.deferred(AnalyticsConnector.class))
            .add(Dependency.deferred(FirebaseRemoteConfigInterop.class))
            .factory(this::buildCrashlytics)
            .eagerInDefaultApp()
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }

  private FirebaseCrashlytics buildCrashlytics(ComponentContainer container) {
    FirebaseApp app = container.get(FirebaseApp.class);

    Deferred<CrashlyticsNativeComponent> nativeComponent =
        container.getDeferred(CrashlyticsNativeComponent.class);

    Deferred<AnalyticsConnector> analyticsConnector =
        container.getDeferred(AnalyticsConnector.class);

    FirebaseInstallationsApi firebaseInstallations = container.get(FirebaseInstallationsApi.class);

    Deferred<FirebaseRemoteConfigInterop> remoteConfigInterop =
        container.getDeferred(FirebaseRemoteConfigInterop.class);

    return FirebaseCrashlytics.init(
        app, firebaseInstallations, nativeComponent, analyticsConnector, remoteConfigInterop);
  }
}
