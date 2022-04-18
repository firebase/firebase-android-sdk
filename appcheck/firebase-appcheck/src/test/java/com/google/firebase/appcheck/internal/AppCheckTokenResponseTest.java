// Copyright 2020 Google LLC
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

package com.google.firebase.appcheck.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.assertThrows;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link AppCheckTokenResponse}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AppCheckTokenResponseTest {

  private static final String APP_CHECK_TOKEN = "appCheckToken";
  private static final String TIME_TO_LIVE = "3600s";

  @Test
  public void fromJsonString_expectDeserialized() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(AppCheckTokenResponse.TOKEN_KEY, APP_CHECK_TOKEN);
    jsonObject.put(AppCheckTokenResponse.TIME_TO_LIVE_KEY, TIME_TO_LIVE);

    AppCheckTokenResponse appCheckTokenResponse =
        AppCheckTokenResponse.fromJsonString(jsonObject.toString());
    assertThat(appCheckTokenResponse.getToken()).isEqualTo(APP_CHECK_TOKEN);
    assertThat(appCheckTokenResponse.getTimeToLive()).isEqualTo(TIME_TO_LIVE);
  }

  @Test
  public void fromJsonString_nullToken_throwsException() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(AppCheckTokenResponse.TIME_TO_LIVE_KEY, TIME_TO_LIVE);

    assertThrows(
        NullPointerException.class,
        () -> AppCheckTokenResponse.fromJsonString(jsonObject.toString()));
  }

  @Test
  public void fromJsonString_nullTimeToLive_throwsException() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(AppCheckTokenResponse.TOKEN_KEY, APP_CHECK_TOKEN);

    assertThrows(
        NullPointerException.class,
        () -> AppCheckTokenResponse.fromJsonString(jsonObject.toString()));
  }
}
