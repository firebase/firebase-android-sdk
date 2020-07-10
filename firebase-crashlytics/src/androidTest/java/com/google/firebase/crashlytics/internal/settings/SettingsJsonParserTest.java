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
import static org.mockito.Mockito.when;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.crashlytics.internal.common.CommonUtils;
import com.google.firebase.crashlytics.internal.common.CurrentTimeProvider;
import com.google.firebase.crashlytics.internal.settings.model.SettingsData;
import java.io.IOException;
import java.io.InputStream;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;

public class SettingsJsonParserTest extends CrashlyticsTestCase {

  private SettingsJsonParser settingsJsonParser;
  private CurrentTimeProvider mockCurrentTimeProvider;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    mockCurrentTimeProvider = mock(CurrentTimeProvider.class);
    when(mockCurrentTimeProvider.getCurrentTimeMillis()).thenReturn(Long.valueOf(10));

    settingsJsonParser = new SettingsJsonParser(mockCurrentTimeProvider);
  }

  public void testSettingsV2Parsing() throws Exception {
    final JSONObject testJson = getTestJSON("default_settings.json");

    final SettingsData settingsData = settingsJsonParser.parseSettingsJson(testJson);

    Assert.assertEquals(
        "http://localhost:3000/spi/v1/platform/android/apps", settingsData.appData.url);
    Assert.assertNull(settingsData.appData.organizationId);
    Assert.assertNull(settingsData.appData.bundleId);
  }

  public void testSettingsV3Parsing() throws Exception {
    final JSONObject testJson = getTestJSON("firebase_settings.json");

    final SettingsData settingsData = settingsJsonParser.parseSettingsJson(testJson);

    Assert.assertEquals(
        "https://update.crashlytics.com/spi/v1/platforms/android/apps/com.google.firebase.crashlytics.sdk.test",
        settingsData.appData.url);
    Assert.assertEquals("12345abcde12345abcde1234", settingsData.appData.organizationId);
    Assert.assertEquals("com.google.firebase.crashlytics.sdk.test", settingsData.appData.bundleId);
  }

  private JSONObject getTestJSON(String fileName) throws IOException, JSONException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    final String testJsonString = CommonUtils.streamToString(jsonInputStream);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }
}
