// Copyright 2023 Google LLC
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

package com.google.firebase.crashlytics.internal.metadata;

import static com.google.common.truth.Truth.assertThat;

import com.google.firebase.crashlytics.internal.CrashlyticsTestCase;
import com.google.firebase.encoders.DataEncoder;
import com.google.firebase.encoders.json.JsonDataEncoderBuilder;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONException;
import org.junit.Test;

public class RolloutAssignmentListTest extends CrashlyticsTestCase {
  private static final String ROLLOUTS_STATE_JSON_1 =
      "[{"
          + "\"rolloutId\":\"rollout_1\","
          + "\"variantId\":\"control\","
          + "\"parameterKey\":\"my_feature\","
          + "\"parameterValue\":\"false\","
          + "\"templateVersion\":1"
          + "}]";

  private static final String ROLLOUTS_STATE_JSON_2 =
      "["
          + "{"
          + "\"rolloutId\":\"rollout_1\","
          + "\"variantId\":\"control\","
          + "\"parameterKey\":\"my_feature\","
          + "\"parameterValue\":\"false\","
          + "\"templateVersion\":1"
          + "},"
          + "{"
          + "\"rollout_id\":\"rollout_2\""
          + "\"variantId\":\"control\","
          + "\"parameterKey\":\"color_feature\","
          + "\"parameterValue\":\"blue\","
          + "\"templateVersion\":2"
          + "}]";

  private static List<RolloutAssignment> ROLLOUTS_STATE_1;

  static {
    ROLLOUTS_STATE_1 = new ArrayList<>();
    ROLLOUTS_STATE_1.add(
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1));
  }

  private static List<RolloutAssignment> ROLLOUTS_STATE_2;

  static {
    ROLLOUTS_STATE_2 = new ArrayList<>();
    ROLLOUTS_STATE_2.add(
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1));
    ROLLOUTS_STATE_2.add(
        RolloutAssignment.create("rollout_2", "color_feature", "false", "control", 2));
  }

  private static final String ROLLOUT_ASSIGNMENT_JSON =
      "{"
          + "\"rolloutId\":\"rollout_1\","
          + "\"variantId\":\"control\","
          + "\"parameterKey\":\"my_feature\","
          + "\"parameterValue\":\"false\","
          + "\"templateVersion\":1"
          + "}";

  private static final int MAX_ENTRIES = 64;

  private final RolloutAssignmentList rolloutAssignmentList =
      new RolloutAssignmentList(MAX_ENTRIES);

  @Test
  public void testRollAssignmentInit_json() throws JSONException {
    RolloutAssignment expected =
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1);
    RolloutAssignment rolloutAssignmentInitFromJson =
        RolloutAssignment.create(ROLLOUT_ASSIGNMENT_JSON);

    assertThat(rolloutAssignmentInitFromJson).isEqualTo(expected);
  }

  @Test
  public void testRolloutAssignment_encodeJSON() {
    RolloutAssignment rolloutAssignment =
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1);
    DataEncoder encoder =
        new JsonDataEncoderBuilder().configureWith(AutoRolloutAssignmentEncoder.CONFIG).build();
    String data = encoder.encode(rolloutAssignment);

    assertThat(data).isNotEmpty();
  }

  @Test
  public void testUpdateRolloutAssignmentList() throws Exception {
    final List<String> changed = new ArrayList<String>();

    rolloutAssignmentList.updateRolloutAssignmentList(ROLLOUTS_STATE_1);

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                changed.add("changed");
                rolloutAssignmentList.updateRolloutAssignmentList(ROLLOUTS_STATE_2);
              }
            })
        .start();

    new Thread(
            new Runnable() {
              @Override
              public void run() {
                List<RolloutAssignment> list = rolloutAssignmentList.getRolloutAssignmentList();
                if (changed.isEmpty()) {
                  assertThat(rolloutAssignmentList.getRolloutAssignmentList().size()).isEqualTo(1);
                } else {
                  assertThat(rolloutAssignmentList.getRolloutAssignmentList().size()).isEqualTo(2);
                }
              }
            })
        .start();
  }
}
