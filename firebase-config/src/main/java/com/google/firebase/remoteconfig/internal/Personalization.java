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

import static com.google.firebase.remoteconfig.FirebaseRemoteConfig.TAG;
import static com.google.firebase.remoteconfig.internal.ConfigContainer.CONFIGS_KEY;
import static com.google.firebase.remoteconfig.internal.ConfigContainer.PERSONALIZATION_METADATA_KEY;

import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import org.json.JSONObject;

public class Personalization {
  static final String ANALYTICS_ORIGIN_PERSONALIZATION = "fp";
  static final String ANALYTICS_PULL_EVENT = "_fpc";
  static final String ARM_KEY = "_fpid";
  static final String ARM_VALUE = "_fpct";
  static final String PERSONALIZATION_ID = "personalizationId";

  /** The app's Firebase Analytics client. */
  private final AnalyticsConnector analyticsConnector;

  /** Creates an instance of {@code Personalization}. */
  public Personalization(@NonNull AnalyticsConnector analyticsConnector) {
    this.analyticsConnector = analyticsConnector;
  }
  /**
   * Called when an arm is pulled, and uses Google Analytics for Firebase to log it if it's a
   * Personalization parameter.
   *
   * @param key Remote Config parameter
   * @param configContainer JSON of {@link ConfigContainer}
   */
  public void logArmActive(@NonNull String key, @NonNull JSONObject configContainer) {
    JSONObject ids = configContainer.optJSONObject(PERSONALIZATION_METADATA_KEY);
    if (ids == null) {
      Log.w(TAG, "Missing Personalization metadata");
      return;
    }

    JSONObject values = configContainer.optJSONObject(CONFIGS_KEY);
    if (values == null) {
      Log.w(TAG, "Missing config");
      return;
    }

    JSONObject item = ids.optJSONObject(key);
    if (item == null) {
      return;
    }

    Bundle params = new Bundle();
    params.putString(ARM_KEY, item.optString(PERSONALIZATION_ID));
    params.putString(ARM_VALUE, values.optString(key));
    analyticsConnector.logEvent(ANALYTICS_ORIGIN_PERSONALIZATION, ANALYTICS_PULL_EVENT, params);
  }
}
