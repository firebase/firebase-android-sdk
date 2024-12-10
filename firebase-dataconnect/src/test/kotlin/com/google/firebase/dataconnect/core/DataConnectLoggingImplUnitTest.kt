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

package com.google.firebase.dataconnect.core

import app.cash.turbine.test
import com.google.firebase.dataconnect.LogLevel
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DataConnectLoggingImplUnitTest {

  @Test
  fun `level setting and getting`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      DataConnectLoggingImpl.level = newLevel
      DataConnectLoggingImpl.level shouldBe newLevel
    }
  }

  @Test
  fun `flow collecting`() = runTest {
    DataConnectLoggingImpl.flow.test {
      withClue("initial item") { awaitItem() shouldBe DataConnectLoggingImpl.level }

      checkAll(Arb.enum<LogLevel>()) { newLevel ->
        val levelChanged = DataConnectLoggingImpl.level != newLevel
        DataConnectLoggingImpl.level = newLevel
        if (levelChanged) {
          awaitItem() shouldBe newLevel
        }
      }
    }
  }

  @Test
  fun `push() should change log level`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      DataConnectLoggingImpl.push(newLevel)
      DataConnectLoggingImpl.level shouldBe newLevel
    }
  }

  @Test
  fun `push() should restore log level when close() is invoked on its return value`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      val originalLevel = DataConnectLoggingImpl.level
      val frame = DataConnectLoggingImpl.push(newLevel)
      frame.close()
      DataConnectLoggingImpl.level shouldBe originalLevel
    }
  }

  @Test
  fun `push() should restore log level when suspendingClose() is invoked on its return value`() =
    runTest {
      checkAll(Arb.enum<LogLevel>()) { newLevel ->
        val originalLevel = DataConnectLoggingImpl.level
        val frame = DataConnectLoggingImpl.push(newLevel)
        frame.suspendingClose()
        DataConnectLoggingImpl.level shouldBe originalLevel
      }
    }

  @Test
  fun `push() should return correct originalLevel and newLevel`() = runTest {
    checkAll(Arb.enum<LogLevel>()) { newLevel ->
      val originalLevel = DataConnectLoggingImpl.level
      val frame = DataConnectLoggingImpl.push(newLevel)
      assertSoftly {
        withClue("originalLevel") { frame.originalLevel shouldBe originalLevel }
        withClue("newLevel") { frame.newLevel shouldBe newLevel }
      }
    }
  }

  @Test
  fun `push() should behave independently when interleaved`() = runTest {
    val arb = Arb.enum<LogLevel>()
    val levels = List(100) { arb.next() }
    val frames = List(levels.size) { DataConnectLoggingImpl.push(levels[it]) }

    frames.shuffled().forEachIndexed { shuffledFrameIndex, frame ->
      withClue("shuffledFrameIndex=$shuffledFrameIndex") {
        frame.close()
        DataConnectLoggingImpl.level shouldBe frame.originalLevel
      }
    }
  }

  @Test
  fun `push() then close() should only have effects on the first invocation`() = runTest {
    val arb = Arb.enum<LogLevel>()
    checkAll(arb, arb) { level1, level2 ->
      val frame = DataConnectLoggingImpl.push(level1)
      frame.close()
      DataConnectLoggingImpl.level = level2
      repeat(10) {
        withClue("superfluous close() call $it (level1=$level1, level2=$level2)") {
          frame.close()
          DataConnectLoggingImpl.level shouldBe level2
        }
      }
    }
  }

  @Test
  fun `push() then suspendingClose() should only have effects on the first invocation`() = runTest {
    val arb = Arb.enum<LogLevel>()
    checkAll(arb, arb) { level1, level2 ->
      val frame = DataConnectLoggingImpl.push(level1)
      frame.suspendingClose()
      DataConnectLoggingImpl.level = level2
      repeat(10) {
        withClue("superfluous suspendingClose() call $it (level1=$level1, level2=$level2)") {
          frame.suspendingClose()
          DataConnectLoggingImpl.level shouldBe level2
        }
      }
    }
  }

  @Test
  fun `push() then close() invoked concurrently`() = runTest {
    val arb = Arb.enum<LogLevel>()
    val level1 = arb.next()
    DataConnectLoggingImpl.level = level1

    val level2 = arb.samples().map { it.value }.filter { it != level1 }.first()
    level1 shouldNotBe level2 // make sure the logic above worked as expected.
    val frame = DataConnectLoggingImpl.push(level2)

    val latch = SuspendingCountDownLatch(10_000)
    val jobs =
      List(latch.count) {
        // Use `Dispatchers.Default` as the dispatcher for the launched coroutines so that, as
        // documented, there will be at least 2 threads used to run the coroutines.

        launch(Dispatchers.Default) {
          latch.countDown()
          latch.await()
          frame.close()
        }
      }

    jobs.forEach { it.join() }
    DataConnectLoggingImpl.level shouldBe level1
  }
}
