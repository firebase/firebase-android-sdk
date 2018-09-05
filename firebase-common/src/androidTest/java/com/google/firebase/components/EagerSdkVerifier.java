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

package com.google.firebase.components;

import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.inject.Provider;

public class EagerSdkVerifier {
  private final Lazy<FirebaseAuth> authLazy;
  private final Lazy<AnalyticsConnector> analyticsLazy;

  EagerSdkVerifier(Provider<FirebaseAuth> authLazy, Provider<AnalyticsConnector> analyticsLazy) {
    this.authLazy = (Lazy<FirebaseAuth>) authLazy;
    this.analyticsLazy = (Lazy<AnalyticsConnector>) analyticsLazy;
  }

  public boolean isAuthInitialized() {
    return authLazy.isInitialized();
  }

  public boolean isAnalyticsInitialized() {
    return analyticsLazy.isInitialized();
  }
}
