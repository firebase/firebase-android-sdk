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

import com.google.android.datatransport.runtime.BackendRegistry;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportBackend;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.scheduling.jobscheduling.WorkScheduler;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Placeholder for the eventual scheduler that will support persistence, retries, respect network
 * conditions and QoS.
 */
public class DefaultScheduler implements Scheduler {

  private final WorkScheduler workScheduler;
  private final Executor executor;
  private final BackendRegistry backendRegistry;
  private final EventStore eventStore;
  private static final Logger LOGGER = Logger.getLogger(TransportRuntime.class.getName());

  @Inject
  public DefaultScheduler(
      Executor executor,
      BackendRegistry backendRegistry,
      WorkScheduler workScheduler,
      EventStore eventStore) {
    this.executor = executor;
    this.backendRegistry = backendRegistry;
    this.workScheduler = workScheduler;
    this.eventStore = eventStore;
  }

  @Override
  public void schedule(String backendName, EventInternal event) {
    executor.execute(
        () -> {
          TransportBackend transportBackend = backendRegistry.get(backendName);
          if (transportBackend == null) {
            LOGGER.warning(String.format("Logger backend '%s' is not registered", backendName));
            return;
          }
          EventInternal decoratedEvent = transportBackend.decorate(event);
          eventStore.persist(backendName, decoratedEvent);
          workScheduler.schedule(backendName, 0);
        });
  }
}
