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
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import java.nio.ByteBuffer;
import java.util.zip.Adler32;

/**
 * Schedules the service {@link JobInfoSchedulerService} based on the backendname. Used for Apis 21
 * and above.
 */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class JobInfoScheduler implements WorkScheduler {
  static final String ATTEMPT_NUMBER = "attemptNumber";
  static final String BACKEND_NAME = "backendName";
  static final String EVENT_PRIORITY = "priority";

  private final Context context;

  private final EventStore eventStore;

  private final SchedulerConfig config;

  public JobInfoScheduler(
      Context applicationContext, EventStore eventStore, SchedulerConfig config) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.config = config;
  }

  @VisibleForTesting
  int getJobId(TransportContext transportContext) {
    Adler32 checksum = new Adler32();
    checksum.update(context.getPackageName().getBytes());
    checksum.update(transportContext.getBackendName().getBytes());
    checksum.update(
        ByteBuffer.allocate(4).putInt(transportContext.getPriority().ordinal()).array());
    return (int) checksum.getValue();
  }

  private boolean isJobServiceOn(JobScheduler scheduler, int jobId, int attemptNumber) {
    for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
      int existingAttemptNumber = jobInfo.getExtras().getInt(ATTEMPT_NUMBER);
      if (jobInfo.getId() == jobId) {
        return existingAttemptNumber >= attemptNumber;
      }
    }
    return false;
  }

  /**
   * Schedules the JobScheduler service.
   *
   * @param transportContext Contains information about the backend and the priority.
   * @param attemptNumber Number of times the JobScheduler has tried to log for this backend.
   */
  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    ComponentName serviceComponent = new ComponentName(context, JobInfoSchedulerService.class);
    JobScheduler jobScheduler =
        (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
    int jobId = getJobId(transportContext);
    // Check if there exists a job scheduled for this backend name.
    if (isJobServiceOn(jobScheduler, jobId, attemptNumber)) return;

    // Schedule the build.
    JobInfo.Builder builder =
        config.configureJob(
            new JobInfo.Builder(jobId, serviceComponent),
            transportContext.getPriority(),
            eventStore.getNextCallTime(transportContext),
            attemptNumber);

    PersistableBundle bundle = new PersistableBundle();
    bundle.putInt(ATTEMPT_NUMBER, attemptNumber);
    bundle.putString(BACKEND_NAME, transportContext.getBackendName());
    bundle.putInt(EVENT_PRIORITY, transportContext.getPriority().ordinal());
    builder.setExtras(bundle);

    jobScheduler.schedule(builder.build());
  }
}
