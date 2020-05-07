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

package com.google.firebase.inappmessaging.internal.injection.modules;

import com.google.android.datatransport.Event;
import com.google.android.datatransport.Transport;
import com.google.android.datatransport.TransportFactory;
import com.google.firebase.FirebaseApp;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.inappmessaging.internal.DeveloperListenerManager;
import com.google.firebase.inappmessaging.internal.MetricsLoggerClient;
import com.google.firebase.inappmessaging.internal.injection.scopes.FirebaseAppScope;
import com.google.firebase.inappmessaging.internal.time.Clock;
import com.google.firebase.installations.FirebaseInstallationsApi;
import dagger.Module;
import dagger.Provides;

/**
 * Bindings for engagementMetrics
 *
 * @hide
 */
@Module
public class TransportClientModule {
  private static final String TRANSPORT_NAME = "FIREBASE_INAPPMESSAGING";

  @Provides
  @FirebaseAppScope
  static MetricsLoggerClient providesMetricsLoggerClient(
      FirebaseApp app,
      TransportFactory transportFactory,
      AnalyticsConnector analyticsConnector,
      FirebaseInstallationsApi firebaseInstallations,
      Clock clock,
      DeveloperListenerManager developerListenerManager) {
    Transport<byte[]> transport =
        transportFactory.getTransport(TRANSPORT_NAME, byte[].class, b -> b);
    return new MetricsLoggerClient(
        bytes -> transport.send(Event.ofData(bytes)),
        analyticsConnector,
        app,
        firebaseInstallations,
        clock,
        developerListenerManager);
  }
}
