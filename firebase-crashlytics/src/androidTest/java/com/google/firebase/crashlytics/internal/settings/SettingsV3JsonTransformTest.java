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
    JSONObject testJson = getTestJSON("firebase_settings.json");
    Settings settings = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settings, false);
  }

  public void testFirebaseSettingsTransform_newApp() throws Exception {
    JSONObject testJson = getTestJSON("firebase_settings_new.json");
    Settings settings = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settings, true);
  }

  public void testFirebaseSettingsTransform_collectAnrs() throws Exception {
    JSONObject testJson = getTestJSON("firebase_settings_collect_anrs.json");
    Settings settings = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settings, false, true);
  }

  public void testFirebaseSettingsTransform_collectBuildIds() throws Exception {
    JSONObject testJson = getTestJSON("firebase_settings_collect_build_ids.json");
    Settings settings = transform.buildFromJson(mockCurrentTimeProvider, testJson);

    verifySettingsDataObject(mockCurrentTimeProvider, settings, false, true, true);
  }

  private void verifySettingsDataObject(
      CurrentTimeProvider mockCurrentTimeProvider, Settings settings, boolean isAppNew) {
    verifySettingsDataObject(mockCurrentTimeProvider, settings, isAppNew, false, false);
  }

  private void verifySettingsDataObject(
      CurrentTimeProvider mockCurrentTimeProvider,
      Settings settings,
      boolean isAppNew,
      boolean collectAnrs) {
    verifySettingsDataObject(mockCurrentTimeProvider, settings, isAppNew, collectAnrs, false);
  }

  private void verifySettingsDataObject(
      CurrentTimeProvider mockCurrentTimeProvider,
      Settings settings,
      boolean isAppNew,
      boolean collectAnrs,
      boolean collectBuildIds) {
    assertEquals(7200010, settings.expiresAtMillis);

    assertEquals(3, settings.settingsVersion);
    assertEquals(7200, settings.cacheDuration);

    assertEquals(8, settings.sessionData.maxCustomExceptionEvents);
    assertEquals(4, settings.sessionData.maxCompleteSessionsCount);

    assertTrue(settings.featureFlagData.collectReports);
    assertEquals(settings.featureFlagData.collectAnrs, collectAnrs);
    assertEquals(settings.featureFlagData.collectBuildIds, collectBuildIds);

    verify(mockCurrentTimeProvider).getCurrentTimeMillis();
  }

  private JSONObject getTestJSON(String fileName) throws IOException, JSONException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    final String testJsonString = CommonUtils.streamToString(jsonInputStream);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }
}
