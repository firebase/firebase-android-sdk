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

package com.google.firebase.perf.metrics;

import com.google.firebase.monitoring.ExtendedTracer;
import com.google.firebase.monitoring.TraceHandle;
import com.google.firebase.perf.util.Timer;
import com.google.firebase.time.Instant;

/** @hide */
public class FirebasePerfInternalTracer implements ExtendedTracer {
  @Override
  public void recordTrace(String name, Instant start, Instant end, String... attrs) {
    Trace trace = Trace.create(name);
    trace.start(new Timer(start.getMicros(), start.getNanos()));
    if (attrs.length != 0) {
      if (attrs.length % 2 != 0) {
        throw new IllegalArgumentException(
            "Key-value pairs expected but got odd number of arguments");
      }
      for (int i = 1; i < attrs.length; i += 2) {
        trace.putAttribute(attrs[i - 1], attrs[i]);
      }
    }
    trace.stop(new Timer(end.getMicros(), end.getNanos()));
  }

  @Override
  public TraceHandle startTrace(String name) {
    Trace trace = Trace.create(name);
    trace.start();
    return new FirebasePerfTraceHandle(trace);
  }

  static class FirebasePerfTraceHandle implements TraceHandle {
    private final Trace trace;

    FirebasePerfTraceHandle(Trace trace) {
      this.trace = trace;
    }

    @Override
    public void addAttribute(String name, String value) {
      trace.putAttribute(name, value);
    }

    @Override
    public void close() {
      trace.stop();
    }
  }
}
