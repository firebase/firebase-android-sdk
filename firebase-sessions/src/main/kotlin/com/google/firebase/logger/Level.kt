package com.google.firebase.logger

import android.util.Log

/** Log levels with each [priority] that matches [Log]. */
enum class Level(internal val priority: Int) {
  VERBOSE(Log.VERBOSE),
  DEBUG(Log.DEBUG),
  INFO(Log.INFO),
  WARN(Log.WARN),
  ERROR(Log.ERROR),
}
