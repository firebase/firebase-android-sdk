/*
 * Copyright 2026 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.firebase.sessions

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import com.google.firebase.sessions.testing.FakeDataStore
import java.io.IOException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
class LazyDataStoreTest {
  @Test
  fun doesNotCreateDelegateBeforeFirstUse() {
    var createCount = 0
    val lazyDataStore = LazyDataStore {
      createCount++
      FakeDataStore(firstValue = 23)
    }

    // Getting the data flow without collecting it doesn't create the delegate.
    lazyDataStore.data

    assertThat(createCount).isEqualTo(0)
    assertThat(lazyDataStore.isInitialized()).isFalse()
  }

  @Test
  fun collectData_createsDelegateAndEmitsItsValues() = runTest {
    val fakeDataStore = FakeDataStore(firstValue = 23)
    val lazyDataStore = LazyDataStore { fakeDataStore }

    val result = mutableListOf<Int>()
    backgroundScope.launch { lazyDataStore.data.collect { result.add(it) } }
    runCurrent()

    assertThat(lazyDataStore.isInitialized()).isTrue()

    lazyDataStore.updateData { 1 }
    lazyDataStore.updateData { it + 1 }
    runCurrent()

    assertThat(result).containsExactly(23, 1, 2).inOrder()
    fakeDataStore.close()
  }

  @Test
  fun updateData_createsDelegateAndUpdatesIt() = runTest {
    val lazyDataStore = LazyDataStore { FakeDataStore(firstValue = 23) }

    assertThat(lazyDataStore.updateData { it + 1 }).isEqualTo(24)
    assertThat(lazyDataStore.isInitialized()).isTrue()
  }

  @Test
  fun multipleUses_createDelegateOnce() = runTest {
    var createCount = 0
    val lazyDataStore = LazyDataStore {
      createCount++
      FakeDataStore(firstValue = 23)
    }

    lazyDataStore.data.first()
    lazyDataStore.updateData { it + 1 }
    lazyDataStore.data.first()

    assertThat(createCount).isEqualTo(1)
  }

  @Test
  fun concurrentFirstUse_createsDelegateOnce() {
    val createCount = AtomicInteger()
    val lazyDataStore = LazyDataStore {
      createCount.incrementAndGet()
      // Slow down creation so the other threads try to create the delegate at the same time.
      Thread.sleep(100)
      FakeDataStore(firstValue = 23)
    }

    val results =
      Executors.newFixedThreadPool(THREAD_COUNT).asCoroutineDispatcher().use { dispatcher ->
        runBlocking {
          List(THREAD_COUNT) { async(dispatcher) { lazyDataStore.data.first() } }.awaitAll()
        }
      }

    assertThat(results).containsExactlyElementsIn(List(THREAD_COUNT) { 23 })
    assertThat(createCount.get()).isEqualTo(1)
  }

  @Test
  fun createsDelegateOnTheThreadThatFirstUsesIt() {
    val creatingThread = AtomicReference<Thread>()
    // Construct the data store on this thread, like the SDK does on the main thread.
    val lazyDataStore = LazyDataStore {
      creatingThread.set(Thread.currentThread())
      FakeDataStore(firstValue = 23)
    }

    // Read it on a background thread, like the SDK does.
    val backgroundThread =
      Executors.newSingleThreadExecutor().asCoroutineDispatcher().use { backgroundDispatcher ->
        runBlocking(backgroundDispatcher) {
          lazyDataStore.data.first()
          Thread.currentThread()
        }
      }

    assertThat(creatingThread.get()).isSameInstanceAs(backgroundThread)
    assertThat(creatingThread.get()).isNotSameInstanceAs(Thread.currentThread())
  }

  @Test
  fun createDelegateFails_throwsToCollector_andNextUseRetries() = runTest {
    var createCount = 0
    val lazyDataStore = LazyDataStore {
      createCount++
      if (createCount == 1) {
        throw IOException("Failed to create data store")
      }
      FakeDataStore(firstValue = 23)
    }

    // The exception is thrown through the data flow, so the SDK's catch operators handle it.
    val firstResult = lazyDataStore.data.catch { emit(-1) }.first()
    val secondResult = lazyDataStore.data.first()

    assertThat(firstResult).isEqualTo(-1)
    assertThat(secondResult).isEqualTo(23)
    assertThat(createCount).isEqualTo(2)
  }

  private companion object {
    const val THREAD_COUNT = 8
  }
}
