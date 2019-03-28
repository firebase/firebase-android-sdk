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

import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static com.google.common.truth.Truth.assertThat;

import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.os.PersistableBundle;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class JobInfoSchedulerTest {
  private final String BACKEND_NAME = "backend1";
  private final Context context = RuntimeEnvironment.application;
  private final EventStore store = new InMemoryEventStore();
  private final JobScheduler jobScheduler =
      (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);
  private final JobInfoScheduler scheduler = new JobInfoScheduler(context, store, () -> 0);

  @Test
  public void schedule_longWaitTimeFirstAttempt() {
    store.recordNextCallTime(BACKEND_NAME, 1000000);
    scheduler.schedule(BACKEND_NAME, 1);
    int jobId = scheduler.getJobId(BACKEND_NAME);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(SchedulerUtil.BACKEND_NAME)).isEqualTo(BACKEND_NAME);
    assertThat(bundle.get(SchedulerUtil.ATTEMPT_NUMBER)).isEqualTo(1);
    assertThat(jobInfo.getMinLatencyMillis()).isEqualTo(1000000);
  }

  @Test
  public void schedule_smallWaitTImeFirstAttempt() {
    store.recordNextCallTime(BACKEND_NAME, 5);
    scheduler.schedule(BACKEND_NAME, 1);
    int jobId = scheduler.getJobId(BACKEND_NAME);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(SchedulerUtil.BACKEND_NAME)).isEqualTo(BACKEND_NAME);
    assertThat(bundle.get(SchedulerUtil.ATTEMPT_NUMBER)).isEqualTo(1);
    assertThat(jobInfo.getMinLatencyMillis()).isEqualTo(60000); // 2^1*DELTA
  }

  @Test
  public void schedule_longWaitTimeTenthAttempt() {
    store.recordNextCallTime(BACKEND_NAME, 1000000);
    scheduler.schedule(BACKEND_NAME, 10);
    int jobId = scheduler.getJobId(BACKEND_NAME);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(SchedulerUtil.BACKEND_NAME)).isEqualTo(BACKEND_NAME);
    assertThat(bundle.get(SchedulerUtil.ATTEMPT_NUMBER)).isEqualTo(10);
    assertThat(jobInfo.getMinLatencyMillis()).isGreaterThan((long) 1000000);
  }

  @Test
  public void schedule_twoJobs() {
    store.recordNextCallTime(BACKEND_NAME, 5);
    int jobId = scheduler.getJobId(BACKEND_NAME);
    // Schedule first job
    scheduler.schedule(BACKEND_NAME, 1);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    // Schedule another job
    scheduler.schedule(BACKEND_NAME, 2);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    // The job should be the first job.
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(SchedulerUtil.ATTEMPT_NUMBER)).isEqualTo(1);
    assertThat(bundle.get(SchedulerUtil.BACKEND_NAME)).isEqualTo(BACKEND_NAME);
  }
}
