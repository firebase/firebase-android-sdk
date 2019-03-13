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
import android.content.Intent;
import android.os.Build;
import android.webkit.JavascriptInterface;

import com.google.android.datatransport.Transport;
import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.BackendResponse;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportBackend;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoScheduler;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/**
 * JobService to be scheduled by the JobScheduler.
 * start another service
 */
@android.support.annotation.RequiresApi(api = Build.VERSION_CODES.LOLLIPOP)
public class JobInfoSchedulerService extends JobService {
    private static final String TAG = "SyncService";

    @Inject
    private BackendRegistry backendRegistry;
    @Inject
    private WorkScheduler workScheduler;

    @Override
    public void onCreate() {
        TransportRuntime.initialize(getApplicationContext());
        TransportRuntime.getInstance().injectService(this);
        super.onCreate();
    }

    @Override
    public boolean onStartJob(JobParameters params) {
        for(String backendName: backendRegistry.getAllBackendNames()) {
            TransportBackend backend = backendRegistry.get(backendName);
            List<EventInternal> eventInternals = new ArrayList<>();
            // Load all the backends to an iterable of event internals.
            BackendResponse response = backend.send(eventInternals);
            if(response.getStatus() == BackendResponse.Status.OK) {
                // call success method of the database with the persisted events.
            }
            else if(response.getStatus() == BackendResponse.Status.TRANSIENT_ERROR) {
               // call failure
            }
            else  {
                // call success
            }
        }
        workScheduler.schedule();
        return true;
    }

    @Override
    public boolean onStopJob(JobParameters params) {
        return false;
    }

}