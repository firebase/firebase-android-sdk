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

import android.app.job.JobInfo;
import android.os.Build;
import androidx.annotation.RequiresApi;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.auto.value.AutoValue;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@AutoValue
public abstract class SchedulerConfig {
  public enum Flag {
    NETWORK_UNMETERED,
    DEVICE_IDLE,
    DEVICE_CHARGING,
  }

  @AutoValue
  public abstract static class ConfigValue {
    abstract long getDelta();

    abstract long getMaxAllowedDelay();

    abstract Set<Flag> getFlags();

    public static ConfigValue.Builder builder() {
      return new AutoValue_SchedulerConfig_ConfigValue.Builder().setFlags(Collections.emptySet());
    }

    @AutoValue.Builder
    public abstract static class Builder {
      public abstract ConfigValue.Builder setDelta(long value);

      public abstract ConfigValue.Builder setMaxAllowedDelay(long value);

      public abstract ConfigValue.Builder setFlags(Set<Flag> value);

      public abstract ConfigValue build();
    }
  }

  private static final long TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000;

  private static final long THIRTY_SECONDS = 30 * 1000;

  private static final long ONE_SECOND = 1000;

  private static final long BACKOFF_LOG_BASE = 10000;

  public static SchedulerConfig getDefault(Clock clock) {
    return SchedulerConfig.builder()
        .addConfig(
            Priority.DEFAULT,
            ConfigValue.builder()
                .setDelta(THIRTY_SECONDS)
                .setMaxAllowedDelay(TWENTY_FOUR_HOURS)
                .build())
        .addConfig(
            Priority.HIGHEST,
            ConfigValue.builder()
                .setDelta(ONE_SECOND)
                .setMaxAllowedDelay(TWENTY_FOUR_HOURS)
                .build())
        .addConfig(
            Priority.VERY_LOW,
            ConfigValue.builder()
                .setDelta(TWENTY_FOUR_HOURS)
                .setMaxAllowedDelay(TWENTY_FOUR_HOURS)
                .setFlags(immutableSetOf(Flag.NETWORK_UNMETERED, Flag.DEVICE_IDLE))
                .build())
        .setClock(clock)
        .build();
  }

  abstract Clock getClock();

  abstract Map<Priority, ConfigValue> getValues();

  public static SchedulerConfig.Builder builder() {
    return new SchedulerConfig.Builder();
  }

  static SchedulerConfig create(Clock clock, Map<Priority, ConfigValue> values) {
    return new AutoValue_SchedulerConfig(clock, values);
  }

  public static class Builder {
    private Clock clock;
    private Map<Priority, ConfigValue> values = new HashMap<>();

    public SchedulerConfig.Builder setClock(Clock clock) {
      this.clock = clock;
      return this;
    }

    public SchedulerConfig.Builder addConfig(Priority priority, ConfigValue value) {
      values.put(priority, value);
      return this;
    }

    public SchedulerConfig build() {
      if (clock == null) {
        throw new NullPointerException("missing required property: clock");
      }

      if (values.keySet().size() < Priority.values().length) {
        throw new IllegalStateException("Not all priorities have been configured");
      }

      Map<Priority, ConfigValue> values = this.values;
      this.values = new HashMap<>();
      return SchedulerConfig.create(clock, values);
    }
  }

  public long getScheduleDelay(Priority priority, long minTimestamp, int attemptNumber) {
    long timeDiff = minTimestamp - getClock().getTime();
    ConfigValue config = getValues().get(priority);

    long delay = Math.max(adjustedExponentialBackoff(attemptNumber, config.getDelta()), timeDiff);
    return Math.min(delay, config.getMaxAllowedDelay());
  }

  private long adjustedExponentialBackoff(int attemptNumber, long delta) {
    int attemptCoefficient = attemptNumber - 1;
    long deltaOr2 = delta > 1 ? delta : 2;

    double logValue = Math.log(BACKOFF_LOG_BASE) / Math.log(deltaOr2 * attemptCoefficient);
    double logRegularized = Math.max(1, logValue);

    return (long) (Math.pow(3, attemptCoefficient) * delta * logRegularized);
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  public JobInfo.Builder configureJob(
      JobInfo.Builder builder, Priority priority, long minimumTimestamp, int attemptNumber) {
    long latency = getScheduleDelay(priority, minimumTimestamp, attemptNumber);
    builder.setMinimumLatency(latency); // wait at least
    populateFlags(builder, getValues().get(priority).getFlags());
    return builder;
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private void populateFlags(JobInfo.Builder builder, Set<SchedulerConfig.Flag> flags) {
    if (flags.contains(SchedulerConfig.Flag.NETWORK_UNMETERED)) {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_UNMETERED);
    } else {
      builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    }

    if (flags.contains(SchedulerConfig.Flag.DEVICE_CHARGING)) {
      builder.setRequiresCharging(true);
    }
    if (flags.contains(SchedulerConfig.Flag.DEVICE_IDLE)) {
      builder.setRequiresDeviceIdle(true);
    }
  }

  public Set<Flag> getFlags(Priority priority) {
    return getValues().get(priority).getFlags();
  }

  private static <T> Set<T> immutableSetOf(T... values) {
    return Collections.unmodifiableSet(new HashSet<>(Arrays.asList(values)));
  }
}
