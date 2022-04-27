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

import com.google.firebase.perf.v1.GaugeMetric;
import com.google.firebase.perf.v1.PerfMetric;

/**
 * Validates a {@link GaugeMetric} to help determine whether or not the {@link PerfMetric} proto
 * containing it should be logged to transport.
 */
final class FirebasePerfGaugeMetricValidator extends PerfMetricValidator {

  private final GaugeMetric gaugeMetric;

  FirebasePerfGaugeMetricValidator(GaugeMetric gaugeMetric) {
    this.gaugeMetric = gaugeMetric;
  }

  /**
   * Validates the GaugeMetric instance used to initialize this Validator.
   *
   * <p>Checks for presence of sessionId and presence of the CPU and Memory Gauge Metrics.
   */
  public boolean isValidPerfMetric() {
    // TODO(b/113113391): Add more GaugeMetric validation.
    return gaugeMetric.hasSessionId()
        && (gaugeMetric.getCpuMetricReadingsCount() > 0
            || gaugeMetric.getAndroidMemoryReadingsCount() > 0
            || (gaugeMetric.hasGaugeMetadata()
                && gaugeMetric.getGaugeMetadata().hasMaxAppJavaHeapMemoryKb()));
  }
}
