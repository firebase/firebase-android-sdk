package com.google.firebase.perf.metrics;

import com.google.firebase.monitoring.ExtendedTracer;
import com.google.firebase.monitoring.TraceHandle;
import com.google.firebase.perf.util.Timer;

/** @hide */
public class FirebasePerfInternalTracer implements ExtendedTracer {
  @Override
  public void recordTrace(String name, long startNanos, long endNanos, String... attrs) {
    Trace trace = Trace.create(name);
    trace.start(Timer.fromNanos(startNanos));
    if (attrs.length != 0) {
      if (attrs.length % 2 != 0) {
        throw new IllegalArgumentException(
            "Key-value pairs expected but got odd number of arguments");
      }
      for (int i = 1; i < attrs.length; i++) {
        trace.putAttribute(attrs[i - 1], attrs[i]);
      }
    }
    trace.stop(Timer.fromNanos(endNanos));
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
