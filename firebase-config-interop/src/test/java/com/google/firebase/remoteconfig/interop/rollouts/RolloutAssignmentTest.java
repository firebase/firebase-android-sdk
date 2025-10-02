/*
 * Copyright 2023 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 *
 * You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.remoteconfig.interop.rollouts;

import static com.google.common.truth.Truth.assertThat;

import org.json.JSONException;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class RolloutAssignmentTest {
  private static final String ROLLOUT_ASSIGNMENT_JSON_1 =
      "{"
          + "\"rolloutId\":\"rollout_1\","
          + "\"variantId\":\"control\","
          + "\"parameterKey\":\"my_feature\","
          + "\"parameterValue\":\"false\","
          + "\"templateVersion\":1"
          + "}";

  private static final RolloutAssignment ROLLOUT_ASSIGNMENT_1 =
      RolloutAssignment.builder()
          .setRolloutId("rollout_1")
          .setVariantId("control")
          .setParameterKey("my_feature")
          .setParameterValue("false")
          .setTemplateVersion(1L)
          .build();

  @Test
  public void create_fromJsonString_parsesValidJson() throws JSONException {
    RolloutAssignment actual = RolloutAssignment.create(ROLLOUT_ASSIGNMENT_JSON_1);

    assertThat(actual).isEqualTo(ROLLOUT_ASSIGNMENT_1);
  }

  @Test
  public void encoder_encodesRolloutAssignment_toJson() throws JSONException {
    String actual = RolloutAssignment.ROLLOUT_ASSIGNMENT_JSON_ENCODER.encode(ROLLOUT_ASSIGNMENT_1);

    assertThat(actual).isEqualTo(ROLLOUT_ASSIGNMENT_JSON_1);
  }
}
