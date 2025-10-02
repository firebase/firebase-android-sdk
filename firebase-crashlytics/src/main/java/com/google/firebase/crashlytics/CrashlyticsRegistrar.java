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
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.crashlytics.internal.CrashlyticsNativeComponent;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.concurrency.CrashlyticsWorkers;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.remoteconfig.interop.FirebaseRemoteConfigInterop;
import com.google.firebase.sessions.api.FirebaseSessionsDependencies;
import com.google.firebase.sessions.api.SessionSubscriber;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ExecutorService;

/** @hide */
public class CrashlyticsRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-cls";
  private final Qualified<ExecutorService> backgroundExecutorService =
      Qualified.qualified(Background.class, ExecutorService.class);
  private final Qualified<ExecutorService> blockingExecutorService =
      Qualified.qualified(Blocking.class, ExecutorService.class);
  private final Qualified<ExecutorService> lightweightExecutorService =
      Qualified.qualified(Lightweight.class, ExecutorService.class);

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
            .add(Dependency.required(backgroundExecutorService))
            .add(Dependency.required(blockingExecutorService))
            .add(Dependency.required(lightweightExecutorService))
            .add(Dependency.deferred(CrashlyticsNativeComponent.class))
            .add(Dependency.deferred(AnalyticsConnector.class))
            .add(Dependency.deferred(FirebaseRemoteConfigInterop.class))
            .factory(this::buildCrashlytics)
            .eagerInDefaultApp()
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }

  private FirebaseCrashlytics buildCrashlytics(ComponentContainer container) {
    CrashlyticsWorkers.setEnforcement(BuildConfig.DEBUG);

    long startTime = System.currentTimeMillis();

    FirebaseCrashlytics crashlytics =
        FirebaseCrashlytics.init(
            container.get(FirebaseApp.class),
            container.get(FirebaseInstallationsApi.class),
            container.getDeferred(CrashlyticsNativeComponent.class),
            container.getDeferred(AnalyticsConnector.class),
            container.getDeferred(FirebaseRemoteConfigInterop.class),
            container.get(backgroundExecutorService),
            container.get(blockingExecutorService),
            container.get(lightweightExecutorService));

    long duration = System.currentTimeMillis() - startTime;
    if (duration > 16) {
      Logger.getLogger().d("Initializing Crashlytics blocked main for " + duration + " ms");
    }

    return crashlytics;
  }
}
