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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import static com.google.common.truth.Truth.assertThat;

import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.TestClock;
import edu.emory.mathcs.backport.java.util.Collections;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class ScheduleDelayTest {
  private final Clock CLOCK = new TestClock(0);
  private static final int MAX_DELAY = 86400;

  @Test
  public void getScheduleDelay_withDelta1_shouldBackoffAsExpected() {
    SchedulerConfig config = createConfig(1);
    long[] expectedDelays = new long[] {1, 39, 59, 138, 358, 972, 2702, 7632, 21795, 62721, 86400};
    for (int i = 0; i < expectedDelays.length; i++) {
      assertThat(config.getScheduleDelay(Priority.DEFAULT, 0, i + 1)).isEqualTo(expectedDelays[i]);
    }
  }

  @Test
  public void getScheduleDelay_withDelta30_shouldBackoffAsExpected() {
    SchedulerConfig config = createConfig(30);
    long[] expectedDelays = new long[] {30, 243, 607, 1657, 4674, 13400, 38789, 86400};
    for (int i = 0; i < expectedDelays.length; i++) {
      assertThat(config.getScheduleDelay(Priority.DEFAULT, 0, i + 1)).isEqualTo(expectedDelays[i]);
    }
  }

  @Test
  public void getScheduleDelay_withDelta24Hours_shouldBackoffAsExpected() {
    SchedulerConfig config = createConfig(MAX_DELAY);
    long[] expectedDelays = new long[] {86400, 86400, 86400};
    for (int i = 0; i < expectedDelays.length; i++) {
      assertThat(config.getScheduleDelay(Priority.DEFAULT, 0, i + 1)).isEqualTo(expectedDelays[i]);
    }
  }

  private SchedulerConfig createConfig(int delta) {
    return SchedulerConfig.builder()
        .setClock(CLOCK)
        .addConfig(
            Priority.DEFAULT,
            SchedulerConfig.ConfigValue.builder()
                .setDelta(delta)
                .setMaxAllowedDelay(MAX_DELAY)
                .setFlags(Collections.emptySet())
                .build())
        .addConfig(
            Priority.VERY_LOW,
            SchedulerConfig.ConfigValue.builder().setDelta(0).setMaxAllowedDelay(0).build())
        .addConfig(
            Priority.HIGHEST,
            SchedulerConfig.ConfigValue.builder().setDelta(0).setMaxAllowedDelay(0).build())
        .build();
  }
}
