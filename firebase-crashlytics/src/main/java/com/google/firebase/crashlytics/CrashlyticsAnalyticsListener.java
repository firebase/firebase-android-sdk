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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsEventReceiver;

/**
 * Crashlytics listener for Firebase Analytics events. Processes incoming events and passes those
 * from the Crashlytics origin to the registered Crashlytics origin event receiver, and all others
 * to the registered breadcrumb event receiver.
 */
class CrashlyticsAnalyticsListener implements AnalyticsConnectorListener {

  static final String EVENT_ORIGIN_KEY = "_o";
  static final String EVENT_NAME_KEY = "name";
  static final String EVENT_PARAMS_KEY = "params";
  static final String CRASHLYTICS_ORIGIN = "clx";

  private AnalyticsEventReceiver crashlyticsOriginEventReceiver;
  private AnalyticsEventReceiver breadcrumbEventReceiver;

  public void setCrashlyticsOriginEventReceiver(@Nullable AnalyticsEventReceiver receiver) {
    this.crashlyticsOriginEventReceiver = receiver;
  }

  public void setBreadcrumbEventReceiver(@Nullable AnalyticsEventReceiver receiver) {
    this.breadcrumbEventReceiver = receiver;
  }

  @Override
  public void onMessageTriggered(int id, @Nullable Bundle extras) {
    Logger.getLogger().d("Received Analytics message: " + id + " " + extras);

    if (extras == null) {
      return;
    }

    final String name = extras.getString(EVENT_NAME_KEY);

    if (name != null) {
      Bundle params = extras.getBundle(EVENT_PARAMS_KEY);
      if (params == null) {
        params = new Bundle();
      }

      notifyEventReceivers(name, params);
    }
  }

  private void notifyEventReceivers(@NonNull String name, @NonNull Bundle params) {
    final String origin = params.getString(EVENT_ORIGIN_KEY);
    final AnalyticsEventReceiver receiver =
        CRASHLYTICS_ORIGIN.equals(origin)
            ? this.crashlyticsOriginEventReceiver
            : this.breadcrumbEventReceiver;
    notifyEventReceiver(receiver, name, params);
  }

  private static void notifyEventReceiver(
      @Nullable AnalyticsEventReceiver receiver, @NonNull String name, @NonNull Bundle params) {
    if (receiver == null) {
      return;
    }
    receiver.onEvent(name, params);
  }
}
