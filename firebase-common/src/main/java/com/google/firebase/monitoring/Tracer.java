package com.google.firebase.monitoring;

public interface Tracer {
  TraceHandle startTrace(String name);
}
