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

package com.google.android.datatransport.runtime.scheduling;

import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Scheduler which persists the events, schedules the services which ultimately logs these events to
 * the corresponding backends. This respects network conditions and QoS.
 */
public class DefaultScheduler implements Scheduler {

  private static final Logger LOGGER = Logger.getLogger(TransportRuntime.class.getName());
  private final WorkScheduler workScheduler;
  private final Executor executor;
  private final BackendRegistry backendRegistry;
  private final EventStore eventStore;
  private final SynchronizationGuard guard;

  @Inject
  public DefaultScheduler(
      Executor executor,
      BackendRegistry backendRegistry,
      WorkScheduler workScheduler,
      EventStore eventStore,
      SynchronizationGuard guard) {
    this.executor = executor;
    this.backendRegistry = backendRegistry;
    this.workScheduler = workScheduler;
    this.eventStore = eventStore;
    this.guard = guard;
  }

  /**
   * Schedules the events to be eventually sent to the backend.
   *
   * @param transportContext The transport context with which the event needs to be sent.
   * @param event The event itself which needs to be logged with additional information.
   */
  @Override
  public void schedule(
      TransportContext transportContext, EventInternal event, TransportScheduleCallback callback) {
    executor.execute(
        () -> {
          try {
            TransportBackend transportBackend =
                backendRegistry.get(transportContext.getBackendName());
            if (transportBackend == null) {
              String errorMsg =
                  String.format(
                      "Transport backend '%s' is not registered",
                      transportContext.getBackendName());
              LOGGER.warning(errorMsg);
              callback.onSchedule(new IllegalArgumentException(errorMsg));
              return;
            }
            EventInternal decoratedEvent = transportBackend.decorate(event);
            guard.runCriticalSection(
                () -> {
                  eventStore.persist(transportContext, decoratedEvent);
                  workScheduler.schedule(transportContext, 1);
                  return null;
                });
            callback.onSchedule(null);
          } catch (Exception e) {
            LOGGER.warning("Error scheduling event " + e.getMessage());
            callback.onSchedule(e);
          }
        });
  }
}
