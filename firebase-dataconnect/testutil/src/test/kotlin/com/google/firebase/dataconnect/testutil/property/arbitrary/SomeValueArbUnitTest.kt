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
import io.kotest.assertions.print.print
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.common.ExperimentalKotest
import io.kotest.inspectors.shouldForAll
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.ranges.shouldBeIn
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
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
import java.util.Objects
import kotlin.reflect.KClass
import kotlin.reflect.full.isSubclassOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SomeValueArbUnitTest {

  // region Tests for SomeValueArb

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
  fun `someValue() valueCopy returns a value that satisfies == value`() = runTest {
    checkAll(propTestConfig, Arb.someValue()) { sample ->
      val copy = sample.valueCopy()
      copy shouldBe sample.value
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
        when (it) {
          is SomeValueArb.Sample.Composite -> it.edgeCases.shouldBeEmpty()
          is SomeValueArb.Sample.Scalar -> it.isEdgeCase shouldBe false
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

  // endregion

  // region Tests for SomeValueArb.Sample.Scalar

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
      val valueCopy = { value }
      val sample =
        SomeValueArb.Sample.Scalar(
          value = value,
          edgeCaseProbability = edgeCaseProbability,
          valueCopy = valueCopy,
          type = type,
          isEdgeCase = isEdgeCase,
        )

      assertSoftly {
        withClue("value") { sample.value shouldBeSameInstanceAs value }
        withClue("edgeCaseProbability") { sample.edgeCaseProbability shouldBe edgeCaseProbability }
        withClue("valueCopy") { sample.valueCopy shouldBeSameInstanceAs valueCopy }
        withClue("type") { sample.type shouldBe type }
        withClue("isEdgeCase") { sample.isEdgeCase shouldBe isEdgeCase }
      }
    }
  }

  @Test
  fun `Sample Scalar toString() returns expected value`() = runTest {
    data class TestValue(val value: Int)
    val testValueArb = Arb.int().map(::TestValue).map { ValueCopierPair(it) { it.copy() } }
    val sampleArb: Arb<SomeValueArb.Sample.Scalar> =
      someValueArbSampleScalarArb(value = testValueArb)
    checkAll(propTestConfig, sampleArb) { sample ->
      assertSoftly {
        sample.toString() shouldContainWithNonAbuttingText sample.value.toString()
        sample.toString() shouldContainWithNonAbuttingText "SomeValueArb.Sample.Scalar"
      }
    }
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `Sample Scalar equals() returns true for equal instances`() = runTest {
    checkAll(propTestConfig, someValueArbSampleScalarEqualPairArb()) { sample ->
      sample.scalarSample1.equals(sample.scalarSample2) shouldBe true
      sample.scalarSample2.equals(sample.scalarSample1) shouldBe true
    }
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `Sample Scalar equals() returns false for unequal instances`() = runTest {
    checkAll(propTestConfig, someValueArbSampleScalarUnequalPairArb()) { sample ->
      sample.scalarSample1.equals(sample.scalarSample2) shouldBe false
      sample.scalarSample2.equals(sample.scalarSample1) shouldBe false
    }
  }

  @Test
  fun `Sample Scalar hashCode() returns same value for equal instances`() = runTest {
    checkAll(propTestConfig, someValueArbSampleScalarEqualPairArb()) { sample ->
      sample.scalarSample1.hashCode() shouldBe sample.scalarSample2.hashCode()
    }
  }

  @Test
  fun `Sample Scalar hashCode() returns different value for unequal instances`() = runTest {
    checkAll(hashEqualityPropTestConfig, someValueArbSampleScalarUnequalPairArb()) { sample ->
      sample.scalarSample1.hashCode() shouldNotBe sample.scalarSample2.hashCode()
    }
  }

  // endregion

  // region Tests for SomeValueArb.Sample.Composite

  @Test
  fun `Sample Composite constructor stores given argument values in properties`() = runTest {
    class TestValue
    val testValueArb = arbitrary { TestValue() }
    checkAll(
      propTestConfig,
      testValueArb,
      Arb.double(),
      Arb.enum<SomeValueArb.Sample.Composite.Type>(),
      Arb.int(),
      Arb.double(),
      Arb.enumSubset<SomeValueArb.Sample.Composite.EdgeCase>(),
    ) { value, edgeCaseProbability, type, maxDepth, compositeProbability, edgeCases ->
      val valueCopy = { value }
      val sample =
        SomeValueArb.Sample.Composite(
          value = value,
          edgeCaseProbability = edgeCaseProbability,
          valueCopy = valueCopy,
          type = type,
          maxDepth = maxDepth,
          compositeProbability = compositeProbability,
          edgeCases = edgeCases,
        )

      assertSoftly {
        withClue("value") { sample.value shouldBeSameInstanceAs value }
        withClue("edgeCaseProbability") { sample.edgeCaseProbability shouldBe edgeCaseProbability }
        withClue("valueCopy") { sample.valueCopy shouldBeSameInstanceAs valueCopy }
        withClue("type") { sample.type shouldBe type }
        withClue("maxDepth") { sample.maxDepth shouldBe maxDepth }
        withClue("compositeProbability") {
          sample.compositeProbability shouldBe compositeProbability
        }
        withClue("edgeCases") { sample.edgeCases shouldBe edgeCases }
      }
    }
  }

  @Test
  fun `Sample Composite toString() returns expected value`() = runTest {
    data class TestValue(val value: Int)
    val testValueArb = Arb.int().map(::TestValue).map { ValueCopierPair(it) { it.copy() } }
    val sampleArb: Arb<SomeValueArb.Sample.Composite> =
      someValueArbSampleCompositeArb(value = testValueArb)
    checkAll(propTestConfig, sampleArb) { sample ->
      assertSoftly {
        sample.toString() shouldContainWithNonAbuttingText sample.value.toString()
        sample.toString() shouldContainWithNonAbuttingText "SomeValueArb.Sample.Composite"
      }
    }
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `Sample Composite equals() returns true for equal instances`() = runTest {
    checkAll(propTestConfig, someValueArbSampleCompositeEqualPairArb()) { sample ->
      sample.compositeSample1.equals(sample.compositeSample2) shouldBe true
      sample.compositeSample2.equals(sample.compositeSample1) shouldBe true
    }
  }

  @Test
  @Suppress("ReplaceCallWithBinaryOperator")
  fun `Sample Composite equals() returns false for unequal instances`() = runTest {
    checkAll(propTestConfig, someValueArbSampleCompositeUnequalPairArb()) { sample ->
      sample.compositeSample1.equals(sample.compositeSample2) shouldBe false
      sample.compositeSample2.equals(sample.compositeSample1) shouldBe false
    }
  }

  @Test
  fun `Sample Composite hashCode() returns same value for equal instances`() = runTest {
    checkAll(propTestConfig, someValueArbSampleCompositeEqualPairArb()) { sample ->
      sample.compositeSample1.hashCode() shouldBe sample.compositeSample2.hashCode()
    }
  }

  @Test
  fun `Sample Composite hashCode() returns different value for unequal instances`() = runTest {
    checkAll(hashEqualityPropTestConfig, someValueArbSampleCompositeUnequalPairArb()) { sample ->
      sample.compositeSample1.hashCode() shouldNotBe sample.compositeSample2.hashCode()
    }
  }

  // endregion

}

// region Helper classes, functions, and properties

/** The default [PropTestConfig] used for property-based tests in this file. */
@OptIn(ExperimentalKotest::class)
private val propTestConfig =
  PropTestConfig(
    iterations = 200,
    edgeConfig = EdgeConfig(edgecasesGenerationProbability = 0.2),
    shrinkingMode = ShrinkingMode.Off,
  )

/**
 * A [PropTestConfig] specifically for testing hash code equality, which allows for a small number
 * of collisions.
 */
@OptIn(ExperimentalKotest::class)
private val hashEqualityPropTestConfig =
  propTestConfig.copy(
    minSuccess = propTestConfig.iterations!! - 2,
    maxFailure = 2,
  )

/** Calculates the maximum depth of nested composite values in the receiver. */
private fun SomeValueArb.Sample.calculateDepth(): Int =
  when (this) {
    is SomeValueArb.Sample.Scalar -> 0
    is SomeValueArb.Sample.Composite -> value.calculateSampleValueDepth()
  }

/** Calculates the maximum depth of nested composite values in the receiver. */
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

/**
 * Calculates the sizes of all collections and maps within the [value] property of the receiver,
 * recursively. If the receiver is a [SomeValueArb.Sample.Scalar] then an empty list is returned
 * unconditionally
 */
private fun SomeValueArb.Sample.calculateSizes(): List<Int> =
  when (this) {
    is SomeValueArb.Sample.Scalar -> emptyList()
    is SomeValueArb.Sample.Composite -> value.calculateSampleValueSizes()
  }

/** Calculates the sizes of all collections and maps within the receiver, recursively. */
private fun Any.calculateSampleValueSizes(): List<Int> {
  val queue = ArrayDeque<Any?>()
  queue.add(this)
  return calculateSampleValueSizes(queue)
}

/** Helper function for Any.calculateSampleValueSizes(); do not call it directly. */
private fun calculateSampleValueSizes(queue: ArrayDeque<Any?>): List<Int> {
  val sizes = mutableListOf<Int>()

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
    }
  }

  return sizes.toList()
}

/** The range of valid `maxDepth` values supported by [SomeValueArb]. */
private val validMaxDepthRange = 0..5

/** An [Arb] that generates Int values outside of [validMaxDepthRange]. */
private fun invalidMaxDepthArb(): Arb<Int> =
  Arb.choice(
    Arb.int(max = validMaxDepthRange.first - 1),
    Arb.int(min = validMaxDepthRange.last + 1)
  )

/** The range of valid `maxSize` values for collections generated by [SomeValueArb]. */
private val validMaxSizeRange = 0..5

/** An [Arb] that generates integers outside of [validMaxSizeRange]. */
private fun invalidMaxSizeArb(): Arb<Int> =
  Arb.choice(Arb.int(max = validMaxSizeRange.first - 1), Arb.int(min = validMaxSizeRange.last + 1))

/** Filters a list of classes, removing those that are superclasses of other classes in the list. */
private fun List<KClass<*>>.filterMostSpecificTypes(): List<KClass<*>> = filterNot { matchingType ->
  any { it !== matchingType && it.isSubclassOf(matchingType) }
}

/** The set of [KClass] objects representing the scalar types generated by [SomeValueArb]. */
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

/** The set of [KClass] objects representing the composite types generated by [SomeValueArb]. */
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

/** The union of [scalarTypes] and [compositeTypes]. */
private val scalarAndCompositeTypes: Set<KClass<*>> = scalarTypes + compositeTypes

/**
 * An [Arb] that generates [SomeValueArb.Sample.Scalar] instances with specified or random
 * properties.
 */
private fun someValueArbSampleScalarArb(
  value: Arb<ValueCopierPair<Any>> = Arb.iceCreams().map { ValueCopierPair(it) { it.copy() } },
  edgeCaseProbability: Arb<Double> = Arb.double(),
  type: Arb<SomeValueArb.Sample.Scalar.Type> = Arb.enum(),
  isEdgeCase: Arb<Boolean> = Arb.boolean(),
): Arb<SomeValueArb.Sample.Scalar> =
  Arb.bind(value, edgeCaseProbability, type, isEdgeCase) {
    (value, valueCopy),
    edgeCaseProbability,
    type,
    isEdgeCase ->
    SomeValueArb.Sample.Scalar(
      value = value,
      edgeCaseProbability = edgeCaseProbability,
      valueCopy = valueCopy,
      type = type,
      isEdgeCase = isEdgeCase,
    )
  }

/** Creates a copy of this [SomeValueArb.Sample.Scalar] with the specified properties. */
private fun SomeValueArb.Sample.Scalar.copy(
  value: Any = this.value,
  edgeCaseProbability: Double = this.edgeCaseProbability,
  valueCopy: () -> Any = this.valueCopy,
  type: SomeValueArb.Sample.Scalar.Type = this.type,
  isEdgeCase: Boolean = this.isEdgeCase,
) =
  SomeValueArb.Sample.Scalar(
    value = value,
    edgeCaseProbability = edgeCaseProbability,
    valueCopy = valueCopy,
    type = type,
    isEdgeCase = isEdgeCase,
  )

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Scalar] instances that should be considered
 * equal by the `==` operator.
 */
private fun someValueArbSampleScalarEqualPairArb(): Arb<SomeValueArbSampleScalarEqualPairSample> =
  someValueArbSampleScalarEqualPairArb(Arb.iceCreams()) { it.copy() }

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Scalar] instances that should be considered
 * equal by the `==` operator.
 */
private fun <T : Any> someValueArbSampleScalarEqualPairArb(
  value: Arb<T>,
  copy: (T) -> T
): Arb<SomeValueArbSampleScalarEqualPairSample> =
  Arb.bind(
    Arb.twoValues(someValueArbSampleScalarArb()),
    value,
    Arb.enum<SomeValueArbSampleEqualityDimension>(),
    Arb.enumSubset<SomeValueArbSampleScalarProperty>(),
  ) { (sampleTemplate1, sampleTemplate2), value, dimension, properties ->
    val sample1 = sampleTemplate1.copy(value = value)
    val sample2 =
      when (dimension) {
        SomeValueArbSampleEqualityDimension.SameInstance -> sample1
        SomeValueArbSampleEqualityDimension.DifferentInstanceSameValueInstance -> sample1.copy()
        SomeValueArbSampleEqualityDimension.DifferentInstanceDifferentButEqualValueInstance ->
          sample1.copy(value = copy(value))
        SomeValueArbSampleEqualityDimension.DifferentInstanceEqualValueDifferentOtherProperties ->
          sample1.copy(
            value =
              if (SomeValueArbSampleScalarProperty.Value in properties) copy(value) else value,
            edgeCaseProbability =
              if (SomeValueArbSampleScalarProperty.EdgeCaseProbability in properties)
                sampleTemplate2.edgeCaseProbability
              else sampleTemplate1.edgeCaseProbability,
            type =
              if (SomeValueArbSampleScalarProperty.Type in properties) sampleTemplate2.type
              else sampleTemplate1.type,
            isEdgeCase =
              if (SomeValueArbSampleScalarProperty.IsEdgeCase in properties)
                sampleTemplate2.isEdgeCase
              else sampleTemplate1.isEdgeCase,
          )
      }
    SomeValueArbSampleScalarEqualPairSample(sample1, sample2, dimension, properties)
  }

/**
 * A sample containing a pair of equal [SomeValueArb.Sample.Scalar] instances and metadata about
 * their creation.
 */
private class SomeValueArbSampleScalarEqualPairSample(
  val scalarSample1: SomeValueArb.Sample.Scalar,
  val scalarSample2: SomeValueArb.Sample.Scalar,
  val dimension: SomeValueArbSampleEqualityDimension,
  val properties: Set<SomeValueArbSampleScalarProperty>,
) {

  override fun toString() =
    "SomeValueArbSampleScalarEqualPairSample(" +
      "scalarSample1=${scalarSample1.print().value}, " +
      "scalarSample2=${scalarSample2.print().value}, " +
      "dimension=${dimension.print().value}, " +
      "properties=${properties.toSortedSet().print().value})"

  override fun equals(other: Any?) =
    other is SomeValueArbSampleScalarEqualPairSample &&
      other.scalarSample1 == scalarSample1 &&
      other.scalarSample2 == scalarSample2

  override fun hashCode() =
    Objects.hash(SomeValueArbSampleScalarEqualPairSample::class, scalarSample1, scalarSample2)
}

/** The different ways two samples can be "equal". */
private enum class SomeValueArbSampleEqualityDimension {
  SameInstance,
  DifferentInstanceSameValueInstance,
  DifferentInstanceDifferentButEqualValueInstance,
  DifferentInstanceEqualValueDifferentOtherProperties,
}

/** The properties of [SomeValueArb.Sample.Scalar] that can be varied in tests. */
private enum class SomeValueArbSampleScalarProperty {
  Value,
  EdgeCaseProbability,
  Type,
  IsEdgeCase,
}

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Scalar] instances that should be considered
 * unequal by the `==` operator.
 */
private fun someValueArbSampleScalarUnequalPairArb():
  Arb<SomeValueArbSampleScalarUnequalPairSample> =
  someValueArbSampleScalarUnequalPairArb(Arb.iceCreams())

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Scalar] instances that should be considered
 * unequal by the `==` operator.
 */
private fun <T : Any> someValueArbSampleScalarUnequalPairArb(
  value: Arb<T>
): Arb<SomeValueArbSampleScalarUnequalPairSample> =
  Arb.bind(
    Arb.twoValues(someValueArbSampleScalarArb()),
    value.distinctPair(),
    Arb.enumSubset<SomeValueArbSampleScalarProperty>(),
  ) { (sampleTemplate1, sampleTemplate2), (value1, value2), properties ->
    val sample1 = sampleTemplate1.copy(value = value1)
    val sample2 =
      sample1.copy(
        value = value2,
        edgeCaseProbability =
          if (SomeValueArbSampleScalarProperty.EdgeCaseProbability in properties)
            sampleTemplate2.edgeCaseProbability
          else sampleTemplate1.edgeCaseProbability,
        type =
          if (SomeValueArbSampleScalarProperty.Type in properties) sampleTemplate2.type
          else sampleTemplate1.type,
        isEdgeCase =
          if (SomeValueArbSampleScalarProperty.IsEdgeCase in properties) sampleTemplate2.isEdgeCase
          else sampleTemplate1.isEdgeCase,
      )

    SomeValueArbSampleScalarUnequalPairSample(sample1, sample2, properties)
  }

/**
 * A sample containing a pair of unequal [SomeValueArb.Sample.Scalar] instances and metadata about
 * their creation.
 */
private class SomeValueArbSampleScalarUnequalPairSample(
  val scalarSample1: SomeValueArb.Sample.Scalar,
  val scalarSample2: SomeValueArb.Sample.Scalar,
  val properties: Set<SomeValueArbSampleScalarProperty>,
) {

  override fun toString() =
    "SomeValueArbSampleScalarUnequalPairSample(" +
      "scalarSample1=${scalarSample1.print().value}, " +
      "scalarSample2=${scalarSample2.print().value}, " +
      "properties=${properties.toSortedSet().print().value})"

  override fun equals(other: Any?) =
    other is SomeValueArbSampleScalarUnequalPairSample &&
      other.scalarSample1 == scalarSample1 &&
      other.scalarSample2 == scalarSample2

  override fun hashCode() =
    Objects.hash(SomeValueArbSampleScalarUnequalPairSample::class, scalarSample1, scalarSample2)
}

/**
 * An [Arb] that generates [SomeValueArb.Sample.Composite] instances with specified or random
 * properties.
 */
private fun someValueArbSampleCompositeArb(
  value: Arb<ValueCopierPair<Any>> = Arb.iceCreams().map { ValueCopierPair(it) { it.copy() } },
  edgeCaseProbability: Arb<Double> = Arb.double(),
  type: Arb<SomeValueArb.Sample.Composite.Type> = Arb.enum(),
  maxDepth: Arb<Int> = Arb.int(),
  compositeProbability: Arb<Double> = Arb.double(),
  edgeCases: Arb<Set<SomeValueArb.Sample.Composite.EdgeCase>> =
    Arb.enumSubset<SomeValueArb.Sample.Composite.EdgeCase>()
): Arb<SomeValueArb.Sample.Composite> =
  Arb.bind(
    value,
    edgeCaseProbability,
    type,
    maxDepth,
    compositeProbability,
    edgeCases,
  ) { (value, valueCopy), edgeCaseProbability, type, maxDepth, compositeProbability, edgeCases ->
    SomeValueArb.Sample.Composite(
      value = value,
      edgeCaseProbability = edgeCaseProbability,
      valueCopy = valueCopy,
      type = type,
      maxDepth = maxDepth,
      compositeProbability = compositeProbability,
      edgeCases = edgeCases,
    )
  }

/** Creates a copy of the receiver [SomeValueArb.Sample.Composite] with the specified properties. */
private fun SomeValueArb.Sample.Composite.copy(
  value: Any = this.value,
  edgeCaseProbability: Double = this.edgeCaseProbability,
  valueCopy: () -> Any = this.valueCopy,
  type: SomeValueArb.Sample.Composite.Type = this.type,
  maxDepth: Int = this.maxDepth,
  compositeProbability: Double = this.compositeProbability,
  edgeCases: Set<SomeValueArb.Sample.Composite.EdgeCase> = this.edgeCases,
) =
  SomeValueArb.Sample.Composite(
    value = value,
    edgeCaseProbability = edgeCaseProbability,
    valueCopy = valueCopy,
    type = type,
    maxDepth = maxDepth,
    compositeProbability = compositeProbability,
    edgeCases = edgeCases,
  )

/** The properties of [SomeValueArb.Sample.Composite] that can be varied in tests. */
private enum class SomeValueArbSampleCompositeProperty {
  Value,
  EdgeCaseProbability,
  Type,
  MaxDepth,
  CompositeProbability,
  EdgeCases,
}

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Composite] instances that should be
 * considered equal by the `==` operator.
 */
private fun someValueArbSampleCompositeEqualPairArb():
  Arb<SomeValueArbSampleCompositeEqualPairSample> =
  someValueArbSampleCompositeEqualPairArb(Arb.iceCreams()) { it.copy() }

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Composite] instances that should be
 * considered equal by the `==` operator.
 */
private fun <T : Any> someValueArbSampleCompositeEqualPairArb(
  value: Arb<T>,
  copy: (T) -> T
): Arb<SomeValueArbSampleCompositeEqualPairSample> =
  Arb.bind(
    Arb.twoValues(someValueArbSampleCompositeArb()),
    value,
    Arb.enum<SomeValueArbSampleEqualityDimension>(),
    Arb.enumSubset<SomeValueArbSampleCompositeProperty>(),
  ) { (sampleTemplate1, sampleTemplate2), value, dimension, properties ->
    val sample1 = sampleTemplate1.copy(value = value)
    val sample2 =
      when (dimension) {
        SomeValueArbSampleEqualityDimension.SameInstance -> sample1
        SomeValueArbSampleEqualityDimension.DifferentInstanceSameValueInstance -> sample1.copy()
        SomeValueArbSampleEqualityDimension.DifferentInstanceDifferentButEqualValueInstance ->
          sample1.copy(value = copy(value))
        SomeValueArbSampleEqualityDimension.DifferentInstanceEqualValueDifferentOtherProperties ->
          sample1.copy(
            value =
              if (SomeValueArbSampleCompositeProperty.Value in properties) copy(value) else value,
            edgeCaseProbability =
              if (SomeValueArbSampleCompositeProperty.EdgeCaseProbability in properties)
                sampleTemplate2.edgeCaseProbability
              else sampleTemplate1.edgeCaseProbability,
            type =
              if (SomeValueArbSampleCompositeProperty.Type in properties) sampleTemplate2.type
              else sampleTemplate1.type,
            maxDepth =
              if (SomeValueArbSampleCompositeProperty.MaxDepth in properties)
                sampleTemplate2.maxDepth
              else sampleTemplate1.maxDepth,
            compositeProbability =
              if (SomeValueArbSampleCompositeProperty.CompositeProbability in properties)
                sampleTemplate2.compositeProbability
              else sampleTemplate1.compositeProbability,
            edgeCases =
              if (SomeValueArbSampleCompositeProperty.EdgeCases in properties)
                sampleTemplate2.edgeCases
              else sampleTemplate1.edgeCases,
          )
      }
    SomeValueArbSampleCompositeEqualPairSample(sample1, sample2, dimension, properties)
  }

/**
 * A sample containing a pair of equal [SomeValueArb.Sample.Composite] instances and metadata about
 * their creation.
 */
private class SomeValueArbSampleCompositeEqualPairSample(
  val compositeSample1: SomeValueArb.Sample.Composite,
  val compositeSample2: SomeValueArb.Sample.Composite,
  val dimension: SomeValueArbSampleEqualityDimension,
  val properties: Set<SomeValueArbSampleCompositeProperty>,
) {

  override fun toString() =
    "SomeValueArbSampleCompositeEqualPairSample(" +
      "compositeSample1=${compositeSample1.print().value}, " +
      "compositeSample2=${compositeSample2.print().value}, " +
      "dimension=${dimension.print().value}, " +
      "properties=${properties.toSortedSet().print().value})"

  override fun equals(other: Any?) =
    other is SomeValueArbSampleCompositeEqualPairSample &&
      other.compositeSample1 == compositeSample1 &&
      other.compositeSample2 == compositeSample2

  override fun hashCode() =
    Objects.hash(
      SomeValueArbSampleCompositeEqualPairSample::class,
      compositeSample1,
      compositeSample2
    )
}

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Composite] instances that should be
 * considered unequal by the `==` operator.
 */
private fun someValueArbSampleCompositeUnequalPairArb():
  Arb<SomeValueArbSampleCompositeUnequalPairSample> =
  someValueArbSampleCompositeUnequalPairArb(Arb.iceCreams())

/**
 * An [Arb] that generates pairs of [SomeValueArb.Sample.Composite] instances that should be
 * considered unequal by the `==` operator.
 */
private fun <T : Any> someValueArbSampleCompositeUnequalPairArb(
  value: Arb<T>
): Arb<SomeValueArbSampleCompositeUnequalPairSample> =
  Arb.bind(
    Arb.twoValues(someValueArbSampleCompositeArb()),
    value.distinctPair(),
    Arb.enumSubset<SomeValueArbSampleCompositeProperty>(),
  ) { (sampleTemplate1, sampleTemplate2), (value1, value2), properties ->
    val sample1 = sampleTemplate1.copy(value = value1)
    val sample2 =
      sample1.copy(
        value = value2,
        edgeCaseProbability =
          if (SomeValueArbSampleCompositeProperty.EdgeCaseProbability in properties)
            sampleTemplate2.edgeCaseProbability
          else sampleTemplate1.edgeCaseProbability,
        type =
          if (SomeValueArbSampleCompositeProperty.Type in properties) sampleTemplate2.type
          else sampleTemplate1.type,
        maxDepth =
          if (SomeValueArbSampleCompositeProperty.MaxDepth in properties) sampleTemplate2.maxDepth
          else sampleTemplate1.maxDepth,
        compositeProbability =
          if (SomeValueArbSampleCompositeProperty.CompositeProbability in properties)
            sampleTemplate2.compositeProbability
          else sampleTemplate1.compositeProbability,
        edgeCases =
          if (SomeValueArbSampleCompositeProperty.EdgeCases in properties) sampleTemplate2.edgeCases
          else sampleTemplate1.edgeCases,
      )

    SomeValueArbSampleCompositeUnequalPairSample(sample1, sample2, properties)
  }

/**
 * A sample containing a pair of unequal [SomeValueArb.Sample.Composite] instances and metadata
 * about their creation.
 */
private class SomeValueArbSampleCompositeUnequalPairSample(
  val compositeSample1: SomeValueArb.Sample.Composite,
  val compositeSample2: SomeValueArb.Sample.Composite,
  val properties: Set<SomeValueArbSampleCompositeProperty>,
) {

  override fun toString() =
    "SomeValueArbSampleCompositeUnequalPairSample(" +
      "compositeSample1=${compositeSample1.print().value}, " +
      "compositeSample2=${compositeSample2.print().value}, " +
      "properties=${properties.toSortedSet().print().value})"

  override fun equals(other: Any?) =
    other is SomeValueArbSampleCompositeUnequalPairSample &&
      other.compositeSample1 == compositeSample1 &&
      other.compositeSample2 == compositeSample2

  override fun hashCode() =
    Objects.hash(
      SomeValueArbSampleCompositeUnequalPairSample::class,
      compositeSample1,
      compositeSample2
    )
}

/** A pair of a value, and a function to create a copy of that value. */
private data class ValueCopierPair<out T>(
  val value: T,
  val valueCopy: () -> T,
)

// endregion
