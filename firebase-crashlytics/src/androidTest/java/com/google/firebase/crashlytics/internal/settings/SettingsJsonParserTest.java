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

  public void testSettingsV3Parsing() throws Exception {
    final JSONObject testJson = getTestJSON("firebase_settings.json");

    Settings settings = settingsJsonParser.parseSettingsJson(testJson);
    Assert.assertEquals(3, settings.settingsVersion);
    Assert.assertEquals(7200, settings.cacheDuration);
    Assert.assertTrue(settings.featureFlagData.collectReports);
  }

  private JSONObject getTestJSON(String fileName) throws IOException, JSONException {
    final InputStream jsonInputStream = getContext().getResources().getAssets().open(fileName);
    final String testJsonString = CommonUtils.streamToString(jsonInputStream);
    final JSONObject testJson = new JSONObject(testJsonString);
    return testJson;
  }
}
