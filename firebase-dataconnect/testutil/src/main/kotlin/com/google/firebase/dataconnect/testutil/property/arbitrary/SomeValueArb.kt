/*
 * Copyright 2024 Google LLC
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

import io.kotest.assertions.print.print
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.bind
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.enum
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.map
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import java.util.Objects
import kotlin.random.nextInt
import kotlin.reflect.KClass

fun Arb.Companion.someValue(maxDepth: Int = 2, maxSize: Int = 3): SomeValueArb =
  SomeValueArb(maxDepth, maxSize)

class SomeValueArb private constructor(private val delegate: ArbWithEdgeCases<Sample>) :
  ArbWithEdgeCases<SomeValueArb.Sample>() {

  constructor(
    maxDepth: Int,
    maxSize: Int
  ) : this(createValueArb(maxDepth = maxDepth, maxSize = maxSize))

  override fun sample(rs: RandomSource) = delegate.sample(rs)

  override fun edgecase(rs: RandomSource) = delegate.edgecase(rs)

  sealed class Sample(
    val value: Any,
    val edgeCaseProbability: Double,
  ) {

    class Scalar(value: Any, edgeCaseProbability: Double, val type: Type, val isEdgeCase: Boolean) :
      Sample(value, edgeCaseProbability) {

      override fun toString() = "SomeValueArb.Sample.Scalar(${value.print().value})"

      override fun equals(other: Any?) = other is Scalar && other.value == value

      override fun hashCode() = Objects.hash(Scalar::class, value)

      @Suppress("RemoveRedundantQualifierName")
      enum class Type(val kClass: KClass<*>) {
        Boolean(kotlin.Boolean::class),
        Byte(kotlin.Byte::class),
        Char(kotlin.Char::class),
        Double(kotlin.Double::class),
        Float(kotlin.Float::class),
        Int(kotlin.Int::class),
        Long(kotlin.Long::class),
        Short(kotlin.Short::class),
        String(kotlin.String::class),
        Unit(kotlin.Unit::class),
        Throwable(kotlin.Throwable::class),
        KClass(kotlin.reflect.KClass::class),
      }
    }

    class Composite(
      value: Any,
      edgeCaseProbability: Double,
      val type: Type,
      val maxDepth: Int,
      val compositeProbability: Double,
      val edgeCases: Set<EdgeCase>,
    ) : Sample(value, edgeCaseProbability) {

      override fun toString() = "SomeValueArb.Sample.Composite(${value.print().value})"

      override fun equals(other: Any?) = other is Composite && other.value == value

      override fun hashCode() = Objects.hash(Composite::class, value)

      enum class EdgeCase {
        MaxDepth,
        Value,
        Composite,
      }

      @Suppress("RemoveRedundantQualifierName")
      enum class Type(val kClass: KClass<*>) {
        List(kotlin.collections.List::class),
        Map(kotlin.collections.Map::class),
        MutableList(kotlin.collections.MutableList::class),
        MutableMap(kotlin.collections.MutableMap::class),
        MutableSet(kotlin.collections.MutableSet::class),
        Pair(kotlin.Pair::class),
        Result(kotlin.Result::class),
        Set(kotlin.collections.Set::class),
        Triple(kotlin.Triple::class),
      }
    }
  }

  companion object {

    private fun createValueArb(maxDepth: Int, maxSize: Int): ArbWithEdgeCases<Sample> {
      val constituentArbs = buildList {
        val scalarArb = SomeScalarValueArb()
        add(scalarArb)

        if (maxDepth != 0) {
          add(SomeCompositeValueArb(maxDepth = maxDepth, maxSize = maxSize, scalarArb = scalarArb))
        }
      }

      return Arb.choice(constituentArbs).toArbWithEdgeCases()
    }
  }
}

internal abstract class SomeScalarOrCompositeValueArb<out T : SomeValueArb.Sample> : Arb<T>() {

  // Remove nullability from return type.
  abstract override fun edgecase(rs: RandomSource): T
}

private class SomeScalarValueArb : SomeScalarOrCompositeValueArb<SomeValueArb.Sample.Scalar>() {

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        edgeCaseProbability = rs.random.nextDouble(),
        isEdgeCase = false,
      )
      .asSample()

  override fun edgecase(rs: RandomSource) =
    generate(
      rs,
      edgeCaseProbability = 1.0,
      isEdgeCase = true,
    )

  fun generate(
    rs: RandomSource,
    edgeCaseProbability: Double,
    isEdgeCase: Boolean,
  ): SomeValueArb.Sample.Scalar {
    val scalarType = SomeValueArb.Sample.Scalar.Type.entries.random(rs.random)
    val arb = scalarType.createArb()
    val value = arb.next(rs, edgeCaseProbability)
    return SomeValueArb.Sample.Scalar(
      value = value,
      edgeCaseProbability = edgeCaseProbability,
      type = scalarType,
      isEdgeCase = isEdgeCase,
    )
  }

  private companion object {

    fun SomeValueArb.Sample.Scalar.Type.createArb(): Arb<Any> =
      when (this) {
        SomeValueArb.Sample.Scalar.Type.Boolean -> Arb.boolean()
        SomeValueArb.Sample.Scalar.Type.Byte -> Arb.byte()
        SomeValueArb.Sample.Scalar.Type.Char -> Arb.char()
        SomeValueArb.Sample.Scalar.Type.Double -> Arb.double()
        SomeValueArb.Sample.Scalar.Type.Float -> Arb.float()
        SomeValueArb.Sample.Scalar.Type.Int -> Arb.int()
        SomeValueArb.Sample.Scalar.Type.Long -> Arb.long()
        SomeValueArb.Sample.Scalar.Type.Short -> Arb.short()
        SomeValueArb.Sample.Scalar.Type.String -> Arb.string()
        SomeValueArb.Sample.Scalar.Type.Unit -> Arb.constant(Unit)
        SomeValueArb.Sample.Scalar.Type.Throwable -> throwableArb()
        SomeValueArb.Sample.Scalar.Type.KClass -> kClassArb()
      }

    private fun kClassArb(): Arb<KClass<*>> {
      val scalarKClassArb = Arb.enum<SomeValueArb.Sample.Scalar.Type>().map { it.kClass }
      val compositeKClassArb = Arb.enum<SomeValueArb.Sample.Composite.Type>().map { it.kClass }
      return Arb.choice(scalarKClassArb, compositeKClassArb)
    }
  }
}

private class SomeCompositeValueArb(
  private val maxDepth: Int,
  maxSize: Int,
  private val scalarArb: Arb<SomeValueArb.Sample.Scalar>
) : SomeScalarOrCompositeValueArb<SomeValueArb.Sample.Composite>() {

  init {
    require(maxDepth in validMaxDepthRange) {
      "invalid maxDepth: $maxDepth (must be in range $validMaxDepthRange)"
    }
    require(maxSize in validMaxSizeRange) {
      "invalid maxSize: $maxSize (must be in range $validMaxSizeRange)"
    }
  }

  private val sizeArb = Arb.int(0..maxSize)

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        maxDepth = maxDepth,
        edgeCaseProbability = rs.random.nextDouble(),
        compositeProbability = rs.random.nextDouble(),
        edgeCases = emptySet(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): SomeValueArb.Sample.Composite {
    val edgeCases: Set<SomeValueArb.Sample.Composite.EdgeCase> = run {
      val edgeCases = SomeValueArb.Sample.Composite.EdgeCase.entries.shuffled(rs.random)
      val edgeCaseCount = rs.random.nextInt(1..edgeCases.size)
      edgeCases.take(edgeCaseCount).toSet()
    }

    return generate(
      rs,
      maxDepth = if (SomeValueArb.Sample.Composite.EdgeCase.MaxDepth in edgeCases) 1 else maxDepth,
      edgeCaseProbability =
        if (SomeValueArb.Sample.Composite.EdgeCase.Value in edgeCases) 1.0 else 0.0,
      compositeProbability =
        if (SomeValueArb.Sample.Composite.EdgeCase.Composite in edgeCases) 1.0 else 0.0,
      edgeCases = edgeCases,
    )
  }

  private fun generate(
    rs: RandomSource,
    maxDepth: Int,
    edgeCaseProbability: Double,
    compositeProbability: Double,
    edgeCases: Set<SomeValueArb.Sample.Composite.EdgeCase>,
  ): SomeValueArb.Sample.Composite {
    require(maxDepth in 1..this.maxDepth) {
      "invalid maxDepth: $maxDepth (this.maxDepth=${this.maxDepth})"
    }

    val valueGenerator =
      ValueGenerator(
        rs,
        edgeCaseProbability = edgeCaseProbability,
        compositeProbability = compositeProbability,
        edgeCases = edgeCases,
      )

    val compositeType = SomeValueArb.Sample.Composite.Type.entries.random(rs.random)
    val value = valueGenerator.generate(compositeType, maxDepth)
    return SomeValueArb.Sample.Composite(
      value = value,
      edgeCaseProbability = edgeCaseProbability,
      type = compositeType,
      maxDepth = maxDepth,
      compositeProbability = compositeProbability,
      edgeCases = edgeCases,
    )
  }

  private inner class ValueGenerator(
    val rs: RandomSource,
    val edgeCaseProbability: Double,
    val compositeProbability: Double,
    val edgeCases: Set<SomeValueArb.Sample.Composite.EdgeCase>,
  ) {

    fun values(maxDepth: Int): Sequence<Any> {
      require(maxDepth >= 0) { "invalid maxDepth: $maxDepth [khyngdhxz6]" }
      return generateSequence {
        val sampleType =
          if (maxDepth == 0) {
            SampleType.Scalar
          } else {
            SampleType.entries.random(rs.random)
          }

        when (sampleType) {
          SampleType.Scalar -> scalarArb.next(rs, edgeCaseProbability)
          SampleType.Composite ->
            generate(
              rs,
              maxDepth = maxDepth,
              edgeCaseProbability = edgeCaseProbability,
              compositeProbability = compositeProbability,
              edgeCases = edgeCases,
            )
        }.value
      }
    }

    fun list(maxDepth: Int): List<*> = mutableList(maxDepth).toList()

    fun mutableList(maxDepth: Int): MutableList<*> {
      val size = if (maxDepth == 0) 0 else sizeArb.next(rs, edgeCaseProbability)
      return values(maxDepth = maxDepth - 1).take(size).toMutableList()
    }

    fun set(maxDepth: Int): Set<*> = mutableSet(maxDepth).toSet()

    fun mutableSet(maxDepth: Int): MutableSet<*> {
      val size = if (maxDepth == 0) 0 else sizeArb.next(rs, edgeCaseProbability)
      return values(maxDepth = maxDepth - 1).take(size).toMutableSet()
    }

    fun map(maxDepth: Int): Map<*, *> = mutableMap(maxDepth).toMap()

    fun mutableMap(maxDepth: Int): MutableMap<*, *> {
      val size = if (maxDepth == 0) 0 else sizeArb.next(rs, edgeCaseProbability)
      val values = values(maxDepth = maxDepth - 1)

      val mutableMap = mutableMapOf<Any, Any>()
      values.take(size).forEach { value ->
        val key = scalarArb.next(rs, edgeCaseProbability)
        mutableMap[key] = value
      }
      return mutableMap
    }

    fun pair(maxDepth: Int): Pair<*, *> {
      val values = values(maxDepth = maxDepth - 1).iterator()
      return Pair(values.next(), values.next())
    }

    fun triple(maxDepth: Int): Triple<*, *, *> {
      val values = values(maxDepth = maxDepth - 1).iterator()
      return Triple(values.next(), values.next(), values.next())
    }

    fun result(maxDepth: Int): Result<*> =
      if (rs.random.nextBoolean()) {
        val throwable = throwableArb().next(rs, edgeCaseProbability)
        Result.failure<Any>(throwable)
      } else {
        val value = values(maxDepth = maxDepth - 1).first()
        Result.success(value)
      }
  }

  private fun ValueGenerator.generate(
    type: SomeValueArb.Sample.Composite.Type,
    maxDepth: Int
  ): Any =
    when (type) {
      SomeValueArb.Sample.Composite.Type.List -> list(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.Map -> map(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.MutableList -> mutableList(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.MutableMap -> mutableMap(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.MutableSet -> mutableSet(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.Pair -> pair(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.Result -> result(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.Set -> set(maxDepth = maxDepth)
      SomeValueArb.Sample.Composite.Type.Triple -> triple(maxDepth = maxDepth)
    }

  private enum class SampleType {
    Scalar,
    Composite,
  }

  private companion object {
    val validMaxDepthRange = 1..5
    val validMaxSizeRange = 0..5
  }
}

private val throwableFactories: List<(String) -> Throwable> =
  listOf(
    ::Throwable,
    ::Exception,
    ::RuntimeException,
    ::IllegalArgumentException,
    ::IllegalStateException,
    ::NoSuchElementException,
    ::IndexOutOfBoundsException,
    ::NullPointerException,
  )

private fun throwableArb(): Arb<Throwable> {
  val messageArb = Arb.string(0..10, Codepoint.alphanumeric())
  val factoryArb = Arb.of(throwableFactories)
  return Arb.bind(messageArb, factoryArb) { message, factory -> factory(message) }
}
