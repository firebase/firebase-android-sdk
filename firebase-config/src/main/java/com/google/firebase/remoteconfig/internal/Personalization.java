package com.google.firebase.remoteconfig.internal;

import static com.google.firebase.remoteconfig.internal.ConfigContainer.CONFIGS_KEY;
import static com.google.firebase.remoteconfig.internal.ConfigContainer.PERSONALIZATION_METADATA_KEY;

import android.os.Bundle;
import androidx.annotation.NonNull;
import com.google.firebase.analytics.connector.AnalyticsConnector;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigException;
import org.json.JSONArray;
import org.json.JSONObject;

public class Personalization {
  static final String ANALYTICS_ORIGIN_PERSONALIZATION = "fp";
  static final String ANALYTICS_PULL_EVENT = "_fpc";
  static final String ARM_KEY = "_fpid";
  static final String ARM_VALUE = "_fpct";
  static final String PARAMETER_KEY = "parameterKey";
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
    JSONArray ids = configContainer.optJSONArray(PERSONALIZATION_METADATA_KEY);
    JSONObject values = configContainer.optJSONObject(CONFIGS_KEY);
    if (ids == null || values == null) {
      return;
    }

    for (int i = 0; i < ids.length(); i++) {
      JSONObject item = ids.optJSONObject(i);
      if (item == null) {
        continue;
      }

      if (key.equals(item.optString(PARAMETER_KEY))) {
        Bundle params = new Bundle();
        params.putString(ARM_KEY, item.optString(PERSONALIZATION_ID));
        params.putString(ARM_VALUE, values.optString(key));
        analyticsConnector.logEvent(ANALYTICS_ORIGIN_PERSONALIZATION, ANALYTICS_PULL_EVENT, params);
        return;
      }
    }
  }
}
