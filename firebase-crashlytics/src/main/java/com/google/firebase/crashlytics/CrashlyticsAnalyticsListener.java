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

package com.google.firebase.crashlytics;

import android.os.Bundle;
import androidx.annotation.Nullable;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsReceiver;

class CrashlyticsAnalyticsListener implements AnalyticsConnectorListener {

  private static final String EVENT_NAME_KEY = "name";
  private static final String EVENT_PARAMS_KEY = "params";

  private AnalyticsReceiver analyticsReceiver;

  @Override
  public void onMessageTriggered(int id, @Nullable Bundle extras) {
    Logger.getLogger().d("Received Analytics message: " + id + " " + extras);

    final AnalyticsReceiver receiver = analyticsReceiver;

    if (receiver == null) {
      return;
    }

    if (extras == null) {
      return;
    }

    final String name = extras.getString(EVENT_NAME_KEY);

    if (name != null) {
      Bundle params = extras.getBundle(EVENT_PARAMS_KEY);
      if (params == null) {
        params = new Bundle();
      }

      receiver.onEvent(name, params);
    }
  }

  void setAnalyticsReceiver(AnalyticsReceiver receiver) {
    this.analyticsReceiver = receiver;
  }
}
