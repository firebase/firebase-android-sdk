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

package com.google.firebase.perf.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.v1.TraceMetric;
import java.util.Map;

/** Utility class that provides some static methods for validating TraceMetrics. */
final class FirebasePerfTraceValidator extends PerfMetricValidator {

  private static final AndroidLogger logger = AndroidLogger.getInstance();

  private TraceMetric mTraceMetric;

  FirebasePerfTraceValidator(@NonNull TraceMetric traceMetric) {
    mTraceMetric = traceMetric;
  }

  /**
   * Validates a trace metric, it validates root trace and subtraces and checks if the name, deep
   * and durations are valid.
   *
   * @return a boolean which indicates if the trace is valid.
   */
  public boolean isValidPerfMetric() {
    if (!isValidTrace(mTraceMetric, 0)) {
      logger.warn("Invalid Trace:" + mTraceMetric.getName());
      return false;
    }

    if (hasCounters(mTraceMetric)) {
      if (!areCountersValid(mTraceMetric)) {
        logger.warn("Invalid Counters for Trace:" + mTraceMetric.getName());
        return false;
      }
    }
    return true;
  }

  /** Checks if the trace metric contains counters. */
  private boolean hasCounters(@NonNull TraceMetric trace) {
    boolean hasTraceCounters = trace.getCountersCount() > 0;
    if (hasTraceCounters) {
      return true;
    }
    for (TraceMetric subtrace : trace.getSubtracesList()) {
      boolean hasSubtraceCounters = subtrace.getCountersCount() > 0;
      if (hasSubtraceCounters) {
        return true;
      }
    }
    return false;
  }

  /**
   * Validates counters in a trace metric, it validates root trace counters and subtrace counters
   * and checks if the counter name and counts are valid.
   */
  private boolean areCountersValid(@NonNull TraceMetric trace) {
    return areCountersValid(trace, 0);
  }

  private boolean areCountersValid(@Nullable TraceMetric trace, int deep) {
    if (trace == null) {
      return false;
    }
    if (deep > Constants.MAX_SUBTRACE_DEEP) {
      logger.warn("Exceed MAX_SUBTRACE_DEEP:" + Constants.MAX_SUBTRACE_DEEP);
      return false;
    }
    // TODO(b/35766630): Add validations for auto instrumented counters.
    for (Map.Entry<String, Long> entry : trace.getCountersMap().entrySet()) {
      if (!isValidCounterId(entry.getKey())) {
        logger.warn("invalid CounterId:" + entry.getKey());
        return false;
      }
      if (!isValidCounterValue(entry.getValue())) {
        logger.warn("invalid CounterValue:" + entry.getValue());
        return false;
      }
    }

    for (TraceMetric subtrace : trace.getSubtracesList()) {
      if (!areCountersValid(subtrace, deep + 1)) {
        return false;
      }
    }
    return true;
  }

  private boolean isValidTrace(@Nullable TraceMetric trace, int deep) {
    if (trace == null) {
      logger.warn("TraceMetric is null");
      return false;
    }
    if (deep > Constants.MAX_SUBTRACE_DEEP) {
      logger.warn("Exceed MAX_SUBTRACE_DEEP:" + Constants.MAX_SUBTRACE_DEEP);
      return false;
    }
    // TODO(b/35766630): Add validations for auto instrumented traces.
    if (!isValidTraceId(trace.getName())) {
      logger.warn("invalid TraceId:" + trace.getName());
      return false;
    }
    if (!isValidTraceDuration(trace)) {
      logger.warn("invalid TraceDuration:" + trace.getDurationUs());
      return false;
    }
    if (!trace.hasClientStartTimeUs()) {
      logger.warn("clientStartTimeUs is null.");
      return false;
    }
    for (TraceMetric subtrace : trace.getSubtracesList()) {
      if (!isValidTrace(subtrace, deep + 1)) {
        return false;
      }
    }
    if (!hasValidAttributes(trace.getCustomAttributesMap())) {
      return false;
    }
    return true;
  }

  private boolean isValidTraceId(@Nullable String traceId) {
    if (traceId == null) {
      return false;
    }
    traceId = traceId.trim();
    return !traceId.isEmpty() && traceId.length() <= Constants.MAX_TRACE_ID_LENGTH;
  }

  private boolean isValidTraceDuration(@Nullable TraceMetric traceMetric) {
    return traceMetric != null && traceMetric.getDurationUs() > 0;
  }

  private boolean isValidCounterId(@Nullable String counterId) {
    if (counterId == null) {
      return false;
    }
    counterId = counterId.trim();
    if (counterId.isEmpty()) {
      logger.warn("counterId is empty");
      return false;
    }
    if (counterId.length() > Constants.MAX_COUNTER_ID_LENGTH) {
      logger.warn("counterId exceeded max length " + Constants.MAX_COUNTER_ID_LENGTH);
      return false;
    }
    return true;
  }

  private boolean hasValidAttributes(Map<String, String> customAttributes) {
    for (Map.Entry<String, String> entry : customAttributes.entrySet()) {
      String err = PerfMetricValidator.validateAttribute(entry);
      if (err != null) {
        logger.warn(err);
        return false;
      }
    }
    return true;
  }

  private boolean isValidCounterValue(@Nullable Long counterValue) {
    return counterValue != null;
  }
}
