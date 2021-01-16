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

  // Constants with PARAM suffix are how the corresponding ones without it are identified on
  // Google Analytics.
  public static final String EXTERNAL_EVENT = "personalization_assignment";
  public static final String EXTERNAL_RC_PARAMETER_PARAM = "arm_key";
  public static final String EXTERNAL_ARM_VALUE_PARAM = "arm_value";
  public static final String PERSONALIZATION_ID = "personalizationId";
  public static final String EXTERNAL_PERSONALIZATION_ID_PARAM = "personalization_id";
  public static final String ARM_INDEX = "armIndex";
  public static final String EXTERNAL_ARM_INDEX_PARAM = "arm_index";
  public static final String GROUP = "group";
  public static final String EXTERNAL_GROUP_PARAM = "group";

  public static final String INTERNAL_EVENT = "_fpc";
  public static final String CHOICE_ID = "choiceId";
  public static final String INTERNAL_CHOICE_ID_PARAM = "_fpid";

  /** The app's Firebase Analytics client. */
  private final AnalyticsConnector analyticsConnector;

  /** A map of Remote Config parameter key to choice ID. */
  private final Map<String, String> loggedChoiceIds =
      Collections.synchronizedMap(new HashMap<String, String>());

  /** Creates an instance of {@code Personalization}. */
  public Personalization(@NonNull AnalyticsConnector analyticsConnector) {
    this.analyticsConnector = analyticsConnector;
  }

  /**
   * Called when a Personalization parameter value (an arm) is retrieved, and uses Google Analytics
   * for Firebase to log metadata if it's a Personalization parameter.
   *
   * @param rcParameter Remote Config parameter
   * @param configContainer {@link ConfigContainer} containing Personalization metadata for {@code
   *     key}
   */
  public void logArmActive(@NonNull String rcParameter, @NonNull ConfigContainer configContainer) {
    JSONObject ids = configContainer.getPersonalizationMetadata();
    if (ids.length() < 1) {
      return;
    }

    JSONObject values = configContainer.getConfigs();
    if (values.length() < 1) {
      return;
    }

    JSONObject metadata = ids.optJSONObject(rcParameter);
    if (metadata == null) {
      return;
    }

    String choiceId = metadata.optString(CHOICE_ID);
    if (choiceId.isEmpty()) {
      return;
    }

    // We only to need to log each choice ID once, so this attempts to prevent too much unnecessary
    // logging.
    synchronized (loggedChoiceIds) {
      if (choiceId.equals(loggedChoiceIds.get(rcParameter))) {
        return;
      }
      loggedChoiceIds.put(rcParameter, choiceId);
    }

    Bundle logParams = new Bundle();
    logParams.putString(EXTERNAL_RC_PARAMETER_PARAM, rcParameter);
    logParams.putString(EXTERNAL_ARM_VALUE_PARAM, values.optString(rcParameter));
    logParams.putString(EXTERNAL_PERSONALIZATION_ID_PARAM, metadata.optString(PERSONALIZATION_ID));
    logParams.putInt(EXTERNAL_ARM_INDEX_PARAM, metadata.optInt(ARM_INDEX, -1));
    logParams.putString(EXTERNAL_GROUP_PARAM, metadata.optString(GROUP));
    analyticsConnector.logEvent(ANALYTICS_ORIGIN_PERSONALIZATION, EXTERNAL_EVENT, logParams);

    Bundle internalLogParams = new Bundle();
    internalLogParams.putString(INTERNAL_CHOICE_ID_PARAM, choiceId);
    analyticsConnector.logEvent(
        ANALYTICS_ORIGIN_PERSONALIZATION, INTERNAL_EVENT, internalLogParams);
  }
}
