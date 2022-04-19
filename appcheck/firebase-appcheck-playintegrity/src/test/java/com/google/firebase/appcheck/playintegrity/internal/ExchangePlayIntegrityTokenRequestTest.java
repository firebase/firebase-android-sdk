// Copyright 2022 Google LLC
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

package com.google.firebase.appcheck.playintegrity.internal;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link ExchangePlayIntegrityTokenRequest}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ExchangePlayIntegrityTokenRequestTest {
  private static final String PLAY_INTEGRITY_TOKEN = "playIntegrityToken";

  @Test
  public void toJsonString_expectSerialized() throws Exception {
    ExchangePlayIntegrityTokenRequest exchangePlayIntegrityTokenRequest =
        new ExchangePlayIntegrityTokenRequest(PLAY_INTEGRITY_TOKEN);

    String jsonString = exchangePlayIntegrityTokenRequest.toJsonString();
    JSONObject jsonObject = new JSONObject(jsonString);

    assertThat(jsonObject.getString(ExchangePlayIntegrityTokenRequest.PLAY_INTEGRITY_TOKEN_KEY))
        .isEqualTo(PLAY_INTEGRITY_TOKEN);
  }
}
