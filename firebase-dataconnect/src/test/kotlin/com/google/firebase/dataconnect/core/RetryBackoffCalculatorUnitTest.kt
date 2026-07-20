/*
 * Copyright 2026 Google LLC
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

import com.google.firebase.dataconnect.core.RetryBackoffCalculatorTesting.backoffValues
import com.google.firebase.dataconnect.core.RetryBackoffCalculatorTesting.jitterTestCase
import com.google.firebase.dataconnect.core.RetryBackoffCalculatorTesting.maxJitterBackoffValues
import com.google.firebase.dataconnect.core.RetryBackoffCalculatorTesting.minJitterBackoffValues
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.longs.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RetryBackoffCalculatorUnitTest {

  @Test
  fun `next() first call returns initial backoff`() {
    val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
    retryBackoffCalculator.next() shouldBe 1000L
  }

  @Test
  fun `next() after reset() returns initial backoff`() = runTest {
    checkAll(propTestConfig, Arb.int(0..100)) { nextCallCountBeforeReset ->
      val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
      repeat(nextCallCountBeforeReset) { retryBackoffCalculator.next() }
      retryBackoffCalculator.reset()

      retryBackoffCalculator.next() shouldBe 1000L
    }
  }

  @Test
  fun `next() returns correct values until reaching the max value`() {
    val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
    backoffValues.forEach { expected -> retryBackoffCalculator.next() shouldBe expected }
  }

  @Test
  fun `next() never returns greater than the max value`() {
    val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
    repeat(1000) {
      withClue("iteration=$it") { retryBackoffCalculator.next() shouldBeLessThanOrEqual 600000L }
    }
  }

  @Test
  fun `next() returns the max value once it is reached`() {
    val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
    var lastValue = retryBackoffCalculator.next()
    while (true) {
      val value = retryBackoffCalculator.next()
      if (value == lastValue || value >= 600000L) {
        break
      }
      lastValue = value
    }

    repeat(1000) { withClue("iteration=$it") { retryBackoffCalculator.next() shouldBe 600000L } }
  }

  @Test
  fun `reset() always resets`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.int(0..20), 20..20)) { interveningNextCallCounts ->
      val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
      interveningNextCallCounts.forEach { count ->
        if (count > 0) {
          retryBackoffCalculator.next() shouldBe 1000L
        }
        repeat(count - 1) { retryBackoffCalculator.next() }
        retryBackoffCalculator.reset()
      }
    }
  }

  @Test
  fun `next() concurrent calls behave correctly`() = runTest {
    val retryBackoffCalculator = RetryBackoffCalculator { 0.0 }
    val latch = SuspendingCountDownLatch(50)

    val jobs =
      List(latch.count) {
        backgroundScope.async(Dispatchers.Default) {
          latch.countDown().await()
          retryBackoffCalculator.next()
        }
      }

    val results = jobs.awaitAll()
    val expected =
      RetryBackoffCalculator { 0.0 }
        .let { testCalculator -> List(jobs.size) { testCalculator.next() } }
    results shouldContainExactlyInAnyOrder expected
  }

  @Test
  fun `next() with max jitter scales backoff correctly`() {
    val retryBackoffCalculator = RetryBackoffCalculator { 0.5 }
    maxJitterBackoffValues.forEach { expected -> retryBackoffCalculator.next() shouldBe expected }
  }

  @Test
  fun `next() with min jitter scales backoff correctly`() {
    val retryBackoffCalculator = RetryBackoffCalculator { -0.5 }
    minJitterBackoffValues.forEach { expected -> retryBackoffCalculator.next() shouldBe expected }
  }

  @Test
  fun `next() with varying jitter scales backoff correctly`() = runTest {
    checkAll(propTestConfig, Arb.jitterTestCase()) { (jitters, expectedBackoffs) ->
      val retryBackoffCalculator = RetryBackoffCalculator(jitters.iterator()::next)
      expectedBackoffs.forEach { expected -> retryBackoffCalculator.next() shouldBe expected }
    }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )
