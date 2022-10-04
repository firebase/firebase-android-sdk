// Copyright 2020 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
//
// You may obtain a copy of the License at
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.perf.metrics;

import androidx.annotation.NonNull;
import com.google.firebase.perf.session.PerfSession;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.Arrays;
import java.util.Map;

/**
 * Builds {@link TraceMetric} from {@link Trace} object
 *
 * @hide
 */
class TraceMetricBuilder {

  private final Trace trace;

  TraceMetricBuilder(@NonNull Trace trace) {
    this.trace = trace;
  }

  TraceMetric build() {
    TraceMetric.Builder traceMetric =
        TraceMetric.newBuilder()
            .setName(trace.getName())
            .setClientStartTimeUs(trace.getStartTime().getMicros())
            .setDurationUs(trace.getStartTime().getDurationMicros(trace.getEndTime()));
    Map<String, Counter> traceCounters = trace.getCounters();

    for (Counter counter : traceCounters.values()) {
      traceMetric.putCounters(counter.getName(), counter.getCount());
    }

    traceMetric.putAllCustomAttributes(trace.getAttributes());

    com.google.firebase.perf.v1.PerfSession[] perfSessions =
        PerfSession.buildAndSort(trace.getSessions());
    if (perfSessions != null) {
      traceMetric.addAllPerfSessions(Arrays.asList(perfSessions));
    }

    return traceMetric.build();
  }
}
