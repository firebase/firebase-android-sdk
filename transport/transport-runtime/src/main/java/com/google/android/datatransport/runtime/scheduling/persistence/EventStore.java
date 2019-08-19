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
import androidx.annotation.WorkerThread;
import com.google.android.datatransport.runtime.EventInternal;
import com.google.android.datatransport.runtime.TransportContext;
import java.io.Closeable;

/**
 * Persistence layer.
 *
 * <p>Responsible for storing events and backend-specific metadata.
 */
@WorkerThread
public interface EventStore extends Closeable {

  /** Persist a new event. */
  @Nullable
  PersistedEvent persist(TransportContext transportContext, EventInternal event);

  /** Communicate to the store that events have failed to get sent. */
  void recordFailure(Iterable<PersistedEvent> events);

  /** Communicate to the store that events have been sent successfully. */
  void recordSuccess(Iterable<PersistedEvent> events);

  /** Returns the timestamp when the backend is allowed to be called next time or zero. */
  long getNextCallTime(TransportContext transportContext);

  /** Record the timestamp when the backend is allowed to be called next time. */
  void recordNextCallTime(TransportContext transportContext, long timestampMs);

  /** Returns true if the store contains any pending events for a give backend. */
  boolean hasPendingEventsFor(TransportContext transportContext);

  /** Load all pending events for a given backend. */
  Iterable<PersistedEvent> loadBatch(TransportContext transportContext);

  /** Load all {@link TransportContext}s that have pending events. */
  Iterable<TransportContext> loadActiveContexts();

  /** Remove events that have been stored for more than 7 days. */
  int cleanUp();
}
