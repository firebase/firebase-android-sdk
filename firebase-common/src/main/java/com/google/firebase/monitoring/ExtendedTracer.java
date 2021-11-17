package com.google.firebase.monitoring;

public interface ExtendedTracer extends Tracer {
  void recordTrace(String name, long startNanos, long endNanos, String... attrs);
}
