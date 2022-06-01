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

package com.google.firebase.testing.fireperf;

import android.util.Log;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/** Generates traces with all the appropriate information. */
public class TraceGenerator {

  private static final String LOG_TAG = TraceGenerator.class.getSimpleName();
  private static final int TRACE_MEAN_DURATION = 3;
  private static final float TRACE_DURATION_STD_DEVIATION = .3f;

  Future<?> launchTraces(final int totalTraces, final int totalSets) {
    return Executors.newSingleThreadExecutor()
        .submit(
            () -> {
              for (int setIndex = 0; setIndex < totalSets; setIndex++) {
                // Create a TreeSet to insert TraceHolder in an ascending order of duration
                List<TraceHolder> traceHolderList = new ArrayList<>();

                for (int traceIndex = 0; traceIndex < totalTraces; traceIndex++) {
                  float gaussianValue =
                      FireperfUtils.randomGaussianValueWithMean(
                          TRACE_MEAN_DURATION, TRACE_DURATION_STD_DEVIATION);

                  float traceDuration = traceIndex + gaussianValue;
                  int counterMeanValue = setIndex + traceIndex + 5;

                  traceHolderList.add(
                      new TraceHolder(
                          traceIndex,
                          FireperfUtils.normalizeTime(traceDuration),
                          counterMeanValue));
                }

                // Since traceHolderSet contains traces in sorted order, we start them one after the
                // other and TraceHolder.stopTrace() is called for the TraceHolder in the same
                // order.
                // TraceHolder.stopTrace() waits (makes the thread sleep) for the duration of the
                // trace before stopping it.
                for (TraceHolder traceHolder : traceHolderList) {
                  traceHolder.startTrace();
                }

                for (TraceHolder traceHolder : traceHolderList) {
                  traceHolder.stopTrace();
                }
              }
            });
  }

  private static class TraceHolder implements Comparable<TraceHolder> {

    private static final int TRACE_MAX_COUNTERS = 32;
    private static final int TRACE_MAX_CUSTOM_ATTRIBUTES = 5;

    private final Map<String, Integer> counters = new HashMap<>();
    private final Trace trace;
    private final String name;

    private long startTime;
    private final long durationMillis;

    private TraceHolder(int traceId, long durationMillis, long counterMeanValue) {
      this.name = String.format(Locale.US, "t%02d", traceId);
      this.trace = FirebasePerformance.getInstance().newTrace(this.name);
      this.durationMillis = durationMillis;

      for (int i = 0; i < TRACE_MAX_COUNTERS; i++) {
        String counterName = String.format(Locale.US, "%sc%02d", this.name, i);
        int counterValue =
            Math.abs(Math.round(FireperfUtils.randomGaussianValueWithMean(counterMeanValue, 1)));
        counters.put(counterName, counterValue);
      }

      // Add custom attributes to the trace
      // d0=t<nn>_d0, d1=t<nn>_d1.. d4=t<nn>_d4
      for (int attributeIndex = 0; attributeIndex < TRACE_MAX_CUSTOM_ATTRIBUTES; attributeIndex++) {
        String attributeKey = String.format(Locale.US, "d%d", attributeIndex);
        String attributeValue = String.format(Locale.US, "t%02d_d%d", traceId, attributeIndex);
        trace.putAttribute(attributeKey, attributeValue);
      }
    }

    void startTrace() {
      startTime = System.currentTimeMillis();
      trace.start();

      for (String counterName : counters.keySet()) {
        trace.incrementMetric(counterName, counters.get(counterName));
      }
    }

    void stopTrace() {
      try {
        long sleepForMillis = durationMillis - (System.currentTimeMillis() - startTime);

        if (sleepForMillis > 0) {
          Thread.sleep(sleepForMillis);
        }

        trace.stop();

      } catch (InterruptedException e) {
        Log.e(LOG_TAG, String.format("Couldn't make trace %s wait for %d", name, durationMillis));
      }
    }

    @Override
    public int compareTo(TraceHolder otherTraceHolder) {
      if (this.durationMillis == otherTraceHolder.durationMillis) {
        return 0;
      }

      long diff = this.durationMillis - otherTraceHolder.durationMillis;
      return diff < 0 ? -1 : 1;
    }
  }
}
