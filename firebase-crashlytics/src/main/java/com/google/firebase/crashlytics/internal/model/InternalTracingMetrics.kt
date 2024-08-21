package com.google.firebase.crashlytics.internal.model

internal class InternalTracingMetrics {
  companion object InternalMetrics {
    var metrics = mutableMapOf<String, String>()
    fun put(key: String, value: String) {
      metrics[key] = value
    }
    fun removeThreadViolationLog() {
      metrics.remove("crashlytics_violate_thread")
    }
  }
}
