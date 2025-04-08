package com.google.firebase.perf.session

import com.google.firebase.perf.util.Constants
import java.util.UUID

/** Identifies whether the [PerfSession] is legacy or not. */
fun PerfSession.isLegacy(): Boolean {
  return this.sessionId().startsWith(Constants.UNDEFINED_AQS_ID_PREFIX)
}

/** Creates a valid session ID for [PerfSession] that can be predictably identified as legacy. */
fun createLegacySessionId(): String {
  val uuid = UUID.randomUUID().toString().replace("-", "")
  return uuid.replaceRange(
    0,
    Constants.UNDEFINED_AQS_ID_PREFIX.length,
    Constants.UNDEFINED_AQS_ID_PREFIX
  )
}
