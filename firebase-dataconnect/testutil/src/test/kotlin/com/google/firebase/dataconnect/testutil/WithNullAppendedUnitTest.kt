/*
 * Copyright 2025 Google LLC
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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.testutil.property.arbitrary.dataConnect
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.arbitrary.list
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class WithNullAppendedUnitTest {

  @Test
  fun `Iterable withNullAppended on an empty iterable`() {
    val iterable: Iterable<Nothing> = emptyList()
    iterable.withNullAppended() shouldBe listOf(null)
  }

  @Test
  fun `Iterable withNullAppended on a non-empty iterable`() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.dataConnect.string(), 1..100)) { list ->
      val iterable: Iterable<String> = list
      val expected = List(list.size + 1) { if (it < list.size) list[it] else null }
      iterable.withNullAppended() shouldBe expected
    }
  }

  @Test
  fun `Collection withNullAppended on an empty collection`() {
    val collection: Collection<Nothing> = emptyList()
    collection.withNullAppended() shouldBe listOf(null)
  }

  @Test
  fun `Collection withNullAppended on a non-empty collection`() = runTest {
    checkAll(NUM_ITERATIONS, Arb.list(Arb.dataConnect.string(), 1..100)) { list ->
      val collection: Collection<String> = list
      val expected = List(list.size + 1) { if (it < list.size) list[it] else null }
      collection.withNullAppended() shouldBe expected
    }
  }

  private companion object {

    const val NUM_ITERATIONS = 100
  }
}
