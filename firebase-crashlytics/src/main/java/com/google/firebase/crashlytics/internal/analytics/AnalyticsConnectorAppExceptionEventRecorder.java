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
import com.google.firebase.analytics.connector.AnalyticsConnector;

class AnalyticsConnectorAppExceptionEventRecorder implements AppExceptionEventRecorder {

  private static final String FIREBASE_CRASH_TYPE = "fatal";
  private static final String FIREBASE_TIMESTAMP = "timestamp";
  private static final String FIREBASE_APPLICATION_EXCEPTION = "_ae";
  private static final String FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS = "clx";
  private static final int FIREBASE_CRASH_TYPE_FATAL = 1;

  private final AnalyticsConnector analyticsConnector;

  AnalyticsConnectorAppExceptionEventRecorder(AnalyticsConnector analyticsConnector) {
    this.analyticsConnector = analyticsConnector;
  }

  @Override
  public void recordAppExceptionEvent(long timestamp) {

    final Bundle params = new Bundle();
    params.putInt(FIREBASE_CRASH_TYPE, FIREBASE_CRASH_TYPE_FATAL);
    params.putLong(FIREBASE_TIMESTAMP, timestamp);

    analyticsConnector.logEvent(
        FIREBASE_ANALYTICS_ORIGIN_CRASHLYTICS, FIREBASE_APPLICATION_EXCEPTION, params);
  }
}
