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
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectLoggingUnitTest {

  @Test
  fun `withLevel() should set and restore the log level`() = runTest {
    val arb = Arb.enum<LogLevel>()
    checkAll(arb, arb) { level1, level2 ->
      FirebaseDataConnect.logging.level = level1

      FirebaseDataConnect.logging.withLevel(level2) {
        withClue("inside withLevel") { FirebaseDataConnect.logging.level shouldBe level2 }
      }

      withClue("after withLevel") { FirebaseDataConnect.logging.level shouldBe level1 }
    }
  }

  @Test
  fun `withLevel() should return the object returned from the block`() = runTest {
    checkAll(Arb.enum<LogLevel>(), Arb.string()) { level, string ->
      val returnValue = FirebaseDataConnect.logging.withLevel(level) { string }

      returnValue shouldBeSameInstanceAs string
    }
  }

  @Test
  fun `withLevel() should call the given block exactly once inline`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { level ->
      val invocationCount = AtomicInteger(0)
      val thread1 = Thread.currentThread()
      val thread2 =
        FirebaseDataConnect.logging.withLevel(level) {
          invocationCount.incrementAndGet()
          Thread.currentThread()
        }

      assertSoftly {
        thread1 shouldBe thread2
        invocationCount.get() shouldBe 1
      }
    }
  }
}
