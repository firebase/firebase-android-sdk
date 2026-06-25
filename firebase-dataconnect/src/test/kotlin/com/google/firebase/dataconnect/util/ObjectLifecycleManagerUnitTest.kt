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

import com.google.firebase.dataconnect.core.Logger
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.newMockLogger
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingTextIgnoringCase
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.mockk.mockk
import java.util.concurrent.Callable
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CyclicBarrier
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class ObjectLifecycleManagerUnitTest {

  @get:Rule val testName = TestName()
  @get:Rule val cleanups = CleanupsRule()

  private val testScope = TestScope()

  @Test
  fun `close() on an unopened instance does not call create()`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()

      objectLifecycleManager.close()

      objectLifecycleManager.createCallCount.get() shouldBe 0
    }

  @Test
  fun `close() on an unopened instance does not call initialize()`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()

      objectLifecycleManager.close()

      objectLifecycleManager.initializeCalls.shouldBeEmpty()
    }

  @Test
  fun `close() on an unopened instance does not call destroy()`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()

      objectLifecycleManager.close()

      objectLifecycleManager.destroyCalls.shouldBeEmpty()
    }

  @Test
  fun `close() on an unopened instance sequential calls do not call other methods`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()

      repeat(100) { objectLifecycleManager.close() }

      objectLifecycleManager.createCallCount.get() shouldBe 0
      objectLifecycleManager.initializeCalls.shouldBeEmpty()
      objectLifecycleManager.destroyCalls.shouldBeEmpty()
    }

  @Test
  fun `close() on an unopened instance concurrent calls do not call other methods`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()
      val latch = SuspendingCountDownLatch(100)
      val jobs =
        List(latch.count) {
          backgroundScope.launch(Dispatchers.Default) {
            latch.countDown().await()
            objectLifecycleManager.close()
          }
        }

      jobs.joinAll()

      objectLifecycleManager.createCallCount.get() shouldBe 0
      objectLifecycleManager.initializeCalls.shouldBeEmpty()
      objectLifecycleManager.destroyCalls.shouldBeEmpty()
    }

  @Test
  fun `close() on an unopened instance causes future open() calls to throw`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()
      objectLifecycleManager.close()

      val exception =
        shouldThrow<ObjectLifecycleManager.ClosedException> { objectLifecycleManager.open() }

      exception.message shouldContainWithNonAbuttingText "cm2ga6aaej"
      exception.message shouldContainWithNonAbuttingTextIgnoringCase "close()"
    }

  @Test
  fun `open() calls create() exactly once`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()

      objectLifecycleManager.open()

      objectLifecycleManager.createCallCount.get() shouldBe 1
    }

  @Test
  fun `open() returns whatever create() returns`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val objectLifecycleManager = TestObjectLifecycleManager(createImpl = { testObject })

      val openReturnValue = objectLifecycleManager.open()

      openReturnValue shouldBeSameInstanceAs testObject
    }

  @Test
  fun `open() calls initialize() exactly once`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()

      objectLifecycleManager.open()

      objectLifecycleManager.initializeCalls shouldHaveSize 1
    }

  @Test
  fun `open() calls initialize() with the return value of create()`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val objectLifecycleManager = TestObjectLifecycleManager(createImpl = { testObject })

      objectLifecycleManager.open()

      objectLifecycleManager.initializeCalls.shouldNotBeEmpty().first() shouldBeSameInstanceAs
        testObject
    }

  @Test
  fun `open() concurrent calls call create() and initialize() exactly once`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val objectLifecycleManager = TestObjectLifecycleManager(createImpl = { testObject })
      val latch = SuspendingCountDownLatch(100)
      val jobs =
        List(latch.count) {
          backgroundScope.launch {
            latch.countDown().await()
            objectLifecycleManager.open()
          }
        }

      jobs.joinAll()

      objectLifecycleManager.createCallCount.get() shouldBe 1
      objectLifecycleManager.initializeCalls shouldHaveSize 1
      objectLifecycleManager.initializeCalls.single() shouldBeSameInstanceAs testObject
    }

  @Test
  fun `open() concurrent calls all return the same instance`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val objectLifecycleManager = TestObjectLifecycleManager(createImpl = { testObject })
      val latch = SuspendingCountDownLatch(100)
      val jobs =
        List(latch.count) {
          backgroundScope.async {
            latch.countDown().await()
            objectLifecycleManager.open()
          }
        }

      val results = jobs.awaitAll()

      results.forEach { it shouldBeSameInstanceAs testObject }
    }

  @Test
  fun `open() calls create() from coroutine with the given dispatcher`() =
    testScope.runTest {
      val executor = Executors.newSingleThreadExecutor()
      cleanups.register { executor.shutdownNow() }
      val createThread = AtomicReference<Thread>()
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = {
            createThread.set(Thread.currentThread())
            mockk()
          },
          coroutineDispatcher = executor.asCoroutineDispatcher()
        )

      objectLifecycleManager.open()

      val executorThread = executor.submit(Callable { Thread.currentThread() }).get()
      createThread.get() shouldBeSameInstanceAs executorThread
    }

  @Test
  fun `open() when closed concurrently during create() throws`() =
    testScope.runTest {
      val barrier = CyclicBarrier(2)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = {
            barrier.await()
            barrier.await()
            error("should never get here [a3cm34bqjm]")
          },
          coroutineDispatcher = Dispatchers.Default,
        )
      backgroundScope.launch(Dispatchers.Default) {
        runInterruptible { barrier.await() }
        objectLifecycleManager.close()
      }

      shouldThrow<ObjectLifecycleManager.ClosedException> { objectLifecycleManager.open() }
    }

  @Test
  fun `open() when closed concurrently during create() causes InterruptedException`() =
    testScope.runTest {
      val barrier = CyclicBarrier(2)
      val awaitException = AtomicReference<Throwable>(null)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = {
            barrier.await()
            barrier.runCatching { await() }.onFailure { awaitException.set(it) }
            mockk()
          },
          coroutineDispatcher = Dispatchers.Default,
        )
      backgroundScope.launch(Dispatchers.Default) {
        runInterruptible { barrier.await() }
        objectLifecycleManager.close()
      }

      objectLifecycleManager.runCatching { open() }

      awaitException.get().shouldBeInstanceOf<InterruptedException>()
    }

  @Test
  fun `open() when closed concurrently during initialize() throws`() =
    testScope.runTest {
      val latch1 = SuspendingCountDownLatch(2)
      val latch2 = SuspendingCountDownLatch(2)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          initializeImpl = {
            latch1.countDown().await()
            latch2.countDown().await()
          }
        )
      backgroundScope.launch(Dispatchers.Default) {
        latch1.countDown().await()
        objectLifecycleManager.close()
      }

      shouldThrow<ObjectLifecycleManager.ClosedException> { objectLifecycleManager.open() }
    }

  @Test
  fun `open() when closed concurrently during initialize() causes CancellationException`() =
    testScope.runTest {
      val latch1 = SuspendingCountDownLatch(2)
      val latch2 = SuspendingCountDownLatch(2)
      val awaitException = AtomicReference<Throwable?>(null)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          initializeImpl = {
            runCatching {
                latch1.countDown().await()
                latch2.countDown().await()
              }
              .onFailure { awaitException.set(it) }
          }
        )
      backgroundScope.launch(Dispatchers.Default) {
        latch1.countDown().await()
        objectLifecycleManager.close()
      }

      objectLifecycleManager.runCatching { open() }

      awaitException.get().shouldBeInstanceOf<CancellationException>()
    }

  @Test
  fun `open() rethrows exception from create()`() =
    testScope.runTest {
      class TestException(message: String) : Exception(message)
      val objectLifecycleManager =
        TestObjectLifecycleManager(createImpl = { throw TestException("qx3p7r8eyf") })

      val exception = shouldThrow<TestException> { objectLifecycleManager.open() }

      exception.message shouldBe "qx3p7r8eyf"
    }

  @Test
  fun `open() rethrows exception from initialize()`() =
    testScope.runTest {
      class TestException(message: String) : Exception(message)
      val objectLifecycleManager =
        TestObjectLifecycleManager(initializeImpl = { throw TestException("p9x27hb52h") })

      val exception = shouldThrow<TestException> { objectLifecycleManager.open() }

      exception.message shouldBe "p9x27hb52h"
    }

  @Test
  fun `open() concurrent and subsequent calls rethrow exception from create()`() =
    testScope.runTest {
      class TestException(message: String) : Exception(message)
      val objectLifecycleManager =
        TestObjectLifecycleManager(createImpl = { throw TestException("zh9zqbxhsr") })
      val latch = SuspendingCountDownLatch(100)
      val jobs =
        List(latch.count) {
          backgroundScope.async {
            latch.countDown().await()
            objectLifecycleManager.runCatching { open() }
          }
        }

      val exceptions = jobs.map { shouldThrow<TestException> { it.await().getOrThrow() } }

      exceptions.forEach { it.message shouldBe "zh9zqbxhsr" }
    }

  @Test
  fun `open() concurrent and subsequent calls rethrow exception from initialize()`() =
    testScope.runTest {
      class TestException(message: String) : Exception(message)
      val objectLifecycleManager =
        TestObjectLifecycleManager(initializeImpl = { throw TestException("bvyfj66jcq") })
      val latch = SuspendingCountDownLatch(100)
      val jobs =
        List(latch.count) {
          backgroundScope.async {
            latch.countDown().await()
            objectLifecycleManager.runCatching { open() }
          }
        }

      val exceptions = jobs.map { shouldThrow<TestException> { it.await().getOrThrow() } }

      exceptions.forEach { it.message shouldBe "bvyfj66jcq" }
    }

  @Test
  fun `open() when calling coroutine is cancelled allows other open() calls to proceed`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val initializeLatch1 = SuspendingCountDownLatch(2)
      val initializeLatch2 = SuspendingCountDownLatch(2)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = { testObject },
          initializeImpl = {
            initializeLatch1.countDown().await()
            initializeLatch2.countDown().await()
          },
        )
      val openJob1Exception = AtomicReference<Throwable>()
      val openJob1 = launch {
        objectLifecycleManager
          .runCatching { open() }
          .onFailure { exception -> openJob1Exception.set(exception) }
      }
      val openJobsLatch = SuspendingCountDownLatch(Runtime.getRuntime().availableProcessors())
      val openJobs =
        List(openJobsLatch.count) {
          backgroundScope.async(Dispatchers.Default) {
            openJobsLatch.countDown()
            objectLifecycleManager.open()
          }
        }
      initializeLatch1.countDown().await()
      openJobsLatch.await()
      openJob1.cancel("pt8s4q8sk9")
      openJob1.join()
      initializeLatch2.countDown()
      val openJobResults = openJobs.awaitAll()

      val cancellationException =
        openJob1Exception.get().shouldBeInstanceOf<CancellationException>()
      cancellationException.message shouldContainWithNonAbuttingText "pt8s4q8sk9"
      openJobResults shouldContainExactly List(openJobs.size) { testObject }
    }

  @Test
  fun `close() after open() calls destroy() exactly once`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()
      objectLifecycleManager.open()

      objectLifecycleManager.close()

      objectLifecycleManager.destroyCalls shouldHaveSize 1
    }

  @Test
  fun `close() after open() calls destroy() with object returned from create()`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val objectLifecycleManager = TestObjectLifecycleManager(createImpl = { testObject })
      objectLifecycleManager.open()

      objectLifecycleManager.close()

      objectLifecycleManager.destroyCalls.shouldNotBeEmpty().first() shouldBeSameInstanceAs
        testObject
    }

  @Test
  fun `close() after open() sequential calls call destroy() exactly once`() =
    testScope.runTest {
      val objectLifecycleManager = TestObjectLifecycleManager()
      objectLifecycleManager.open()

      repeat(100) { objectLifecycleManager.close() }

      objectLifecycleManager.destroyCalls shouldHaveSize 1
    }

  @Test
  fun `close() during create() calls destroy()`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val barrier = CyclicBarrier(2)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = {
            barrier.runCatching {
              await()
              Thread.sleep(Long.MAX_VALUE)
            }
            testObject
          },
          coroutineDispatcher = Dispatchers.Default,
        )
      backgroundScope.launch(Dispatchers.Default) { objectLifecycleManager.runCatching { open() } }

      runInterruptible { barrier.await() }
      objectLifecycleManager.close()

      objectLifecycleManager.destroyCalls.shouldHaveSize(1)
      objectLifecycleManager.destroyCalls[0] shouldBeSameInstanceAs testObject
    }

  @Test
  fun `close() during initialize() calls destroy()`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val latch1 = SuspendingCountDownLatch(2)
      val latch2 = SuspendingCountDownLatch(2)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = { testObject },
          initializeImpl = {
            latch1.countDown().await()
            latch2.await()
          },
        )
      backgroundScope.launch { objectLifecycleManager.runCatching { open() } }

      latch1.countDown().await()
      objectLifecycleManager.close()

      objectLifecycleManager.destroyCalls.shouldHaveSize(1)
      objectLifecycleManager.destroyCalls[0] shouldBeSameInstanceAs testObject
    }

  @Test
  fun `close() when initialize() throws still calls destroy()`() =
    testScope.runTest {
      val testObject: TestType = mockk()
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          createImpl = { testObject },
          initializeImpl = { throw Exception("forced test exception [hra4hzrfha]") },
        )

      objectLifecycleManager.runCatching { open() }
      objectLifecycleManager.close()

      objectLifecycleManager.destroyCalls.shouldHaveSize(1)
      objectLifecycleManager.destroyCalls[0] shouldBeSameInstanceAs testObject
    }

  @Test
  fun `close() throws exception from destroy()`() =
    testScope.runTest {
      class TestException(message: String) : Exception(message)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          destroyImpl = { throw TestException("kvdwd69s9e") },
        )
      objectLifecycleManager.open()

      val exception = shouldThrow<TestException> { objectLifecycleManager.close() }

      exception.message shouldBe "kvdwd69s9e"
    }

  @Test
  fun `close() concurrent calls all throw exception from destroy()`() =
    testScope.runTest {
      class TestException(message: String) : Exception(message)
      val objectLifecycleManager =
        TestObjectLifecycleManager(
          destroyImpl = { throw TestException("zbyqek6ft2") },
        )
      objectLifecycleManager.open()
      val latch = SuspendingCountDownLatch(100)
      val jobs =
        List(latch.count) {
          backgroundScope.async(Dispatchers.Default) {
            latch.countDown().await()
            objectLifecycleManager.runCatching { close() }
          }
        }

      val exceptions = jobs.map { shouldThrow<TestException> { it.await().getOrThrow() } }

      exceptions.forEach { it.message shouldBe "zbyqek6ft2" }
    }

  private fun newMockLogger(): Logger = newMockLogger(testName.methodName)

  private interface TestType

  private inner class TestObjectLifecycleManager(
    private val createImpl: () -> TestType = { mockk() },
    private val initializeImpl: suspend () -> Unit = {},
    private val destroyImpl: suspend () -> Unit = {},
    coroutineDispatcher: CoroutineDispatcher = StandardTestDispatcher(testScope.testScheduler),
    logger: Logger = newMockLogger(),
  ) : ObjectLifecycleManager<TestType>(coroutineDispatcher, logger) {

    val createCallCount = AtomicInteger(0)
    val initializeCalls = CopyOnWriteArrayList<TestType>()
    val destroyCalls = CopyOnWriteArrayList<TestType>()

    override fun create(): TestType {
      createCallCount.incrementAndGet()
      return createImpl()
    }

    override suspend fun initialize(instance: TestType) {
      initializeCalls.add(instance)
      initializeImpl()
    }

    override suspend fun destroy(instance: TestType) {
      destroyCalls.add(instance)
      destroyImpl()
    }
  }
}

private class TestCoroutineContextElement :
  AbstractCoroutineContextElement(TestCoroutineContextElement) {
  companion object Key : CoroutineContext.Key<TestCoroutineContextElement>
}
