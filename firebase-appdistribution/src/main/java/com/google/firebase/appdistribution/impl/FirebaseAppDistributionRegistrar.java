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

import android.annotation.SuppressLint;
import android.app.Application;
import android.content.Context;
import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import com.google.firebase.FirebaseApp;
import com.google.firebase.annotations.concurrent.Background;
import com.google.firebase.annotations.concurrent.Blocking;
import com.google.firebase.annotations.concurrent.Lightweight;
import com.google.firebase.appdistribution.FirebaseAppDistribution;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.components.Qualified;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

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
    Qualified<Executor> backgroundExecutor = Qualified.qualified(Background.class, Executor.class);
    Qualified<Executor> blockingExecutor = Qualified.qualified(Blocking.class, Executor.class);
    Qualified<Executor> lightweightExecutor =
        Qualified.qualified(Lightweight.class, Executor.class);
    return Arrays.asList(
        Component.builder(FirebaseAppDistribution.class)
            .name(LIBRARY_NAME)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.requiredProvider(FirebaseInstallationsApi.class))
            .add(Dependency.required(backgroundExecutor))
            .add(Dependency.required(blockingExecutor))
            .add(Dependency.required(lightweightExecutor))
            .factory(c -> buildFirebaseAppDistribution(c, backgroundExecutor, blockingExecutor, lightweightExecutor))
            // construct FirebaseAppDistribution instance on startup so we can register for
            // activity lifecycle callbacks before the API is called
            .alwaysEager()
            .build(),
        Component.builder(FeedbackSender.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.requiredProvider(FirebaseInstallationsApi.class))
            .add(Dependency.required(backgroundExecutor))
            .add(Dependency.required(blockingExecutor))
            .factory(c -> buildFeedbackSender(c, c.get(blockingExecutor)))
            .build(),
        LibraryVersionComponent.create(LIBRARY_NAME, BuildConfig.VERSION_NAME));
  }

  private FeedbackSender buildFeedbackSender(
      ComponentContainer container, Executor blockingExecutor) {
    FirebaseApp firebaseApp = container.get(FirebaseApp.class);
    Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider =
        container.getProvider(FirebaseInstallationsApi.class);
    FirebaseAppDistributionTesterApiClient testerApiClient =
        new FirebaseAppDistributionTesterApiClient(
            firebaseApp,
            firebaseInstallationsApiProvider,
            new TesterApiHttpClient(firebaseApp),
            blockingExecutor);
    return new FeedbackSender(testerApiClient, blockingExecutor);
  }

  private FirebaseAppDistribution buildFirebaseAppDistribution(
      ComponentContainer container,
      Qualified<Executor> backgroundExecutorType,
      Qualified<Executor> blockingExecutorType,
      Qualified<Executor> lightweightExecutorType) {
    FirebaseApp firebaseApp = container.get(FirebaseApp.class);
    Executor backgroundExecutor = container.get(backgroundExecutorType);
    Executor blockingExecutor = container.get(blockingExecutorType);
    Executor lightweightExecutor = container.get(lightweightExecutorType);
    Context context = firebaseApp.getApplicationContext();
    Provider<FirebaseInstallationsApi> firebaseInstallationsApiProvider =
        container.getProvider(FirebaseInstallationsApi.class);
    FirebaseAppDistributionTesterApiClient testerApiClient =
        new FirebaseAppDistributionTesterApiClient(
            firebaseApp,
            firebaseInstallationsApiProvider,
            new TesterApiHttpClient(firebaseApp),
            blockingExecutor);
    SignInStorage signInStorage = new SignInStorage(context);
    FirebaseAppDistributionLifecycleNotifier lifecycleNotifier =
        FirebaseAppDistributionLifecycleNotifier.getInstance();
    ReleaseIdentifier releaseIdentifier = new ReleaseIdentifier(firebaseApp, testerApiClient);
    FirebaseAppDistribution appDistribution =
        new FirebaseAppDistributionImpl(
            firebaseApp,
            new TesterSignInManager(firebaseApp, firebaseInstallationsApiProvider, signInStorage),
            new NewReleaseFetcher(
                firebaseApp.getApplicationContext(), testerApiClient, releaseIdentifier),
            new ApkUpdater(firebaseApp, new ApkInstaller(), blockingExecutor),
            new AabUpdater(blockingExecutor),
            signInStorage,
            lifecycleNotifier,
            releaseIdentifier,
            new ScreenshotTaker(firebaseApp, lifecycleNotifier, backgroundExecutor),
            lightweightExecutor,
            blockingExecutor);

    if (context instanceof Application) {
      Application firebaseApplication = (Application) context;
      firebaseApplication.registerActivityLifecycleCallbacks(lifecycleNotifier);
    } else {
      LogWrapper.e(
          TAG,
          String.format(
              "Context %s was not an Application, can't register for lifecycle callbacks. SDK"
                  + " might not function correctly.",
              context));
    }

    return appDistribution;
  }
}
