package com.google.firebase.monitoring;

import com.google.firebase.time.Instant;

public interface ExtendedTracer extends Tracer {
  void recordTrace(String name, Instant start, Instant end, String... attrs);
}
