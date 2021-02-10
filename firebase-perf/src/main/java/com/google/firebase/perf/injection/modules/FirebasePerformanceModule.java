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

package com.google.firebase.perf.injection.modules;

import androidx.annotation.NonNull;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.inject.Provider;
import com.google.firebase.installations.FirebaseInstallationsApi;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.config.ConfigResolver;
import com.google.firebase.perf.internal.GaugeManager;
import com.google.firebase.perf.internal.RemoteConfigManager;
import com.google.firebase.remoteconfig.RemoteConfigComponent;
import dagger.Module;
import dagger.Provides;

/**
 * Provider for {@link FirebasePerformance}.
 *
 * @hide
 */
@Module
public class FirebasePerformanceModule {
  private final FirebaseApp firebaseApp;
  private final FirebaseInstallationsApi firebaseInstallations;
  private final Provider<RemoteConfigComponent> remoteConfigComponentProvider;
  private final Provider<TransportFactory> transportFactoryProvider;

  public FirebasePerformanceModule(
      @NonNull FirebaseApp firebaseApp,
      @NonNull FirebaseInstallationsApi firebaseInstallations,
      @NonNull Provider<RemoteConfigComponent> remoteConfigComponentProvider,
      @NonNull Provider<TransportFactory> transportFactoryProvider) {
    this.firebaseApp = firebaseApp;
    this.firebaseInstallations = firebaseInstallations;
    this.remoteConfigComponentProvider = remoteConfigComponentProvider;
    this.transportFactoryProvider = transportFactoryProvider;
  }

  @Provides
  FirebaseApp providesFirebaseApp() {
    return firebaseApp;
  }

  @Provides
  FirebaseInstallationsApi providesFirebaseInstallations() {
    return firebaseInstallations;
  }

  @Provides
  Provider<RemoteConfigComponent> providesRemoteConfigComponent() {
    return remoteConfigComponentProvider;
  }

  @Provides
  Provider<TransportFactory> providesTransportFactoryProvider() {
    return transportFactoryProvider;
  }

  @Provides
  RemoteConfigManager providesRemoteConfigManager() {
    return RemoteConfigManager.getInstance();
  }

  @Provides
  ConfigResolver providesConfigResolver() {
    return ConfigResolver.getInstance();
  }

  @Provides
  GaugeManager providesGaugeManager() {
    return GaugeManager.getInstance();
  }
}
