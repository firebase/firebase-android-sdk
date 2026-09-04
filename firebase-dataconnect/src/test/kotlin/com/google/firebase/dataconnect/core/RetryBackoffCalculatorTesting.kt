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

package com.google.firebase.dataconnect.core

import io.kotest.assertions.print.print
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import kotlin.math.roundToLong

object RetryBackoffCalculatorTesting {

  val backoffValues: List<Long> =
    listOf(
      1000,
      1500,
      2250,
      3375,
      5063,
      7595,
      11393,
      17090,
      25635,
      38453,
      57680,
      60000,
    )

  val minBackoffValue: Long = backoffValues.first().also { check(it == 1000L) }

  val maxBackoffValue: Long = backoffValues.last().also { check(it == 60_000L) }

  val minJitterBackoffValues: List<Long> =
    listOf(
      500,
      750,
      1125,
      1688,
      2532,
      3798,
      5697,
      8545,
      12818,
      19227,
      28840,
      30000,
    )

  val maxJitterBackoffValues: List<Long> =
    listOf(
      1500,
      2250,
      3375,
      5063,
      7595,
      11393,
      17090,
      25635,
      38453,
      57680,
      86520,
      90000,
    )

  data class JitterTestCase(
    val jitters: List<Double>,
    val expectedBackoffs: List<Long>,
  ) {

    init {
      check(jitters.size == expectedBackoffs.size) {
        "internal error yds47wscpa: jitters.size=${jitters.size} must equal " +
          "expectedBackoffs.size=${expectedBackoffs.size}"
      }
    }

    override fun toString() =
      "JitterTestCase(jitters=${jitters.print().value}, " +
        "expectedBackoffs=${expectedBackoffs.print().value})"
  }

  fun Arb.Companion.jitterTestCase(): Arb<JitterTestCase> {
    val unjitteredBackoffValues = backoffValues + List(5) { backoffValues.last() }
    return arbitrary { rs ->
      val jitters = List(unjitteredBackoffValues.size) { rs.random.nextDouble() - 0.5 }
      val jitteredBackoffValues =
        unjitteredBackoffValues.zip(jitters).map { (backoff, jitter) ->
          backoff + (backoff * jitter).roundToLong()
        }
      JitterTestCase(jitters, jitteredBackoffValues)
    }
  }
}
