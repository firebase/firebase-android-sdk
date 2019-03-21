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

package com.google.android.datatransport.runtime.synchronization;

import android.support.annotation.WorkerThread;
import com.google.android.datatransport.runtime.scheduling.persistence.EventStore;

@WorkerThread
public interface SynchronizationGuard {
  interface CriticalSection<T> {
    T execute();
  }

  /**
   * All operations against the {@link EventStore} will be done as one atomic unit of work.
   *
   * <p>Example usage:
   *
   * <pre>{@code
   * store.atomically(() -> {
   *   store.persist("foo", event);
   *   store.recordSuccess(Collections.singleton(event));
   * });
   * }</pre>
   *
   * @param lockTimeoutMs how long to wait to acquire the lock before throwing {@link
   *     SynchronizationException}.
   * @param criticalSection Critical section to run while holding the lock.
   * @throws SynchronizationException if unable to enter critical section.
   */
  <T> T runCriticalSection(long lockTimeoutMs, CriticalSection<T> criticalSection);
}
