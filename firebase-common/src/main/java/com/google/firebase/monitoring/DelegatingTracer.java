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

package com.google.firebase.monitoring;

import java.util.concurrent.atomic.AtomicReference;

public class DelegatingTracer implements ExtendedTracer {
  private final AtomicReference<ExtendedTracer> delegate = new AtomicReference<>();

  public void setTracer(ExtendedTracer tracer) {
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

  @Override
  public void recordTrace(String name, long startNanos, long endNanos, String... attrs) {
    ExtendedTracer tracer = delegate.get();
    if (tracer == null) {
      return;
    }
    tracer.recordTrace(name, startNanos, endNanos, attrs);
  }
}
