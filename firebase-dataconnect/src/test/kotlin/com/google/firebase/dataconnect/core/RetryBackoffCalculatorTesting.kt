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
      1750,
      3063,
      5360,
      9380,
      16415,
      28726,
      50271,
      87974,
      153955,
      269421,
      471487,
      600000,
    )

  val minJitterBackoffValues: List<Long> =
    listOf(
      500,
      875,
      1532,
      2680,
      4690,
      8208,
      14363,
      25136,
      43987,
      76978,
      134711,
      235744,
      300000,
    )

  val maxJitterBackoffValues: List<Long> =
    listOf(
      1500,
      2625,
      4595,
      8040,
      14070,
      24623,
      43089,
      75407,
      131961,
      230933,
      404132,
      707231,
      900000,
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
