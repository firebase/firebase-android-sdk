// Copyright 2024 Google LLC
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

import static android.util.Base64.DEFAULT;
import static android.util.Base64.encodeToString;

import android.content.Context;
import androidx.annotation.VisibleForTesting;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.logging.Logging;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.zip.Adler32;

public class WorkManagerScheduler implements WorkScheduler {
  private static final String LOG_TAG = "JobInfoScheduler";

  static final String ATTEMPT_NUMBER = "attemptNumber";
  static final String BACKEND_NAME = "backendName";
  static final String EVENT_PRIORITY = "priority";
  static final String EXTRAS = "extras";
  private static final Map<Integer, UUID> JOBS = new HashMap<>();
  private final Context context;

  private final EventStore eventStore;

  private final SchedulerConfig config;

  public WorkManagerScheduler(
      Context applicationContext, EventStore eventStore, SchedulerConfig config) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.config = config;
  }

  @VisibleForTesting
  int getJobId(TransportContext transportContext) {
    Adler32 checksum = new Adler32();
    checksum.update(context.getPackageName().getBytes(Charset.forName("UTF-8")));
    checksum.update(transportContext.getBackendName().getBytes(Charset.forName("UTF-8")));
    checksum.update(
        ByteBuffer.allocate(4)
            .putInt(PriorityMapping.toInt(transportContext.getPriority()))
            .array());
    if (transportContext.getExtras() != null) {
      checksum.update(transportContext.getExtras());
    }
    return (int) checksum.getValue();
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    schedule(transportContext, attemptNumber, false);
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber, boolean force) {
    WorkManager manager = WorkManager.getInstance(context);

    int jobId = getJobId(transportContext);
    if (!force && JOBS.containsKey(jobId)) {
      try {
        if (!manager.getWorkInfoById(JOBS.get(jobId)).get().getState().isFinished()) {
          Logging.d(
              LOG_TAG,
              "Upload for context %s is already scheduled. Returning...",
              transportContext);
          return;
        }
      } catch (Exception e) {
      }
    }

    Data.Builder dataBuilder = new Data.Builder();
    dataBuilder.putInt(ATTEMPT_NUMBER, attemptNumber);
    dataBuilder.putString(BACKEND_NAME, transportContext.getBackendName());
    dataBuilder.putInt(EVENT_PRIORITY, PriorityMapping.toInt(transportContext.getPriority()));
    if (transportContext.getExtras() != null) {
      dataBuilder.putString(EXTRAS, encodeToString(transportContext.getExtras(), DEFAULT));
    }

    long backendTime = eventStore.getNextCallTime(transportContext);
    boolean hasPendingEvents = force && eventStore.hasPendingEventsFor(transportContext);

    long scheduleDelay =
        config.getScheduleDelay(
            transportContext.getPriority(), backendTime, attemptNumber, hasPendingEvents);

    Logging.d(
        LOG_TAG,
        "Scheduling upload for context %s in %dms(Backend next call timestamp %d). Attempt %d",
        transportContext,
        scheduleDelay,
        backendTime,
        attemptNumber);

    WorkRequest request =
        new OneTimeWorkRequest.Builder(WorkManagerSchedulerWorker.class)
            .setInitialDelay(scheduleDelay, TimeUnit.MILLISECONDS)
            .setInputData(dataBuilder.build())
            .build();
    JOBS.put(jobId, request.getId());
    manager.enqueue(request);
  }
}
