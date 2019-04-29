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

import com.google.auto.value.AutoValue;

@AutoValue
public abstract class SchedulerConfig {

  abstract long getDelta();

  abstract long getMaxAllowedTime();

  abstract long getMaximumDelay();

  public static SchedulerConfig.Builder builder() {
    return new AutoValue_SchedulerConfig.Builder();
  }

  @AutoValue.Builder
  public abstract static class Builder {
    public abstract SchedulerConfig.Builder setDelta(long value);

    public abstract SchedulerConfig.Builder setMaxAllowedTime(long value);

    public abstract SchedulerConfig.Builder setMaximumDelay(long value);

    public abstract SchedulerConfig build();
  }

  public long getScheduleDelay(long backendTimeDiff, int attemptNumber) {
    if (attemptNumber > 11) {
      return getMaxAllowedTime();
    }
    return Math.max(((long) Math.pow(2, attemptNumber)) * getDelta(), backendTimeDiff);
  }
}
