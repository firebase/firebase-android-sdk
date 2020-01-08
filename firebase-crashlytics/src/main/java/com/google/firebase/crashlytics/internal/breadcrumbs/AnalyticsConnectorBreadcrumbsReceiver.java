// Copyright 2019 Google LLC
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

package com.google.firebase.crashlytics.internal.breadcrumbs;

import android.os.Bundle;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.crashlytics.internal.Logger;
import org.json.JSONException;
import org.json.JSONObject;

public class AnalyticsConnectorBreadcrumbsReceiver
    implements AnalyticsConnectorListener, BreadcrumbsReceiver {

  public interface BreadcrumbHandler {
    void dropBreadcrumb(String breadcrumb);
  }

  static final String CRASH_ORIGIN = "crash";
  private static final String EVENT_NAME_KEY = "name";
  private static final String EVENT_PARAMS_KEY = "params";
  private static final String BREADCRUMB_PARAMS_KEY = "parameters";
  private static final String BREADCRUMB_PREFIX = "$A$:";

  private final AnalyticsConnector analyticsConnector;
  private final BreadcrumbHandler breadcrumbHandler;

  private AnalyticsConnectorHandle analyticsConnectorHandle;

  public AnalyticsConnectorBreadcrumbsReceiver(
      AnalyticsConnector analyticsConnector, BreadcrumbHandler breadcrumbHandler) {
    this.analyticsConnector = analyticsConnector;
    this.breadcrumbHandler = breadcrumbHandler;
  }

  @Override
  public boolean register() {
    if (analyticsConnector == null) {
      // This is a valid state, if the app does not have Firebase Analytics, so we'll log
      // at the DEBUG level. The rest of the logging below uses WARN because they are
      // unexpected states that will prevent the breadcrumbs feature from working.
      Logger.getLogger()
          .d(
              Logger.TAG,
              "Firebase Analytics is not present; you will not see automatic logging of "
                  + "events before a crash occurs.");
      return false;
    }

    analyticsConnectorHandle =
        analyticsConnector.registerAnalyticsConnectorListener(CRASH_ORIGIN, this);

    return analyticsConnectorHandle != null;
  }

  @Override
  public void unregister() {
    if (analyticsConnectorHandle != null) {
      analyticsConnectorHandle.unregister();
    }
  }

  @Override
  public void onMessageTriggered(int id, @Nullable Bundle extras) {
    if (extras == null) {
      return;
    }

    final String name = extras.getString(EVENT_NAME_KEY);
    if (name == null) {
      return;
    }

    Bundle params = extras.getBundle(EVENT_PARAMS_KEY);
    if (params == null) {
      params = new Bundle();
    }

    try {
      final String serializedEvent = BREADCRUMB_PREFIX + serializeEvent(name, params);
      breadcrumbHandler.dropBreadcrumb(serializedEvent);
    } catch (JSONException e) {
      Logger.getLogger().w(Logger.TAG, "Unable to serialize Firebase Analytics event.");
    }
  }

  private static String serializeEvent(@NonNull String name, @NonNull Bundle params)
      throws JSONException {

    final JSONObject enclosingObject = new JSONObject();
    final JSONObject paramsObject = new JSONObject();

    for (String key : params.keySet()) {
      paramsObject.put(key, params.get(key));
    }

    enclosingObject.put(EVENT_NAME_KEY, name);
    enclosingObject.put(BREADCRUMB_PARAMS_KEY, paramsObject);

    return enclosingObject.toString();
  }
}
