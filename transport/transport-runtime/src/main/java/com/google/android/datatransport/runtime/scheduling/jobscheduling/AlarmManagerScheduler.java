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
import android.support.annotation.RestrictTo;
import android.support.annotation.VisibleForTesting;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;
import javax.inject.Inject;

/**
 * Schedules the service {@link AlarmManagerSchedulerBroadcastReceiver} based on the backendname.
 * Used for Apis 20 and below.
 */
public class AlarmManagerScheduler implements WorkScheduler {

  private final Context context;

  private final EventStore eventStore;

  private final Clock clock;

  private AlarmManager alarmManager;

  private final int DELTA = 30000; // 30 seconds delta

  @Inject
  public AlarmManagerScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
    this.alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    ;
  }

  @RestrictTo(RestrictTo.Scope.TESTS)
  @VisibleForTesting
  AlarmManagerScheduler(
      Context applicationContext, EventStore eventStore, Clock clock, AlarmManager alarmManager) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
    this.alarmManager = alarmManager;
  }

  @VisibleForTesting
  boolean isJobServiceOn(Intent intent) {
    return (PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
  }

  /**
   * Schedules the AlarmManager service.
   *
   * @param backendName The backend to where the events are logged.
   * @param attemptNumber Number of times the AlarmManager has tried to log for this backend.
   */
  @Override
  public void schedule(TransportContext transportContext, int attemptNumber) {
    // Create Intent
    String backendName = transportContext.getBackendName();
    Uri.Builder intentDataBuilder = new Uri.Builder();
    intentDataBuilder.appendQueryParameter(SchedulerUtil.BACKEND_NAME, backendName);
    intentDataBuilder.appendQueryParameter(
        SchedulerUtil.APPLICATION_BUNDLE_ID, context.getPackageName());
    Intent intent = new Intent(context, AlarmManagerSchedulerBroadcastReceiver.class);
    intent.setData(intentDataBuilder.build());
    intent.putExtra(SchedulerUtil.ATTEMPT_NUMBER, attemptNumber);

    if (isJobServiceOn(intent)) return;

    Long backendTime = eventStore.getNextCallTime(transportContext);

    long timeDiff = 0;
    if (backendTime != null) {
      timeDiff = backendTime - clock.getTime();
    }

    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    this.alarmManager.set(
        AlarmManager.ELAPSED_REALTIME,
        SchedulerUtil.getScheduleDelay(timeDiff, DELTA, attemptNumber),
        pendingIntent);
  }
}
