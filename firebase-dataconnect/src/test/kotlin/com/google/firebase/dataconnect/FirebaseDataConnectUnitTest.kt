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

import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class FirebaseDataConnectUnitTest {

  @Test
  @Suppress("DEPRECATION")
  fun `logLevel set and get`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      FirebaseDataConnect.logLevel = newLevel
      FirebaseDataConnect.logLevel shouldBe newLevel
    }
  }

  @Test
  @Suppress("DEPRECATION")
  fun `logLevel set should forward to logging dot level`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      FirebaseDataConnect.logLevel = newLevel
      FirebaseDataConnect.logging.level shouldBe newLevel
    }
  }

  @Test
  @Suppress("DEPRECATION")
  fun `logLevel get should retrieve from logging dot level`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      FirebaseDataConnect.logging.level = newLevel
      FirebaseDataConnect.logLevel shouldBe newLevel
    }
  }
}
