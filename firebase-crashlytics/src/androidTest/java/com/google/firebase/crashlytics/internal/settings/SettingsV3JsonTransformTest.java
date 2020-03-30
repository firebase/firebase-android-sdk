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

public class SettingsV3JsonTransformTest extends CrashlyticsTestCase {
  CurrentTimeProvider mockCurrentTimeProvider;
  SettingsJsonTransform transform;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockCurrentTimeProvider = mock(CurrentTimeProvider.class);
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(Long.valueOf(10));

    transform = new SettingsV3JsonTransform();
  }

  public void testFirebaseSettingsTransform() throws Exception {
    final JSONObject testJson = getTestJSON("firebase_settings.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settingsData, false);
  }

  public void testFirebaseSettingsTransform_newApp() throws Exception {
    final JSONObject testJson = getTestJSON("firebase_settings_new.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settingsData, true);
  }

  public void testToJsonAndBackSurvival() throws IOException, JSONException {
    final JSONObject testJson = getTestJSON("firebase_settings.json");

    final SettingsData settingsData = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    final SettingsData roundtrippedSettingsData =
        transform.buildFromJson(mockCurrentTimeProvider, transform.toJson(settingsData));

    verifySettingsDataObject(mockCurrentTimeProvider, roundtrippedSettingsData);
  }

  private void assertAppData(AppSettingsData appData, boolean isAppNew) {
    assertEquals(isAppNew ? "new" : "activated", appData.status);
    assertEquals(
        isAppNew
            ? "https://update.crashlytics.com/spi/v1/platforms/android/apps"
            : "https://update.crashlytics.com/spi/v1/platforms/android/apps/com.google.firebase.crashlytics.sdk.test",
        appData.url);
    assertEquals(
        "https://reports.crashlytics.com/spi/v1/platforms/android/apps/com.google.firebase.crashlytics.sdk.test/reports",
        appData.reportsUrl);
    assertEquals(
        "https://reports.crashlytics.com/sdk-api/v1/platforms/android/apps/com.google.firebase.crashlytics.sdk.test/minidumps",
        appData.ndkReportsUrl);
    assertTrue(appData.updateRequired);
    assertEquals(2, appData.reportUploadVariant);
    assertEquals(2, appData.nativeReportUploadVariant);
  }

  private void assertSettingsData(SessionSettingsData settingsData) {
    assertEquals(8, settingsData.maxCustomExceptionEvents);
    assertEquals(4, settingsData.maxCompleteSessionsCount);
  }

  private void assertFeaturesData(FeaturesSettingsData featuresSettingsData) {
    assertTrue(featuresSettingsData.collectReports);
  }

  private void verifySettingsDataObject(
      CurrentTimeProvider mockCurrentTimeProvider, SettingsData settingsData) {
    verifySettingsDataObject(mockCurrentTimeProvider, settingsData, false);
  }

  private void verifySettingsDataObject(
      CurrentTimeProvider mockCurrentTimeProvider, SettingsData settingsData, boolean isAppNew) {
    assertEquals(7200010, settingsData.expiresAtMillis);

    assertEquals(3, settingsData.settingsVersion);
    assertEquals(7200, settingsData.cacheDuration);

    assertAppData(settingsData.appData, isAppNew);

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
