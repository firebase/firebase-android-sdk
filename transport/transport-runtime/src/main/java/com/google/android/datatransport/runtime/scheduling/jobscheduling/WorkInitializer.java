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

import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/**
 * Re-schedules any contexts that have pending events and are not currently scheduled.
 *
 * <p>The reasons for them not to be scheduled include:
 *
 * <ul>
 *   <li>Host application update to newer version
 *   <li>Device restart
 * </ul>
 *
 * Note: there is no way for us to know how many attempts had been previously tried, so we
 * re-schedule from attempt 1.
 */
public class WorkInitializer {
  private final Executor executor;
  private final EventStore store;
  private final WorkScheduler scheduler;
  private final SynchronizationGuard guard;

  @Inject
  WorkInitializer(
      Executor executor, EventStore store, WorkScheduler scheduler, SynchronizationGuard guard) {
    this.executor = executor;
    this.store = store;
    this.scheduler = scheduler;
    this.guard = guard;
  }

  public void ensureContextsScheduled() {
    executor.execute(
        () ->
            guard.runCriticalSection(
                () -> {
                  for (TransportContext context : store.loadActiveContexts()) {
                    scheduler.schedule(context, 1);
                  }
                  return null;
                }));
  }
}
