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
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;

import com.google.android.datatransport.runtime.BuildConfig;
import com.google.android.datatransport.runtime.scheduling.Scheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.time.Clock;

public class AlarmManagerScheduler implements WorkScheduler {

  private final Context context;

  private final EventStore eventStore;

  private Clock clock;

  public AlarmManagerScheduler(Context applicationContext, EventStore eventStore, Clock clock) {
    this.context = applicationContext;
    this.eventStore = eventStore;
    this.clock = clock;
  }

  private boolean isJobServiceOn(Intent intent) {
    return (PendingIntent.getBroadcast(context, 0,
            intent,
            PendingIntent.FLAG_NO_CREATE) != null);
  }

  @Override
  public void schedule(String backendName, int numberOfAttempts) {
    // Create Intent
    Uri.Builder intentDataBuilder = new Uri.Builder();
    intentDataBuilder.appendQueryParameter(SchedulerUtil.BACKEND_NAME_CONSTANT, backendName);
    intentDataBuilder.appendQueryParameter(SchedulerUtil.APPLICATION_BUNDLE_ID, BuildConfig.APPLICATION_ID);
    Intent intent = new Intent(context, AlarmManagerScheduler.class);
    intent.setData(intentDataBuilder.build());
    intent.putExtra(SchedulerUtil.NUMBER_OF_ATTEMPTS_CONSTANT, numberOfAttempts);

    if(isJobServiceOn(intent)) return;

    long timeDiff = eventStore.getNextCallTime(backendName)-clock.getTime();

    AlarmManager alarmManager =( AlarmManager)context.getSystemService(Context.ALARM_SERVICE);
    PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent, 0);
    alarmManager.set(AlarmManager.RTC_WAKEUP, SchedulerUtil.getScheduleDelay(timeDiff, 5000, numberOfAttempts), pendingIntent);
  }
}
