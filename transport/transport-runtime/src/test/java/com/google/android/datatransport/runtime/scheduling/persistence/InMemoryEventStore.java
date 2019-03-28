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

import com.google.android.datatransport.runtime.EventInternal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** In memory implementation used for development/testing. */
public class InMemoryEventStore implements EventStore {
  private final AtomicLong idCounter = new AtomicLong();
  private final Map<String, Map<Long, EventInternal>> store = new HashMap<>();
  private final Map<String, Long> backendCallTime = new HashMap<>();

  @Override
  public synchronized PersistedEvent persist(String backendName, EventInternal event) {
    long newId = idCounter.incrementAndGet();
    getOrCreateBackendStore(backendName).put(newId, event);

    return PersistedEvent.create(newId, backendName, event);
  }

  private Map<Long, EventInternal> getOrCreateBackendStore(String backendName) {
    if (!store.containsKey(backendName)) {
      store.put(backendName, new HashMap<>());
    }
    return store.get(backendName);
  }

  @Override
  public synchronized void recordFailure(Iterable<PersistedEvent> events) {
    // discard failed events.
    recordSuccess(events);
  }

  @Override
  public synchronized void recordSuccess(Iterable<PersistedEvent> events) {
    for (PersistedEvent event : events) {
      Map<Long, EventInternal> backendStore = store.get(event.getBackendName());
      if (backendStore == null) {
        return;
      }
      backendStore.remove(event.getId());
    }
  }

  @Override
  public Long getNextCallTime(String backendName) {
    return backendCallTime.get(backendName);
  }

  @Override
  public void recordNextCallTime(String backendName, long timestampMs) {
    backendCallTime.put(backendName, timestampMs);
  }

  @Override
  public synchronized boolean hasPendingEventsFor(String backendName) {
    Map<Long, EventInternal> backendStore = store.get(backendName);
    if (backendStore == null) {
      return false;
    }
    return !backendStore.isEmpty();
  }

  @Override
  public synchronized Iterable<PersistedEvent> loadAll(String backendName) {
    Map<Long, EventInternal> backendStore = store.get(backendName);
    if (backendStore == null) {
      return Collections.emptyList();
    }
    List<PersistedEvent> events = new ArrayList<>();
    for (Map.Entry<Long, EventInternal> entry : backendStore.entrySet()) {
      events.add(PersistedEvent.create(entry.getKey(), backendName, entry.getValue()));
    }
    return events;
  }
}
