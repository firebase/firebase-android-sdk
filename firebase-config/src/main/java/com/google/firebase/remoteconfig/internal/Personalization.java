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
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;

public class Personalization {
  public static final String ANALYTICS_ORIGIN_PERSONALIZATION = "fp";

  // Constants with LOG_KEY suffix are how the corresponding ones without it are identified on
  // Google Analytics.
  public static final String ANALYTICS_PULL_EVENT = "personalization_assignment";
  public static final String ARM_KEY = "arm_key";
  public static final String ARM_VALUE = "arm_value";
  public static final String PERSONALIZATION_ID = "personalizationId";
  public static final String PERSONALIZATION_ID_LOG_KEY = "personalization_id";
  public static final String ARM_INDEX = "armIndex";
  public static final String ARM_INDEX_LOG_KEY = "arm_index";
  public static final String GROUP = "group";

  public static final String ANALYTICS_PULL_EVENT_INTERNAL = "_fpc";
  public static final String CHOICE_ID = "choiceId";
  public static final String CHOICE_ID_LOG_KEY = "_fpid";

  /** The app's Firebase Analytics client. */
  private final AnalyticsConnector analyticsConnector;

  /** A map of Remote Config parameter key to choice ID. */
  private final Map<String, String> armsCache =
      Collections.synchronizedMap(new HashMap<String, String>());

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

    String choiceId = metadata.optString(CHOICE_ID);
    if (choiceId.isEmpty()) {
      return;
    }

    synchronized (armsCache) {
      if (choiceId.equals(armsCache.get(key))) {
        return;
      }
      armsCache.put(key, choiceId);
    }

    Bundle logParams = new Bundle();
    logParams.putString(ARM_KEY, key);
    logParams.putString(ARM_VALUE, values.optString(key));
    logParams.putString(PERSONALIZATION_ID_LOG_KEY, metadata.optString(PERSONALIZATION_ID));
    logParams.putInt(ARM_INDEX_LOG_KEY, metadata.optInt(ARM_INDEX, -1));
    logParams.putString(GROUP, metadata.optString(GROUP));
    analyticsConnector.logEvent(ANALYTICS_ORIGIN_PERSONALIZATION, ANALYTICS_PULL_EVENT, logParams);

    Bundle internalLogParams = new Bundle();
    internalLogParams.putString(CHOICE_ID_LOG_KEY, choiceId);
    analyticsConnector.logEvent(
        ANALYTICS_ORIGIN_PERSONALIZATION, ANALYTICS_PULL_EVENT_INTERNAL, internalLogParams);
  }
}
