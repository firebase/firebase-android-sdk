package com.google.firebase.crashlytics.internal.metadata

/** A class that represents information to attach to a specific event. */
data class EventMetadata(
  val sessionId: String,
  val timestamp: Long,
  val userInfo: Map<String, String> = mapOf()
)
