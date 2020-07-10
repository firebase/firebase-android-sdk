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
import com.google.firebase.crashlytics.internal.Logger;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbHandler;
import com.google.firebase.crashlytics.internal.breadcrumbs.BreadcrumbSource;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * AnalyticsReceiver which serializes Firebase Analytics events into Crashlytics breadcrumbs, then
 * passes them to a BreadcrumbHandler.
 */
public class BreadcrumbAnalyticsEventReceiver implements AnalyticsEventReceiver, BreadcrumbSource {

  private static final String BREADCRUMB_NAME_KEY = "name";
  private static final String BREADCRUMB_PARAMS_KEY = "parameters";
  private static final String BREADCRUMB_PREFIX = "$A$:";

  @Nullable private BreadcrumbHandler breadcrumbHandler;

  @Override
  public void onEvent(@NonNull String name, @NonNull Bundle params) {
    final BreadcrumbHandler receiver = breadcrumbHandler;
    if (receiver != null) {
      try {
        final String serializedEvent = BREADCRUMB_PREFIX + serializeEvent(name, params);
        receiver.handleBreadcrumb(serializedEvent);
      } catch (JSONException e) {
        Logger.getLogger().w("Unable to serialize Firebase Analytics event to breadcrumb.");
      }
    }
  }

  @Override
  public void registerBreadcrumbHandler(@Nullable BreadcrumbHandler breadcrumbHandler) {
    this.breadcrumbHandler = breadcrumbHandler;
    Logger.getLogger().d("Registered Firebase Analytics event receiver for breadcrumbs");
  }

  @NonNull
  private static String serializeEvent(@NonNull String name, @NonNull Bundle params)
      throws JSONException {

    final JSONObject enclosingObject = new JSONObject();
    final JSONObject paramsObject = new JSONObject();

    for (String key : params.keySet()) {
      paramsObject.put(key, params.get(key));
    }

    enclosingObject.put(BREADCRUMB_NAME_KEY, name);
    enclosingObject.put(BREADCRUMB_PARAMS_KEY, paramsObject);

    return enclosingObject.toString();
  }
}
