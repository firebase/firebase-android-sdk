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

import com.google.firebase.dataconnect.testutil.BlockReturning
import com.google.firebase.dataconnect.testutil.BlockReturningUnit
import com.google.firebase.dataconnect.testutil.BlockReturningWithParameter
import com.google.firebase.dataconnect.testutil.BlockThrowing
import com.google.firebase.dataconnect.testutil.BlockThrowingWithParameter
import com.google.firebase.dataconnect.testutil.BlockWithParameter
import com.google.firebase.dataconnect.testutil.property.arbitrary.shouldHaveSameValueAs
import com.google.firebase.dataconnect.testutil.property.arbitrary.someValue
import com.google.firebase.dataconnect.testutil.property.arbitrary.twoValues
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldHaveSingleElement
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.throwable.shouldHaveMessage
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.orNull
import io.kotest.property.checkAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MaybeValueStateFlowUnitTest {

  // region Tests for StateFlow<MaybeValue>.isEmpty

  @Test
  fun `isEmpty returns true for Empty`() {
    val stateFlow = MutableStateFlow<MaybeValue<*>>(MaybeValue.Empty)
    stateFlow.isEmpty shouldBe true
  }

  @Test
  fun `isEmpty returns false for Value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value?.value))
      stateFlow.isEmpty shouldBe false
    }
  }

  // endregion

  // region Tests for StateFlow<MaybeValue>.getOrNull()

  @Test
  fun `getOrNull returns null for Empty`() {
    val stateFlow = MutableStateFlow<MaybeValue<Int>>(MaybeValue.Empty)
    stateFlow.getOrNull().shouldBeNull()
  }

  @Test
  fun `getOrNull returns value for Value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value?.value))
      stateFlow.getOrNull().shouldHaveSameValueAs(value)
    }
  }

  // endregion

  // region Tests for StateFlow<MaybeValue>.getOrThrow()

  @Test
  fun `getOrThrow throws for Empty`() {
    val stateFlow = MutableStateFlow<MaybeValue<Int>>(MaybeValue.Empty)
    val exception = shouldThrow<IllegalStateException> { stateFlow.getOrThrow() }
    exception shouldHaveMessage "no value"
  }

  @Test
  fun `getOrThrow returns value for Value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value?.value))
      stateFlow.getOrThrow().shouldHaveSameValueAs(value)
    }
  }

  // endregion

  // region Tests for StateFlow<MaybeValue>.getOrElse

  @Test
  fun `getOrElse returns result of block for Empty`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.2)) { value ->
      val stateFlow = MutableStateFlow<MaybeValue<Any>>(MaybeValue.Empty)
      val block = BlockReturning(value?.value)

      val result = stateFlow.getOrElse(block)

      result.shouldHaveSameValueAs(value)
      block.callCount shouldBe 1
    }
  }

  @Test
  fun `getOrElse returns value and does not call block for Value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.2)) { value ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value))
      val block = BlockThrowing("block should not be called [jdqedgps94]")

      val result = stateFlow.getOrElse(block)

      result.shouldHaveSameValueAs(value)
      block.callCount shouldBe 0
    }
  }

  // endregion

  // region Tests for StateFlow<MaybeValue>.ifEmpty

  @Test
  fun `ifEmpty calls block for Empty`() {
    val stateFlow = MutableStateFlow<MaybeValue<Int>>(MaybeValue.Empty)
    val block = BlockReturningUnit()

    stateFlow.ifEmpty(block)

    block.callCount shouldBe 1
  }

  @Test
  fun `ifEmpty does not call block for Value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value?.value))
      val block = BlockThrowing("block should not be called [at5vkheznb]")

      stateFlow.ifEmpty(block)

      block.callCount shouldBe 0
    }
  }

  // endregion

  // region Tests for StateFlow<MaybeValue>.ifNonEmpty

  @Test
  fun `ifNonEmpty does not call block for Empty`() {
    val stateFlow = MutableStateFlow<MaybeValue<Nothing?>>(MaybeValue.Empty)
    val block = BlockThrowingWithParameter("block should not be called [yp9hg6zeam]")

    stateFlow.ifNonEmpty(block)

    block.callCount shouldBe 0
  }

  @Test
  fun `ifNonEmpty calls block with value for Value`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value?.value))
      val block = BlockWithParameter<Any?>()

      stateFlow.ifNonEmpty(block)

      block.calls shouldHaveSingleElement value
    }
  }

  // endregion

  // region Tests for StateFlow<MaybeValue>.fold

  @Test
  fun `fold calls onEmpty for Empty`() = runTest {
    checkAll(propTestConfig, Arb.someValue().orNull(nullProbability = 0.3)) { value ->
      val stateFlow = MutableStateFlow<MaybeValue<Nothing?>>(MaybeValue.Empty)
      val onEmpty = BlockReturning(value?.value)
      val onNonEmpty = BlockThrowingWithParameter("onNonEmpty should not be called [v7zhnjpjcy]")

      val result = stateFlow.fold(onEmpty = onEmpty, onNonEmpty = onNonEmpty)

      result shouldBeSameInstanceAs value
      onEmpty.callCount shouldBe 1
      onNonEmpty.callCount shouldBe 0
    }
  }

  @Test
  fun `fold calls onNonEmpty with value for Value`() = runTest {
    checkAll(propTestConfig, Arb.twoValues(Arb.someValue().orNull(nullProbability = 0.3))) {
      (value, resultValue) ->
      val stateFlow = MutableStateFlow(MaybeValue.Value(value?.value))
      val onEmpty = BlockThrowing("onEmpty should not be called [n76e59r3px]")
      val onNonEmpty = BlockReturningWithParameter<Any?, Any?>(resultValue?.value)

      val result = stateFlow.fold(onEmpty = onEmpty, onNonEmpty = onNonEmpty)

      result shouldBeSameInstanceAs resultValue
      onEmpty.callCount shouldBe 0
      onNonEmpty.calls shouldHaveSingleElement value?.value
    }
  }

  // endregion

}

// region Helper classes, functions, and properties

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 100,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

// endregion
