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
import android.os.Build;
import androidx.annotation.RequiresApi;
import androidx.work.Data;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.logging.Logging;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.util.PriorityMapping;
import java.util.concurrent.TimeUnit;

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class WorkManagerScheduler implements WorkScheduler {
  private static final String LOG_TAG = "WorkManagerScheduler";

  static final String ATTEMPT_NUMBER = "attemptNumber";
  static final String BACKEND_NAME = "backendName";
  static final String EVENT_PRIORITY = "priority";
  static final String EXTRAS = "extras";
  private final Context context;

  private final EventStore eventStore;

  private final SchedulerConfig config;

  public WorkManagerScheduler(
      Context applicationContext, EventStore eventStore, SchedulerConfig config) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.config = config;
  }

  private String getTag(int jobId) {
    return "transport-" + jobId;
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    schedule(transportContext, attemptNumber, false);
  }

  @Override
  public void schedule(TransportContext transportContext, int attemptNumber, boolean force) {
    WorkManager manager = WorkManager.getInstance(context);

    int jobId = WorkScheduler.getJobId(context, transportContext);
    if (!force) {
      try {
        for (WorkInfo info : manager.getWorkInfosByTag(this.getTag(jobId)).get()) {
          if (!info.getState().isFinished()) {
            Logging.d(
                LOG_TAG,
                "Upload for context %s is already scheduled. Returning...",
                transportContext);
            return;
          }
        }
      } catch (Exception e) {
        // Various Future failure states that shouldn't be possible
        throw new RuntimeException(e);
      }
    }

    Data.Builder dataBuilder =
        new Data.Builder()
            .putInt(ATTEMPT_NUMBER, attemptNumber)
            .putString(BACKEND_NAME, transportContext.getBackendName())
            .putInt(EVENT_PRIORITY, PriorityMapping.toInt(transportContext.getPriority()));
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
            .addTag(this.getTag(jobId))
            .build();
    manager.enqueue(request);
  }
}
