// Copyright 2025 Google LLC
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

package com.google.firebase.perf.session.gauges

import androidx.annotation.VisibleForTesting
import java.util.concurrent.atomic.AtomicInteger

/**
 * [GaugeCounter] is a thread-safe counter for gauge metrics. If the metrics count reaches or
 * exceeds [MAX_METRIC_COUNT], it attempts to log the metrics to Firelog.
 */
object GaugeCounter {
  private const val MAX_METRIC_COUNT = 50
  // For debugging explore re-introducing logging.
  private val counter = AtomicInteger(0)

  @set:VisibleForTesting(otherwise = VisibleForTesting.NONE)
  @set:JvmStatic
  var gaugeManager: GaugeManager = GaugeManager.getInstance()

  @JvmStatic
  fun incrementCounter() {
    val metricsCount = counter.incrementAndGet()

    if (metricsCount >= MAX_METRIC_COUNT) {
      gaugeManager.logGaugeMetrics()
    }
  }

  @JvmStatic
  fun decrementCounter() {
    counter.decrementAndGet()
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE)
  @JvmStatic
  fun resetCounter() {
    counter.set(0)
  }

  @VisibleForTesting(otherwise = VisibleForTesting.NONE) @JvmStatic fun count(): Int = counter.get()
}
