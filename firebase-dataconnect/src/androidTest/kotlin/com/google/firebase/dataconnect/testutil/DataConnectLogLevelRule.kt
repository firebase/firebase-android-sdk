package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.logLevel
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that sets the Firebase Data Connect log level to the desired level, then
 * restores it upon completion of the test.
 */
class DataConnectLogLevelRule(val logLevelDuringTest: LogLevel? = LogLevel.DEBUG) :
  ExternalResource() {

  private lateinit var logLevelBefore: LogLevel

  override fun before() {
    logLevelBefore = logLevel
    logLevelDuringTest?.also { logLevel = it }
  }

  override fun after() {
    logLevel = logLevelBefore
  }
}
