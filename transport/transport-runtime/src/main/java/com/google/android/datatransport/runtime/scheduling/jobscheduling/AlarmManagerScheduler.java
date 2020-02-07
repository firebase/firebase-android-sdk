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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.logging.Logging;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.util.PriorityMapping;

/**
 * Schedules the service {@link AlarmManagerSchedulerBroadcastReceiver} based on the backendname.
 * Used for Apis 20 and below.
 */
public class AlarmManagerScheduler implements WorkScheduler {
  private static final String LOG_TAG = "AlarmManagerScheduler";
  static final String ATTEMPT_NUMBER = "attemptNumber";
  static final String BACKEND_NAME = "backendName";
  static final String EVENT_PRIORITY = "priority";
  static final String EXTRAS = "extras";

  private final Context context;

  private final EventStore eventStore;

  private AlarmManager alarmManager;

  private final SchedulerConfig config;

  private final Clock clock;

  public AlarmManagerScheduler(
      Context applicationContext, EventStore eventStore, Clock clock, SchedulerConfig config) {
    this(
        applicationContext,
        eventStore,
        (AlarmManager) applicationContext.getSystemService(Context.ALARM_SERVICE),
        clock,
        config);
  }

  @VisibleForTesting
  AlarmManagerScheduler(
      Context applicationContext,
      EventStore eventStore,
      AlarmManager alarmManager,
      Clock clock,
      SchedulerConfig config) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.alarmManager = alarmManager;
    this.clock = clock;
    this.config = config;
  }

  @VisibleForTesting
  boolean isJobServiceOn(Intent intent) {
    return (PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
  }

  /**
   * Schedules the AlarmManager service.
   *
   * @param transportContext Contains information about the backend and the priority.
   * @param attemptNumber Number of times the AlarmManager has tried to log for this backend.
   */
  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    Uri.Builder intentDataBuilder = new Uri.Builder();
    intentDataBuilder.appendQueryParameter(BACKEND_NAME, transportContext.getBackendName());
    intentDataBuilder.appendQueryParameter(
        EVENT_PRIORITY, String.valueOf(PriorityMapping.toInt(transportContext.getPriority())));
    if (transportContext.getExtras() != null) {
      intentDataBuilder.appendQueryParameter(
          EXTRAS, Base64.encodeToString(transportContext.getExtras(), Base64.DEFAULT));
    }
    Intent intent = new Intent(context, AlarmManagerSchedulerBroadcastReceiver.class);
    intent.setData(intentDataBuilder.build());
    intent.putExtra(ATTEMPT_NUMBER, attemptNumber);

    if (isJobServiceOn(intent)) {
      Logging.d(
          LOG_TAG, "Upload for context %s is already scheduled. Returning...", transportContext);
      return;
    }

    long backendTime = eventStore.getNextCallTime(transportContext);

    long scheduleDelay =
        config.getScheduleDelay(transportContext.getPriority(), backendTime, attemptNumber);

    Logging.d(
        LOG_TAG,
        "Scheduling upload for context %s in %dms(Backend next call timestamp %d). Attempt %d",
        transportContext,
        scheduleDelay,
        backendTime,
        attemptNumber);

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    this.alarmManager.set(
        AlarmManager.ELAPSED_REALTIME, clock.getTime() + scheduleDelay, pendingIntent);
  }
}
