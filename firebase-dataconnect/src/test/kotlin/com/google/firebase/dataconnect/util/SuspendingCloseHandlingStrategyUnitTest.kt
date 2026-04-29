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

import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHaveSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.checkAll
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SuspendingCloseHandlingStrategyUnitTest {

  // region Tests for SuspendingCloseHandlingStrategy.Block

  @Test
  fun `Block handle returns the result of the block`() = runTest {
    checkAll(Arb.someValue()) { (value, valueCopy) ->
      val result = SuspendingCloseHandlingStrategy.Block.handle(this@runTest) { value }

      result.isCompleted shouldBe true
      result.await().shouldHaveSameValueAs(value, valueCopy)
    }
  }

  @Test
  fun `Block handle rethrows exceptions via Deferred`() = runTest {
    val exception = Exception("test exception [q8p3pe]")
    val result = SuspendingCloseHandlingStrategy.Block.handle(this@runTest) { throw exception }

    result.isCompleted shouldBe true
    result.getCompletionExceptionOrNull() shouldBeSameInstanceAs exception
  }

  @Test
  fun `Block handle is synchronous`() = runTest {
    val blockStarted = AtomicBoolean(false)
    val blockFinished = AtomicBoolean(false)

    val result =
      SuspendingCloseHandlingStrategy.Block.handle(this@runTest) {
        blockStarted.set(true)
        delay(10)
        blockFinished.set(true)
        "done"
      }

    blockStarted.get() shouldBe true
    blockFinished.get() shouldBe true
    result.isCompleted shouldBe true
    result.await() shouldBe "done"
  }

  // endregion

  // region Tests for SuspendingCloseHandlingStrategy.Async

  @Test
  fun `Async handle returns the result of the block`() = runTest {
    checkAll(Arb.someValue()) { (value, valueCopy) ->
      val result = SuspendingCloseHandlingStrategy.Async.handle(this@runTest) { value }

      result.await().shouldHaveSameValueAs(value, valueCopy)
    }
  }

  @Test
  fun `Async handle rethrows exceptions via Deferred`() = runTest {
    val exception = Exception("test exception [hqa9c6]")
    val result =
      SuspendingCloseHandlingStrategy.Async.handle(CoroutineScope(SupervisorJob())) {
        throw exception
      }

    val caught = result.runCatching { await() }.exceptionOrNull()
    caught.shouldNotBeNull()
    caught.message shouldBe exception.message
  }

  @Test
  fun `Async handle is asynchronous`() = runTest {
    val blockStarted = CompletableDeferred<Unit>()
    val resultDeferred = AtomicReference<String?>(null)

    val job = launch {
      val deferred =
        SuspendingCloseHandlingStrategy.Async.handle(this) {
          blockStarted.complete(Unit)
          delay(100)
          "done"
        }
      resultDeferred.set(deferred.await())
    }

    blockStarted.await()
    resultDeferred.get() shouldBe null
    job.join()
    resultDeferred.get() shouldBe "done"
  }

  @Test
  fun `Async handle uses NonCancellable`() = runTest {
    val blockStarted = CompletableDeferred<Unit>()
    val blockFinished = CompletableDeferred<Unit>()
    val resultValue = AtomicReference<String?>(null)

    val job = launch {
      val deferred =
        SuspendingCloseHandlingStrategy.Async.handle(this) {
          blockStarted.complete(Unit)
          try {
            delay(1000)
          } finally {
            resultValue.set("finished")
            blockFinished.complete(Unit)
          }
          "done"
        }
      deferred.await()
    }

    blockStarted.await()
    job.cancelAndJoin()

    // Even though the job was cancelled, the block should continue to run because it's wrapped in
    // NonCancellable
    blockFinished.await()
    resultValue.get() shouldBe "finished"
  }

  // endregion
}
