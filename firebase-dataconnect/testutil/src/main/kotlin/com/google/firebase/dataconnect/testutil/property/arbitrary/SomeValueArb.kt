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

import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.byte
import io.kotest.property.arbitrary.char
import io.kotest.property.arbitrary.constant
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.float
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.arbitrary.short
import io.kotest.property.arbitrary.string
import io.kotest.property.asSample
import kotlin.random.nextInt
import kotlin.reflect.KClass

fun Arb.Companion.someValue(maxDepth: Int = 2, maxSize: Int = 3): Arb<Any> =
  SomeValueArb(maxDepth, maxSize)

private class SomeValueArb(maxDepth: Int, maxSize: Int) : Arb<Any>() {

  private val scalarArb = SomeScalarValueArb()
  private val compositeArb =
    SomeCompositeValueArb(maxDepth = maxDepth, maxSize = maxSize, scalarArb = scalarArb)

  override fun sample(rs: RandomSource) =
    generate(
        rs = rs,
        edgeCaseProbability = rs.random.nextDouble(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource) =
    generate(
      rs = rs,
      edgeCaseProbability = 1.0,
    )

  private fun generate(
    rs: RandomSource,
    edgeCaseProbability: Double,
  ): Any {
    val arb =
      when (SampleType.entries.random(rs.random)) {
        SampleType.Scalar -> scalarArb
        SampleType.Composite -> compositeArb
      }
    return arb.next(rs, edgeCaseProbability)
  }

  enum class SampleType {
    Scalar,
    Composite,
  }
}

private class SomeScalarValueArb : Arb<Any>() {

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        edgeCaseProbability = rs.random.nextDouble(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource) = generate(rs, edgeCaseProbability = 1.0)

  private fun generate(
    rs: RandomSource,
    edgeCaseProbability: Double,
  ): Any {
    val scalarType = ScalarType.entries.random(rs.random)
    val arb = scalarType.createArb()
    return arb.next(rs, edgeCaseProbability)
  }

  private enum class ScalarType(val createArb: () -> Arb<Any>) {
    Boolean({ Arb.boolean() }),
    Byte({ Arb.byte() }),
    Char({ Arb.char() }),
    Double({ Arb.double() }),
    Float({ Arb.float() }),
    Int({ Arb.int() }),
    Long({ Arb.long() }),
    Short({ Arb.short() }),
    String({ Arb.string() }),
    Unit({ Arb.constant(Unit) }),
  }
}

private class SomeCompositeValueArb(
  private val maxDepth: Int,
  maxSize: Int,
  private val scalarArb: Arb<Any>
) : Arb<Any>() {

  init {
    require(maxDepth in validMaxDepthRange) {
      "invalid maxDepth: $maxDepth (must be in range $validMaxDepthRange)"
    }
    require(maxSize in validMaxSizeRange) {
      "invalid maxSize: $maxSize (must be in range $validMaxSizeRange)"
    }
  }

  private val sizeArb = Arb.int(0..maxSize)
  private val messageArb = Arb.string(0..10, Codepoint.alphanumeric())

  override fun sample(rs: RandomSource) =
    generate(
        rs,
        maxDepth = maxDepth,
        edgeCaseProbability = rs.random.nextDouble(),
        compositeProbability = rs.random.nextDouble(),
      )
      .asSample()

  override fun edgecase(rs: RandomSource): Any {
    val edgeCases = run {
      val edgeCases = EdgeCase.entries.shuffled(rs.random)
      val edgeCaseCount = rs.random.nextInt(1..edgeCases.size)
      edgeCases.take(edgeCaseCount).toSet()
    }

    return generate(
      rs,
      maxDepth = if (EdgeCase.MaxDepth in edgeCases) 1 else maxDepth,
      edgeCaseProbability = if (EdgeCase.Value in edgeCases) 1.0 else 0.0,
      compositeProbability = if (EdgeCase.Composite in edgeCases) 1.0 else 0.0,
    )
  }

  private fun generate(
    rs: RandomSource,
    maxDepth: Int,
    edgeCaseProbability: Double,
    compositeProbability: Double,
  ): Any {
    require(maxDepth in 1..this.maxDepth) {
      "invalid maxDepth: $maxDepth (this.maxDepth=${this.maxDepth})"
    }

    val valueGenerator =
      ValueGenerator(
        rs,
        edgeCaseProbability = edgeCaseProbability,
        compositeProbability = compositeProbability,
      )

    val compositeType = CompositeType.entries.random(rs.random)
    return compositeType.generate(valueGenerator, maxDepth)
  }

  private inner class ValueGenerator(
    val rs: RandomSource,
    val edgeCaseProbability: Double,
    val compositeProbability: Double,
  ) {

    fun values(maxDepth: Int): Sequence<Any> {
      require(maxDepth >= 0) { "invalid maxDepth: $maxDepth [khyngdhxz6]" }
      return generateSequence {
        val sampleType =
          if (maxDepth == 0) {
            SomeValueArb.SampleType.Scalar
          } else {
            SomeValueArb.SampleType.entries.random(rs.random)
          }

        when (sampleType) {
          SomeValueArb.SampleType.Scalar -> scalarArb.next(rs, edgeCaseProbability)
          SomeValueArb.SampleType.Composite ->
            generate(
              rs,
              maxDepth = maxDepth,
              edgeCaseProbability = edgeCaseProbability,
              compositeProbability = compositeProbability,
            )
        }
      }
    }

    fun kClass(): KClass<*> = values(maxDepth = 1).first()::class

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

    fun throwable(): Throwable {
      val message = messageArb.next(rs, edgeCaseProbability)
      val factory = throwableFactories.random(rs.random)
      return factory(message)
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
        val throwable = throwable()
        Result.failure<Any>(throwable)
      } else {
        val value = values(maxDepth = maxDepth - 1).first()
        Result.success(value)
      }
  }

  private enum class EdgeCase {
    MaxDepth,
    Value,
    Composite,
  }

  private enum class CompositeType(val generate: (ValueGenerator, maxDepth: Int) -> Any) {
    KClass({ generator, _ -> generator.kClass() }),
    List({ generator, maxDepth -> generator.list(maxDepth = maxDepth) }),
    Map({ generator, maxDepth -> generator.map(maxDepth = maxDepth) }),
    MutableList({ generator, maxDepth -> generator.mutableList(maxDepth = maxDepth) }),
    MutableMap({ generator, maxDepth -> generator.mutableMap(maxDepth = maxDepth) }),
    MutableSet({ generator, maxDepth -> generator.mutableSet(maxDepth = maxDepth) }),
    Pair({ generator, maxDepth -> generator.pair(maxDepth = maxDepth) }),
    Result({ generator, maxDepth -> generator.result(maxDepth = maxDepth) }),
    Set({ generator, maxDepth -> generator.set(maxDepth = maxDepth) }),
    Throwable({ generator, _ -> generator.throwable() }),
    Triple({ generator, maxDepth -> generator.triple(maxDepth = maxDepth) }),
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

  companion object {
    val validMaxDepthRange = 1..5
    val validMaxSizeRange = 0..5
  }
}
