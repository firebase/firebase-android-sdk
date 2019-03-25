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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.PowerManager;


import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.SchedulerUtil;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;

import javax.inject.Inject;

public class AlarmManagerSchedulerService extends BroadcastReceiver {

    @Inject WorkScheduler workScheduler;
    @Inject EventStore eventStore;
    @Inject BackendRegistry backendRegistry;

    @Override
    public void onReceive(Context context, Intent intent)
    {
        TransportRuntime.initialize(context);
        TransportRuntime.getInstance().injectMembers(this);
        PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wl = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "");
        wl.acquire(20000);
        String backendName = intent.getData().getQueryParameter(SchedulerUtil.BACKEND_NAME_CONSTANT);
        int numberOfAttempts = intent.getExtras().getInt(SchedulerUtil.NUMBER_OF_ATTEMPTS_CONSTANT);
        if(!ServiceUtil.isNetworkAvailable(context)) {
            workScheduler.schedule(backendName, numberOfAttempts+1);
            return;
        }
        ServiceUtil.logAndUpdateState(backendRegistry, workScheduler, backendName, eventStore, numberOfAttempts);
        wl.release();
    }

}