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

import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.events.Subscriber;
import com.google.firebase.inappmessaging.internal.StubAnalyticsConnector;
import dagger.Module;
import dagger.Provides;
import javax.inject.Singleton;

/**
 * Bindings for app measurement
 *
 * @hide
 */
@Module
public class AppMeasurementModule {

  private AnalyticsConnector analyticsConnector;
  private Subscriber firebaseEventsSubscriber;

  public AppMeasurementModule(
      AnalyticsConnector analyticsConnector, Subscriber firebaseEventsSubscriber) {
    this.analyticsConnector =
        analyticsConnector != null ? analyticsConnector : StubAnalyticsConnector.instance;
    this.firebaseEventsSubscriber = firebaseEventsSubscriber;
  }

  @Provides
  @Singleton
  AnalyticsConnector providesAnalyticsConnector() {
    return analyticsConnector;
  }

  @Provides
  @Singleton
  Subscriber providesSubsriber() {
    return firebaseEventsSubscriber;
  }
}
