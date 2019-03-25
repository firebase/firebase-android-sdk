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
import android.os.PersistableBundle;
import android.support.annotation.RequiresApi;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;
import java.util.zip.Adler32;

public class JobInfoScheduler implements WorkScheduler {

  private final Context context;

  public static final String NUMBER_OF_ATTEMPTS_CONSTANT = "numberOfAttempts";

  public static final String BACKEND_NAME_CONSTANT = "backendName";

  private final EventStore eventStore;

  private Clock clock;

  public JobInfoScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  private int getJobId(String backendName) {
    Adler32 checksum = new Adler32();
    checksum.update(backendName.getBytes());
    return (int) checksum.getValue();
  }

  @RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  private boolean isJobServiceOn(JobScheduler scheduler, int jobId) {
    for (JobInfo jobInfo : scheduler.getAllPendingJobs()) {
      if (jobInfo.getId() == jobId) {
        return true;
      }
    }
    return false;
  }

  @TargetApi(Build.VERSION_CODES.M)
  @android.support.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
  @Override
  public void schedule(String backendName, int numberOfAttempts) {
    ComponentName serviceComponent = new ComponentName(context, JobInfoSchedulerService.class);
    JobScheduler jobScheduler = context.getSystemService(JobScheduler.class);
    int jobId = getJobId(backendName);
    // Check if there exists a job scheduled for this backend name.
    if (isJobServiceOn(jobScheduler, jobId)) return;
    // Obtain the next available call time for the backend.
    long timeDiff = eventStore.getNextCallTime(backendName) - clock.getTime();
    // Schedule the build.
    PersistableBundle bundle = new PersistableBundle();
    bundle.putLong(NUMBER_OF_ATTEMPTS_CONSTANT, numberOfAttempts);
    bundle.putString(BACKEND_NAME_CONSTANT, backendName);
    JobInfo.Builder builder = new JobInfo.Builder(jobId, serviceComponent);
    builder.setMinimumLatency(
        clock.getTime()
            + SchedulerUtil.getScheduleDelay(timeDiff, 5000, numberOfAttempts)); // wait at least
    builder.setExtras(bundle);
    builder.setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY);
    jobScheduler.schedule(builder.build());
  }
}
