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
