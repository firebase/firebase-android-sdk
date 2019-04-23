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

import javax.inject.Inject;

public class SchedulerConfig {

  private long delta;
  private long maxAllowedTime;
  private long maximumDelay;

  @Inject
  public SchedulerConfig(long delta, long maxAllowedTime, long maximumDelay) {
    this.delta = delta;
    this.maxAllowedTime = maxAllowedTime;
    this.maximumDelay = maximumDelay;
  }

  public long getMaximumDelay() {
    return maximumDelay;
  }

  public long getScheduleDelay(long backendTimeDiff, int attemptNumber) {
    if (attemptNumber > 11) {
      return maxAllowedTime;
    }
    return Math.max(((long) Math.pow(2, attemptNumber)) * delta, backendTimeDiff);
  }
}
