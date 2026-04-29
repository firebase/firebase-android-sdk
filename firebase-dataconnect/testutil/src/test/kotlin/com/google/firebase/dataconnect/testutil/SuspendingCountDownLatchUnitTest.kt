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

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.arbitrary.int
import io.kotest.property.checkAll
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.withTimeout
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds

class SuspendingCountDownLatchUnitTest {

  @Test
  fun `init with positive count succeeds`() = runTest {
    checkAll(Arb.int(1..1000)) { count ->
      val latch = SuspendingCountDownLatch(count)
      latch.count shouldBe count
    }
  }

  @Test
  fun `init with zero count throws IllegalArgumentException`() {
    shouldThrow<IllegalArgumentException> { SuspendingCountDownLatch(0) }.message shouldBe
      "invalid count: 0"
  }

  @Test
  fun `init with negative count throws IllegalArgumentException`() = runTest {
    checkAll(Arb.int(Int.MIN_VALUE..-1)) { count ->
      shouldThrow<IllegalArgumentException> { SuspendingCountDownLatch(count) }.message shouldBe
        "invalid count: $count"
    }
  }

  @Test
  fun `countDown decrements count`() {
    val latch = SuspendingCountDownLatch(10)
    latch.countDown()
    latch.count shouldBe 9
    latch.countDown()
    latch.count shouldBe 8
  }

  @Test
  fun `countDown returns same instance`() {
    val latch = SuspendingCountDownLatch(10)
    latch.countDown() shouldBeSameInstanceAs latch
  }

  @Test
  fun `countDown throws IllegalStateException when count is already 0`() {
    val latch = SuspendingCountDownLatch(1)
    latch.countDown()
    latch.count shouldBe 0
    val exception = shouldThrow<IllegalStateException> { latch.countDown() }
    exception.message shouldBe "countDown() called too many times (currentValue=0)"
  }

  @Test
  fun `await returns immediately when count has reached 0`() = runTest {
    val latch = SuspendingCountDownLatch(1)
    latch.countDown()

    withTimeout(100.milliseconds) { latch.await() }
  }

  @Test
  fun `await suspends until count reaches 0`() = runTest {
    val latch = SuspendingCountDownLatch(2)
    val deferred = async {
      latch.await()
      true
    }

    deferred.isCompleted shouldBe false
    latch.countDown()
    deferred.isCompleted shouldBe false
    latch.countDown()

    withTimeout(100.milliseconds) { deferred.await() shouldBe true }
  }

  @Test
  fun `multiple awaiters are all resumed when count reaches 0`() = runTest {
    val latch = SuspendingCountDownLatch(3)
    val deferreds =
      List(10) {
        async {
          latch.await()
          it
        }
      }

    latch.countDown()
    latch.countDown()
    deferreds.all { !it.isCompleted } shouldBe true

    latch.countDown()

    withTimeout(100.milliseconds) { deferreds.awaitAll() shouldBe List(10) { it } }
  }

  @Test
  fun `await is cancellable`() = runTest {
    val latch = SuspendingCountDownLatch(1)
    val job = launch { latch.await() }

    delay(10.milliseconds)
    job.isActive shouldBe true
    job.cancel()
    job.join()
    job.isCancelled shouldBe true
  }

  @Test
  fun `concurrent countDown calls are thread-safe`() = runTest {
    val count = 100
    val latch = SuspendingCountDownLatch(count)

    val deferreds = List(count) { async { latch.countDown() } }

    deferreds.awaitAll()
    latch.count shouldBe 0

    withTimeout(100.milliseconds) { latch.await() }
  }
}
