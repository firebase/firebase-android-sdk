package com.google.firebase.monitoring;

import java.util.concurrent.atomic.AtomicReference;

public class DelegatingTracer implements Tracer {
  private final AtomicReference<Tracer> delegate = new AtomicReference<>();

  public void setTracer(Tracer tracer) {
    delegate.set(tracer);
  }

  @Override
  public TraceHandle startTrace(String name) {
    Tracer tracer = delegate.get();
    if (tracer == null) {
      return TraceHandle.NOOP;
    }
    return tracer.startTrace(name);
  }
}
