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

package com.google.android.datatransport.runtime.scheduling.persistence;

import androidx.annotation.Nullable;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** In memory implementation used for development/testing. */
public class InMemoryEventStore implements EventStore {
  private final AtomicLong idCounter = new AtomicLong();
  private final Map<TransportContext, Map<Long, EventInternal>> store = new HashMap<>();
  private final Map<TransportContext, Long> backendCallTime = new HashMap<>();

  @Override
  @Nullable
  public synchronized PersistedEvent persist(
      TransportContext transportContext, EventInternal event) {

    long newId = idCounter.incrementAndGet();
    getOrCreateBackendStore(transportContext).put(newId, event);

    return PersistedEvent.create(newId, transportContext, event);
  }

  private Map<Long, EventInternal> getOrCreateBackendStore(TransportContext transportContext) {
    if (!store.containsKey(transportContext)) {
      store.put(transportContext, new HashMap<>());
    }
    return store.get(transportContext);
  }

  @Override
  public synchronized void recordFailure(Iterable<PersistedEvent> events) {
    // discard failed events.
    recordSuccess(events);
  }

  @Override
  public synchronized void recordSuccess(Iterable<PersistedEvent> events) {
    for (PersistedEvent event : events) {
      Map<Long, EventInternal> backendStore = store.get(event.getTransportContext());
      if (backendStore == null) {
        return;
      }
      backendStore.remove(event.getId());
    }
  }

  @Override
  public long getNextCallTime(TransportContext transportContext) {
    Long nextCalltime = backendCallTime.get(transportContext);
    if (nextCalltime == null) {
      return 0;
    } else {
      return nextCalltime;
    }
  }

  @Override
  public void recordNextCallTime(TransportContext transportContext, long timestampMs) {
    backendCallTime.put(transportContext, timestampMs);
  }

  @Override
  public synchronized boolean hasPendingEventsFor(TransportContext transportContext) {
    Map<Long, EventInternal> backendStore = store.get(transportContext);
    if (backendStore == null) {
      return false;
    }
    return !backendStore.isEmpty();
  }

  @Override
  public synchronized Iterable<PersistedEvent> loadBatch(TransportContext transportContext) {
    Map<Long, EventInternal> backendStore = store.get(transportContext);
    if (backendStore == null) {
      return Collections.emptyList();
    }
    List<PersistedEvent> events = new ArrayList<>();
    for (Map.Entry<Long, EventInternal> entry : backendStore.entrySet()) {
      events.add(PersistedEvent.create(entry.getKey(), transportContext, entry.getValue()));
    }
    return events;
  }

  @Override
  public synchronized Iterable<TransportContext> loadActiveContexts() {
    List<TransportContext> results = new ArrayList<>();
    for (Map.Entry<TransportContext, Map<Long, EventInternal>> entry : store.entrySet()) {
      if (entry.getValue().isEmpty()) {
        continue;
      }
      results.add(entry.getKey());
    }
    return results;
  }

  @Override
  public int cleanUp() {
    return 0;
  }

  @Override
  public void close() {}
}
