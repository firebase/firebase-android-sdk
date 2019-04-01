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

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.BackendRequest;
import com.google.android.datatransport.runtime.BackendResponse;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/** Handles upload of all the events corresponding to a backend. */
public class Uploader {

  private final int LOCK_TIMEOUT = 10000;
  private final Context context;
  private final BackendRegistry backendRegistry;
  private final EventStore eventStore;
  private final WorkScheduler workScheduler;
  private final Executor executor;
  private final SynchronizationGuard guard;

  @Inject
  public Uploader(
      Context context,
      BackendRegistry backendRegistry,
      EventStore eventStore,
      WorkScheduler workScheduler,
      Executor executor,
      SynchronizationGuard guard) {
    this.context = context;
    this.backendRegistry = backendRegistry;
    this.eventStore = eventStore;
    this.workScheduler = workScheduler;
    this.executor = executor;
    this.guard = guard;
  }

  boolean isNetworkAvailable() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    if (connectivityManager == null) {
      return false;
    }
    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  void upload(String backendName, int attemptNumber, Runnable callback) {
    this.executor.execute(
        () -> {
          try {
            if (!isNetworkAvailable()) {
              guard.runCriticalSection(
                  LOCK_TIMEOUT,
                  () -> {
                    workScheduler.schedule(backendName, attemptNumber + 1);
                    return null;
                  });
            } else {
              logAndUpdateState(backendName, attemptNumber);
            }
          } finally {
            callback.run();
          }
        });
  }

  void logAndUpdateState(String backendName, int attemptNumber) {
    TransportBackend backend = backendRegistry.get(backendName);
    List<EventInternal> eventInternals = new ArrayList<>();
    Iterable<PersistedEvent> persistedEvents =
        guard.runCriticalSection(LOCK_TIMEOUT, () -> eventStore.loadAll(backendName));

    // Donot make a call to the backend if the list is empty.
    if (!persistedEvents.iterator().hasNext()) {
      return;
    }
    // Load all the backends to an iterable of event internals.
    for (PersistedEvent persistedEvent : persistedEvents) {
      eventInternals.add(persistedEvent.getEvent());
    }
    BackendResponse response = backend.send(BackendRequest.create(eventInternals));
    guard.runCriticalSection(
        LOCK_TIMEOUT,
        () -> {
          if (response.getStatus() == BackendResponse.Status.OK) {
            eventStore.recordSuccess(persistedEvents);
            eventStore.recordNextCallTime(backendName, response.getNextRequestWaitMillis());
          } else if (response.getStatus() == BackendResponse.Status.TRANSIENT_ERROR) {
            eventStore.recordFailure(persistedEvents);
            workScheduler.schedule(backendName, (int) attemptNumber + 1);
          } else {
            eventStore.recordSuccess(persistedEvents);
          }
          return null;
        });
  }
}
