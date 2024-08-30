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

import android.content.Context;
import android.os.Build;
import android.util.Base64;
import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.work.Data;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.util.PriorityMapping;

@RequiresApi(api = Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class WorkManagerSchedulerWorker extends Worker {

  public WorkManagerSchedulerWorker(
      @NonNull Context context, @NonNull WorkerParameters workerParams) {
    super(context, workerParams);
  }

  @NonNull
  @Override
  public Result doWork() {
    Data data = getInputData();
    String backendName = data.getString(JobInfoScheduler.BACKEND_NAME);
    String extras = data.getString(JobInfoScheduler.EXTRAS);

    int priority = data.getInt(JobInfoScheduler.EVENT_PRIORITY, 0);
    int attemptNumber = data.getInt(JobInfoScheduler.ATTEMPT_NUMBER, 0);
    TransportRuntime.initialize(getApplicationContext());
    TransportContext.Builder transportContext =
        TransportContext.builder()
            .setBackendName(backendName)
            .setPriority(PriorityMapping.valueOf(priority));

    if (extras != null) {
      transportContext.setExtras(Base64.decode(extras, Base64.DEFAULT));
    }

    TransportRuntime.getInstance()
        .getUploader()
        .upload(transportContext.build(), attemptNumber, () -> {});
    return Result.success();
  }
}
