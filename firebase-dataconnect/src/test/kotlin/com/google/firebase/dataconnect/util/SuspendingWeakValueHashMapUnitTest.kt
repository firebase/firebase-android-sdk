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
@file:OptIn(ExperimentalKotest::class, DelicateKotest::class)

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.SuspendingWeakValueHashMap
import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.delayIgnoringTestScheduler
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.randomSeed
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.distinct
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.next
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.orNull
import io.kotest.property.arbitrary.take
import io.kotest.property.arbs.usernames
import io.kotest.property.checkAll
import java.lang.ref.WeakReference
import java.util.concurrent.ThreadFactory
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName

class SuspendingWeakValueHashMapUnitTest {

  @get:Rule val cleanups = CleanupsRule()

  @get:Rule val testName = TestName()

  @Test
  fun `size() returns 0 on an empty map`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }

    map.size() shouldBe 0
  }

  @Test
  fun `size() returns the size of a populated map`() = runTest {
    checkAll(propTestConfig, Arb.int(0..50), Arb.randomSeed()) { size, randomSeed ->
      val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
      cleanups.registerSuspending { map.close() }
      map.populate(size, randomSeed)

      map.size() shouldBe size
    }
  }

  @Test
  fun `size() returns smaller values as background cleanup occurs`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    map.populate()

    val sizes =
      List(50) {
        map.size().also {
          if (it > 0) {
            System.gc()
            delayIgnoringTestScheduler(50.milliseconds)
          }
        }
      }

    sizes shouldContainExactly sizes.sortedDescending()
  }

  @Test
  fun `size() does not return smaller values as garbage collection occurs without cleanup`() =
    runTest {
      val map = SuspendingWeakValueHashMap<Int, Value>(noopThreadFactory)
      cleanups.registerSuspending { map.close() }
      val expectedSize = map.populate().size

      val sizes =
        List(50) {
          map.size().also {
            if (it > 0) {
              System.gc()
              delayIgnoringTestScheduler(1.milliseconds)
            }
          }
        }

      val expectedSizes = List(sizes.size) { expectedSize }
      sizes shouldContainExactly expectedSizes
    }

  @Test
  fun `get() returns null on an empty map`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }

    checkAll(propTestConfig, Arb.int()) { key -> map.get(key).shouldBeNull() }
  }

  @Test
  fun `get() returns the value specified to put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val valueStrongReferences = mutableListOf<Value>()

    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      valueStrongReferences.add(value)
      map.put(key, value)

      map.get(key) shouldBeSameInstanceAs value
    }
  }

  @Test
  fun `get() returns the most recent value specified to put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val valueStrongReferences = mutableListOf<Value>()

    checkAll(propTestConfig, Arb.int().distinct(), valueArb(), valueArb()) { key, value1, value2 ->
      valueStrongReferences.add(value1)
      valueStrongReferences.add(value2)
      map.put(key, value1)
      map.put(key, value2)

      map.get(key) shouldBeSameInstanceAs value2
    }
  }

  @Test
  fun `get() returns null for keys never specified to put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val expectedMap = mutableMapOf<Int, Value>()

    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      expectedMap[key] = value
      map.put(key, value)
      val unsetKey = Arb.int().filterNot { it in expectedMap }.bind()

      map.get(unsetKey).shouldBeNull()
    }
  }

  @Test
  fun `get(key) returns null after remove(key)`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    populatedData.keys.shuffled().forEach {
      map.remove(it)

      map.get(it).shouldBeNull()
    }
  }

  @Test
  fun `get() returns null after clear()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    map.clear()

    populatedData.keys.shuffled().forEach { map.get(it).shouldBeNull() }
  }

  @Test
  fun `get() returns null values after background cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val keys = map.populate().keys.toList()

    val nonNullCounts =
      List(50) {
        var nonNullCount = 0
        keys.forEach {
          if (map.get(it) !== null) {
            nonNullCount++
          }
        }
        if (nonNullCount > 0) {
          System.gc()
          delayIgnoringTestScheduler(50.milliseconds)
        }
        nonNullCount
      }

    nonNullCounts.last() shouldBe 0
    nonNullCounts shouldContainExactly nonNullCounts.sortedDescending()
  }

  @Test
  fun `get() returns null as garbage collection occurs without cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(noopThreadFactory)
    cleanups.registerSuspending { map.close() }
    val keys = map.populate().keys.toList()

    val nonNullCounts =
      List(50) {
        var nonNullCount = 0
        keys.forEach {
          if (map.get(it) !== null) {
            nonNullCount++
          }
        }
        if (nonNullCount > 0) {
          System.gc()
          delayIgnoringTestScheduler(50.milliseconds)
        }
        nonNullCount
      }

    nonNullCounts.last() shouldBe 0
    nonNullCounts shouldContainExactly nonNullCounts.sortedDescending()
  }

  @Test
  fun `put() returns null on an empty map`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }

    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      map.put(key, value).shouldBeNull()
    }
  }

  @Test
  fun `put() returns null if key is not mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    checkAll(propTestConfig, Arb.int().filterNot { it in populatedData }, valueArb()) { key, value
      ->
      map.put(key, value).shouldBeNull()
    }
  }

  @Test
  fun `put() returns previous value if key was mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate().toMutableMap()

    checkAll(propTestConfig, Arb.of(populatedData.keys.sorted()), valueArb()) { key, newValue ->
      val oldValue = populatedData[key]!!

      map.put(key, newValue) shouldBeSameInstanceAs oldValue

      populatedData[key] = newValue
    }
  }

  @Test
  fun `put() does not affect other key-value pairs`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate().toMutableMap()

    checkAll(propTestConfig, Arb.of(populatedData.keys.sorted()), valueArb()) { key, newValue ->
      map.put(key, newValue)
      populatedData[key] = newValue

      populatedData.keys.sorted().shuffled(randomSource().random).forEach {
        map.get(it) shouldBeSameInstanceAs populatedData[it]
      }
    }
  }

  @Test
  fun `put() increments size if key was not previously mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()
    var expectedSize = populatedData.size

    checkAll(propTestConfig, Arb.int().filterNot { it in populatedData }, valueArb()) { key, value
      ->
      expectedSize++

      map.put(key, value)

      map.size() shouldBe expectedSize
    }
  }

  @Test
  fun `put() does not change size if key was previously mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()
    val expectedSize = populatedData.size

    checkAll(propTestConfig, Arb.of(populatedData.keys.sorted()), valueArb()) { key, value ->
      map.put(key, value)

      map.size() shouldBe expectedSize
    }
  }

  @Test
  fun `put() returns null values after background cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val keys = map.populate().keys.toList()

    repeat(50) {
      if (map.size() != 0) {
        System.gc()
        delayIgnoringTestScheduler(50.milliseconds)
      }
    }

    keys.sorted().shuffled().forEach { map.put(it, valueArb().next()).shouldBeNull() }
  }

  @Test
  fun `put() returns null as garbage collection occurs without cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(noopThreadFactory)
    cleanups.registerSuspending { map.close() }
    val keys = map.populate().keys.toMutableSet()

    repeat(50) {
      var nonNullFound = false
      keys.toList().forEach { key ->
        if (map.get(key) !== null) {
          nonNullFound = true
        } else {
          map.put(key, valueArb().next()).shouldBeNull()
        }
        keys.remove(key)
      }
      if (nonNullFound) {
        System.gc()
        delayIgnoringTestScheduler(50.milliseconds)
      }
    }

    keys.shouldBeEmpty()
  }

  @Test
  fun `remove() returns null on an empty map`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }

    checkAll(propTestConfig, Arb.int()) { key -> map.remove(key).shouldBeNull() }
  }

  @Test
  fun `remove(key) causes get(key) to return null`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    checkAll(propTestConfig, Arb.int().filterNot { it in populatedData }) { key ->
      map.remove(key)

      map.get(key).shouldBeNull()
    }
  }

  @Test
  fun `remove(key) does not affect get(some other key)`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate().toMutableMap()

    checkAll(propTestConfig, Arb.int().filterNot { it in populatedData }) { key ->
      populatedData.remove(key)

      map.remove(key)

      populatedData.keys.sorted().shuffled(randomSource().random).forEach {
        map.get(it) shouldBeSameInstanceAs populatedData[it]
      }
    }
  }

  @Test
  fun `remove(key) decrements size`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    populatedData.keys.sorted().shuffled().forEachIndexed { index, key ->
      map.remove(key)

      map.size() shouldBe populatedData.size - index - 1
    }
  }

  @Test
  fun `remove() returns null if key is not mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    checkAll(propTestConfig, Arb.int().filterNot { it in populatedData }) { key ->
      map.remove(key).shouldBeNull()
    }
  }

  @Test
  fun `remove() returns removed value if key was mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    populatedData.keys.sorted().shuffled().forEach {
      val oldValue = populatedData[it]!!

      map.remove(it) shouldBeSameInstanceAs oldValue
    }
  }

  @Test
  fun `remove() returns null values after background cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val keys = map.populate().keys.toList()

    repeat(50) {
      if (map.size() != 0) {
        System.gc()
        delayIgnoringTestScheduler(50.milliseconds)
      }
    }

    keys.sorted().shuffled().forEach { map.remove(it).shouldBeNull() }
  }

  @Test
  fun `remove() returns null as garbage collection occurs without cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(noopThreadFactory)
    cleanups.registerSuspending { map.close() }
    val keys = map.populate().keys.toMutableSet()

    repeat(50) {
      var nonNullFound = false
      keys.toList().forEach { key ->
        if (map.get(key) !== null) {
          nonNullFound = true
        } else {
          map.remove(key).shouldBeNull()
        }
        keys.remove(key)
      }
      if (nonNullFound) {
        System.gc()
        delayIgnoringTestScheduler(50.milliseconds)
      }
    }

    keys.shouldBeEmpty()
  }

  @Test
  fun `clear() returns 0 on an empty map`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }

    map.clear() shouldBe 0
  }

  @Test
  fun `clear() causes get(key) to return null`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val populatedData = map.populate()

    map.clear()

    populatedData.keys.sorted().shuffled().forEach { map.get(it).shouldBeNull() }
  }

  @Test
  fun `clear() causes size() to return 0`() = runTest {
    checkAll(propTestConfig, Arb.int(0..50), Arb.randomSeed()) { size, randomSeed ->
      val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
      cleanups.registerSuspending { map.close() }
      map.populate(size = size, randomSeed = randomSeed)

      map.clear()

      map.size() shouldBe 0
      map.close()
    }
  }

  @Test
  fun `clear() returns the number of key-value pairs removed`() = runTest {
    checkAll(propTestConfig, Arb.int(0..50), Arb.randomSeed()) { size, randomSeed ->
      val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
      cleanups.registerSuspending { map.close() }
      map.populate(size = size, randomSeed = randomSeed)

      map.clear() shouldBe size

      map.close()
    }
  }

  @Test
  fun `close() on a new instance causes all other methods to throw`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    map.close()

    map.verifyAllMethodsThrowIllegalStateException()
  }

  @Test
  fun `close() on a populated instance causes all other methods to throw`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    map.populate()
    map.close()

    map.verifyAllMethodsThrowIllegalStateException()
  }

  @Test
  fun `close() can be called multiple times`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)

    repeat(10) { map.close() }
  }

  @Test
  fun `cleanup thread cleans up garbage collected values`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }

    map.put(1, Value("gc-test"))

    repeat(50) {
      if (map.size() != 0) {
        System.gc()
        delayIgnoringTestScheduler(50.milliseconds)
      }
    }

    map.size() shouldBe 0
    map.get(1) shouldBe null
  }

  @Test
  fun `cleanup thread is not started until first use`() = runTest {
    val underlyingThreadFactory = cleanupThreadFactory
    val threadRef = MutableStateFlow(NullableReference<Thread>())
    val cleanupThreadFactory = ThreadFactory {
      val thread = underlyingThreadFactory.newThread(it)
      threadRef.value = NullableReference(thread)
      thread
    }
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    threadRef.value.ref.shouldBeNull()

    map.put(1, valueArb().next())

    threadRef.value.ref.shouldNotBeNull()
  }

  @Test
  fun `cleanup thread is stopped by close()`() = runTest {
    val underlyingThreadFactory = cleanupThreadFactory
    val threadRef = MutableStateFlow(NullableReference<Thread>())
    val cleanupThreadFactory = ThreadFactory {
      val thread = underlyingThreadFactory.newThread(it)
      threadRef.value = NullableReference(thread)
      thread
    }
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    threadRef.value.ref.shouldBeNull()
    map.put(1, valueArb().next())

    map.close()

    threadRef.value.ref.shouldNotBeNull().let { thread ->
      thread.join(1000)
      thread.state shouldBe Thread.State.TERMINATED
    }
  }

  @Test
  fun `cleanup thread ignores stale values`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val key = Arb.int().next()
    val (weakValue1, value2) =
      run {
        val (value1, value2) = valueArb().pair().next()
        map.put(key, value1)
        map.put(key, value2)
        Pair(WeakReference(value1), value2)
      }

    repeat(50) {
      if (weakValue1.get() !== null) {
        System.gc()
        delayIgnoringTestScheduler(50.milliseconds)
      }
    }

    map.get(key) shouldBeSameInstanceAs value2
    weakValue1.get().shouldBeNull()
  }

  @Test
  fun `thread safety of put, get, and remove`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(cleanupThreadFactory)
    cleanups.registerSuspending { map.close() }
    val mutex = Mutex()
    val candidateValues = Arb.int().distinct().take(5).associateWith { mutableListOf<Value?>() }
    val latch = SuspendingCountDownLatch(50)
    val jobs =
      List(latch.count) {
        backgroundScope.launch(Dispatchers.Default) {
          val keys = Arb.of(candidateValues.keys).take(500).toList()
          val values = valueArb().orNull(nullProbability = 0.002).take(keys.size).toList()
          latch.countDown().await()
          for (i in keys.indices) {
            val key = keys[i]
            val value = values[i]
            map.get(key)
            if (value !== null) {
              map.put(key, value)
            } else {
              map.remove(key)
            }
          }
          mutex.withLock {
            keys.zip(values).forEach { (key, value) -> candidateValues[key]!!.add(value) }
          }
        }
      }
    jobs.joinAll()

    candidateValues.entries.forEach { (key, candidateValues) ->
      map.get(key) shouldBeIn candidateValues
    }
  }

  /** A [ThreadFactory] that always returns a new thread whose run() method returns immediately. */
  val noopThreadFactory: ThreadFactory
    get() = ThreadFactory { Thread(testName.methodName) }

  private val cleanupThreadFactory
    get() = ThreadFactory {
      Thread(it).apply {
        name = "${testName.methodName}-cleanup"
        isDaemon = true
      }
    }
}

private val propTestConfig = PropTestConfig(iterations = 500, shrinkingMode = ShrinkingMode.Off)

private fun valueArb(string: Arb<String> = Arb.usernames().map { it.value }): Arb<Value> =
  string.map(::Value)

private data class Value(val string: String)

private suspend fun SuspendingWeakValueHashMap<Int, Value>.populate(
  size: Int = propTestConfig.iterations!!,
  randomSeed: Long = Random.nextLong(),
): Map<Int, Value> {
  val insertedValues = mutableMapOf<Int, Value>()
  val propTestConfig = propTestConfig.copy(iterations = size, seed = randomSeed)

  checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
    insertedValues[key] = value
    put(key, value)
  }

  return insertedValues.toMap()
}

private suspend fun SuspendingWeakValueHashMap<Int, Value>
  .verifyAllMethodsThrowIllegalStateException() {
  val keyArb = Arb.int()
  val valueArb = valueArb()
  withClue("get()") { shouldThrow<IllegalStateException> { get(keyArb.next()) } }
  withClue("put()") { shouldThrow<IllegalStateException> { put(keyArb.next(), valueArb.next()) } }
  withClue("remove()") { shouldThrow<IllegalStateException> { remove(keyArb.next()) } }
  withClue("clear()") { shouldThrow<IllegalStateException> { clear() } }
  withClue("size()") { shouldThrow<IllegalStateException> { size() } }
}
