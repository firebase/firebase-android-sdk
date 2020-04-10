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
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorHandle;
import com.google.firebase.analytics.connector.AnalyticsConnector.AnalyticsConnectorListener;
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.analytics.AnalyticsConnectorBridge.BreadcrumbHandler;
import org.json.JSONException;
import org.json.JSONObject;

public class AnalyticsListener implements AnalyticsConnectorListener {
  private static final String CRASHLYTICS_ORIGIN = "clx";
  private static final String LEGACY_CRASH_ORIGIN = "crash";

  private static final String EVENT_NAME_KEY = "name";
  private static final String APP_EXCEPTION_EVENT_NAME = "_ae";
  private static final String EVENT_ORIGIN_KEY = "_o";
  private static final String EVENT_PARAMS_KEY = "params";

  private static final String BREADCRUMB_PARAMS_KEY = "parameters";
  private static final String BREADCRUMB_PREFIX = "$A$:";

  public static AnalyticsConnectorHandle subscribeToAnalyticsEvents(@NonNull AnalyticsConnector analyticsConnector, @NonNull AnalyticsListener listener) {
    AnalyticsConnectorHandle handle =
        analyticsConnector.registerAnalyticsConnectorListener(CRASHLYTICS_ORIGIN, listener);

    if (handle == null) {
      Logger.getLogger()
          .d("Could not register AnalyticsConnectorListener with Crashlytics origin.");
      // Older versions of FA don't support CRASHLYTICS_ORIGIN. We can try using the old Firebase
      // Crash Reporting origin
      handle = analyticsConnector.registerAnalyticsConnectorListener(LEGACY_CRASH_ORIGIN, listener);

      // If FA allows us to connect with the legacy origin, but not the new one, nudge customers
      // to update their FA version.
      if (handle != null) {
        Logger.getLogger()
            .w(
                "A new version of the Google Analytics for Firebase SDK is now available. "
                    + "For improved performance and compatibility with Crashlytics, please "
                    + "update to the latest version.");
      }
    }

    return handle;
  }

  @Nullable
  private AppExceptionEventTaskHandler appExceptionEventTaskHandler;

  @Nullable
  private BreadcrumbHandler breadcrumbHandler;

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
      final String origin = params.getString(EVENT_ORIGIN_KEY);
      if (CRASHLYTICS_ORIGIN.equals(origin)) {
        if (APP_EXCEPTION_EVENT_NAME.equals(name) && appExceptionEventTaskHandler != null) {
          appExceptionEventTaskHandler.handleRecordedAppExceptionEvent();
        }
      } else {
        // Place breadcrumbs for all named events which did not originate from Crashlytics
        dispatchBreadcrumbEvent(name, params);
      }
    }
  }

  public void setBreadcrumbHandler(@Nullable BreadcrumbHandler breadcrumbHandler) {
    this.breadcrumbHandler = breadcrumbHandler;
  }

  public void setAppExceptionEventTaskHandler(@Nullable AppExceptionEventTaskHandler appExceptionEventTaskHandler) {
    this.appExceptionEventTaskHandler = appExceptionEventTaskHandler;
  }

  private void dispatchBreadcrumbEvent(@NonNull String name, @NonNull Bundle params) {
    if (breadcrumbHandler != null) {
      try {
        final String serializedEvent = BREADCRUMB_PREFIX + serializeEvent(name, params);
        breadcrumbHandler.dropBreadcrumb(serializedEvent);
      } catch (JSONException e) {
        Logger.getLogger().w("Unable to serialize Firebase Analytics event.");
      }
    }
  }

  @NonNull
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
