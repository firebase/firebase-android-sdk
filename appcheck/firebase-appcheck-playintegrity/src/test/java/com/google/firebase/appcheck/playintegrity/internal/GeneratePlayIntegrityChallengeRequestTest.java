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

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Tests for {@link GeneratePlayIntegrityChallengeRequest}. */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class GeneratePlayIntegrityChallengeRequestTest {
  private static final String EMPTY_JSON = "{}";

  @Test
  public void toJsonString_expectSerialized() throws Exception {
    GeneratePlayIntegrityChallengeRequest generatePlayIntegrityChallengeRequest =
        new GeneratePlayIntegrityChallengeRequest();

    assertThat(generatePlayIntegrityChallengeRequest.toJsonString()).isEqualTo(EMPTY_JSON);
  }
}
