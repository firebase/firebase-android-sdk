// Copyright 2018 Google LLC
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

package com.google.firebase.abt;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.abt.AbtExperimentInfo.EXPERIMENT_ID_KEY;
import static com.google.firebase.abt.AbtExperimentInfo.EXPERIMENT_START_TIME_KEY;
import static com.google.firebase.abt.AbtExperimentInfo.TIME_TO_LIVE_KEY;
import static com.google.firebase.abt.AbtExperimentInfo.TRIGGER_EVENT_KEY;
import static com.google.firebase.abt.AbtExperimentInfo.TRIGGER_TIMEOUT_KEY;
import static com.google.firebase.abt.AbtExperimentInfo.VARIANT_ID_KEY;
import static com.google.firebase.abt.AbtExperimentInfo.protoTimestampStringParser;

import java.util.HashMap;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * Unit tests for {@link AbtExperimentInfo}.
 *
 * @author Miraziz Yusupov
 */
@RunWith(RobolectricTestRunner.class)
@Config(manifest = Config.NONE)
public class AbtExperimentInfoTest {

  @Test
  public void fromMap_hasTriggerEvent_returnsConvertedExperiment() throws Exception {

    Map<String, String> experimentInfoMap = createExperimentInfoMap("exp1", "var1", "trigger");

    AbtExperimentInfo info = AbtExperimentInfo.fromMap(experimentInfoMap);
    assertThat(info.getTriggerEventName()).isEqualTo("trigger");
  }

  @Test
  public void fromMap_noTriggerEvent_returnsExperimentWithEmptyTriggerEvent() throws Exception {

    Map<String, String> experimentInfoMap = createExperimentInfoMap("exp2", "var2", "");

    AbtExperimentInfo info = AbtExperimentInfo.fromMap(experimentInfoMap);
    assertThat(info.getTriggerEventName()).isEmpty();
  }

  private static Map<String, String> createExperimentInfoMap(
      String experimentId, String variantId, String triggerEvent) {

    Map<String, String> experimentInfoMap = new HashMap<>();
    experimentInfoMap.put(EXPERIMENT_ID_KEY, experimentId);
    experimentInfoMap.put(VARIANT_ID_KEY, variantId);
    if (!triggerEvent.isEmpty()) {
      experimentInfoMap.put(TRIGGER_EVENT_KEY, triggerEvent);
    }
    experimentInfoMap.put(EXPERIMENT_START_TIME_KEY, protoTimestampStringParser.format(555L));
    experimentInfoMap.put(TRIGGER_TIMEOUT_KEY, "5");
    experimentInfoMap.put(TIME_TO_LIVE_KEY, "5");
    return experimentInfoMap;
  }
}
