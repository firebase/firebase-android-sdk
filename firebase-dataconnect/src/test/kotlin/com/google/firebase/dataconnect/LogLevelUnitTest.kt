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

package com.google.firebase.dataconnect

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.test.runTest
import org.junit.Test

class LoggerUnitTest {

  @Test
  fun `noisiestOf()`() = runTest {
    assertSoftly {
      verifyNoisiestOf(LogLevel.NONE, LogLevel.NONE, LogLevel.NONE)
      verifyNoisiestOf(LogLevel.NONE, LogLevel.WARN, LogLevel.WARN)
      verifyNoisiestOf(LogLevel.NONE, LogLevel.DEBUG, LogLevel.DEBUG)
      verifyNoisiestOf(LogLevel.WARN, LogLevel.NONE, LogLevel.WARN)
      verifyNoisiestOf(LogLevel.WARN, LogLevel.WARN, LogLevel.WARN)
      verifyNoisiestOf(LogLevel.WARN, LogLevel.DEBUG, LogLevel.DEBUG)
      verifyNoisiestOf(LogLevel.DEBUG, LogLevel.NONE, LogLevel.DEBUG)
      verifyNoisiestOf(LogLevel.DEBUG, LogLevel.WARN, LogLevel.DEBUG)
      verifyNoisiestOf(LogLevel.DEBUG, LogLevel.DEBUG, LogLevel.DEBUG)
    }
  }

  private companion object {

    fun verifyNoisiestOf(logLevel1: LogLevel, logLevel2: LogLevel, expected: LogLevel) {
      withClue("noisiestOf($logLevel1, $logLevel2)") {
        LogLevel.noisiestOf(logLevel1, logLevel2) shouldBe expected
      }
    }
  }
}
