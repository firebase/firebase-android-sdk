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
import com.google.firebase.perf.internal.PerfSession;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds {@link TraceMetric} from {@link Trace} object
 *
 * @hide
 */
/** @hide */
class TraceMetricBuilder {
  private final Trace mTrace;

  TraceMetricBuilder(@NonNull Trace trace) {
    mTrace = trace;
  }

  TraceMetric build() {
    TraceMetric.Builder traceMetric =
        TraceMetric.newBuilder()
            .setName(mTrace.getName())
            .setClientStartTimeUs(mTrace.getStartTime().getMicros())
            .setDurationUs(mTrace.getStartTime().getDurationMicros(mTrace.getEndTime()));
    Map<String, Counter> traceCounters = mTrace.getCounters();

    for (Counter counter : traceCounters.values()) {
      traceMetric.putCounters(counter.getName(), counter.getCount());
    }

    List<Trace> subTraces = mTrace.getSubtraces();
    if (!subTraces.isEmpty()) {
      for (Trace subtrace : subTraces) {
        traceMetric.addSubtraces(new TraceMetricBuilder(subtrace).build());
      }
    }

    traceMetric.putAllCustomAttributes(mTrace.getAttributes());

    com.google.firebase.perf.v1.PerfSession[] perfSessions =
        PerfSession.buildAndSort(mTrace.getSessions());
    if (perfSessions != null) {
      traceMetric.addAllPerfSessions(Arrays.asList(perfSessions));
    }

    return traceMetric.build();
  }
}
