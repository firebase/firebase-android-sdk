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

import androidx.annotation.Keep;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.components.Component;
import com.google.firebase.components.ComponentContainer;
import com.google.firebase.components.ComponentRegistrar;
import com.google.firebase.components.Dependency;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.monitoring.TraceHandle;
import com.google.firebase.monitoring.Tracer;
import com.google.firebase.perf.injection.components.DaggerFirebasePerformanceComponent;
import com.google.firebase.perf.injection.components.FirebasePerformanceComponent;
import com.google.firebase.perf.injection.modules.FirebasePerformanceModule;
import com.google.firebase.perf.metrics.Trace;
import com.google.firebase.platforminfo.LibraryVersionComponent;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
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
        Component.of(
            name -> {
              Trace trace = Trace.create(name);
              trace.start();
              return new TraceHandle() {
                @Override
                public void addAttribute(String name, String value) {
                  trace.putAttribute(name, value);
                }

                @Override
                public void close() {
                  trace.stop();
                }
              };
            },
            Tracer.class),
        /**
         * Fireperf SDK is lazily by {@link FirebasePerformanceInitializer} during {@link
         * com.google.firebase.perf.application.AppStateMonitor#onActivityResumed(Activity)}. we use
         * "lazy" dependency for some components that are not required during initialization so as
         * not to force initialize them at app startup (refer
         * https://github.com/google/guice/wiki/InjectingProviders#providers-for-lazy-loading)*
         */
        LibraryVersionComponent.create("fire-perf", BuildConfig.VERSION_NAME));
  }

  private static FirebasePerformance providesFirebasePerformance(ComponentContainer container) {
    FirebasePerformanceComponent component =
        DaggerFirebasePerformanceComponent.builder()
            .firebasePerformanceModule(
                new FirebasePerformanceModule(
                    container.get(FirebaseApp.class),
                    container.get(FirebaseInstallationsApi.class),
                    container.getProvider(RemoteConfigComponent.class),
                    container.getProvider(TransportFactory.class)))
            .build();

    return component.getFirebasePerformance();
  }
}
