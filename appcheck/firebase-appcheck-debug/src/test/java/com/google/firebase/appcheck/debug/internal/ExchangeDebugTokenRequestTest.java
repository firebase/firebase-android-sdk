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

package com.google.firebase.appcheck.debug.internal;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

/** Tests for {@link ExchangeDebugTokenRequest}. */
@RunWith(RobolectricTestRunner.class)
public class ExchangeDebugTokenRequestTest {
  private static final String DEBUG_TOKEN = "debugToken";

  @Test
  public void toJsonString_expectSerialized() throws Exception {
    ExchangeDebugTokenRequest exchangeDebugTokenRequest =
        new ExchangeDebugTokenRequest(DEBUG_TOKEN);

    String jsonString = exchangeDebugTokenRequest.toJsonString();
    JSONObject jsonObject = new JSONObject(jsonString);

    assertThat(jsonObject.getString(ExchangeDebugTokenRequest.DEBUG_TOKEN_KEY))
        .isEqualTo(DEBUG_TOKEN);
  }
}
