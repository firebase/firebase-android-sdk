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
import androidx.annotation.VisibleForTesting;
import com.google.android.datatransport.Encoding;
import com.google.android.datatransport.runtime.EncodedPayload;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import com.google.android.datatransport.runtime.backends.BackendRegistry;
import com.google.android.datatransport.runtime.backends.BackendRequest;
import com.google.android.datatransport.runtime.backends.BackendResponse;
import com.google.android.datatransport.runtime.backends.TransportBackend;
import com.google.android.datatransport.runtime.firebase.transport.ClientMetrics;
import com.google.android.datatransport.runtime.firebase.transport.LogEventDropped;
import com.google.android.datatransport.runtime.logging.Logging;
import com.google.android.datatransport.runtime.scheduling.persistence.ClientHealthMetricsStore;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;
import com.google.android.datatransport.runtime.scheduling.persistence.PersistedEvent;
import com.google.android.datatransport.runtime.synchronization.SynchronizationException;
import com.google.android.datatransport.runtime.synchronization.SynchronizationGuard;
import com.google.android.datatransport.runtime.time.Clock;
import com.google.android.datatransport.runtime.time.Monotonic;
import com.google.android.datatransport.runtime.time.WallTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import javax.inject.Inject;

/** Handles upload of all the events corresponding to a backend. */
public class Uploader {

  private static final String LOG_TAG = "Uploader";
  private static final String CLIENT_HEALTH_METRICS_LOG_SOURCE = "GDT_CLIENT_METRICS";

  private final Context context;
  private final BackendRegistry backendRegistry;
  private final EventStore eventStore;
  private final WorkScheduler workScheduler;
  private final Executor executor;
  private final SynchronizationGuard guard;
  private final Clock clock;
  private final Clock uptimeClock;
  private final ClientHealthMetricsStore clientHealthMetricsStore;

  @Inject
  public Uploader(
      Context context,
      BackendRegistry backendRegistry,
      EventStore eventStore,
      WorkScheduler workScheduler,
      Executor executor,
      SynchronizationGuard guard,
      @WallTime Clock clock,
      @Monotonic Clock uptimeClock,
      ClientHealthMetricsStore clientHealthMetricsStore) {
    this.context = context;
    this.backendRegistry = backendRegistry;
    this.eventStore = eventStore;
    this.workScheduler = workScheduler;
    this.executor = executor;
    this.guard = guard;
    this.clock = clock;
    this.uptimeClock = uptimeClock;
    this.clientHealthMetricsStore = clientHealthMetricsStore;
  }

  boolean isNetworkAvailable() {
    ConnectivityManager connectivityManager =
        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
    NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
    return activeNetworkInfo != null && activeNetworkInfo.isConnected();
  }

  public void upload(TransportContext transportContext, int attemptNumber, Runnable callback) {
    this.executor.execute(
        () -> {
          try {
            guard.runCriticalSection(eventStore::cleanUp);
            if (!isNetworkAvailable()) {
              guard.runCriticalSection(
                  () -> {
                    workScheduler.schedule(transportContext, attemptNumber + 1);
                    return null;
                  });
            } else {
              logAndUpdateState(transportContext, attemptNumber);
            }
          } catch (SynchronizationException e) {
            workScheduler.schedule(transportContext, attemptNumber + 1);
          } finally {
            callback.run();
          }
        });
  }

  void logAndUpdateState(TransportContext transportContext, int attemptNumber) {
    TransportBackend backend = backendRegistry.get(transportContext.getBackendName());
    long maxNextRequestWaitMillis = 0;

    while (guard.runCriticalSection(() -> eventStore.hasPendingEventsFor(transportContext))) {
      Iterable<PersistedEvent> persistedEvents =
          guard.runCriticalSection(() -> eventStore.loadBatch(transportContext));

      // Do not make a call to the backend if the list is empty.
      if (!persistedEvents.iterator().hasNext()) {
        return;
      }

      BackendResponse response;
      if (backend == null) {
        Logging.d(
            LOG_TAG, "Unknown backend for %s, deleting event batch for it...", transportContext);
        response = BackendResponse.fatalError();
      } else {
        List<EventInternal> eventInternals = new ArrayList<>();

        for (PersistedEvent persistedEvent : persistedEvents) {
          eventInternals.add(persistedEvent.getEvent());
        }

        if (transportContext.shouldUploadClientHealthMetrics()) {
          eventInternals.add(createMetricsEvent(backend));
        }

        response =
            backend.send(
                BackendRequest.builder()
                    .setEvents(eventInternals)
                    .setExtras(transportContext.getExtras())
                    .build());
      }
      if (response.getStatus() == BackendResponse.Status.TRANSIENT_ERROR) {
        long finalMaxNextRequestWaitMillis1 = maxNextRequestWaitMillis;
        guard.runCriticalSection(
            () -> {
              eventStore.recordFailure(persistedEvents);
              eventStore.recordNextCallTime(
                  transportContext, clock.getTime() + finalMaxNextRequestWaitMillis1);
              return null;
            });
        workScheduler.schedule(transportContext, attemptNumber + 1, true);
        return;
      } else {
        guard.runCriticalSection(
            () -> {
              eventStore.recordSuccess(persistedEvents);
              return null;
            });
        if (response.getStatus() == BackendResponse.Status.OK) {
          maxNextRequestWaitMillis =
              Math.max(maxNextRequestWaitMillis, response.getNextRequestWaitMillis());
          if (transportContext.shouldUploadClientHealthMetrics()) {
            guard.runCriticalSection(
                () -> {
                  clientHealthMetricsStore.resetClientMetrics();
                  return null;
                });
          }
        } else if (response.getStatus() == BackendResponse.Status.INVALID_PAYLOAD) {
          Map<String, Integer> countMap = new HashMap<>();
          for (PersistedEvent persistedEvent : persistedEvents) {
            String logSource = persistedEvent.getEvent().getTransportName();
            if (!countMap.containsKey(logSource)) {
              countMap.put(logSource, 1);
            } else {
              countMap.put(logSource, countMap.get(logSource) + 1);
            }
          }
          guard.runCriticalSection(
              () -> {
                for (Map.Entry<String, Integer> entry : countMap.entrySet()) {
                  clientHealthMetricsStore.recordLogEventDropped(
                      entry.getValue(), LogEventDropped.Reason.INVALID_PAYLOD, entry.getKey());
                }
                return null;
              });
        }
      }
    }
    long finalMaxNextRequestWaitMillis = maxNextRequestWaitMillis;
    guard.runCriticalSection(
        () -> {
          eventStore.recordNextCallTime(
              transportContext, clock.getTime() + finalMaxNextRequestWaitMillis);
          return null;
        });
  }

  @VisibleForTesting
  public EventInternal createMetricsEvent(TransportBackend backend) {
    ClientMetrics clientMetrics =
        guard.runCriticalSection(clientHealthMetricsStore::loadClientMetrics);
    return backend.decorate(
        EventInternal.builder()
            .setEventMillis(clock.getTime())
            .setUptimeMillis(uptimeClock.getTime())
            .setTransportName(CLIENT_HEALTH_METRICS_LOG_SOURCE)
            .setEncodedPayload(
                new EncodedPayload(Encoding.of("proto"), clientMetrics.toByteArray()))
            .build());
  }
}
