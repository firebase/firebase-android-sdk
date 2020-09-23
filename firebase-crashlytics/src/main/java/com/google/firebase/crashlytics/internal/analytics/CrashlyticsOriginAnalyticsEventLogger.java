// Copyright 2020 Google LLC
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

package com.google.firebase.crashlytics.internal.analytics;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.analytics.connector.AnalyticsConnector;

/**
 * Analytics event logger which logs events directly to Firebase Analytics using the Crashlytics
 * origin
 */
public class CrashlyticsOriginAnalyticsEventLogger implements AnalyticsEventLogger {

  static final String FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS = "clx";

  public CrashlyticsOriginAnalyticsEventLogger(@NonNull AnalyticsConnector analyticsConnector) {
    this.analyticsConnector = analyticsConnector;
  }

  @NonNull private final AnalyticsConnector analyticsConnector;

  @Override
  public void logEvent(@NonNull String name, @Nullable Bundle params) {
    analyticsConnector.logEvent(FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS, name, params);
  }
}
