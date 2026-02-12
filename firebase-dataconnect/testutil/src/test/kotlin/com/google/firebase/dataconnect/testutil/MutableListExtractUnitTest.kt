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

package com.google.firebase.dataconnect.testutil

import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.list
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.string
import io.kotest.property.checkAll
import kotlinx.coroutines.test.runTest
import org.junit.Test

class MutableListExtractUnitTest {

  @Test
  fun `extract() should return the extracted elements`() = runTest {
    checkAll(propTestConfig, stringMutableListArb()) { mutableList ->
      val extractSequence = extractSequenceArb(mutableList.size).bind().sequence.iterator()
      val predicate = ExtractSequencePredicate<String>(extractSequence)

      val result = mutableList.extract(predicate)

      check(!extractSequence.hasNext())
      result shouldContainExactlyInAnyOrder predicate.satisfyingElementByIndex.values
    }
  }

  @Test
  fun `extract() should remove the number of elements that were extracted`() = runTest {
    checkAll(propTestConfig, stringMutableListArb()) { mutableList ->
      val extractSequence = extractSequenceArb(mutableList.size).bind().sequence.iterator()
      val predicate = ExtractSequencePredicate<String>(extractSequence)
      val sizeBefore = mutableList.size

      mutableList.extract(predicate)

      check(!extractSequence.hasNext())
      mutableList.size shouldBe sizeBefore - predicate.satisfyingElementByIndex.size
    }
  }

  @Test
  fun `extract() should call the predicate for each index exactly once`() = runTest {
    checkAll(propTestConfig, stringMutableListArb()) { mutableList ->
      val invocationIndices = mutableListOf<Int>()
      val expectedInvocationIndices = mutableList.indices.toList()

      mutableList.extract { index, _ -> invocationIndices.add(index) }

      invocationIndices shouldContainExactlyInAnyOrder expectedInvocationIndices
    }
  }

  @Test
  fun `extract() should call the predicate with index matching element`() = runTest {
    checkAll(propTestConfig, stringMutableListArb()) { mutableList ->
      val extractSequence = extractSequenceArb(mutableList.size).bind().sequence.iterator()
      val originalList = mutableList.toList()

      mutableList.extract { index, value ->
        withClue("index=$index") { value shouldBe originalList[index] }
        extractSequence.next()
      }
    }
  }

  @Test
  fun `extract() should remove the extracted elements from the receiver MutableList`() = runTest {
    checkAll(propTestConfig, stringMutableListArb()) { mutableList ->
      val originalList = mutableList.toList()
      val extractSequence = extractSequenceArb(mutableList.size).bind().sequence.iterator()
      val predicate = ExtractSequencePredicate<String>(extractSequence)

      mutableList.extract(predicate)

      check(!extractSequence.hasNext())
      val expectedRemainingElements =
        originalList.filterIndexed { index, _ ->
          !predicate.satisfyingElementByIndex.containsKey(index)
        }
      mutableList shouldContainExactly expectedRemainingElements
    }
  }
}

private val alphanumericStringArb = Arb.string(0..10, Codepoint.alphanumeric())

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.33),
    shrinkingMode = ShrinkingMode.Off,
  )

private data class ExtractSequence(val sequence: List<Boolean>)

private fun extractSequenceArb(size: Int): Arb<ExtractSequence> =
  Arb.list(Arb.boolean(), size..size).map(::ExtractSequence)

private class ExtractSequencePredicate<T>(val iterator: Iterator<Boolean>) : ((Int, T) -> Boolean) {

  val satisfyingElementByIndex: MutableMap<Int, T> = mutableMapOf()

  override operator fun invoke(index: Int, element: T): Boolean {
    val satisfiesPredicate = iterator.next()
    if (satisfiesPredicate) {
      satisfyingElementByIndex[index] = element
    }
    return satisfiesPredicate
  }
}

private fun stringMutableListArb(): Arb<MutableList<String>> =
  Arb.list(alphanumericStringArb, 0..20).map { it.toMutableList() }
