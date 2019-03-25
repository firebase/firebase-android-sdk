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

package com.google.android.datatransport.runtime.scheduling.jobscheduling.service;

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.content.Context;
import android.os.Build;
import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.SchedulerUtil;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import javax.inject.Inject;

/** JobService to be scheduled by the JobScheduler. start another service */
@android.support.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class JobInfoSchedulerService extends JobService {
  private static final String TAG = "SyncService";

  @Inject BackendRegistry backendRegistry;
  @Inject WorkScheduler workScheduler;
  @Inject EventStore eventStore;
  @Inject Context context;

  @Override
  public void onCreate() {
    super.onCreate();
    TransportRuntime.initialize(getApplicationContext());
    TransportRuntime.getInstance().injectMembers(this);
  }

  @Override
  public boolean onStartJob(JobParameters params) {
    String backendName = params.getExtras().getString(SchedulerUtil.BACKEND_NAME_CONSTANT);
    long numberOfAttempts = params.getExtras().getLong(SchedulerUtil.NUMBER_OF_ATTEMPTS_CONSTANT);

    // If there is no network available reschedule.
    if(!ServiceUtil.isNetworkAvailable(context)) {
        workScheduler.schedule(backendName, (int)numberOfAttempts+1);
        return true;
    }
    ServiceUtil.logAndUpdateState(backendRegistry, workScheduler, backendName, eventStore, (int)numberOfAttempts);
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    return false;
  }
}
