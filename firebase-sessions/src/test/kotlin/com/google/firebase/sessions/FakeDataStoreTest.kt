/*
 * Copyright 2025 Google LLC
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.junit.runner.RunWith

/** Tests for the [FakeDataStore] implementation. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(AndroidJUnit4::class)
internal class FakeDataStoreTest {
  @Test
  fun emitsProvidedValues() = runTest {
    val fakeDataStore = FakeDataStore(23)

    val result = mutableListOf<Int>()

    // Collect data into result list
    backgroundScope.launch { fakeDataStore.data.collect { result.add(it) } }

    fakeDataStore.updateData { 1 }
    fakeDataStore.updateData { 2 }
    fakeDataStore.updateData { 3 }
    fakeDataStore.updateData { 4 }

    runCurrent()

    assertThat(result).containsExactly(23, 1, 2, 3, 4)
  }

  @Test
  fun throwsProvidedExceptionOnEmit() = runTest {
    val fakeDataStore = FakeDataStore(23)

    val result = mutableListOf<String>()
    backgroundScope.launch {
      fakeDataStore.data
        .catch { ex -> result.add(ex.message!!) }
        .collect { result.add(it.toString()) }
    }

    fakeDataStore.updateData { 1 }
    fakeDataStore.throwOnNextEmit(IOException("oops"))

    runCurrent()

    assertThat(result).containsExactly("23", "1", "oops")
  }

  @Test(expected = IndexOutOfBoundsException::class)
  fun throwsProvidedExceptionOnUpdateData() = runTest {
    val fakeDataStore = FakeDataStore(23)

    fakeDataStore.updateData { 1 }
    fakeDataStore.throwOnNextUpdateData(IndexOutOfBoundsException("oops"))

    // Expected to throw
    fakeDataStore.updateData { 2 }
  }

  @Test(expected = IllegalArgumentException::class)
  fun throwsFirstProvidedExceptionOnCollect() = runTest {
    val fakeDataStore = FakeDataStore(23, IllegalArgumentException("oops"))

    // Expected to throw
    fakeDataStore.data.collect {}
  }

  @Test(expected = IllegalStateException::class)
  fun throwsFirstProvidedExceptionOnFirst() = runTest {
    val fakeDataStore = FakeDataStore(23, IllegalStateException("oops"))

    // Expected to throw
    fakeDataStore.data.first()
  }

  @Test
  fun consistentAfterManyUpdates() = runTest {
    val fakeDataStore = FakeDataStore(0)

    var collectResult = 0
    backgroundScope.launch { fakeDataStore.data.collect { collectResult = it } }

    var updateResult = 0
    // 100 is bigger than the channel buffer size so this will cause suspending
    repeat(100) { updateResult = fakeDataStore.updateData { it.inc() } }

    runCurrent()

    assertThat(collectResult).isEqualTo(100)
    assertThat(updateResult).isEqualTo(100)

    fakeDataStore.close()
  }
}
