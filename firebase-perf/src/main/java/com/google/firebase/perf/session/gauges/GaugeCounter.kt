package com.google.firebase.perf.session.gauges

import java.util.concurrent.atomic.AtomicInteger


class GaugeCounter private constructor() {
    private val counter = AtomicInteger(0)
    private val gaugeManager: GaugeManager = GaugeManager.getInstance()

    fun incrementCounter() {
        val metricsCount = counter.incrementAndGet()

        if (metricsCount >= MAX_METRIC_COUNT && gaugeManager.logGaugeMetrics()) {
            counter.set(0)
        }
    }

    fun resetCounter() {
        counter.set(0)
    }

    companion object {
        val instance: GaugeCounter by lazy(LazyThreadSafetyMode.SYNCHRONIZED) { GaugeCounter() }
        const val MAX_METRIC_COUNT = 25
    }
}
