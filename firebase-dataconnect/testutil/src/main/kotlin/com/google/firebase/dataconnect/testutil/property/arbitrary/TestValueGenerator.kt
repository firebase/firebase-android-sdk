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

import com.google.firebase.dataconnect.testutil.RandomSeedTestRule
import io.kotest.common.DelicateKotest
import io.kotest.property.Arb
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.distinct
import kotlin.random.Random
import kotlin.random.nextInt

/** Convenience class to generate values from [Arb] objects outside property-based tests. */
class TestValueGenerator(val seed: Long, val edgeCaseProbability: Double) {

  constructor(
    random: Random,
    edgeCaseProbability: Double
  ) : this(random.nextLong(), edgeCaseProbability)

  constructor(rs: RandomSource, edgeCaseProbability: Double) : this(rs.random, edgeCaseProbability)

  constructor(
    randomSeedTestRule: RandomSeedTestRule,
    edgeCaseProbability: Double
  ) : this(randomSeedTestRule.rs.value, edgeCaseProbability)

  init {
    require(edgeCaseProbability in 0.0..1.0) {
      "invalid edgeCaseProbability: $edgeCaseProbability [dskqgdzq3z]"
    }
  }

  val rs: RandomSource = RandomSource.seeded(seed)
  val random: Random
    get() = rs.random

  fun <T> generateValueFrom(arb: Arb<T>): T {
    if (random.nextDouble() < edgeCaseProbability) {
      arb.edgecase(rs)?.let {
        return it
      }
    }
    return arb.sample(rs).value
  }

  fun <T> Arb<T>.generateValue(): T = generateValueFrom(this)

  fun <T> generateValuesFrom(arb: Arb<T>, count: Int): List<T> {
    require(count >= 0) { "invalid count: $count [s9hk6kf92r]" }
    return List(count) { generateValueFrom(arb) }
  }

  fun <T> Arb<T>.generateValues(count: Int): List<T> = generateValuesFrom(this, count)

  fun <T> generateValuesFrom(arb: Arb<T>, count: IntRange): List<T> {
    require(count.first >= 0) { "invalid count: $count [hb792ys4tw]" }
    require(!count.isEmpty()) { "invalid count: $count; range must not be empty [j5cwambyjk]" }
    return generateValuesFrom(arb, random.nextInt(count))
  }

  fun <T> Arb<T>.generateValues(count: IntRange): List<T> = generateValuesFrom(this, count)

  fun <T> generateDistinctValuesFrom(arb: Arb<T>, count: Int): List<T> {
    @OptIn(DelicateKotest::class) val distinctArb = arb.distinct()
    return generateValuesFrom(distinctArb, count)
  }

  fun <T> Arb<T>.generateDistinctValues(count: Int): List<T> =
    generateDistinctValuesFrom(this, count)

  fun <T> generateDistinctValuesFrom(arb: Arb<T>, count: IntRange): List<T> {
    require(count.first >= 0) { "invalid count: $count [haxdhjr3da]" }
    require(!count.isEmpty()) { "invalid count: $count; range must not be empty [txr58kan9c]" }
    return generateDistinctValuesFrom(arb, random.nextInt(count))
  }

  fun <T> Arb<T>.generateDistinctValues(count: IntRange): List<T> =
    generateDistinctValuesFrom(this, count)

  fun <T> shuffled(iterable: Iterable<T>): List<T> = iterable.shuffled(random)

  @JvmName("Iterable_shuffled_ext") fun <T> Iterable<T>.shuffled(): List<T> = shuffled(random)

  fun <T> randomValuesFrom(values: List<T>, count: Int): List<T> {
    require(count >= 0) { "invalid count: $count [zr9nrzn7nq]" }
    require(count == 0 || values.isNotEmpty()) {
      "values is empty, but count is greater than zero ($count) [jxd2ar7van]"
    }
    return List(count) { values.random(random) }
  }

  fun <T> List<T>.randomValues(count: Int): List<T> = randomValuesFrom(this, count)

  fun <T> randomValuesFrom(values: List<T>, count: IntRange = 0..values.size): List<T> {
    require(count.first >= 0) { "invalid count: $count; count.first < 0 [sxxwjdz3q9]" }
    require(!count.isEmpty()) { "invalid count: $count; range must not be empty [f5pd45hgtv]" }
    return randomValuesFrom(values, random.nextInt(count))
  }

  fun <T> List<T>.randomValues(count: IntRange = 0..size): List<T> = randomValuesFrom(this, count)

  fun <T> randomDistinctValuesFrom(values: List<T>, count: Int): List<T> {
    require(count >= 0) { "invalid count: $count [dj326qtqc8]" }
    require(count <= values.size) {
      "invalid count: $count; must be less than or equal to values.size, ${values.size} [mqyn2sggwk]"
    }
    return values.shuffled().take(count)
  }

  fun <T> List<T>.randomDistinctValues(count: Int): List<T> = randomDistinctValuesFrom(this, count)

  fun <T> randomDistinctValuesFrom(values: List<T>, count: IntRange = 0..values.size): List<T> {
    require(count.first >= 0) { "invalid count: $count; count.first < 0 [mxjs3dvywm]" }
    require(!count.isEmpty()) { "invalid count: $count; range must not be empty [v2dyhdchqp]" }
    require(count.last <= values.size) {
      "invalid count: $count; count.last (${count.last}) must be less than or equal to " +
        "values.size (${values.size}) [cka7mepcxm]"
    }
    return randomDistinctValuesFrom(values, random.nextInt(count))
  }

  fun <T> List<T>.randomDistinctValues(count: IntRange = 0..size): List<T> =
    randomDistinctValuesFrom(this, count)
}
