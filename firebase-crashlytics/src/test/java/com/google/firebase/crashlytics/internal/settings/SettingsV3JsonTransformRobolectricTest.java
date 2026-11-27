// Copyright 2026 Google LLC
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

package com.google.firebase.crashlytics.internal.settings;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import androidx.test.core.app.ApplicationProvider;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.persistence.FileStore;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class SettingsV3JsonTransformRobolectricTest {
  private static final String RATE_KEY = SettingsJsonConstants.ON_DEMAND_UPLOAD_RATE_PER_MINUTE_KEY;
  private static final String BASE_KEY = SettingsJsonConstants.ON_DEMAND_BACKOFF_BASE_KEY;
  private static final String STEP_KEY =
      SettingsJsonConstants.ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_KEY;

  private static final double DEFAULT_RATE =
      SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_DEFAULT;
  private static final double MAX_RATE =
      SettingsJsonConstants.SETTINGS_ON_DEMAND_UPLOAD_RATE_PER_MINUTE_MAX;
  private static final double DEFAULT_BASE =
      SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_BASE_DEFAULT;
  private static final int DEFAULT_STEP =
      SettingsJsonConstants.SETTINGS_ON_DEMAND_BACKOFF_STEP_DURATION_SECONDS_DEFAULT;

  private final CurrentTimeProvider currentTimeProvider = () -> 10L;
  private final SettingsJsonTransform transform = new SettingsV3JsonTransform();

  @Test
  public void validOnDemandValues_arePreserved() throws JSONException {
    Settings settings = parse(settingsJson().put(RATE_KEY, 15).put(BASE_KEY, 3.0).put(STEP_KEY, 9));

    assertThat(settings.onDemandUploadRatePerMinute).isEqualTo(15.0);
    assertThat(settings.onDemandBackoffBase).isEqualTo(3.0);
    assertThat(settings.onDemandBackoffStepDurationSeconds).isEqualTo(9);
  }

  @Test
  public void missingOnDemandValues_useDefaults() throws JSONException {
    Settings settings = parse(settingsJson());

    assertThat(settings.onDemandUploadRatePerMinute).isEqualTo(DEFAULT_RATE);
    assertThat(settings.onDemandBackoffBase).isEqualTo(DEFAULT_BASE);
    assertThat(settings.onDemandBackoffStepDurationSeconds).isEqualTo(DEFAULT_STEP);
  }

  @Test
  public void hugeUploadRate_isClampedToMax() throws JSONException {
    for (Object rate : new Object[] {2147483647, 1e9}) {
      assertWithMessage("rate %s", rate)
          .that(parseWith(RATE_KEY, rate).onDemandUploadRatePerMinute)
          .isEqualTo(MAX_RATE);
    }
  }

  @Test
  public void zeroAndFractionalUploadRates_arePreserved() throws JSONException {
    assertThat(parseWith(RATE_KEY, 0).onDemandUploadRatePerMinute).isEqualTo(0.0);
    assertThat(parseWith(RATE_KEY, 0.5).onDemandUploadRatePerMinute).isEqualTo(0.5);
  }

  @Test
  public void invalidUploadRate_fallsBackToDefault() throws JSONException {
    // optDouble also parses strings, so "NaN" and "Infinity" are read as those doubles.
    for (Object rate : new Object[] {-1, "NaN", "Infinity", "-Infinity", "fast"}) {
      assertWithMessage("rate %s", rate)
          .that(parseWith(RATE_KEY, rate).onDemandUploadRatePerMinute)
          .isEqualTo(DEFAULT_RATE);
    }
  }

  @Test
  public void invalidBackoffBase_fallsBackToDefault() throws JSONException {
    for (Object base : new Object[] {-1, "NaN", "Infinity", "-Infinity"}) {
      assertWithMessage("base %s", base)
          .that(parseWith(BASE_KEY, base).onDemandBackoffBase)
          .isEqualTo(DEFAULT_BASE);
    }
  }

  @Test
  public void nonPositiveBackoffStepDuration_fallsBackToDefault() throws JSONException {
    for (Object stepDuration : new Object[] {0, -5}) {
      assertWithMessage("step duration %s", stepDuration)
          .that(parseWith(STEP_KEY, stepDuration).onDemandBackoffStepDurationSeconds)
          .isEqualTo(DEFAULT_STEP);
    }
  }

  @Test
  public void cachedSettingsWithInvalidValues_areValidatedWhenLoaded() throws JSONException {
    // The raw settings JSON from the server is cached and parsed again at every launch, so invalid
    // values in the cache, including ones written by older SDK versions, are validated on load.
    CachedSettingsIo cachedSettingsIo =
        new CachedSettingsIo(new FileStore(ApplicationProvider.getApplicationContext()));
    cachedSettingsIo.writeCachedSettings(
        /* expiresAtMillis= */ 0,
        settingsJson().put(RATE_KEY, 2147483647).put(BASE_KEY, "NaN").put(STEP_KEY, 0));

    Settings settings =
        new SettingsJsonParser(currentTimeProvider)
            .parseSettingsJson(cachedSettingsIo.readCachedSettings());

    assertThat(settings.onDemandUploadRatePerMinute).isEqualTo(MAX_RATE);
    assertThat(settings.onDemandBackoffBase).isEqualTo(DEFAULT_BASE);
    assertThat(settings.onDemandBackoffStepDurationSeconds).isEqualTo(DEFAULT_STEP);
  }

  private Settings parse(JSONObject json) throws JSONException {
    return transform.buildFromJson(currentTimeProvider, json);
  }

  private Settings parseWith(String key, Object value) throws JSONException {
    return parse(settingsJson().put(key, value));
  }

  /** Returns minimal settings JSON, as sent by the server, without any on-demand values. */
  private static JSONObject settingsJson() throws JSONException {
    return new JSONObject()
        .put(SettingsJsonConstants.SETTINGS_VERSION, 3)
        .put(SettingsJsonConstants.CACHE_DURATION_KEY, 86400)
        .put(SettingsJsonConstants.FEATURES_KEY, new JSONObject());
  }
}
