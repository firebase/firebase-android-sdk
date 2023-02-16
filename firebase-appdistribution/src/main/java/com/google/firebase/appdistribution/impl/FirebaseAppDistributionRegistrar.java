// Copyright 2021 Google LLC
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

import android.app.Application;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.annotations.concurrent.UiThread;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Registers FirebaseAppDistribution and related components.
 *
 * @hide
 */
@Keep
public class FirebaseAppDistributionRegistrar implements ComponentRegistrar {
  private static final String LIBRARY_NAME = "fire-appdistribution";

  private static String TAG = "Registrar";

  @Override
  public @NonNull List<Component<?>> getComponents() {
    Qualified<ScheduledExecutorService> backgroundExecutor =
        Qualified.qualified(Background.class, ScheduledExecutorService.class);
    Qualified<ScheduledExecutorService> blockingExecutor =
        Qualified.qualified(Blocking.class, ScheduledExecutorService.class);
    Qualified<ScheduledExecutorService> lightweightExecutor =
        Qualified.qualified(Lightweight.class, ScheduledExecutorService.class);
    Qualified<Executor> uiThreadExecutor = Qualified.qualified(UiThread.class, Executor.class);
    return Arrays.asList(
        Component.builder(FirebaseAppDistribution.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(Context.class))
            .add(Dependency.required(AppDistroComponent.class))
            .factory(
                c ->
                    buildFirebaseAppDistribution(
                        c.get(AppDistroComponent.class), c.get(Context.class)))
            // construct FirebaseAppDistribution instance on startup so we can register for
            // activity lifecycle callbacks before the API is called
            .alwaysEager()
            .build(),
        Component.builder(AppDistroComponent.class)
            .add(Dependency.required(Context.class))
            .add(Dependency.required(FirebaseOptions.class))
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.requiredProvider(FirebaseInstallationsApi.class))
            .add(Dependency.required(backgroundExecutor))
            .add(Dependency.required(blockingExecutor))
            .add(Dependency.required(lightweightExecutor))
            .add(Dependency.required(uiThreadExecutor))
            .factory(
                container ->
                    DaggerAppDistroComponent.builder()
                        .setApplicationContext(container.get(Context.class))
                        .setOptions(container.get(FirebaseOptions.class))
                        .setApp(container.get(FirebaseApp.class))
                        .setFis(container.getProvider(FirebaseInstallationsApi.class))
                        .setBackgroundExecutor(container.get(backgroundExecutor))
                        .setBlockingExecutor(container.get(blockingExecutor))
                        .setLightweightExecutor(container.get(lightweightExecutor))
                        .setUiThreadExecutor(container.get(uiThreadExecutor))
                        .build())
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }

  private FirebaseAppDistribution buildFirebaseAppDistribution(
      AppDistroComponent appDistroComponent, Context applicationContext) {

    if (applicationContext instanceof Application) {
      Application application = (Application) applicationContext;
      appDistroComponent.getLifecycleNotifier().registerActivityLifecycleCallbacks(application);
    } else {
      LogWrapper.e(
          TAG,
          String.format(
              "Context %s was not an Application, can't register for lifecycle callbacks. SDK"
                  + " might not function correctly.",
              applicationContext));
    }

    return appDistroComponent.getAppDistribution();
  }
}
