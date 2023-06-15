package com.google.firebase.logger

import android.util.Log
import androidx.annotation.VisibleForTesting

/** Simple internal Kotlin wrapper around [Log]. */
internal sealed class LogWrapper private constructor(val tag: String) {
  abstract fun log(level: Level, format: String, vararg args: Any?, throwable: Throwable?): Int

  /** Implementation of the wrapper around [Log]. */
  internal class Android(tag: String) : LogWrapper(tag) {
    override fun log(level: Level, format: String, vararg args: Any?, throwable: Throwable?): Int {
      val msg = if (args.isEmpty()) format else String.format(format, args)
      return when (level) {
        Level.VERBOSE -> throwable?.let { Log.v(tag, msg, throwable) } ?: Log.v(tag, msg)
        Level.DEBUG -> throwable?.let { Log.d(tag, msg, throwable) } ?: Log.d(tag, msg)
        Level.INFO -> throwable?.let { Log.i(tag, msg, throwable) } ?: Log.i(tag, msg)
        Level.WARN -> throwable?.let { Log.w(tag, msg, throwable) } ?: Log.w(tag, msg)
        Level.ERROR -> throwable?.let { Log.e(tag, msg, throwable) } ?: Log.e(tag, msg)
      }
    }
  }

  /** Fake implementation of the log wrapper that allows recording and asserting on log messages. */
  @VisibleForTesting
  internal class Fake(tag: String) : LogWrapper(tag) {
    private val record: MutableList<String> = ArrayList()

    override fun log(level: Level, format: String, vararg args: Any?, throwable: Throwable?): Int {
      val logMessage = toLogMessage(level, format, args, throwable = throwable)
      println("Log: $logMessage")
      record.add(logMessage)
      return logMessage.length
    }

    /** Clear the recorded log messages. */
    @VisibleForTesting fun clearLogMessages(): Unit = record.clear()

    /** Returns if the record has any message that contains the given [message] as a substring. */
    @VisibleForTesting
    fun hasLogMessage(message: String): Boolean = record.any { it.contains(message) }

    /** Returns if the record has any message that matches the given [predicate]. */
    @VisibleForTesting
    fun hasLogMessageThat(predicate: (String) -> Boolean): Boolean = record.any(predicate)

    /** Builds a log message from all the log params. */
    private fun toLogMessage(
      level: Level,
      format: String,
      vararg args: Any?,
      throwable: Throwable?,
    ): String {
      val msg = if (args.isEmpty()) format else String.format(format, args)
      return throwable?.let { "$level $msg ${Log.getStackTraceString(throwable)}" } ?: "$level $msg"
    }
  }
}
