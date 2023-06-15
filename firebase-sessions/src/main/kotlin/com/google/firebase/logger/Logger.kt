package com.google.firebase.logger

import androidx.annotation.VisibleForTesting
import java.util.concurrent.ConcurrentHashMap

/** Common logger interface that handles Android logcat logging. */
class Logger private constructor(private val logWrapper: LogWrapper) {
  var enabled: Boolean = true
  var minLevel: Level = Level.INFO

  @JvmOverloads
  fun verbose(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    log(Level.VERBOSE, format, args, throwable = throwable)

  @JvmOverloads
  fun verbose(msg: String, throwable: Throwable? = null): Int =
    log(Level.VERBOSE, msg, throwable = throwable)

  @JvmOverloads
  fun debug(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    log(Level.DEBUG, format, args, throwable = throwable)

  @JvmOverloads
  fun debug(msg: String, throwable: Throwable? = null): Int =
    log(Level.DEBUG, msg, throwable = throwable)

  @JvmOverloads
  fun info(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    log(Level.INFO, format, args, throwable = throwable)

  @JvmOverloads
  fun info(msg: String, throwable: Throwable? = null): Int =
    log(Level.INFO, msg, throwable = throwable)

  @JvmOverloads
  fun warn(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    log(Level.WARN, format, args, throwable = throwable)

  @JvmOverloads
  fun warn(msg: String, throwable: Throwable? = null): Int =
    log(Level.WARN, msg, throwable = throwable)

  @JvmOverloads
  fun error(format: String, vararg args: Any?, throwable: Throwable? = null): Int =
    log(Level.ERROR, format, args, throwable = throwable)

  @JvmOverloads
  fun error(msg: String, throwable: Throwable? = null): Int =
    log(Level.ERROR, msg, throwable = throwable)

  private fun log(level: Level, format: String, vararg args: Any?, throwable: Throwable?): Int =
    if (enabled && level.priority >= minLevel.priority) {
      logWrapper.log(level, format, args, throwable = throwable)
    } else {
      0
    }

  companion object {
    private val loggers = ConcurrentHashMap<String, Logger>()

    /** Gets (or creates) the single instance of [Logger] with the given [tag]. */
    @JvmStatic
    fun getLogger(tag: String): Logger = loggers.getOrPut(tag) { Logger(LogWrapper.Android(tag)) }

    /** Sets (or replaces) the instance of [Logger] with the given [tag] for testing purposes. */
    @VisibleForTesting
    internal fun setupFakeLogger(tag: String): LogWrapper.Fake {
      val logWrapper = LogWrapper.Fake(tag)
      loggers[logWrapper.tag] = Logger(logWrapper)
      return logWrapper
    }
  }
}
