package com.google.firebase.perf.session.gauges

import java.util.concurrent.atomic.AtomicInteger

/**
 * [GaugeCounter] is a threadsafe counter for gauge metrics. If the metrics count exceeds
 * [MAX_METRIC_COUNT], it attempts to log the metrics to Firelog.
 */
class GaugeCounter private constructor() {
  private val counter = AtomicInteger(0)
  private val gaugeManager: GaugeManager = GaugeManager.getInstance()

  fun incrementCounter() {
    val metricsCount = counter.incrementAndGet()

    if (metricsCount >= MAX_METRIC_COUNT) {
      gaugeManager.logGaugeMetrics()
    }
  }

  fun decrementCounter() {
    counter.decrementAndGet()
  }

  companion object {
    val instance: GaugeCounter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GaugeCounter() }
    const val MAX_METRIC_COUNT = 25
  }
}
