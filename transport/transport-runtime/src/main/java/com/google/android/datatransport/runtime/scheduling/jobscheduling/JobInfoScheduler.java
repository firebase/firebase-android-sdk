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

package com.google.android.datatransport.runtime.scheduling.jobscheduling;

import android.annotation.TargetApi;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.service.JobInfoSchedulerService;
import com.google.android.datatransport.runtime.scheduling.persistence.BackendNextCallTime;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;
import java.util.Iterator;

public class JobInfoScheduler implements WorkScheduler {

  private final Context context;

  private final int UNIQUE_JOB_ID = 101;

  private final int STANDARD_DELTA = 30000; // 30 seconds

  private final EventStore eventStore;

  private Clock clock;

  public JobInfoScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  @TargetApi(Build.VERSION_CODES.M)
  @android.support.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void schedule() {
    long nextBackendCallDelta;
    ComponentName serviceComponent = new ComponentName(context, JobInfoSchedulerService.class);
    JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
    if (jobScheduler != null) {
      jobScheduler.cancel(UNIQUE_JOB_ID);

      Iterator itr = eventStore.getBackendNextCallTimes().iterator();
      if (itr.hasNext()) {
        long minBackendCallTime = ((BackendNextCallTime) itr.next()).getTimestampMs();
        while (itr.hasNext()) {
          long backendWaitTime = ((BackendNextCallTime) itr.next()).getTimestampMs();
          if (backendWaitTime < minBackendCallTime) {
            minBackendCallTime = backendWaitTime;
          }
        }
        nextBackendCallDelta = minBackendCallTime - clock.getTime();
      } else {
        return;
      }

      JobInfo.Builder builder = new JobInfo.Builder(UNIQUE_JOB_ID, serviceComponent);
      builder.setMinimumLatency(nextBackendCallDelta); // wait at least
      builder.setOverrideDeadline(nextBackendCallDelta + STANDARD_DELTA); // maximum delay
      jobScheduler.schedule(builder.build());
    }
  }
}
