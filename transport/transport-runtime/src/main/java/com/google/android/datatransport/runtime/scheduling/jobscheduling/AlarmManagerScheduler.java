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

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import com.google.android.datatransport.runtime.BuildConfig;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;

import javax.inject.Inject;

public class AlarmManagerScheduler implements WorkScheduler {

  private final Context context;

  private final EventStore eventStore;

  private final Clock clock;

  @Inject
  public AlarmManagerScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  private boolean isJobServiceOn(Intent intent) {
    return (PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_NO_CREATE) != null);
  }

  @Override
  public void schedule(String backendName, int attemptNumber) {
    // Create Intent
    Uri.Builder intentDataBuilder = new Uri.Builder();
    intentDataBuilder.appendQueryParameter(SchedulerUtil.BACKEND_NAME, backendName);
    intentDataBuilder.appendQueryParameter(
        SchedulerUtil.APPLICATION_BUNDLE_ID, BuildConfig.APPLICATION_ID);
    Intent intent = new Intent(context, AlarmManagerScheduler.class);
    intent.setData(intentDataBuilder.build());
    intent.putExtra(SchedulerUtil.ATTEMPT_NUMBER, attemptNumber);

    if (isJobServiceOn(intent)) return;

    long timeDiff = eventStore.getNextCallTime(backendName) - clock.getTime();

    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    alarmManager.set(
        AlarmManager.RTC_WAKEUP,
        SchedulerUtil.getScheduleDelay(timeDiff, 5000, attemptNumber + 1),
        pendingIntent);
  }
}
