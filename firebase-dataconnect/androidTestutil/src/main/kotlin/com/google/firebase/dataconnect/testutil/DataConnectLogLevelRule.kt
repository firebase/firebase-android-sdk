package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.*
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that sets the Firebase Data Connect log level to the desired level, then
 * restores it upon completion of the test.
 */
class DataConnectLogLevelRule(val logLevelDuringTest: LogLevel? = LogLevel.DEBUG) :
  ExternalResource() {

  private lateinit var logLevelBefore: LogLevel

  override fun before() {
    logLevelBefore = FirebaseDataConnect.logLevel
    logLevelDuringTest?.also { FirebaseDataConnect.logLevel = it }
  }

  override fun after() {
    FirebaseDataConnect.logLevel = logLevelBefore
  }
}
