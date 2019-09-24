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

package com.google.firebase.inappmessaging;

import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.events.Subscriber;
import dagger.Module;
import dagger.Provides;

/** Test bindings for API client */
@Module
public class TestAppMeasurementModule {

  private AnalyticsConnector analyticsConnector;
  private Subscriber firebaseEventSubscriber;

  public TestAppMeasurementModule(
      AnalyticsConnector analyticsConnector, Subscriber firebaseEventSubscriber) {
    this.analyticsConnector = analyticsConnector;
    this.firebaseEventSubscriber = firebaseEventSubscriber;
  }

  @Provides
  AnalyticsConnector providesAnalyticsConnector() {
    return analyticsConnector;
  }

  @Provides
  Subscriber providesSubscriber() {
    return firebaseEventSubscriber;
  }
}
