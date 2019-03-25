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


import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.BackendResponse;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;

import java.util.ArrayList;
import java.util.List;

public class ServiceUtil {

    static boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager
                = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }

    static void logAndUpdateState(BackendRegistry backendRegistry, WorkScheduler workScheduler, String backendName, EventStore eventStore, int numberOfAttempts) {
        TransportBackend backend = backendRegistry.get(backendName);
        List<EventInternal> eventInternals = new ArrayList<>();
        Iterable<PersistedEvent> persistedEvents = eventStore.loadAll(backendName);

        // Donot make a call to the backend if the list is empty.
        if(!persistedEvents.iterator().hasNext()) {
            return;
        }
        // Load all the backends to an iterable of event internals.
        for (PersistedEvent persistedEvent : persistedEvents) {
            eventInternals.add(persistedEvent.getEvent());
        }
        BackendResponse response = backend.send(eventInternals);
        if (response.getStatus() == BackendResponse.Status.OK) {
            eventStore.recordSuccess(persistedEvents);
            eventStore.recordNextCallTime(backendName, response.getNextRequestWaitMillis());
        } else if (response.getStatus() == BackendResponse.Status.TRANSIENT_ERROR) {
            eventStore.recordFailure(persistedEvents);
            workScheduler.schedule(backendName, (int) numberOfAttempts+1);
        } else {
            eventStore.recordSuccess(persistedEvents);
        }
    }
}   