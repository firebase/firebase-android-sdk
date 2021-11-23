// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import androidx.annotation.Keep;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.monitoring.ExtendedTracer;
import com.google.firebase.monitoring.Tracer;
import com.google.firebase.perf.application.AppStateMonitor;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.injection.components.DaggerFirebasePerformanceComponent;
import com.google.firebase.perf.injection.components.FirebasePerformanceComponent;
import com.google.firebase.perf.injection.modules.FirebasePerformanceModule;
import com.google.firebase.perf.metrics.AppStartTrace;
import com.google.firebase.perf.metrics.FirebasePerfInternalTracer;
import com.google.firebase.perf.session.SessionManager;
import com.google.firebase.perf.util.Clock;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import com.google.firebase.time.StartupTime;
import java.util.Arrays;
import java.util.List;

/**
 * {@link com.google.firebase.components.ComponentRegistrar} for the Firebase Performance SDK.
 *
 * <p>See go/firebase-android-components and go/firebase-components-android-integration-guide for
 * more details.
 *
 * @hide
 */
@Keep
public class FirebasePerfRegistrar implements ComponentRegistrar {

  @Override
  @Keep
  public List<Component<?>> getComponents() {
    return Arrays.asList(
        Component.builder(FirebasePerformance.class)
            .add(Dependency.required(FirebaseApp.class))
            .add(Dependency.requiredProvider(RemoteConfigComponent.class))
            .add(Dependency.required(FirebaseInstallationsApi.class))
            .add(Dependency.requiredProvider(TransportFactory.class))
            .factory(FirebasePerfRegistrar::providesFirebasePerformance)
            .build(),
        Component.builder(ExtendedTracer.class, Tracer.class)
            .add(Dependency.required(Context.class))
            .add(Dependency.optionalProvider(StartupTime.class))
            .factory(FirebasePerfRegistrar::providesFirebasePerfInternalTracer)
            .build(),
        /**
         * Fireperf SDK is lazily by {@link FirebasePerformanceInitializer} during {@link
         * com.google.firebase.perf.application.AppStateMonitor#onActivityResumed(Activity)}. we use
         * "lazy" dependency for some components that are not required during initialization so as
         * not to force initialize them at app startup (refer
         * https://github.com/google/guice/wiki/InjectingProviders#providers-for-lazy-loading)*
         */
        LibraryVersionComponent.create("fire-perf", BuildConfig.VERSION_NAME));
  }

  @VisibleForTesting
  static FirebasePerfInternalTracer providesFirebasePerfInternalTracer(
      ComponentContainer container) {
    Context appContext = container.get(Context.class);
    Provider<StartupTime> startupTimeProvider = container.getProvider(StartupTime.class);

    // Initialize ConfigResolver early for accessing device caching layer.
    ConfigResolver.getInstance().setApplicationContext(appContext);

    AppStateMonitor appStateMonitor = AppStateMonitor.getInstance();
    appStateMonitor.registerActivityLifecycleCallbacks(appContext);
    appStateMonitor.registerForAppColdStart(new FirebasePerformanceInitializer());

    if (startupTimeProvider.get() != null) {
      StartupTime startupTime = startupTimeProvider.get();
      AppStartTrace appStartTrace =
          new AppStartTrace(
              new Timer(startupTime.getInstant().getMicros(), startupTime.getInstant().getNanos()));
      appStartTrace.registerActivityLifecycleCallbacks(appContext);

      new Handler(Looper.getMainLooper())
          .post(new AppStartTrace.StartFromBackgroundRunnable(appStartTrace));
    }

    // In the case of cold start, we create a session and start collecting gauges as early as
    // possible.
    // There is code in SessionManager that prevents us from resetting the session twice in case
    // of app cold start.
    SessionManager.getInstance().initializeGaugeCollection();
    return new FirebasePerfInternalTracer();
  }

  private static FirebasePerformance providesFirebasePerformance(ComponentContainer container) {
    FirebasePerformanceComponent component =
        DaggerFirebasePerformanceComponent.builder()
            .firebasePerformanceModule(
                new FirebasePerformanceModule(
                    container.get(FirebaseApp.class),
                    container.get(FirebaseInstallationsApi.class),
                    container.getProvider(RemoteConfigComponent.class),
                    container.getProvider(TransportFactory.class),
                    new Clock().getTime()))
            .build();

    return component.getFirebasePerformance();
  }
}
