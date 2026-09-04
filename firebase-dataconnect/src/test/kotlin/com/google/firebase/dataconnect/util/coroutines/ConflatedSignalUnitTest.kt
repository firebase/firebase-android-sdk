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

package com.google.firebase.dataconnect.util.coroutines

import app.cash.turbine.test
import app.cash.turbine.turbineScope
import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.RandomSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.Rule
import org.junit.Test

class ConflatedSignalUnitTest {

  @get:Rule(order = Int.MIN_VALUE) val randomSeedTestRule = RandomSeedTestRule()

  val rs: RandomSource by randomSeedTestRule.rs

  @Test
  fun `await returns immediately without suspending if there is a pending signal`() = runTest {
    val signal = ConflatedSignal<Unit>()
    signal.signal()

    val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }

    job.isCompleted shouldBe true
  }

  @Test
  fun `await suspends if there is NO pending signal`() = runTest {
    val signal = ConflatedSignal<Unit>()

    val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }

    job.isCompleted shouldBe false
  }

  @Test
  fun `multiple signals are conflated into one`() = runTest {
    val signal = ConflatedSignal<Unit>()
    repeat(10) { signal.signal() }

    val job1 = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }
    job1.isCompleted shouldBe true
    val job2 = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }
    job2.isCompleted shouldBe false
  }

  @Test
  fun `each call to signal resumes one call to await, leaving the others suspended`() = runTest {
    val signal = ConflatedSignal<Unit>()
    val jobs =
      List(10) { backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() } }
    check(jobs.all { !it.isCompleted })

    repeat(jobs.size) { iterationIndex ->
      signal.signal()
      yield()
      val completedCount = jobs.count { it.isCompleted }
      withClue("iterationIndex=$iterationIndex") { completedCount shouldBe iterationIndex + 1 }
    }
  }

  @Test
  fun `suspended await() calls are promptly canceled`() = runTest {
    val signal = ConflatedSignal<Unit>()
    val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }
    job.cancel()
    yield()
    job.isCompleted shouldBe true
  }

  @Test
  fun `canceling suspended await() calls do not disturb other awaiters`() = runTest {
    val signal = ConflatedSignal<Unit>()
    val jobs =
      List(10) { backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() } }

    val jobsRemaining = jobs.toMutableList()
    jobsRemaining.shuffle(rs.random)
    while (jobsRemaining.isNotEmpty()) {
      withClue("jobsRemaining.size=${jobsRemaining.size}") {
        val canceledJob = jobsRemaining.removeLast()
        canceledJob.cancelAndJoin()
        signal.signal()
        yield()
        val completedJobs = jobsRemaining.filter { it.isCompleted }
        completedJobs shouldHaveSize 1
        jobsRemaining.remove(completedJobs.single())
      }
    }
  }

  @Test
  fun `hasPendingSignal is initially false`() {
    val signal = ConflatedSignal<Unit>()
    signal.hasPendingSignal shouldBe false
  }

  @Test
  fun `hasPendingSignal is true after signal is called`() {
    val signal = ConflatedSignal<Unit>()
    signal.signal()
    signal.hasPendingSignal shouldBe true
  }

  @Test
  fun `hasPendingSignal transitions to false after await completes`() = runTest {
    val signal = ConflatedSignal<Unit>()
    signal.signal()
    signal.hasPendingSignal shouldBe true

    signal.await()
    signal.hasPendingSignal shouldBe false
  }

  @Test
  fun `hasPendingSignal remains true after multiple signals`() {
    val signal = ConflatedSignal<Unit>()
    repeat(5) { signal.signal() }
    signal.hasPendingSignal shouldBe true
  }

  @Test
  fun `hasPendingSignal remains false when multiple awaiters`() = runTest {
    val signal = ConflatedSignal<Unit>()
    repeat(5) { backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() } }

    repeat(100) {
      signal.hasPendingSignal shouldBe false
      yield()
    }
  }

  @Test
  fun `pendingSignal is initially null`() {
    val signal = ConflatedSignal<String>()
    signal.pendingSignal shouldBe null
  }

  @Test
  fun `pendingSignal is the signaled value after signal is called`() {
    val signal = ConflatedSignal<String>()
    signal.signal("hello")
    signal.pendingSignal shouldBe "hello"
  }

  @Test
  fun `pendingSignal transitions to null after await completes`() = runTest {
    val signal = ConflatedSignal<String>()
    signal.signal("hello")
    signal.pendingSignal shouldBe "hello"

    signal.await() shouldBe "hello"
    signal.pendingSignal shouldBe null
  }

  @Test
  fun `pendingSignal remains the latest value after multiple signals`() {
    val signal = ConflatedSignal<String>()
    signal.signal("first")
    signal.signal("second")
    signal.pendingSignal shouldBe "second"
  }

  @Test
  fun `signals flow emits immediately if there is a pending signal`() = runTest {
    val signal = ConflatedSignal<Unit>()
    signal.signal()

    signal.signals.test {
      awaitItem() shouldBe Unit
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `signals flow collection suspends if there is NO pending signal`() = runTest {
    val signal = ConflatedSignal<Unit>()

    val job =
      backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.signals.collect {} }

    job.isCompleted shouldBe false
  }

  @Test
  fun `signals flow emits when signal is called`() = runTest {
    val signal = ConflatedSignal<Unit>()

    signal.signals.test {
      signal.signal()
      awaitItem() shouldBe Unit
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `multiple signals are conflated in the signals flow`() = runTest {
    val signal = ConflatedSignal<Unit>()
    repeat(10) { signal.signal() }

    signal.signals.test {
      awaitItem() shouldBe Unit
      expectNoEvents()
    }
  }

  @Test
  fun `multiple collectors of signals flow compete for signals`() = runTest {
    val signal = ConflatedSignal<Unit>()

    turbineScope {
      val collector1 = signal.signals.testIn(backgroundScope, name = "collector1")
      val collector2 = signal.signals.testIn(backgroundScope, name = "collector2")

      signal.signal()
      yield()

      val events1 = collector1.cancelAndConsumeRemainingEvents()
      val events2 = collector2.cancelAndConsumeRemainingEvents()

      val itemsCount1 = events1.count { it is app.cash.turbine.Event.Item }
      val itemsCount2 = events2.count { it is app.cash.turbine.Event.Item }
      (itemsCount1 + itemsCount2) shouldBe 1
    }
  }

  @Test
  fun `signals flow collectors compete with direct awaiters`() = runTest {
    val signal = ConflatedSignal<Unit>()

    turbineScope {
      val collector = signal.signals.testIn(backgroundScope, name = "collector")
      val awaitJob = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }

      signal.signal()
      yield()

      val flowEvents = collector.cancelAndConsumeRemainingEvents()
      val awaitCompleted = awaitJob.isCompleted

      val flowGotSignal = flowEvents.count { it is app.cash.turbine.Event.Item } == 1

      (flowGotSignal xor awaitCompleted) shouldBe true
    }
  }

  @Test
  fun `toString contains hasPendingSignal=false initially`() {
    val signal = ConflatedSignal<Unit>()
    check(!signal.hasPendingSignal)

    assertSoftly {
      signal.toString() shouldContainWithNonAbuttingText "ConflatedSignal"
      signal.toString() shouldContainWithNonAbuttingTextIgnoringCase "hasPendingSignal=false"
    }
  }

  @Test
  fun `toString contains hasPendingSignal=true after signal`() {
    val signal = ConflatedSignal<Unit>()
    signal.signal()
    check(signal.hasPendingSignal)

    assertSoftly {
      signal.toString() shouldContainWithNonAbuttingText "ConflatedSignal"
      signal.toString() shouldContainWithNonAbuttingTextIgnoringCase "hasPendingSignal=true"
    }
  }

  @Test
  fun `await returns the signaled value`() = runTest {
    val signal = ConflatedSignal<String>()
    signal.signal("hello")

    val result = signal.await()
    result shouldBe "hello"
  }

  @Test
  fun `signals are conflated to the latest value`() = runTest {
    val signal = ConflatedSignal<String>()
    signal.signal("A")
    signal.signal("B")

    val result = signal.await()
    result shouldBe "B"
  }

  @Test
  fun `signals flow emissions carry correct values`() = runTest {
    val signal = ConflatedSignal<String>()

    signal.signals.test {
      signal.signal("hello")
      awaitItem() shouldBe "hello"

      signal.signal("world")
      awaitItem() shouldBe "world"
      cancelAndIgnoreRemainingEvents()
    }
  }

  @Test
  fun `clear() discards pending signal and causes subsequent await to suspend`() = runTest {
    val signal = ConflatedSignal<Unit>()
    signal.signal()
    signal.clear()

    val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }
    job.isCompleted shouldBe false
  }

  @Test
  fun `clear() updates hasPendingSignal and pendingSignal`() {
    val signal = ConflatedSignal<String>()
    signal.signal("hello")
    signal.clear()

    signal.hasPendingSignal shouldBe false
    signal.pendingSignal shouldBe null
  }

  @Test
  fun `clear() clears pending signal in signals flow`() = runTest {
    val signal = ConflatedSignal<String>()
    signal.signal("hello")
    signal.clear()

    val job =
      backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.signals.collect {} }
    job.isCompleted shouldBe false
  }

  @Test
  fun `signalIfNotNull with non-null value triggers signal`() = runTest {
    val signal = ConflatedSignal<String>()
    signal.signalIfNotNull("hello")

    signal.hasPendingSignal shouldBe true
    signal.await() shouldBe "hello"
  }

  @Test
  fun `signalIfNotNull with null value does not trigger signal`() = runTest {
    val signal = ConflatedSignal<String>()
    signal.signalIfNotNull(null)

    signal.hasPendingSignal shouldBe false
    val job = backgroundScope.launch(start = CoroutineStart.UNDISPATCHED) { signal.await() }
    job.isCompleted shouldBe false
  }
}
