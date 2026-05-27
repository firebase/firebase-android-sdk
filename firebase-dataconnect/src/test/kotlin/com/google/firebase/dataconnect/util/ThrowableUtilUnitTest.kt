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

@file:Suppress("NonAsciiCharacters")

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import com.google.firebase.dataconnect.testutil.property.arbitrary.listNoRepeat
import com.google.firebase.dataconnect.testutil.property.arbitrary.random
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.result.shouldBeFailure
import io.kotest.matchers.result.shouldBeSuccess
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.next
import io.kotest.property.checkAll
import java.io.IOException
import kotlin.random.nextInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test

class ThrowableUtilUnitTest {

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()
  private val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `List․combine() on empty list returns null`() {
    val list = emptyList<Throwable>()
    list.combine().shouldBeNull()
  }

  @Test
  fun `List․combine() on single element list returns that element`() {
    val exception = exceptionArb().next(rs)
    val result = listOf(exception).combine()
    result.shouldNotBeNull() shouldBeSameInstanceAs exception
    result.suppressed shouldHaveSize 0
  }

  @Test
  fun `List․combine() on multiple distinct elements returns first with others suppressed`() =
    runTest {
      checkAll(propTestConfig, Arb.listNoRepeat(exceptionArb(), 2..10)) { exceptions ->
        val result = exceptions.combine()

        result.shouldNotBeNull() shouldBeSameInstanceAs exceptions.first()
        result.suppressed.toList() shouldContainExactly exceptions.drop(1)
      }
    }

  @Test
  fun `List․combine() deduplicates suppressed exceptions`() = runTest {
    val exceptionsArb = Arb.listNoRepeat(exceptionArb(), 1..5)
    val repeatCountArb = Arb.int(1..10)

    data class RepeatedExceptions(
      val repeatCount: Int,
      val originalList: List<Throwable>,
      val listWithRepeats: List<Throwable>
    )
    val repeatedExceptionsArb =
      Arb.bind(exceptionsArb, repeatCountArb, Arb.random()) { exceptions, repeatCount, random ->
        check(exceptions.isNotEmpty())
        check(repeatCount > 0)
        val exceptionsWithRepeats = buildList {
          addAll(exceptions)
          repeat(repeatCount) {
            val repeatIndex = random.nextInt(indices)
            val insertIndex = random.nextInt((repeatIndex + 1)..size)
            add(insertIndex, get(repeatIndex))
          }
        }
        RepeatedExceptions(repeatCount, exceptions, exceptionsWithRepeats)
      }

    checkAll(propTestConfig, repeatedExceptionsArb) {
      (_, exceptionsNoRepeats, exceptionsWithRepeats) ->
      check(exceptionsNoRepeats[0] === exceptionsWithRepeats[0])

      val result = exceptionsWithRepeats.combine()

      result.shouldNotBeNull() shouldBeSameInstanceAs exceptionsWithRepeats[0]
      result.suppressed.toList() shouldContainExactly exceptionsNoRepeats.drop(1)
    }
  }

  @Test
  fun `CombineFailureScope․runCatching() captures failures`() = runTest {
    checkAll(propTestConfig, Arb.listNoRepeat(exceptionArb(), 1..5)) { exceptions ->
      val captured = mutableListOf<Throwable>()
      val scope = CombineFailureScope(captured::add)

      exceptions.forEach { exception -> scope.runCatching { throw exception } }

      captured shouldContainExactly exceptions
    }
  }

  @Test
  fun `CombineFailureScope․runCatching() returns the correct failure result`() {
    val exception = exceptionArb().next(rs)
    val scope = CombineFailureScope {}

    val result = scope.runCatching { throw exception }

    result.shouldBeFailure<Throwable>() shouldBeSameInstanceAs exception
  }

  @Test
  fun `CombineFailureScope․runCatching() does not capture successes`() = runTest {
    checkAll(propTestConfig, Arb.list(Arb.dataConnect.alphabeticString(), 1..5)) { strings ->
      val captured = mutableListOf<Throwable>()
      val scope = CombineFailureScope(captured::add)

      strings.forEach { string -> scope.runCatching { string } }

      captured.shouldBeEmpty()
    }
  }

  @Test
  fun `CombineFailureScope․runCatching() returns the correct success result`() {
    val string = Arb.dataConnect.alphabeticString().next(rs)
    val scope = CombineFailureScope {}

    val result = scope.runCatching { string }

    result.shouldBeSuccess() shouldBe string
  }

  @Test
  fun `CombineFailureScope․T․runCatching() captures failures`() = runTest {
    class TestReceiver(private val exception: Throwable) {
      fun foo() {
        throw exception
      }
    }
    checkAll(propTestConfig, Arb.listNoRepeat(exceptionArb(), 1..5)) { exceptions ->
      val captured = mutableListOf<Throwable>()
      val scope = CombineFailureScope(captured::add)

      exceptions.forEach { exception ->
        val receiver = TestReceiver(exception)
        scope.run { receiver.runCatching { foo() } }
      }

      captured shouldContainExactly exceptions
    }
  }

  @Test
  fun `CombineFailureScope․T․runCatching() returns the correct failure result`() {
    class TestReceiver(private val exception: Throwable) {
      fun foo() {
        throw exception
      }
    }
    val exception = exceptionArb().next(rs)
    val receiver = TestReceiver(exception)
    val scope = CombineFailureScope {}

    val result = scope.run { receiver.runCatching { foo() } }

    result.shouldBeFailure<Throwable>() shouldBeSameInstanceAs exception
  }

  @Test
  fun `CombineFailureScope․T․runCatching() does not capture successes`() = runTest {
    class TestReceiver(private val string: String) {
      fun foo() = string
    }
    checkAll(propTestConfig, Arb.list(Arb.dataConnect.alphabeticString(), 1..5)) { strings ->
      val captured = mutableListOf<Throwable>()
      val scope = CombineFailureScope(captured::add)

      strings.forEach { string ->
        val receiver = TestReceiver(string)
        scope.run { receiver.runCatching { foo() } }
      }

      captured.shouldBeEmpty()
    }
  }

  @Test
  fun `CombineFailureScope․T․runCatching() returns the correct success result`() {
    class TestReceiver(private val string: String) {
      fun foo() = string
    }
    val string = Arb.dataConnect.alphabeticString().next(rs)
    val receiver = TestReceiver(string)
    val scope = CombineFailureScope {}

    val result = scope.run { receiver.runCatching { foo() } }

    result.shouldBeSuccess() shouldBe string
  }

  @Test
  fun `throwCombinedException() returns successful block value`() = runTest {
    checkAll(propTestConfig, Arb.dataConnect.alphabeticString()) { string ->
      val result = throwCombinedException { string }
      result shouldBe string
    }
  }

  @Test
  fun `throwCombinedException() with direct exception throws`() = runTest {
    checkAll(propTestConfig, exceptionArb()) { exception ->
      val exception = shouldThrow<Throwable> { throwCombinedException { throw exception } }
      exception shouldBeSameInstanceAs exception
      exception.suppressed shouldHaveSize 0
    }
  }

  @Test
  fun `throwCombinedException() with captured exceptions throws`() = runTest {
    checkAll(propTestConfig, Arb.listNoRepeat(exceptionArb(), 1..10)) { exceptions ->
      val exception =
        shouldThrow<Throwable> {
          throwCombinedException {
            exceptions.forEach { exception -> runCatching { throw exception } }
          }
        }

      exception shouldBeSameInstanceAs exceptions[0]
      exception.suppressed.toList() shouldContainExactly exceptions.drop(1)
    }
  }

  @Test
  fun `throwCombinedException() with captured and direct exceptions throws`() = runTest {
    checkAll(propTestConfig, exceptionArb(), Arb.listNoRepeat(exceptionArb(), 1..10)) {
      directException,
      capturedExceptions ->
      val exception =
        shouldThrow<Throwable> {
          throwCombinedException {
            capturedExceptions.forEach { exception -> runCatching { throw exception } }
            throw directException
          }
        }

      exception shouldBeSameInstanceAs directException
      exception.suppressed.toList() shouldContainExactly capturedExceptions
    }
  }

  @Test
  fun `throwCombinedException() thread safety captures concurrent errors`() = runTest {
    val exceptions = Arb.listNoRepeat(exceptionArb(), 5).next(rs)
    val latch = SuspendingCountDownLatch(exceptions.size)

    val exception =
      shouldThrow<Throwable> {
        throwCombinedException {
          val jobs =
            exceptions.map { exception ->
              backgroundScope.launch(Dispatchers.Default) {
                latch.countDown().await()
                runCatching { throw exception }
              }
            }
          jobs.joinAll()
        }
      }

    exception shouldBeIn exceptions
    exception.suppressed.toList() shouldContainExactlyInAnyOrder exceptions.minus(exception)
  }

  @Test
  fun `throwCombinedException() silently drops errors recorded after block returns`() {
    val exception = exceptionArb().next(rs)
    var scopeRef: CombineFailureScope? = null

    throwCombinedException { scopeRef = this }

    checkNotNull(scopeRef).runCatching { throw exception }
  }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
  )

@Suppress("unused")
private enum class ExceptionType(val createInstance: (String) -> Throwable) {
  Throwable(::Throwable),
  Exception(::Exception),
  RuntimeException(::RuntimeException),
  IllegalArgumentException(::IllegalArgumentException),
  IllegalStateException(::IllegalStateException),
  IOException(::IOException),
  CancellationException(::CancellationException),
  OutOfMemoryError(::OutOfMemoryError),
}

private fun exceptionArb(
  type: Arb<ExceptionType> = Arb.enum(),
  message: Arb<String> = Arb.dataConnect.alphabeticString(length = 8),
): Arb<Throwable> = Arb.bind(type, message) { type, message -> type.createInstance(message) }
