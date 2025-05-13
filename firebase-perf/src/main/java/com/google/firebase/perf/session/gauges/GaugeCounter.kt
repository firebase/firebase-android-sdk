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

import com.google.firebase.perf.logging.AndroidLogger
import java.util.concurrent.atomic.AtomicInteger

/**
 * [GaugeCounter] is a thread-safe counter for gauge metrics. If the metrics count exceeds
 * [MAX_METRIC_COUNT], it attempts to log the metrics to Firelog.
 */
object GaugeCounter {
  private const val MAX_METRIC_COUNT = 25
  private val counter = AtomicInteger(0)
  private val logger = AndroidLogger.getInstance()
  // TODO(b/394127311): Setting this as a var for a unit test. Refactor it.
  var gaugeManager: GaugeManager = GaugeManager.getInstance()

  fun incrementCounter() {
    val metricsCount = counter.incrementAndGet()

    if (metricsCount >= MAX_METRIC_COUNT) {
      gaugeManager.logGaugeMetrics()
    }

    logger.debug("Incremented logger to $metricsCount")
  }

  fun decrementCounter() {
    val curr = counter.decrementAndGet()
    logger.debug("Decremented logger to $curr")
  }

  // TODO: Add annotation to only call from tests
  fun resetCounter() {
    counter.set(0)
  }

  // TODO: Add annotation to only call from tests
  fun count(): Int = counter.get()
}
