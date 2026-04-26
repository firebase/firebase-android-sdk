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

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.shouldContainWithNonAbuttingText
import io.kotest.assertions.asClue
import io.kotest.assertions.assertSoftly
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeSameInstanceAs
import io.kotest.property.Arb
import io.kotest.property.EdgeConfig
import io.kotest.property.PropTestConfig
import io.kotest.property.ShrinkingMode
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.filterIsInstance
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.arbs.fooddrink.iceCreams
import io.kotest.property.checkAll
import kotlin.Any
import kotlin.Double
import kotlin.random.nextInt
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SomeValueArbUnitTest {

  @Test
  fun `someValue(maxDepth=invalid) throws`() = runTest {
    checkAll(propTestConfig, invalidMaxDepthArb()) { invalidMaxDepth ->
      shouldThrow<IllegalArgumentException> { Arb.someValue(maxDepth = invalidMaxDepth) }
    }
  }

  @Test
  fun `someValue(maxSize=invalid) throws`() = runTest {
    checkAll(propTestConfig, invalidMaxSizeArb()) { invalidMaxSize ->
      shouldThrow<IllegalArgumentException> { Arb.someValue(maxSize = invalidMaxSize) }
    }
  }

  @Test
  fun `someValue(maxDepth) is respected`() = runTest {
    checkAll(propTestConfig, Arb.int(validMaxDepthRange)) { maxDepth ->
      val arb = Arb.someValue(maxDepth = maxDepth)

      val value = arb.bind()

      val depth = value.calculateDepth()
      depth shouldBeLessThanOrEqual maxDepth
    }
  }

  @Test
  fun `someValue() edgeCaseProbability is reasonable`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { sample ->
      sample.edgeCaseProbability shouldBeIn 0.0..1.0
    }
  }

  @Test
  fun `someValue() compositeProbability is reasonable`() = runTest {
    checkAll(propTestConfig, Arb.someValue().filterIsInstance<SomeValueArb.Sample.Composite>()) {
      sample ->
      sample.compositeProbability shouldBeIn 0.0..1.0
    }
  }

  @Test
  fun `someValue() maxDepth is reasonable`() = runTest {
    checkAll(propTestConfig, Arb.int(1..validMaxDepthRange.last)) { maxDepth ->
      val arb = Arb.someValue(maxDepth = maxDepth).filterIsInstance<SomeValueArb.Sample.Composite>()

      val sample = arb.bind()

      sample.maxDepth shouldBeIn 1..maxDepth
    }
  }

  @Test
  fun `someValue() sample() produces non-edge cases`() = runTest {
    checkAll(propTestConfig, Arb.constant(Arb.someValue())) { arb ->
      val sample = arb.sample(randomSource()).value
      sample.asClue {
        when (val sample = arb.sample(randomSource()).value) {
          is SomeValueArb.Sample.Composite -> sample.edgeCases.shouldBeEmpty()
          is SomeValueArb.Sample.Scalar -> sample.isEdgeCase shouldBe false
        }
      }
    }
  }

  @Test
  fun `someValue() edgeCase() produces edge cases`() = runTest {
    val arb = Arb.someValue()
    checkAll(propTestConfig, Arb.constant(Unit)) { _ ->
      when (val sample = arb.edgecase(randomSource())) {
        is SomeValueArb.Sample.Composite -> sample.edgeCases.shouldNotBeEmpty()
        is SomeValueArb.Sample.Scalar -> sample.isEdgeCase shouldBe true
      }
    }
  }

  @Test
  fun `someValue() type matches value type`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { sample ->
      val purportedType =
        when (sample) {
          is SomeValueArb.Sample.Composite -> sample.type.kClass
          is SomeValueArb.Sample.Scalar -> sample.type.kClass
        }

      withClue(
        "purportedType=${purportedType.qualifiedName} " +
          "valueType=${sample.value::class.qualifiedName}"
      ) {
        purportedType.isInstance(sample.value) shouldBe true
      }
    }
  }

  @Test
  fun `someValue(maxSize) is respected`() = runTest {
    checkAll(propTestConfig, Arb.int(validMaxSizeRange)) { maxSize ->
      val arb = Arb.someValue(maxSize = maxSize)

      val value = arb.bind()

      val sizes = value.calculateSizes()
      sizes.shouldForAll { it shouldBeLessThanOrEqual maxSize }
    }
  }

  @Test
  fun `someValue() produces the entire range of types`() = runTest {
    val unexpectedTypes = mutableSetOf<KClass<*>>()
    val missingTypes = scalarAndCompositeTypes.toMutableSet()

    checkAll(propTestConfig.withIterations(iterations = 2000), Arb.someValue()) { sample ->
      val kClass = sample.value::class

      val matchingExpectedTypes =
        scalarAndCompositeTypes.filter { kClass.isSubclassOf(it) }.filterMostSpecificTypes().toSet()
      if (matchingExpectedTypes.isNotEmpty()) {
        missingTypes.removeAll(matchingExpectedTypes)
      } else {
        unexpectedTypes.add(kClass)
      }
    }

    assertSoftly {
      withClue("unexpectedTypes") { unexpectedTypes.shouldBeEmpty() }
      withClue("missingTypes") { missingTypes.shouldBeEmpty() }
    }
  }

  @Test
  fun `Sample Scalar constructor stores given argument values in properties`() = runTest {
    class TestValue
    val testValueArb = arbitrary { TestValue() }
    checkAll(
      propTestConfig,
      testValueArb,
      Arb.double(),
      Arb.enum<SomeValueArb.Sample.Scalar.Type>(),
      Arb.boolean(),
    ) { value, edgeCaseProbability, type, isEdgeCase ->
      val sample =
        SomeValueArb.Sample.Scalar(
          value = value,
          edgeCaseProbability = edgeCaseProbability,
          type = type,
          isEdgeCase = isEdgeCase,
        )

      assertSoftly {
        withClue("value") { sample.value shouldBeSameInstanceAs value }
        withClue("edgeCaseProbability") { sample.edgeCaseProbability shouldBe edgeCaseProbability }
        withClue("type") { sample.type shouldBe type }
        withClue("isEdgeCase") { sample.isEdgeCase shouldBe isEdgeCase }
      }
    }
  }

  @Test
  fun `Sample Scalar toString() returns expected value`() = runTest {
    data class TestValue(val value: Int)
    val testValueArb = Arb.int().map(::TestValue)
    val sampleArb: Arb<SomeValueArb.Sample.Scalar> =
      someValueArbSampleScalarArb(value = testValueArb)
    checkAll(propTestConfig, sampleArb) { sample ->
      assertSoftly {
        sample.toString() shouldContainWithNonAbuttingText sample.value.toString()
        sample.toString() shouldContainWithNonAbuttingText "SomeValueArb.Sample"
      }
    }
  }

  @Test
  fun `Sample Scalar equals() returns true when given self`() = runTest {
    checkAll(propTestConfig, someValueArbSampleScalarArb()) { sample: SomeValueArb.Sample.Scalar ->
      @Suppress("ReplaceCallWithBinaryOperator")
      sample.equals(sample) shouldBe true
    }
  }

  @Test
  fun `Sample Scalar equals() returns true when given distinct, but equal instance`() = runTest {
    checkAll(propTestConfig, someValueArbSampleScalarArb()) { sample: SomeValueArb.Sample.Scalar ->
      val sampleCopy = sample.copy()

      @Suppress("ReplaceCallWithBinaryOperator")
      sample.equals(sampleCopy) shouldBe true
    }
  }

  @Test
  fun `Sample Scalar equals() returns true when given instance with same value but different other properties`() =
    runTest {
      checkAll(
        propTestConfig,
        Arb.iceCreams(),
        Arb.double().distinctPair(),
        Arb.enum<SomeValueArb.Sample.Scalar.Type>().distinctPair(),
        Arb.boolean().distinctPair(),
      ) { value, edgeCaseProbabilities, types, isEdgeCases ->
        val sample1 =
          SomeValueArb.Sample.Scalar(
            value,
            edgeCaseProbabilities.first,
            types.first,
            isEdgeCases.first
          )
        val sample2 = run {
          val changeDomain = listOf(edgeCaseProbabilities, types, isEdgeCases)
          val changeCount = randomSource().random.nextInt(1..changeDomain.size)
          val changes = changeDomain.shuffled(randomSource().random).take(changeCount)
          SomeValueArb.Sample.Scalar(
            value,
            edgeCaseProbability =
              if (edgeCaseProbabilities in changes) edgeCaseProbabilities.second
              else edgeCaseProbabilities.first,
            type = if (types in changes) types.second else types.first,
            isEdgeCase = if (isEdgeCases in changes) isEdgeCases.second else isEdgeCases.first,
          )
        }

        @Suppress("ReplaceCallWithBinaryOperator")
        sample1.equals(sample2) shouldBe true
      }
    }

  @Test fun `Sample Scalar equals() more tests`() = runTest { TODO("more tests!") }
}

@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

private fun SomeValueArb.Sample.calculateDepth(): Int =
  when (this) {
    is SomeValueArb.Sample.Scalar -> 0
    is SomeValueArb.Sample.Composite -> value.calculateSampleValueDepth()
  }

private fun Any?.calculateSampleValueDepth(): Int =
  when (this) {
    is Iterable<*> -> {
      val elementDepths = map { it.calculateSampleValueDepth() }
      1 + (elementDepths.maxOrNull() ?: 0)
    }
    is Map<*, *> -> {
      val valueDepths = values.map { it.calculateSampleValueDepth() }
      1 + (valueDepths.maxOrNull() ?: 0)
    }
    is Pair<*, *> ->
      1 + maxOf(first.calculateSampleValueDepth(), second.calculateSampleValueDepth())
    is Triple<*, *, *> ->
      1 +
        maxOf(
          first.calculateSampleValueDepth(),
          second.calculateSampleValueDepth(),
          third.calculateSampleValueDepth()
        )
    is Result<*> -> 1 + (fold(onSuccess = { it.calculateSampleValueDepth() }, onFailure = { 0 }))
    else -> 0 // Scalars, KClass, Throwable, Unit, etc.
  }

private fun SomeValueArb.Sample.calculateSizes(): List<Int> =
  when (this) {
    is SomeValueArb.Sample.Scalar -> emptyList()
    is SomeValueArb.Sample.Composite -> value.calculateSampleValueSizes()
  }

private fun Any.calculateSampleValueSizes(): List<Int> {
  val sizes = mutableListOf<Int>()
  val queue = ArrayDeque<Any?>()
  queue.add(this)

  while (!queue.isEmpty()) {
    when (val value = queue.removeFirst()) {
      is Collection<*> -> {
        sizes.add(value.size)
        queue.addAll(value)
      }
      is Map<*, *> -> {
        sizes.add(value.size)
        queue.addAll(value.values)
      }
      else -> 0 // Pairs, Triples, Results have fixed size (not governed by maxSize)
    }

    return sizes.toList()
  }

  return sizes.toList()
}

private val validMaxDepthRange = 0..5

private fun invalidMaxDepthArb(): Arb<Int> =
  Arb.choice(
    Arb.int(max = validMaxDepthRange.first - 1),
    Arb.int(min = validMaxDepthRange.last + 1)
  )

private val validMaxSizeRange = 0..5

private fun invalidMaxSizeArb(): Arb<Int> =
  Arb.choice(Arb.int(max = validMaxSizeRange.first - 1), Arb.int(min = validMaxSizeRange.last + 1))

private fun List<KClass<*>>.filterMostSpecificTypes(): List<KClass<*>> = filterNot { matchingType ->
  any { it !== matchingType && it.isSubclassOf(matchingType) }
}

private val scalarTypes: Set<KClass<*>> =
  setOf(
    Boolean::class,
    Byte::class,
    Char::class,
    Double::class,
    Float::class,
    Int::class,
    Long::class,
    Short::class,
    String::class,
    Unit::class,
    Throwable::class,
    KClass::class,
  )

private val compositeTypes: Set<KClass<*>> =
  setOf(
    Map::class,
    MutableMap::class,
    List::class,
    MutableList::class,
    Set::class,
    MutableSet::class,
    Result::class,
    Pair::class,
    Triple::class,
  )

private val scalarAndCompositeTypes: Set<KClass<*>> = scalarTypes + compositeTypes

private fun someValueArbSampleScalarArb(
  value: Arb<Any> = Arb.iceCreams(),
  edgeCaseProbability: Arb<Double> = Arb.double(),
  type: Arb<SomeValueArb.Sample.Scalar.Type> = Arb.enum(),
  isEdgeCase: Arb<Boolean> = Arb.boolean(),
): Arb<SomeValueArb.Sample.Scalar> =
  Arb.bind(value, edgeCaseProbability, type, isEdgeCase, SomeValueArb.Sample::Scalar)

private fun SomeValueArb.Sample.Scalar.copy(
  value: Any = this.value,
  edgeCaseProbability: Double = this.edgeCaseProbability,
  type: SomeValueArb.Sample.Scalar.Type = this.type,
  isEdgeCase: Boolean = this.isEdgeCase,
) =
  SomeValueArb.Sample.Scalar(
    value = value,
    edgeCaseProbability = edgeCaseProbability,
    type = type,
    isEdgeCase = isEdgeCase,
  )
