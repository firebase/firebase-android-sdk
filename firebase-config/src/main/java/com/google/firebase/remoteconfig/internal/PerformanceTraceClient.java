// Copyright 2020 Google LLC
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

package com.google.firebase.remoteconfig.internal;

import com.google.firebase.inject.Provider;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import java.util.concurrent.TimeUnit;

public class PerformanceTraceClient implements PerformanceTracer {
  private static PerformanceTraceClient instance;
  private final Provider<FirebasePerformance> firebasePerformance;

  public static PerformanceTraceClient getInstance(Provider<FirebasePerformance> firebasePerformance) {
    PerformanceTraceClient localRef = instance;
    if (localRef == null) {
      synchronized (PerformanceTracer.class) {
        localRef = instance;
        if (localRef == null) {
          instance = localRef = new PerformanceTraceClient(firebasePerformance);
        }
      }
    }
    return localRef;
  }

  private PerformanceTraceClient(Provider<FirebasePerformance> firebasePerformance) {
    this.firebasePerformance = firebasePerformance;
  }

  @Override
  public Trace newTrace(String s) {
    return firebasePerformance.get().newTrace(s);
  }

  @Override
  public Trace startTrace(String s) {
    Trace trace = newTrace(s);
    trace.start();
    return trace;
  }

  @Override
  public Timer newTimer() {
    return new Stopwatch();
  }

  static class Stopwatch implements Timer {
    private long end;
    private long start;

    @Override
    public void start() {
      start = System.nanoTime();
    }

    @Override
    public void stop() {
      end = System.nanoTime();
    }

    @Override
    public long getElapsedTimeMillis() {
      return TimeUnit.MILLISECONDS.convert(end - start, TimeUnit.NANOSECONDS);
    }

    @Override
    public long getElapsedTimeHours() {
      return TimeUnit.HOURS.convert(end - start, TimeUnit.NANOSECONDS);
    }

    @Override
    public long getElapsedTimeSeconds() {
      return TimeUnit.SECONDS.convert(end - start, TimeUnit.NANOSECONDS);
    }
  }
}
