/*
 * Copyright 2024 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.FirebaseDataConnect
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.logLevel
import org.junit.rules.ExternalResource

/**
 * A JUnit test rule that sets the Firebase Data Connect log level to the desired level, then
 * restores it upon completion of the test.
 */
class DataConnectLogLevelRule(val logLevelDuringTest: LogLevel = LogLevel.DEBUG) :
  ExternalResource() {

  private lateinit var logLevelBefore: LogLevel

  override fun before() {
    logLevelBefore = FirebaseDataConnect.logLevel.value
    logLevelDuringTest.also { FirebaseDataConnect.logLevel.value = it }
  }

  override fun after() {
    FirebaseDataConnect.logLevel.value = logLevelBefore
  }
}
