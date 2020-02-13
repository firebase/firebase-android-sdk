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
import android.util.Base64;
import com.google.android.datatransport.Priority;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.InMemoryEventStore;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.charset.Charset;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@Config(sdk = {LOLLIPOP})
@RunWith(RobolectricTestRunner.class)
public class JobInfoSchedulerTest {
  private static final long TWENTY_FOUR_HOURS = 24 * 60 * 60 * 1000;
  private static final long THIRTY_SECONDS = 30 * 1000;

  private static final TransportContext TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").build();

  private static final TransportContext UNMETERED_TRANSPORT_CONTEXT =
      TransportContext.builder().setBackendName("backend1").setPriority(Priority.VERY_LOW).build();

  private final Context context = RuntimeEnvironment.application;
  private final EventStore store = new InMemoryEventStore();
  private final JobScheduler jobScheduler =
      (JobScheduler) context.getSystemService(Context.JOB_SCHEDULER_SERVICE);

  private final SchedulerConfig config = SchedulerConfig.getDefault(() -> 1);
  private final JobInfoScheduler scheduler = new JobInfoScheduler(context, store, config);

  @Test
  public void schedule_longWaitTimeFirstAttempt() {
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    int jobId = scheduler.getJobId(TRANSPORT_CONTEXT);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(TRANSPORT_CONTEXT.getBackendName());
    assertThat(bundle.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(1);
    assertThat(jobInfo.getMinLatencyMillis()).isEqualTo(999999);
  }

  @Test
  public void schedule_noTimeRecordedForBackend() {
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    int jobId = scheduler.getJobId(TRANSPORT_CONTEXT);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(TRANSPORT_CONTEXT.getBackendName());
    assertThat(bundle.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(1);
    assertThat(jobInfo.getMinLatencyMillis()).isEqualTo(THIRTY_SECONDS); // 2^0*DELTA
  }

  @Test
  public void schedule_smallWaitTImeFirstAttempt() {
    store.recordNextCallTime(TRANSPORT_CONTEXT, 5);
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    int jobId = scheduler.getJobId(TRANSPORT_CONTEXT);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(TRANSPORT_CONTEXT.getBackendName());
    assertThat(bundle.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(1);
    assertThat(jobInfo.getMinLatencyMillis()).isEqualTo(THIRTY_SECONDS); // 2^0*DELTA
  }

  @Test
  public void schedule_longWaitTimeTenthAttempt() {
    store.recordNextCallTime(TRANSPORT_CONTEXT, 1000000);
    scheduler.schedule(TRANSPORT_CONTEXT, 10);
    int jobId = scheduler.getJobId(TRANSPORT_CONTEXT);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(TRANSPORT_CONTEXT.getBackendName());
    assertThat(bundle.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(10);
    assertThat(jobInfo.getMinLatencyMillis()).isGreaterThan((long) 1000000);
  }

  @Test
  public void schedule_twoJobs() {
    store.recordNextCallTime(TRANSPORT_CONTEXT, 5);
    int jobId = scheduler.getJobId(TRANSPORT_CONTEXT);
    // Schedule first job
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    // Schedule another job
    scheduler.schedule(TRANSPORT_CONTEXT, 2);
    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(1);
    // The job should be the second job.
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(jobInfo.getId()).isEqualTo(jobId);
    assertThat(bundle.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(2);
    assertThat(bundle.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(TRANSPORT_CONTEXT.getBackendName());
  }

  @Test
  public void schedule_whenExtrasEvailable_transmitsExtras() {
    String extras = "e1";
    TransportContext transportContext =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras(extras.getBytes(Charset.defaultCharset()))
            .build();
    store.recordNextCallTime(transportContext, 1000000);
    scheduler.schedule(transportContext, 1);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(bundle.get(JobInfoScheduler.EXTRAS))
        .isEqualTo(
            Base64.encodeToString(extras.getBytes(Charset.defaultCharset()), Base64.DEFAULT));
  }

  @Test
  public void schedule_withMultipleContexts_whenExtrasAvailable_schedulesForBothContexts() {
    String extras1 = "e1";
    String extras2 = "e2";
    TransportContext ctx1 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras(extras1.getBytes(Charset.defaultCharset()))
            .build();
    TransportContext ctx2 =
        TransportContext.builder()
            .setBackendName("backend1")
            .setExtras(extras2.getBytes(Charset.defaultCharset()))
            .build();

    store.recordNextCallTime(ctx1, 1000000);
    store.recordNextCallTime(ctx2, 1000000);
    scheduler.schedule(ctx1, 1);
    scheduler.schedule(ctx2, 1);
    assertThat(jobScheduler.getAllPendingJobs()).hasSize(2);
    JobInfo jobInfo = jobScheduler.getAllPendingJobs().get(0);
    PersistableBundle bundle = jobInfo.getExtras();
    assertThat(bundle.get(JobInfoScheduler.EXTRAS))
        .isEqualTo(
            Base64.encodeToString(extras1.getBytes(Charset.defaultCharset()), Base64.DEFAULT));

    jobInfo = jobScheduler.getAllPendingJobs().get(1);
    bundle = jobInfo.getExtras();
    assertThat(bundle.get(JobInfoScheduler.EXTRAS))
        .isEqualTo(
            Base64.encodeToString(extras2.getBytes(Charset.defaultCharset()), Base64.DEFAULT));
  }

  @Test
  public void schedule_smallWaitTImeFirstAttempt_multiplePriorities() {
    store.recordNextCallTime(TRANSPORT_CONTEXT, 5);
    scheduler.schedule(TRANSPORT_CONTEXT, 1);
    scheduler.schedule(UNMETERED_TRANSPORT_CONTEXT, 1);
    int jobId1 = scheduler.getJobId(TRANSPORT_CONTEXT);
    int jobId2 = scheduler.getJobId(UNMETERED_TRANSPORT_CONTEXT);

    assertThat(jobScheduler.getAllPendingJobs()).isNotEmpty();
    assertThat(jobScheduler.getAllPendingJobs().size()).isEqualTo(2);
    JobInfo jobInfo1 = jobScheduler.getAllPendingJobs().get(0);
    assertThat(jobInfo1.getId()).isEqualTo(jobId1);
    assertThat(jobInfo1.getMinLatencyMillis()).isEqualTo(THIRTY_SECONDS); // 2^0*DELTA

    PersistableBundle bundle1 = jobInfo1.getExtras();
    assertThat(bundle1.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(TRANSPORT_CONTEXT.getBackendName());
    assertThat(bundle1.get(JobInfoScheduler.EVENT_PRIORITY))
        .isEqualTo(PriorityMapping.toInt(Priority.DEFAULT));
    assertThat(bundle1.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(1);

    JobInfo jobInfo2 = jobScheduler.getAllPendingJobs().get(1);
    assertThat(jobInfo2.getId()).isEqualTo(jobId2);
    assertThat(jobInfo2.getMinLatencyMillis()).isEqualTo(TWENTY_FOUR_HOURS); // 2^0*DELTA

    PersistableBundle bundle2 = jobInfo2.getExtras();
    assertThat(bundle2.get(JobInfoScheduler.BACKEND_NAME))
        .isEqualTo(UNMETERED_TRANSPORT_CONTEXT.getBackendName());
    assertThat(bundle2.get(JobInfoScheduler.EVENT_PRIORITY))
        .isEqualTo(PriorityMapping.toInt(Priority.VERY_LOW));
    assertThat(bundle2.get(JobInfoScheduler.ATTEMPT_NUMBER)).isEqualTo(1);
  }
}
