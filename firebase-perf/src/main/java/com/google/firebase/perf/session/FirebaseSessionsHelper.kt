package com.google.firebase.perf.session

import com.google.firebase.perf.util.Constants
import java.util.UUID

/** Identifies whether the [PerfSession] is an AQS or not. */
fun PerfSession.isAQS(): Boolean {
  return !this.sessionId().startsWith(Constants.UNDEFINED_AQS_ID_PREFIX)
}

fun createLegacySessionId(): String {
  val uuid = UUID.randomUUID().toString().replace("-", "")
  return uuid.replaceRange(
    0,
    Constants.UNDEFINED_AQS_ID_PREFIX.length,
    Constants.UNDEFINED_AQS_ID_PREFIX
  )
}
