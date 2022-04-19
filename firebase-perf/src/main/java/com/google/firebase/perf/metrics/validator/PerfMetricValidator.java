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

package com.google.firebase.perf.metrics.validator;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.perf.logging.AndroidLogger;
import com.google.firebase.perf.util.Constants;
import com.google.firebase.perf.v1.PerfMetric;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** An abstract class providing an interface to validate PerfMetric */
public abstract class PerfMetricValidator {

  /**
   * Creates a list of PerfMetricValidator classes based on the contents of PerfMetric
   *
   * @param perfMetric The PerfMetric to be sent to server
   * @param context The app Context post FirebaseApp configuration.
   * @return List of PerfMetricValidator implementation classes valid for given PerfMetric
   */
  @NonNull
  private static List<PerfMetricValidator> getValidators(
      @NonNull PerfMetric perfMetric, @NonNull Context context) {
    List<PerfMetricValidator> validators = new ArrayList<>();
    if (perfMetric.hasTraceMetric()) {
      validators.add(new FirebasePerfTraceValidator(perfMetric.getTraceMetric()));
    }
    if (perfMetric.hasNetworkRequestMetric()) {
      validators.add(
          new FirebasePerfNetworkValidator(perfMetric.getNetworkRequestMetric(), context));
    }
    if (perfMetric.hasApplicationInfo()) {
      validators.add(new FirebasePerfApplicationInfoValidator(perfMetric.getApplicationInfo()));
    }
    if (perfMetric.hasGaugeMetric()) {
      validators.add(new FirebasePerfGaugeMetricValidator(perfMetric.getGaugeMetric()));
    }
    return validators;
  }

  /**
   * Validates the given PerfMetric
   *
   * @param perfMetric PerfMetric to validate
   * @return true if the given PerfMetric is valid to send to server, false otherwise.
   */
  public static boolean isValid(@NonNull PerfMetric perfMetric, @NonNull Context context) {
    List<PerfMetricValidator> validators = getValidators(perfMetric, context);
    if (validators.isEmpty()) {
      AndroidLogger.getInstance().debug("No validators found for PerfMetric.");
      return false;
    }

    for (PerfMetricValidator validator : validators) {
      if (!validator.isValidPerfMetric()) {
        return false;
      }
    }
    return true;
  }

  /**
   * Checks whether the given string fits name constraints for Trace ID
   *
   * @param str Trace name
   * @return null if the string can be used as Trace name, if not, an error string explaining why it
   *     can't be used.
   */
  @Nullable
  public static String validateTraceName(@Nullable String str) {
    if (str == null) {
      return "Trace name must not be null";
    } else if (str.length() > Constants.MAX_TRACE_ID_LENGTH) {
      return String.format(
          Locale.US, "Trace name must not exceed %d characters", Constants.MAX_TRACE_ID_LENGTH);
    } else if (str.startsWith("_")) {
      Constants.TraceNames[] validTraceNames = Constants.TraceNames.values();
      for (Constants.TraceNames traceName : validTraceNames) {
        if (traceName.toString().equals(str)) {
          return null;
        }
      }
      if (str.startsWith("_st_")) {
        // Screen trace.
        return null;
      }
      return "Trace name must not start with '_'";
    }
    return null;
  }

  /**
   * Checks whether the given string fits name constraints for Counter ID
   *
   * @param str Counter name
   * @return null if the string can be used as Counter name, if not, an error string explaining why
   *     it can't be used.
   */
  @Nullable
  public static String validateMetricName(@Nullable String str) {
    if (str == null) {
      return "Metric name must not be null";
    } else if (str.length() > Constants.MAX_COUNTER_ID_LENGTH) {
      return String.format(
          Locale.US, "Metric name must not exceed %d characters", Constants.MAX_COUNTER_ID_LENGTH);
    } else if (str.startsWith("_")) {
      Constants.CounterNames[] validCounterNames = Constants.CounterNames.values();
      for (Constants.CounterNames counterName : validCounterNames) {
        if (counterName.toString().equals(str)) {
          return null;
        }
      }
      return "Metric name must not start with '_'";
    }
    return null;
  }

  /**
   * Checks whether the given map entry fits key/value constraints for a Trace Attribute.
   *
   * @param attribute A key/value pair for an Attribute
   * @return null if the entry can be used as an Attribute, if not, an error string explaining why
   *     it can't be used.
   */
  @Nullable
  public static void validateAttribute(@NonNull Map.Entry<String, String> attribute) {
    String key = attribute.getKey();
    String value = attribute.getValue();
    if (key == null || key.length() == 0) {
      throw new IllegalArgumentException("Attribute key must not be null or empty");
    } else if (value == null || value.length() == 0) {
      throw new IllegalArgumentException("Attribute value must not be null or empty");
    } else if (key.length() > Constants.MAX_ATTRIBUTE_KEY_LENGTH) {
      throw new IllegalArgumentException(
          String.format(
              Locale.US,
              "Attribute key length must not exceed %d characters",
              Constants.MAX_ATTRIBUTE_KEY_LENGTH));
    } else if (value.length() > Constants.MAX_ATTRIBUTE_VALUE_LENGTH) {
      throw new IllegalArgumentException(
          String.format(
              Locale.US,
              "Attribute value length must not exceed %d characters",
              Constants.MAX_ATTRIBUTE_VALUE_LENGTH));
    } else if (!key.matches("^(?!(firebase_|google_|ga_))[A-Za-z][A-Za-z_0-9]*")) {
      throw new IllegalArgumentException(
          "Attribute key must start with letter, must only contain alphanumeric characters and"
              + " underscore and must not start with \"firebase_\", \"google_\" and \"ga_");
    }
  }

  public abstract boolean isValidPerfMetric();
}
