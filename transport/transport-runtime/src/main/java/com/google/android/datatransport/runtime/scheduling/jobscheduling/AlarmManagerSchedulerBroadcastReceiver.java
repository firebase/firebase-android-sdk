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

import static android.util.Base64.*;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.util.PriorityMapping;

/** The service responsible for uploading information to the backend. */
public class AlarmManagerSchedulerBroadcastReceiver extends BroadcastReceiver {

  @Override
  public void onReceive(Context context, Intent intent) {
    String backendName = intent.getData().getQueryParameter(AlarmManagerScheduler.BACKEND_NAME);
    String extras = intent.getData().getQueryParameter(AlarmManagerScheduler.EXTRAS);
    int priority =
        Integer.valueOf(intent.getData().getQueryParameter(AlarmManagerScheduler.EVENT_PRIORITY));
    int attemptNumber = intent.getExtras().getInt(AlarmManagerScheduler.ATTEMPT_NUMBER);
    TransportRuntime.initialize(context);

    TransportContext.Builder transportContext =
        TransportContext.builder()
            .setBackendName(backendName)
            .setPriority(PriorityMapping.valueOf(priority));

    if (extras != null) {
      transportContext.setExtras(decode(extras, DEFAULT));
    }

    TransportRuntime.getInstance()
        .getUploader()
        .upload(transportContext.build(), attemptNumber, () -> {});
  }
}
