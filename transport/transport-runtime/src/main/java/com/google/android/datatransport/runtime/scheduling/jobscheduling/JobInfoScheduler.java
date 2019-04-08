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

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.ComponentName;
import android.content.Context;
import android.os.Build;
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;
import android.support.annotation.VisibleForTesting;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;
import java.util.zip.Adler32;
import javax.inject.Inject;

/**
 * Schedules the service {@link JobInfoSchedulerService} based on the backendname. Used for Apis 21
 * and above.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class JobInfoScheduler implements WorkScheduler {

  private final Context context;

  private final EventStore eventStore;

  private final Clock clock;

  private final int DELTA = 30000; // 30 seconds delta

  @Inject
  public JobInfoScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  @VisibleForTesting
  int getJobId(TransportContext transportContext) {
    Adler32 checksum = new Adler32();
    checksum.update(transportContext.getBackendName().getBytes());
    return (int) checksum.getValue();
  }

  private boolean isJobServiceOn(JobScheduler scheduler, int jobId) {
    for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
      if (jobInfo.getId() == jobId) {
        return true;
      }
    }
    return false;
  }

  /**
   * Schedules the JobScheduler service.
   *
   * @param backendName The backend to where the events are logged.
   * @param attemptNumber Number of times the JobScheduler has tried to log for this backend.
   */
  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    ComponentName serviceComponent = new ComponentName(context, JobInfoSchedulerService.class);
    JobScheduler jobScheduler =
        (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    int jobId = getJobId(transportContext);
    // Check if there exists a job scheduled for this backend name.
    if (isJobServiceOn(jobScheduler, jobId)) return;
    // Obtain the next available call time for the backend.
    Long backendTime = eventStore.getNextCallTime(transportContext);

    long timeDiff = 0;
    if (backendTime != null) {
      timeDiff = backendTime - clock.getTime();
    }
    // Schedule the build.
    PersistableBundle bundle = new PersistableBundle();
    bundle.putInt(SchedulerUtil.ATTEMPT_NUMBER, attemptNumber);
    bundle.putString(SchedulerUtil.BACKEND_NAME, transportContext.getBackendName());
    JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent);
    builder.setMinimumLatency(
        SchedulerUtil.getScheduleDelay(timeDiff, DELTA, attemptNumber)); // wait at least
    builder.setExtras(bundle);
    builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    jobScheduler.schedule(builder.build());
  }
}
