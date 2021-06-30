// Copyright 2021 Google LLC
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

import com.google.android.datatransport.runtime.firebase.transport.ClientMetrics;
import com.google.android.datatransport.runtime.firebase.transport.LogEventDropped.Reason;

/**
 * Persistent Layer
 *
 * <p>Responsible for storing, updating, and retrieving client analytics data.
 */
public interface ClientHealthMetricsStore {
  /**
   * Record log event dropped.
   *
   * @param eventsDroppedCount number of events dropped
   * @param reason reason why events are dropped
   * @param logSource log source of the dropped events
   */
  void recordLogEventDropped(long eventsDroppedCount, Reason reason, String logSource);

  /**
   * Load ClientMetrics.
   *
   * @return the returned ClientMetrics is fully populated.
   */
  ClientMetrics loadClientMetrics();

  /**
   * Reset Client Metrics, it will delete every row at log_event_dropped table. This method should
   * only be called once the Client Metrics are successfully sent to the server.
   */
  void resetClientMetrics();
}
