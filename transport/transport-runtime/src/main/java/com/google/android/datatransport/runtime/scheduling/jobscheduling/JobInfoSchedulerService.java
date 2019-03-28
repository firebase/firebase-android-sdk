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

import android.app.job.JobParameters;
import android.app.job.JobService;
import android.os.Build;
import android.support.annotation.RequiresApi;
import com.google.android.datatransport.runtime.TransportRuntime;

/** The service responsible for uploading information to the backend. */
@RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class JobInfoSchedulerService extends JobService {

  @Override
  public boolean onStartJob(JobParameters params) {
    String backendName = params.getExtras().getString(SchedulerUtil.BACKEND_NAME);
    int attemptNumber = params.getExtras().getInt(SchedulerUtil.ATTEMPT_NUMBER);
    TransportRuntime.initialize(getApplicationContext());
    TransportRuntime.getInstance()
        .getUploader()
        .upload(backendName, attemptNumber, () -> this.jobFinished(params, false));
    return true;
  }

  @Override
  public boolean onStopJob(JobParameters params) {
    return true;
  }
}
