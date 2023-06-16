// Copyright 2023 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.logger

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import java.io.FileNotFoundException
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LoggerTest {
  private lateinit var fakeLogger: Logger.FakeLogger

  @Before
  fun setUp() {
    fakeLogger = Logger.setupFakeLogger(TAG)
  }

  @Test
  fun hasLogMessage_positive() {
    Logger.getLogger(TAG).minLevel = Logger.Level.DEBUG
    Logger.getLogger(TAG).debug("this is a debug log message")

    assertThat(fakeLogger.hasLogMessage("this is a debug log message")).isTrue()
  }

  @Test
  fun hasLogMessage_negative() {
    Logger.getLogger(TAG).info("this is an info log message")

    assertThat(fakeLogger.hasLogMessage("nobody logged this")).isFalse()
  }

  @Test
  fun hasLogMessage_containsExceptionMessage() {
    Logger.getLogger(TAG).warn("eh", RuntimeException("some exception message"))

    assertThat(fakeLogger.hasLogMessage("some exception message")).isTrue()
  }

  @Test
  fun hasLogMessage_containsExceptionType() {
    Logger.getLogger(TAG).warn("eh", FileNotFoundException())

    assertThat(fakeLogger.hasLogMessage("FileNotFoundException")).isTrue()
  }

  @Test
  fun hasLogMessage_containsLevel() {
    Logger.getLogger(TAG).warn("eh")

    assertThat(fakeLogger.hasLogMessage("WARN")).isTrue()
  }

  @Test
  fun hasLogMessageThat_positive() {
    Logger.getLogger(TAG).info("this is a log message")

    assertThat(fakeLogger.hasLogMessageThat { logMessage -> logMessage.endsWith("log message") })
      .isTrue()
  }

  @Test
  fun hasLogMessageThat_negative() {
    Logger.getLogger(TAG).info("this is a log message")

    assertThat(fakeLogger.hasLogMessageThat { logMessage -> logMessage.isEmpty() }).isFalse()
  }

  @Test
  fun clearLogMessages() {
    Logger.getLogger(TAG).warn("this is a log message")

    assertThat(fakeLogger.hasLogMessageThat { true }).isTrue()

    fakeLogger.clearLogMessages()

    // The predicate will never happen on a cleared record.
    assertThat(fakeLogger.hasLogMessageThat { true }).isFalse()
  }

  companion object {
    private const val TAG = "tag"
  }
}
