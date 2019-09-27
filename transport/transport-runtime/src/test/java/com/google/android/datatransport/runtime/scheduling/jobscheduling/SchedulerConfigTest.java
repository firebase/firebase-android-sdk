// Copyright 2019 Google LLC
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
import static org.junit.Assert.assertThrows;

import android.app.job.JobInfo;
import android.content.ComponentName;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.TestClock;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class SchedulerConfigTest {

  private static final int DELTA = 10;
  private static final int MAX_DELAY = 100;
  private final Clock CLOCK = new TestClock(0);

  @Test
  public void build_whenClockNotSet_shouldThrow() {
    Exception ex =
        assertThrows(NullPointerException.class, () -> SchedulerConfig.builder().build());

    assertThat(ex.getMessage()).contains("clock");
  }

  @Test
  public void build_whenNotAllPrioritiesSet_shouldThrow() {
    Exception ex =
        assertThrows(
            IllegalStateException.class,
            () ->
                SchedulerConfig.builder()
                    .setClock(CLOCK)
                    .addConfig(
                        Priority.DEFAULT,
                        SchedulerConfig.ConfigValue.builder()
                            .setDelta(1)
                            .setMaxAllowedDelay(1)
                            .build())
                    .build());

    assertThat(ex.getMessage()).contains("priorities");
  }

  private SchedulerConfig createConfig(Set<SchedulerConfig.Flag> flags) {
    return SchedulerConfig.builder()
        .setClock(CLOCK)
        .addConfig(
            Priority.DEFAULT,
            SchedulerConfig.ConfigValue.builder()
                .setDelta(DELTA)
                .setMaxAllowedDelay(MAX_DELAY)
                .setFlags(flags)
                .build())
        .addConfig(
            Priority.VERY_LOW,
            SchedulerConfig.ConfigValue.builder().setDelta(0).setMaxAllowedDelay(0).build())
        .addConfig(
            Priority.HIGHEST,
            SchedulerConfig.ConfigValue.builder().setDelta(0).setMaxAllowedDelay(0).build())
        .build();
  }

  @Test
  public void getScheduleDelay_whenNoMinTimestampAndFirstAttempt_shouldReturnExpectedValue() {
    long delay = createConfig(Collections.emptySet()).getScheduleDelay(Priority.DEFAULT, 0, 1);

    assertThat(delay).isEqualTo(10);
  }

  @Test
  public void getScheduleDelay_whenNoMinTimestampAndSecondAttempt_shouldReturnExpectedValue() {
    long delay = createConfig(Collections.emptySet()).getScheduleDelay(Priority.DEFAULT, 0, 2);

    assertThat(delay).isEqualTo(20);
  }

  @Test
  public void getScheduleDelay_withMinTimestampAndFirstAttempt_shouldReturnExpectedValue() {
    long delay = createConfig(Collections.emptySet()).getScheduleDelay(Priority.DEFAULT, 50, 1);

    assertThat(delay).isEqualTo(50);
  }

  @Test
  public void getScheduleDelay_withMinTimestampAndFourthAttempt_shouldReturnExpectedValue() {
    long delay = createConfig(Collections.emptySet()).getScheduleDelay(Priority.DEFAULT, 50, 4);

    assertThat(delay).isEqualTo(80);
  }

  @Test
  public void getScheduleDelay_withMinTimestampAndFifthAttempt_shouldReturnMaxDelay() {
    long delay = createConfig(Collections.emptySet()).getScheduleDelay(Priority.DEFAULT, 50, 5);

    assertThat(delay).isEqualTo(100);
  }

  @Test
  public void configureJob_shouldSetCorrectDelay() {
    ComponentName serviceComponent =
        new ComponentName(RuntimeEnvironment.application, JobInfoSchedulerService.class);
    SchedulerConfig config = createConfig(Collections.emptySet());

    JobInfo job =
        config
            .configureJob(new JobInfo.Builder(1, serviceComponent), Priority.DEFAULT, 0, 1)
            .build();

    assertThat(job.getMinLatencyMillis())
        .isEqualTo(config.getScheduleDelay(Priority.DEFAULT, 0, 1));
  }

  @Test
  public void configureJob_withDefaults_shouldSetCorrectFlags() {
    ComponentName serviceComponent =
        new ComponentName(RuntimeEnvironment.application, JobInfoSchedulerService.class);
    SchedulerConfig config = createConfig(Collections.emptySet());

    JobInfo job =
        config
            .configureJob(new JobInfo.Builder(1, serviceComponent), Priority.DEFAULT, 0, 1)
            .build();

    assertThat(job.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_ANY);
    assertThat(job.isRequireDeviceIdle()).isFalse();
    assertThat(job.isRequireCharging()).isFalse();
  }

  @Test
  public void configureJob_whenUnmetered_shouldSetCorrectFlags() {
    ComponentName serviceComponent =
        new ComponentName(RuntimeEnvironment.application, JobInfoSchedulerService.class);
    SchedulerConfig config = createConfig(EnumSet.of(SchedulerConfig.Flag.NETWORK_UNMETERED));

    JobInfo job =
        config
            .configureJob(new JobInfo.Builder(1, serviceComponent), Priority.DEFAULT, 0, 1)
            .build();

    assertThat(job.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_UNMETERED);
    assertThat(job.isRequireDeviceIdle()).isFalse();
    assertThat(job.isRequireCharging()).isFalse();
  }

  @Test
  public void configureJob_whenIdle_shouldSetCorrectFlags() {
    ComponentName serviceComponent =
        new ComponentName(RuntimeEnvironment.application, JobInfoSchedulerService.class);
    SchedulerConfig config = createConfig(EnumSet.of(SchedulerConfig.Flag.DEVICE_IDLE));

    JobInfo job =
        config
            .configureJob(new JobInfo.Builder(1, serviceComponent), Priority.DEFAULT, 0, 1)
            .build();

    assertThat(job.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_ANY);
    assertThat(job.isRequireDeviceIdle()).isTrue();
    assertThat(job.isRequireCharging()).isFalse();
  }

  @Test
  public void configureJob_whenCharging_shouldSetCorrectFlags() {
    ComponentName serviceComponent =
        new ComponentName(RuntimeEnvironment.application, JobInfoSchedulerService.class);
    SchedulerConfig config = createConfig(EnumSet.of(SchedulerConfig.Flag.DEVICE_CHARGING));

    JobInfo job =
        config
            .configureJob(new JobInfo.Builder(1, serviceComponent), Priority.DEFAULT, 0, 1)
            .build();

    assertThat(job.getNetworkType()).isEqualTo(JobInfo.NETWORK_TYPE_ANY);
    assertThat(job.isRequireDeviceIdle()).isFalse();
    assertThat(job.isRequireCharging()).isTrue();
  }
}
