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
import static org.junit.Assert.assertThrows;

import com.google.firebase.FirebaseException;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link GeneratePlayIntegrityChallengeResponse}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class GeneratePlayIntegrityChallengeResponseTest {
  private static final String CHALLENGE = "testChallenge";
  private static final String TIME_TO_LIVE = "3600s";

  @Test
  public void fromJsonString_expectDeserialized() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(GeneratePlayIntegrityChallengeResponse.CHALLENGE_KEY, CHALLENGE);
    jsonObject.put(GeneratePlayIntegrityChallengeResponse.TIME_TO_LIVE_KEY, TIME_TO_LIVE);

    GeneratePlayIntegrityChallengeResponse generatePlayIntegrityChallengeResponse =
        GeneratePlayIntegrityChallengeResponse.fromJsonString(jsonObject.toString());
    assertThat(generatePlayIntegrityChallengeResponse.getChallenge()).isEqualTo(CHALLENGE);
    assertThat(generatePlayIntegrityChallengeResponse.getTimeToLive()).isEqualTo(TIME_TO_LIVE);
  }

  @Test
  public void fromJsonString_nullChallenge_throwsException() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(GeneratePlayIntegrityChallengeResponse.TIME_TO_LIVE_KEY, TIME_TO_LIVE);

    assertThrows(
        FirebaseException.class,
        () -> GeneratePlayIntegrityChallengeResponse.fromJsonString(jsonObject.toString()));
  }

  @Test
  public void fromJsonString_nullTimeToLive_throwsException() throws Exception {
    JSONObject jsonObject = new JSONObject();
    jsonObject.put(GeneratePlayIntegrityChallengeResponse.CHALLENGE_KEY, CHALLENGE);

    assertThrows(
        FirebaseException.class,
        () -> GeneratePlayIntegrityChallengeResponse.fromJsonString(jsonObject.toString()));
  }
}
