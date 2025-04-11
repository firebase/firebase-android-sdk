package com.google.firebase.perf.session

import com.google.firebase.perf.util.Clock

fun createTestSession(suffix: Int): PerfSession {
  // TODO(b/394127311): Add a method to verify legacy behavior.
  // only hex characters and so it's AQS.
  return PerfSession(testSessionId(suffix), Clock())
}

fun testSessionId(suffix: Int): String {
  return "abc$suffix"
}
