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
@file:OptIn(ExperimentalKotest::class, DelicateKotest::class, ExperimentalCoroutinesApi::class)

package com.google.firebase.dataconnect.util

import com.google.firebase.dataconnect.testutil.CleanupsRule
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import com.google.firebase.dataconnect.testutil.delayIgnoringTestScheduler
import com.google.firebase.dataconnect.testutil.property.arbitrary.distinctPair
import com.google.firebase.dataconnect.testutil.property.arbitrary.pair
import com.google.firebase.dataconnect.testutil.property.arbitrary.withIterations
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.DelicateKotest
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.PropertyContext
import io.kotest.property.RandomSource
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.choice
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
import io.mockk.mockk
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.test.runTest
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestName
import java.lang.ref.WeakReference
import java.util.concurrent.Executors
import java.util.concurrent.locks.ReentrantLock
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

class SuspendingWeakValueHashMapUnitTest {

  @get:Rule val cleanups = CleanupsRule()

  @get:Rule val testName = TestName()

  @Test
  fun `size() returns 0 on an new instance`() = verifyWithNewInstance {
    it.size() shouldBe 0
  }

  @Test
  fun `size() returns 0 on an empty but previously non-empty instance`() = verifyWithEmptyButPreviouslyNonEmptyInstance {
    it.size() shouldBe 0
  }

  @Test
  fun `size() returns 0 on a closed instance that was _never_ populated`() = verifyWithClosedInstanceThatWasNeverPopulated {
    it.size() shouldBe 0
  }

  @Test
  fun `size() returns 0 on a closed instance that _was_ populated`() = verifyWithClosedInstanceThatWasPopulated {
    it.size() shouldBe 0
  }

  @Test
  fun `size() returns the size of a populated map`() = verifyWithPopulatedMap { map, populatedValues ->
    map.size() shouldBe populatedValues.size
  }

  @Test
  fun `size() returns smaller values as background cleanup occurs`() = verifyAsValuesAreGarbageCollected(
    onIteration = { map, _ -> map.size() },
    verify = { _, sizes, populatedKeys ->
      sizes shouldContainExactly sizes.sortedDescending()
      if (sizes.isNotEmpty()) {
        sizes.max() shouldBeLessThanOrEqual populatedKeys.size
      }
    }
  )

  @Test
  fun `size() does not return smaller values as garbage collection occurs without cleanup`() = verifyAsCleanupLoopIsStalledAndValuesAreGarbageCollected(
    onIteration = { map, _ ->
      map.size()
                  },
    verify = { _, sizes, populatedKeys ->
      val expectedSizes = List(sizes.size) { populatedKeys.size }
      sizes shouldContainExactly expectedSizes
    }
  )

  private fun verifyWithNewInstance(verify: suspend (SuspendingWeakValueHashMap<Int, Value>) -> Unit) = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(mockk())
    cleanups.register(map)
    verify(map)
  }

  private fun verifyWithEmptyButPreviouslyNonEmptyInstance(verify: suspend (SuspendingWeakValueHashMap<Int, Value>) -> Unit) = runTest {
    checkAll(propTestConfig, Arb.int(0..50)) { size ->
      val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
      val cleanupRegistration = cleanups.register(map)
      map.startCleanupJob(backgroundScope)
      map.populate(size, randomSource())
      map.clear()
      verify(map)
      map.close()
      cleanups.unregister(cleanupRegistration)
    }
  }

  private fun verifyWithClosedInstanceThatWasNeverPopulated(verify: suspend (SuspendingWeakValueHashMap<Int, Value>) -> Unit) = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(mockk())
    map.close()
    verify(map)
  }

  private fun verifyWithClosedInstanceThatWasPopulated(verify: suspend (SuspendingWeakValueHashMap<Int, Value>) -> Unit) = runTest {
    checkAll(propTestConfig, Arb.int(0..50)) { size ->
      val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
      val cleanupRegistration = cleanups.register(map)
      map.startCleanupJob(backgroundScope)
      map.populate(size, randomSource())
      map.close()
      verify(map)
      map.close()
      cleanups.unregister(cleanupRegistration)
    }
  }

  private fun verifyWithPopulatedMap(verify: suspend (SuspendingWeakValueHashMap<Int, Value>, Map<Int, Value>) -> Unit) = runTest {
    checkAll(propTestConfig, Arb.int(1..50)) { size ->
      val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
      val cleanupRegistration = cleanups.register(map)
      map.startCleanupJob(backgroundScope)
      val populatedValues = map.populate(size, randomSource())
      verify(map, populatedValues)
      map.close()
      cleanups.unregister(cleanupRegistration)
    }
  }

  private fun verifyAsValuesAreGarbageCollected(onIteration: suspend PropertyContext.(SuspendingWeakValueHashMap<Int, Value>, List<Int>) -> Int, verify: suspend PropertyContext.(SuspendingWeakValueHashMap<Int, Value>, List<Int>, List<Int>) -> Unit) = verifyWithGarbageCollectionIntervals(onIteration, verify, blockingDispatcher, gcDelay = 50.milliseconds, iterations=50)

  private fun verifyAsCleanupLoopIsStalledAndValuesAreGarbageCollected(onIteration: suspend PropertyContext.(SuspendingWeakValueHashMap<Int, Value>, List<Int>) -> Int, verify: suspend PropertyContext.(SuspendingWeakValueHashMap<Int, Value>, List<Int>, List<Int>) -> Unit) {
    val singleThreadExecutor = Executors.newSingleThreadExecutor()
    cleanups.register { singleThreadExecutor.shutdownNow() }

    run {
      val lock = ReentrantLock()
      check(lock.tryLock())
      singleThreadExecutor.execute {
        try {
          lock.lockInterruptibly()
        } catch (_: InterruptedException) {
          Thread.currentThread().interrupt()
        }
      }
    }

    return verifyWithGarbageCollectionIntervals(onIteration, verify, singleThreadExecutor.asCoroutineDispatcher(), gcDelay = 1.nanoseconds, iterations=5,)
  }

  private fun verifyWithGarbageCollectionIntervals(onIteration: suspend PropertyContext.(SuspendingWeakValueHashMap<Int, Value>, List<Int>) -> Int, verify: suspend PropertyContext.(SuspendingWeakValueHashMap<Int, Value>, List<Int>, List<Int>) -> Unit, mapCoroutineDispatcher: CoroutineDispatcher, gcDelay: Duration, iterations: Int) = runTest {
    checkAll(propTestConfig.withIterations(iterations), Arb.int(0..50)) { size ->
      val map = SuspendingWeakValueHashMap<Int, Value>(mapCoroutineDispatcher)
      val cleanupRegistration = cleanups.register(map)
      map.startCleanupJob(backgroundScope)
      val populatedKeys = map.populate(size, randomSource()).keys.toList().sorted().shuffled(randomSource().random)

      val values =
        List(50) {
          val value = onIteration(map, populatedKeys)
          if (value != 0) {
            System.gc()
            delayIgnoringTestScheduler(gcDelay)
          }
          value
        }

      verify(map, values, populatedKeys)
      map.close()
      cleanups.unregister(cleanupRegistration)
    }
  }

  @Test
  fun `get() returns null on an new instance`() = verifyWithNewInstance {
    checkAll(propTestConfig, Arb.int()) { key -> it.get(key).shouldBeNull() }
  }

  @Test
  fun `get() returns null on an empty but previously non-empty instance`() = verifyWithEmptyButPreviouslyNonEmptyInstance {
    checkAll(propTestConfig, Arb.int()) { key -> it.get(key).shouldBeNull() }
  }

  @Test
  fun `get() returns null on a closed instance that was _never_ populated`() = verifyWithClosedInstanceThatWasNeverPopulated {
    checkAll(propTestConfig, Arb.int()) { key -> it.get(key).shouldBeNull() }
  }

  @Test
  fun `get() returns null on a closed instance that _was_ populated`() = verifyWithClosedInstanceThatWasPopulated {
    checkAll(propTestConfig, Arb.int()) { key -> it.get(key).shouldBeNull() }
  }


  @Test
  fun `get() returns the value specified to put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
    val valueStrongReferences = mutableListOf<Value>()

    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      valueStrongReferences.add(value)
      map.put(key, value)

      map.get(key) shouldBeSameInstanceAs value
    }
  }

  @Test
  fun `get() returns the most recent value specified to put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
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
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
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
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
    val populatedData = map.populate()

    populatedData.keys.shuffled().forEach {
      map.remove(it)

      map.get(it).shouldBeNull()
    }
  }

  @Test
  fun `get() returns null after clear()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
    val populatedData = map.populate()

    map.clear()

    populatedData.keys.shuffled().forEach { map.get(it).shouldBeNull() }
  }

  @Test
  fun `get() returns null values after background cleanup`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
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
  fun `get() returns null as background cleanup occurs`() = verifyAsValuesAreGarbageCollected(
    onIteration = { map, populatedKeys ->
      var nonNullCount = 0
      populatedKeys.forEach {
        if (map.get(it) !== null) {
          nonNullCount++
        }
      }
      nonNullCount
    },
    verify = { _, nonNullCounts, _ ->
      nonNullCounts.last() shouldBe 0
      nonNullCounts shouldContainExactly nonNullCounts.sortedDescending()
    }
  )

  @Test
  fun `get() returns null as garbage collection occurs without cleanup`() = verifyAsCleanupLoopIsStalledAndValuesAreGarbageCollected(
    onIteration = { map, populatedKeys ->
      var nonNullCount = 0
      populatedKeys.forEach {
        if (map.get(it) !== null) {
          nonNullCount++
        }
      }
      nonNullCount
    },
    verify = { _, nonNullCounts, _ ->
      nonNullCounts.last() shouldBe 0
      nonNullCounts shouldContainExactly nonNullCounts.sortedDescending()
    }
  )

  @Test
  fun `put() throws on an new instance`() = verifyWithNewInstance {
    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      shouldThrow<IllegalStateException> { it.put(key, value) }
    }
  }

  @Test
  fun `put() returns null on an empty but previously non-empty instance`() = verifyWithEmptyButPreviouslyNonEmptyInstance {
    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      it.put(key, value).shouldBeNull()
    }
  }

  @Test
  fun `put() throws on a closed instance that was _never_ populated`() = verifyWithClosedInstanceThatWasNeverPopulated {
    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      shouldThrow<IllegalStateException> { it.put(key, value) }
    }
  }

  @Test
  fun `put() throws on a closed instance that _was_ populated`() = verifyWithClosedInstanceThatWasPopulated {
    checkAll(propTestConfig, Arb.int().distinct(), valueArb()) { key, value ->
      shouldThrow<IllegalStateException> { it.put(key, value) }
    }
  }

  @Test
  fun `put() returns null if key is not mapped`() = verifyWithPopulatedMap { map, populatedValues ->
    val keyArb = Arb.int().filterNot { it in populatedValues }.distinct()
    checkAll(propTestConfig, keyArb, valueArb()) { key, value ->
      map.put(key, value).shouldBeNull()
    }
  }

  @Test
  fun `put() returns previous value if key was mapped`() = verifyWithPopulatedMap { map, populatedValues ->
    val upToDatePopulatedValues = populatedValues.toMutableMap()
    checkAll(propTestConfig, Arb.of(populatedValues.keys.sorted()), valueArb()) { key, newValue ->
      val oldValue = upToDatePopulatedValues[key]!!

      map.put(key, newValue) shouldBeSameInstanceAs oldValue

      upToDatePopulatedValues[key] = newValue
    }
  }

  @Test
  fun `put() does not affect other key-value pairs`() = verifyWithPopulatedMap { map, populatedValues ->
    val upToDatePopulatedValues = populatedValues.toMutableMap()

    checkAll(propTestConfig, Arb.of(populatedValues.keys.sorted()), valueArb()) { key, newValue ->
      map.put(key, newValue)
      upToDatePopulatedValues[key] = newValue

      populatedValues.keys.sorted().shuffled(randomSource().random).forEach {
        map.get(it) shouldBeSameInstanceAs upToDatePopulatedValues[it]
      }
    }
  }

  @Test
  fun `put() increments size if key was not previously mapped`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
    val populatedData = map.populate()
    var expectedSize = populatedData.size
    val unsetKeyArb = Arb.int().filterNot { it in populatedData }.distinct()

    checkAll(propTestConfig, unsetKeyArb, valueArb()) { key, value
      ->
      expectedSize++

      map.put(key, value)

      map.size() shouldBe expectedSize
    }
  }

  @Test
  fun `put() does not change size if key was previously mapped`() = verifyWithPopulatedMap { map, populatedValues ->
    val valueStrongReferences = mutableListOf<Value>()
    checkAll(propTestConfig, Arb.of(populatedValues.keys.sorted()), valueArb()) { key, value ->
      valueStrongReferences.add(value)
      map.put(key, value)

      map.size() shouldBe populatedValues.size
    }
  }

  @Test
  fun `put() returns null as background cleanup occurs`() = verifyAsValuesAreGarbageCollected(
    onIteration = {map, _ -> map.size()},
    verify = { map, _, populatedKeys ->
      val valueArb = valueArb()
      populatedKeys.sorted().shuffled().forEach {
        map.put(it, valueArb.next(randomSource())).shouldBeNull()
      }
    }
  )

  @Test
  fun `put() returns null as garbage collection occurs without cleanup`() {
    val valueArb = valueArb()
    var currentMap: SuspendingWeakValueHashMap<*,*>? = null
    var currentRemainingKeys: MutableSet<Int>? = null

    return verifyAsCleanupLoopIsStalledAndValuesAreGarbageCollected(
      onIteration = run {
        { map, populatedKeys ->
          if (currentMap !== map) {
            currentMap = map
            currentRemainingKeys = populatedKeys.toMutableSet()
          }
          val remainingKeys = currentRemainingKeys!!
          remainingKeys.toList().forEach { key ->
            if (map.get(key) === null) {
              map.put(key, valueArb.next(randomSource())).shouldBeNull()
              remainingKeys.remove(key)
            }
          }
          remainingKeys.size
        }
      },
      verify = { map, _, _ ->
        check(map === currentMap)
        currentMap = null
        currentRemainingKeys!!.shouldBeEmpty()
        currentRemainingKeys = null
      }
    )
  }







  @Test
  fun `remove() returns null on an new instance`() = verifyWithNewInstance {
    checkAll(propTestConfig, Arb.int()) { key -> it.remove(key).shouldBeNull() }
  }

  @Test
  fun `remove() returns null on an empty but previously non-empty instance`() = verifyWithEmptyButPreviouslyNonEmptyInstance {
    checkAll(propTestConfig, Arb.int()) { key -> it.remove(key).shouldBeNull() }
  }

  @Test
  fun `remove() returns null on a closed instance that was _never_ populated`() = verifyWithClosedInstanceThatWasNeverPopulated {
    checkAll(propTestConfig, Arb.int()) { key -> it.remove(key).shouldBeNull() }
  }

  @Test
  fun `remove() returns null on a closed instance that _was_ populated`() = verifyWithClosedInstanceThatWasPopulated {
    checkAll(propTestConfig, Arb.int()) { key -> it.remove(key).shouldBeNull() }
  }

  @Test
  fun `remove(key) returns the removed value`() = verifyWithPopulatedMap { map, populatedValues ->
    populatedValues.keys.shuffled().forEach { key ->
      map.remove(key) shouldBeSameInstanceAs populatedValues[key]
    }
  }

  @Test
  fun `remove(key) returns null after removing the value`() = verifyWithPopulatedMap { map, populatedValues ->
    populatedValues.keys.shuffled().forEach { key ->
      map.remove(key)
      map.remove(key).shouldBeNull()
    }
  }

  @Test
  fun `remove(key) returns null after clear()`() = verifyWithPopulatedMap { map, populatedValues ->
    map.clear()
    populatedValues.keys.shuffled().forEach { key ->
      map.remove(key).shouldBeNull()
    }
  }

  @Test
  fun `remove(key) returns null for keys never set`() = verifyWithPopulatedMap { map, populatedValues ->
    checkAll(propTestConfig, Arb.int().filterNot { it in populatedValues }) { key ->
      map.remove(key).shouldBeNull()
    }
  }

  @Test
  fun `remove(key) causes get(key) to return null`() = verifyWithPopulatedMap { map, populatedValues ->
    checkAll(propTestConfig, Arb.int().filterNot { it in populatedValues }) { key ->
      map.remove(key)
      map.get(key).shouldBeNull()
    }
  }

  @Test
  fun `remove(key) does not affect get(some other key)`() = verifyWithPopulatedMap { map, populatedValues ->
    val remainingKeys = populatedValues.keys.toMutableSet()
    val keyArb = Arb.choice(Arb.int(), Arb.of(populatedValues.keys))

    checkAll(propTestConfig, keyArb.distinctPair()) { (key1, key2) ->
      val key2ValueBefore = map.get(key2)
      remainingKeys.remove(key1)

      map.remove(key1)

      val key2ValueAfter = map.get(key2)
      key2ValueBefore shouldBeSameInstanceAs key2ValueAfter
    }
  }

  @Test
  fun `remove(key) decrements size`() = verifyWithPopulatedMap { map, populatedValues ->
    populatedValues.keys.shuffled().forEachIndexed { index, key ->
      map.remove(key)

      map.size() shouldBe populatedValues.size - index - 1
    }
  }

  @Test
  fun `remove() returns null as background cleanup occurs`() = verifyAsValuesAreGarbageCollected(
    onIteration = {map, _ -> map.size()},
    verify = { map, _, populatedKeys ->
      populatedKeys.sorted().shuffled().forEach {
        map.remove(it).shouldBeNull()
      }
    }
  )

  @Test
  fun `remove() returns null as garbage collection occurs without cleanup`() {
    var currentMap: SuspendingWeakValueHashMap<*,*>? = null
    var currentRemainingKeys: MutableSet<Int>? = null

    return verifyAsCleanupLoopIsStalledAndValuesAreGarbageCollected(
      onIteration = run {
        { map, populatedKeys ->
          if (currentMap !== map) {
            currentMap = map
            currentRemainingKeys = populatedKeys.toMutableSet()
          }
          val remainingKeys = currentRemainingKeys!!
          remainingKeys.toList().forEach { key ->
            if (map.get(key) === null) {
              map.remove(key).shouldBeNull()
              remainingKeys.remove(key)
            }
          }
          remainingKeys.size
        }
      },
      verify = { map, _, _ ->
        check(map === currentMap)
        currentMap = null
        currentRemainingKeys!!.shouldBeEmpty()
        currentRemainingKeys = null
      }
    )
  }



  @Test
  fun `clear() returns 0 on an new instance`() = verifyWithNewInstance {
    it.clear() shouldBe 0
  }

  @Test
  fun `clear() returns 0 on an empty but previously non-empty instance`() = verifyWithEmptyButPreviouslyNonEmptyInstance {
    it.clear() shouldBe 0
  }

  @Test
  fun `clear() returns 0 on a closed instance that was _never_ populated`() = verifyWithClosedInstanceThatWasNeverPopulated {
    it.clear() shouldBe 0
  }

  @Test
  fun `clear() returns 0 on a closed instance that _was_ populated`() = verifyWithClosedInstanceThatWasPopulated {
    it.clear() shouldBe 0
  }














  @Test
  fun `clear() causes get(key) to return null`() = verifyWithPopulatedMap { map, populatedValues ->
    map.clear()
    populatedValues.keys.shuffled().forEach { map.get(it).shouldBeNull() }
  }

  @Test
  fun `clear() causes size() to return 0`() = verifyWithPopulatedMap { map, _ ->
      map.clear()
      map.size() shouldBe 0
  }

  @Test
  fun `clear() returns the number of key-value pairs removed`() = verifyWithPopulatedMap { map, populatedValues ->
    map.clear() shouldBe populatedValues.size
  }


  @Test
  fun `close() can be called multiple times before put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)

    repeat(10) { map.close() }
  }

  @Test
  fun `close() can be called multiple times after put()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
    map.put(Arb.int().next(), valueArb().next())

    repeat(10) { map.close() }
  }

  @Test
  fun `cleanupLoop thread is cancelled by close()`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    val cleanupJob = map.startCleanupJob(backgroundScope)

    map.close()

    cleanupJob.isCancelled shouldBe true
  }

  @Test
  fun `cleanup loop ignores stale values`() = runTest {
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
    map.startCleanupJob(backgroundScope)
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
    val map = SuspendingWeakValueHashMap<Int, Value>(blockingDispatcher)
    cleanups.register(map)
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

}

private val propTestConfig = PropTestConfig(iterations = 500, shrinkingMode = ShrinkingMode.Off)

private val blockingDispatcher = Dispatchers.IO

private fun valueArb(string: Arb<String> = Arb.usernames().map { it.value }): Arb<Value> =
  string.map(::Value)

private data class Value(val string: String)

private suspend fun SuspendingWeakValueHashMap<Int, Value>.populate(size: Int, rs: RandomSource,): Map<Int, Value> = populate(size=size, randomSeed = rs.random.nextLong())

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
