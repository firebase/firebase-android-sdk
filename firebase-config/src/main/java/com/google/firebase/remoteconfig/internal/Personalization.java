// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.remoteconfig.internal;

import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import org.json.JSONObject;

public class Personalization {
  public static final String ANALYTICS_ORIGIN_PERSONALIZATION = "fp";
  public static final String ANALYTICS_PULL_EVENT = "_fpc";
  public static final String ARM_KEY = "_fpid";
  public static final String ARM_VALUE = "_fpct";
  static final String PERSONALIZATION_ID = "personalizationId";

  /** The app's Firebase Analytics client. */
  private final AnalyticsConnector analyticsConnector;

  /** Creates an instance of {@code Personalization}. */
  public Personalization(@NonNull AnalyticsConnector analyticsConnector) {
    this.analyticsConnector = analyticsConnector;
  }

  /**
   * Called when a Personalization parameter value (an arm) is retrieved, and uses Google Analytics
   * for Firebase to log metadata if it's a Personalization parameter.
   *
   * @param key Remote Config parameter
   * @param configContainer {@link ConfigContainer} containing Personalization metadata for {@code
   *     key}
   */
  public void logArmActive(@NonNull String key, @NonNull ConfigContainer configContainer) {
    JSONObject ids = configContainer.getPersonalizationMetadata();
    if (ids.length() < 1) {
      return;
    }

    JSONObject values = configContainer.getConfigs();
    if (values.length() < 1) {
      return;
    }

    JSONObject metadata = ids.optJSONObject(key);
    if (metadata == null) {
      return;
    }

    Bundle params = new Bundle();
    params.putString(ARM_KEY, metadata.optString(PERSONALIZATION_ID));
    params.putString(ARM_VALUE, values.optString(key));
    analyticsConnector.logEvent(ANALYTICS_ORIGIN_PERSONALIZATION, ANALYTICS_PULL_EVENT, params);
  }
}
