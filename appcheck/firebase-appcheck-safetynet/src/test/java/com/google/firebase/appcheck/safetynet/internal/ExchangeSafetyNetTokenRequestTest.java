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

package com.google.firebase.appcheck.safetynet.internal;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link ExchangeSafetyNetTokenRequest}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class ExchangeSafetyNetTokenRequestTest {
  private static final String SAFETY_NET_TOKEN = "safetyNetToken";

  @Test
  public void toJsonString_expectSerialized() throws Exception {
    ExchangeSafetyNetTokenRequest exchangeSafetyNetTokenRequest =
        new ExchangeSafetyNetTokenRequest(SAFETY_NET_TOKEN);

    String jsonString = exchangeSafetyNetTokenRequest.toJsonString();
    JSONObject jsonObject = new JSONObject(jsonString);

    assertThat(jsonObject.getString(ExchangeSafetyNetTokenRequest.SAFETY_NET_TOKEN_KEY))
        .isEqualTo(SAFETY_NET_TOKEN);
  }
}
