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

/**
 * AnalyticsReceiver which splits incoming analytics events by origin, sending those from the
 * Crashlytics origin to the Crashlytics origin AnalyticsReceiver, and all others to the breadcrumb
 * AnalyticsReceiver.
 */
public class BaseAnalyticsReceiver implements AnalyticsReceiver {

  private static final String CRASHLYTICS_ORIGIN = "clx";
  private static final String EVENT_ORIGIN_KEY = "_o";

  private final AnalyticsReceiver breadcrumbAnalyticsReceiver;
  private final AnalyticsReceiver crashlyticsOriginAnalyticsReceiver;

  public BaseAnalyticsReceiver(
      AnalyticsReceiver breadcrumbAnalyticsReceiver,
      AnalyticsReceiver crashlyticsOriginAnalyticsReceiver) {
    this.breadcrumbAnalyticsReceiver = breadcrumbAnalyticsReceiver;
    this.crashlyticsOriginAnalyticsReceiver = crashlyticsOriginAnalyticsReceiver;
  }

  @Override
  public void onEvent(@NonNull String name, @NonNull Bundle params) {
    final String origin = params.getString(EVENT_ORIGIN_KEY);
    if (CRASHLYTICS_ORIGIN.equals(origin)) {
      crashlyticsOriginAnalyticsReceiver.onEvent(name, params);
    } else {
      // Place breadcrumbs for all named events which did not originate from Crashlytics
      breadcrumbAnalyticsReceiver.onEvent(name, params);
    }
  }
}
