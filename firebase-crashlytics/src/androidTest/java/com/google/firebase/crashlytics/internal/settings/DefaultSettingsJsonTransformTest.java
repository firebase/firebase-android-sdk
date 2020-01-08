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

package com.google.firebase.crashlytics.internal.settings;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.settings.model.AppSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.FeaturesSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SessionSettingsData;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONException;
import org.json.JSONObject;

public class DefaultSettingsJsonTransformTest extends CrashlyticsTestCase {

  CurrentTimeProvider mockCurrentTimeProvider;
  SettingsJsonTransform transform;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockCurrentTimeProvider = mock(CurrentTimeProvider.class);
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(Long.valueOf(10));

    transform = new DefaultSettingsJsonTransform();
  }

  public void testLoad09xSettings() throws Exception {
    try {
      final JSONObject testJson = getTestJSON("0_9_x-settings.json");

      transform.buildFromJson(mockCurrentTimeProvider, testJson);
      fail();
    } catch (JSONException e) {
      // expected exception
    }
  }

  public void testSettingsJsonTransform() throws Exception {
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(Long.valueOf(10));

    final JSONObject testJson = getTestJSON("default_settings.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settingsData);
  }

  public void testSettingsJsonTransformWithAnalyticsDefaults() throws Exception {
    final JSONObject testJson = getTestJSON("default_settings_omitted.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settingsData);
  }

  public void testSettingsJsonTransformWithAnalyticsSampling() throws Exception {
    final JSONObject testJson = getTestJSON("settings_with_sampling.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settingsData);
  }

  public void testSettingsJsonTransform_customEventTrackingDisabled() throws Exception {
    final JSONObject testJson = getTestJSON("settings_without_custom_event_tracking.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settingsData);
  }

  public void testToJsonAndBackSurvival() throws IOException, JSONException {
    final JSONObject testJson = getTestJSON("default_settings.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    final SettingsData roundtrippedSettingsData =
        transform.buildFromJson(mockCurrentTimeProvider, transform.toJson(settingsData));

    verifySettingsDataObject(mockCurrentTimeProvider, roundtrippedSettingsData);
  }

  public void testNoIconJsonTransform() throws Exception {
    final JSONObject testJson = getTestJSON("no_icon_settings.json");
    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    assertEquals(3600010, settingsData.expiresAtMillis);
    assertAppData(settingsData.appData);
    assertSettingsData(settingsData.sessionData);
    assertFeaturesData(settingsData.featuresData);

    verify(mockCurrentTimeProvider).getCurrentTimeMillis();
  }

  public void testEmptyIconJsonTransform() throws Exception {
    final JSONObject testJson = getTestJSON("no_icon_settings.json");
    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    assertEquals(3600010, settingsData.expiresAtMillis);
    assertAppData(settingsData.appData);
    assertSettingsData(settingsData.sessionData);
    assertFeaturesData(settingsData.featuresData);

    verify(mockCurrentTimeProvider).getCurrentTimeMillis();
  }

  public void testCachedJsonTransform() throws Exception {
    final JSONObject testJson = getTestJSON("cached_settings.json");
    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    assertEquals(1234567890, settingsData.expiresAtMillis);
    assertEquals(3600, settingsData.cacheDuration);
    assertAppData(settingsData.appData);
    assertSettingsData(settingsData.sessionData);
    assertFeaturesData(settingsData.featuresData);
    verifyZeroInteractions(mockCurrentTimeProvider);
  }

  private void assertAppData(AppSettingsData appData) {
    assertEquals("activated", appData.status);
    assertEquals("http://localhost:3000/spi/v1/platform/android/apps", appData.url);
    assertEquals(
        "http://localhost:3000/spi/v1/platform/android/apps/com.crashlytics.android/reports",
        appData.reportsUrl);
  }

  private void assertSettingsData(SessionSettingsData settingsData) {
    assertEquals(64, settingsData.maxCustomExceptionEvents);
    assertEquals(4, settingsData.maxCompleteSessionsCount);
  }

  private void assertFeaturesData(FeaturesSettingsData featuresSettingsData) {
    assertTrue(featuresSettingsData.collectReports);
  }

  private void verifySettingsDataObject(
      CurrentTimeProvider mockCurrentTimeProvider, SettingsData settingsData) {
    assertEquals(3600010, settingsData.expiresAtMillis);

    assertEquals(2, settingsData.settingsVersion);
    assertEquals(3600, settingsData.cacheDuration);

    assertAppData(settingsData.appData);

    assertFeaturesData(settingsData.featuresData);

    assertSettingsData(settingsData.sessionData);

    verify(mockCurrentTimeProvider).getCurrentTimeMillis();
  }

  private JSONObject getTestJSON(String fileName) throws IOException, JSONException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    final String testJsonString = CommonUtils.streamToString(jsonInputStream);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }
}
