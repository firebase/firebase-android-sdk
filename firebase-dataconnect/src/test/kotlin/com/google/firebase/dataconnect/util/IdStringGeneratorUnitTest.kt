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

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.property.arbitrary.random
import com.google.firebase.dataconnect.testutil.property.arbitrary.randomSeed
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeSorted
import io.kotest.matchers.collections.shouldBeUnique
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldEndWith
import io.kotest.matchers.string.shouldNotBeEmpty
import io.kotest.matchers.string.shouldStartWith
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import java.lang.Long.parseLong
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IdStringGeneratorUnitTest {

  @Test
  fun `nextIdString() returns strings beginning with the given prefix`() = runTest {
    checkAll(propTestConfig, Arb.random(), Arb.string(0..10)) { random, prefix ->
      val result = random.nextIdString(prefix)
      result shouldStartWith prefix
    }
  }

  @Test
  fun `nextIdString() returns strings ending with hex numbers`() = runTest {
    checkAll(propTestConfig, Arb.random(), Arb.string(0..10, Codepoint.nonHexLetters())) {
      random,
      prefix ->
      val result = random.nextIdString(prefix)
      val hexSuffix = result.filter { it in hexDigits }
      hexSuffix.shouldNotBeEmpty()
      result shouldEndWith hexSuffix
    }
  }

  @Test
  fun `nextIdString() returns strings with monotonically-increasing hex suffixes`() = runTest {
    checkAll(
      propTestConfig,
      Arb.random(),
      Arb.string(0..10, Codepoint.nonHexLetters()),
      Arb.int(2..500)
    ) { random, prefix, count ->
      val hexSuffixes =
        List(count) {
          val result = random.nextIdString(prefix)
          result.filter { it in hexDigits }
        }
      val parsedHexSuffixes = hexSuffixes.map { parseLong(it, 16) }
      parsedHexSuffixes.shouldBeSorted()
    }
  }

  @Test
  fun `nextIdString() returns unique strings`() = runTest {
    checkAll(propTestConfig, Arb.random(), Arb.string(0..10), Arb.int(2..500)) {
      random,
      prefix,
      count ->
      val results = List(count) { random.nextIdString(prefix) }
      results.shouldBeUnique()
    }
  }

  @Test
  fun `nextIdString() uses the receiver Random`() = runTest {
    // Warm up the global sequence number to ensure it has a stable hex string length
    // (at least 5 digits) during the test, preventing mismatched random padding counts.
    repeat(0x10000) { Random.nextIdString("") }
    checkAll(propTestConfig, Arb.randomSeed(), Arb.string(0..10), Arb.int(1..500)) {
      seed,
      prefix,
      count ->
      val (results1, results2) =
        List(2) {
          val random = Random(seed)
          List(count) { random.nextIdString(prefix).filterNot { it in hexDigits } }
        }
      results1 shouldContainExactly results2
    }
  }

  @Test
  fun `nextIdString() returns strings with length at least prefix length plus 8`() = runTest {
    checkAll(propTestConfig, Arb.random(), Arb.string(0..10)) { random, prefix ->
      val result = random.nextIdString(prefix)
      result.length shouldBeGreaterThanOrEqual (prefix.length + 8)
    }
  }

  @Test
  fun `nextIdString() returns strings where non-hex chars are from the allowed set`() = runTest {
    @Suppress("SpellCheckingInspection") val allowedChars = "ghjkmnpqrstvwxyz".toSet()
    checkAll(propTestConfig, Arb.random(), Arb.string(0..10, Codepoint.nonHexLetters())) {
      random,
      prefix ->
      val result = random.nextIdString(prefix)
      val hexSuffix = result.filter { it in hexDigits }
      val paddingAndPrefix = result.substring(0, result.length - hexSuffix.length)
      val padding = paddingAndPrefix.substring(prefix.length)
      padding.all { it in allowedChars } shouldBe true
    }
  }

  @Test
  fun `nextIdString() returns unique strings when called concurrently`() = runTest {
    val latch = SuspendingCountDownLatch(200)
    val jobs =
      List(latch.count) {
        async(Dispatchers.Default) {
          latch.countDown().await()
          List(100) { Random.nextIdString("") }
        }
      }
    jobs.awaitAll().flatten().shouldBeUnique()
  }
}

@Suppress("SpellCheckingInspection") private val hexDigits = "abcdef0123456789".toSet()

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
  )

private fun Codepoint.Companion.nonHexLetters(): Arb<Codepoint> =
  Arb.of(('g'.code..'z'.code).map(::Codepoint))
