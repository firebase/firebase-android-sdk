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

package com.google.firebase.crashlytics.internal;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.firebase.crashlytics.internal.metadata.RolloutAssignment;
import com.google.firebase.crashlytics.internal.metadata.UserMetadata;
import com.google.firebase.remoteconfig.interop.rollouts.RolloutsState;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class CrashlyticsRemoteConfigListenerTest {
  private static final String ROLLOUT_ASSIGNMENT_JSON_1 =
      "{"
          + "\"rolloutId\":\"rollout_1\","
          + "\"variantId\":\"control\","
          + "\"parameterKey\":\"my_feature\","
          + "\"parameterValue\":\"false\","
          + "\"templateVersion\":1"
          + "}";

  private static final String ROLLOUT_ASSIGNMENT_JSON_2 =
      "{"
          + "\"rolloutId\":\"rollout_2\","
          + "\"variantId\":\"enabled\","
          + "\"parameterKey\":\"my_another_feature\","
          + "\"parameterValue\":\"false\","
          + "\"templateVersion\":1"
          + "}";

  private static List<RolloutAssignment> CRASHLYTICS_ROLLOUT_ASSIGNMENT_SINGLE_ELEMENT_LIST;

  static {
    CRASHLYTICS_ROLLOUT_ASSIGNMENT_SINGLE_ELEMENT_LIST = new ArrayList<>();
    CRASHLYTICS_ROLLOUT_ASSIGNMENT_SINGLE_ELEMENT_LIST.add(
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1));
  }

  private static List<RolloutAssignment> CRASHLYTICS_ROLLOUT_ASSIGNMENT_MULTIPLE_ELEMENTS_LIST;

  static {
    CRASHLYTICS_ROLLOUT_ASSIGNMENT_MULTIPLE_ELEMENTS_LIST = new ArrayList<>();
    CRASHLYTICS_ROLLOUT_ASSIGNMENT_MULTIPLE_ELEMENTS_LIST.add(
        RolloutAssignment.create("rollout_1", "my_feature", "false", "control", 1));
    CRASHLYTICS_ROLLOUT_ASSIGNMENT_MULTIPLE_ELEMENTS_LIST.add(
        RolloutAssignment.create("rollout_2", "my_another_feature", "false", "enabled", 1));
  }

  @Mock private UserMetadata userMetadata;

  @Captor private ArgumentCaptor<ArrayList<RolloutAssignment>> captor;

  private CrashlyticsRemoteConfigListener listener;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.openMocks(this);
    listener = new CrashlyticsRemoteConfigListener(userMetadata);
  }

  @Test
  public void testListener_onChangeUpdateRCInteropClassToCrashlyticsClass() throws Exception {
    RolloutsState rolloutsState = createInteropRolloutsStateWithSingleElement();
    listener.onRolloutsStateChanged(rolloutsState);

    verify(userMetadata).updateRolloutsState(CRASHLYTICS_ROLLOUT_ASSIGNMENT_SINGLE_ELEMENT_LIST);
  }

  @Test
  public void testListener_onChangeUpdateRCInteropClassToCrashlyticsClassMultipleTimes()
      throws Exception {
    RolloutsState rolloutsState = createInteropRolloutsStateWithSingleElement();
    listener.onRolloutsStateChanged(rolloutsState);

    RolloutsState newRolloutsState = createInteropRolloutsStateWithMultipleElements();
    listener.onRolloutsStateChanged(newRolloutsState);

    verify(userMetadata, times(2)).updateRolloutsState(captor.capture());
    assertThat(captor.getAllValues().get(1).size()).isEqualTo(2);
  }

  private RolloutsState createInteropRolloutsStateWithSingleElement() throws Exception {
    com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment assignment =
        com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment.create(
            ROLLOUT_ASSIGNMENT_JSON_1);
    Set<com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment> rolloutAssignmentsSet =
        new HashSet<>();
    rolloutAssignmentsSet.add(assignment);

    RolloutsState rolloutsState = RolloutsState.create(rolloutAssignmentsSet);
    return rolloutsState;
  }

  private RolloutsState createInteropRolloutsStateWithMultipleElements() throws Exception {
    com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment assignment1 =
        com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment.create(
            ROLLOUT_ASSIGNMENT_JSON_1);

    com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment assignment2 =
        com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment.create(
            ROLLOUT_ASSIGNMENT_JSON_2);
    Set<com.google.firebase.remoteconfig.interop.rollouts.RolloutAssignment> rolloutAssignmentsSet =
        new HashSet<>();

    rolloutAssignmentsSet.add(assignment1);
    rolloutAssignmentsSet.add(assignment2);

    RolloutsState rolloutsState = RolloutsState.create(rolloutAssignmentsSet);
    return rolloutsState;
  }
}
