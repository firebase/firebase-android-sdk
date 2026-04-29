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

import com.google.firebase.dataconnect.testutil.BlockReturningUnit
import com.google.firebase.dataconnect.testutil.BlockThrowing
import com.google.firebase.dataconnect.testutil.SuspendingCountDownLatch
import io.kotest.assertions.withClue
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SuspendingFlagUnitTests {

  // region Tests for SuspendingFlag constructor

  @Test
  fun `constructor default value is unset`() {
    val flag = SuspendingFlag()

    flag.state.getOrNull() shouldBe Unit
    flag.isSet shouldBe false
  }

  @Test
  fun `constructor with unset value`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.unset)

    flag.state.getOrNull() shouldBe Unit
    flag.isSet shouldBe false
  }

  @Test
  fun `constructor with set value`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.set)

    flag.state.getOrNull() shouldBe null
    flag.isSet shouldBe true
  }

  // endregion

  // region Tests for SuspendingFlag.isSet

  @Test
  fun `isSet returns false for newly created flag`() {
    SuspendingFlag().isSet shouldBe false
  }

  @Test
  fun `isSet returns true after set() is called`() {
    val flag = SuspendingFlag()
    flag.set()
    flag.isSet shouldBe true
  }

  @Test
  fun `isSet returns true if initialized as set`() {
    SuspendingFlag(SuspendingFlag.Values.set).isSet shouldBe true
  }

  // endregion

  // region Tests for SuspendingFlag.set()

  @Test
  fun `set() returns true when transitioning from unset to set`() {
    val flag = SuspendingFlag()
    flag.set() shouldBe true
  }

  @Test
  fun `set() returns false when already set`() {
    val flag = SuspendingFlag()
    flag.set()
    flag.set() shouldBe false
  }

  @Test
  fun `set() returns false if initialized as set`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.set)
    flag.set() shouldBe false
  }

  @Test
  fun `set() concurrent calls`() = runTest {
    val flag = SuspendingFlag()
    val latch = SuspendingCountDownLatch(50)

    val jobs =
      List(latch.count) {
        backgroundScope.async(Dispatchers.Default) {
          latch.countDown().await()
          flag.set()
        }
      }
    val results = jobs.awaitAll()

    results.count { it } shouldBe 1
    results.count { !it } shouldBe jobs.size - 1
    flag.isSet shouldBe true
  }

  // endregion

  // region Tests for SuspendingFlag.ifSet()

  @Test
  fun `ifSet() calls block if set`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.set)
    val block = BlockReturningUnit()

    flag.ifSet(block)

    block.callCount shouldBe 1
  }

  @Test
  fun `ifSet() does not call block if unset`() {
    val flag = SuspendingFlag()
    val block = BlockThrowing("block should not be called")

    flag.ifSet(block)

    block.callCount shouldBe 0
  }

  @Test
  fun `ifSet() calls block after set()`() {
    val flag = SuspendingFlag()
    flag.set()
    val block = BlockReturningUnit()

    flag.ifSet(block)

    block.callCount shouldBe 1
  }

  // endregion

  // region Tests for SuspendingFlag.ifNotSet()

  @Test
  fun `ifNotSet() calls block if unset`() {
    val flag = SuspendingFlag()
    val block = BlockReturningUnit()

    flag.ifNotSet(block)

    block.callCount shouldBe 1
  }

  @Test
  fun `ifNotSet() does not call block if set`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.set)
    val block = BlockThrowing("block should not be called")

    flag.ifNotSet(block)

    block.callCount shouldBe 0
  }

  @Test
  fun `ifNotSet() does not call block after set()`() {
    val flag = SuspendingFlag()
    flag.set()
    val block = BlockThrowing("block should not be called")

    flag.ifNotSet(block)

    block.callCount shouldBe 0
  }

  // endregion

  // region Tests for SuspendingFlag.setOrElse()

  @Test
  fun `setOrElse() transitions to set and does not call block if unset`() {
    val flag = SuspendingFlag()
    val block = BlockThrowing("block should not be called")

    flag.setOrElse(block)

    flag.isSet shouldBe true
    block.callCount shouldBe 0
  }

  @Test
  fun `setOrElse() calls block if already set`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.set)
    val block = BlockReturningUnit()

    flag.setOrElse(block)

    flag.isSet shouldBe true
    block.callCount shouldBe 1
  }

  @Test
  fun `setOrElse() concurrent calls`() = runTest {
    val flag = SuspendingFlag()
    val latch = SuspendingCountDownLatch(50)
    val block = BlockReturningUnit()

    val jobs =
      List(latch.count) {
        backgroundScope.async(Dispatchers.Default) {
          latch.countDown().await()
          flag.setOrElse(block)
        }
      }
    jobs.awaitAll()

    flag.isSet shouldBe true
    // Exactly jobs.size - 1 calls should have been made to the block
    // because one call succeeded in clearing the value.
    block.callCount shouldBe (jobs.size - 1)
  }

  // endregion

  // region Tests for SuspendingFlag.fold()

  @Test
  fun `fold() calls onNotSet if unset`() {
    val flag = SuspendingFlag()
    val onSet = BlockThrowing("onSet should not be called")
    val onNotSet = BlockReturningUnit()

    flag.fold(onSet = onSet, onNotSet = onNotSet)

    onSet.callCount shouldBe 0
    onNotSet.callCount shouldBe 1
  }

  @Test
  fun `fold() calls onSet if set`() {
    val flag = SuspendingFlag(SuspendingFlag.Values.set)
    val onSet = BlockReturningUnit()
    val onNotSet = BlockThrowing("onNotSet should not be called")

    flag.fold(onSet = onSet, onNotSet = onNotSet)

    onSet.callCount shouldBe 1
    onNotSet.callCount shouldBe 0
  }

  @Test
  fun `fold() returns value from block`() {
    val flag = SuspendingFlag()
    val result = flag.fold(onSet = { "set" }, onNotSet = { "unset" })
    result shouldBe "unset"

    flag.set()
    val result2 = flag.fold(onSet = { "set" }, onNotSet = { "unset" })
    result2 shouldBe "set"
  }

  // endregion

  // region Tests for SuspendingFlag.toString()

  @Test
  fun `toString() returns correct string when unset`() {
    SuspendingFlag().toString() shouldBe "SuspendingFlag(set=false)"
  }

  @Test
  fun `toString() returns correct string when set`() {
    val flag = SuspendingFlag()
    flag.set()
    flag.toString() shouldBe "SuspendingFlag(set=true)"
  }

  @Test
  fun `toString() returns correct string when initialized as set`() {
    SuspendingFlag(SuspendingFlag.Values.set).toString() shouldBe "SuspendingFlag(set=true)"
  }

  // endregion

}
