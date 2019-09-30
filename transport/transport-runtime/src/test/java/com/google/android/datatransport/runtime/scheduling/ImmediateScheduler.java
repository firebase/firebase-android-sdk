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

package com.google.android.datatransport.runtime.scheduling;

import com.google.android.datatransport.TransportScheduleCallback;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.TransportRuntime;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.logging.Logger;
import javax.inject.Inject;

/**
 * Simple scheduler implementation that tries to transmit event immediately.
 *
 * <p>It does not respect QoS of the event, network conditions, backend wait time millis.
 */
public class ImmediateScheduler implements Scheduler {
  private static final Logger LOGGER = Logger.getLogger(TransportRuntime.class.getName());

  private final Executor executor;
  private final BackendRegistry backendRegistry;

  @Inject
  public ImmediateScheduler(Executor executor, BackendRegistry backendRegistry) {
    this.executor = executor;
    this.backendRegistry = backendRegistry;
  }

  @Override
  public void schedule(
      TransportContext transportContext, EventInternal event, TransportScheduleCallback callback) {
    executor.execute(
        () -> {
          try {
            TransportBackend backend = backendRegistry.get(transportContext.getBackendName());
            if (backend == null) {
              String errorMsg =
                  String.format(
                      "Transport backend '%s' is not registered",
                      transportContext.getBackendName());
              LOGGER.warning(errorMsg);
              callback.onSchedule(new IllegalArgumentException(errorMsg));
              return;
            }
            backend.send(BackendRequest.create(Collections.singleton(backend.decorate(event))));
            callback.onSchedule(null);
          } catch (Exception e) {
            callback.onSchedule(e);
          }
        });
  }
}
