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

package com.google.firebase.dataconnect.testutil

import com.google.firebase.dataconnect.FirebaseDataConnect.CallerSdkType
import io.kotest.property.Arb
import io.kotest.property.arbitrary.Codepoint
import io.kotest.property.arbitrary.alphanumeric
import io.kotest.property.arbitrary.arabic
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.ascii
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.cyrillic
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.egyptianHieroglyphs
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.filterIsInstance
import io.kotest.property.arbitrary.filterNot
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.merge
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string

fun Arb.Companion.anyNumberScalar(): Arb<Double> = anyScalar().filterIsInstance<Double>()

fun Arb.Companion.anyStringScalar(): Arb<String> = anyScalar().filterIsInstance<String>()

fun Arb.Companion.anyListScalar(): Arb<List<Any?>> = anyScalar().filterIsInstance<List<Any?>>()

fun Arb.Companion.anyMapScalar(): Arb<Map<String, Any?>> =
  anyScalar().filterIsInstance<Map<String, Any?>>()

fun Arb.Companion.anyScalar(): Arb<Any?> =
  arbitrary(edgecases = EdgeCases.anyScalars) {
    // Put the arbs into an `object` so that `lists`, `maps`, and `allValues` can contain
    // circular references to each other.
    val anyScalarArbs =
      object {
        val booleans = Arb.boolean()
        val numbers = Arb.double()
        val nulls = Arb.of(null)

        val codepoints =
          Codepoint.ascii()
            .merge(Codepoint.egyptianHieroglyphs())
            .merge(Codepoint.arabic())
            .merge(Codepoint.cyrillic())
            // Do not produce character code 0 because it's not supported by Postgresql:
            // https://www.postgresql.org/docs/current/datatype-character.html
            .filterNot { it.value == 0 }
        val strings = Arb.string(minSize = 1, maxSize = 40, codepoints = codepoints)

        val lists: Arb<List<Any?>> = arbitrary {
          val size = Arb.int(1..3).bind()
          List(size) { allValues.bind() }
        }

        val maps: Arb<Map<String, Any?>> = arbitrary {
          buildMap {
            val size = Arb.int(1..3).bind()
            repeat(size) { put(strings.bind(), allValues.bind()) }
          }
        }

        val allValues: Arb<Any?> = Arb.choice(booleans, numbers, strings, nulls, lists, maps)
      }

    anyScalarArbs.allValues.bind()
  }

fun <A> Arb<A>.filterNotAnyScalarMatching(value: Any?) = filter {
  if (it == value) {
    false
  } else if (it === null || value === null) {
    true
  } else {
    expectedAnyScalarRoundTripValue(it) != expectedAnyScalarRoundTripValue(value)
  }
}

fun <A> Arb<List<A>>.filterNotIncludesAllMatchingAnyScalars(values: List<Any?>) = filter {
  require(values.isNotEmpty()) { "values must not be empty" }

  val allValues = buildList {
    for (value in it) {
      add(value)
      add(expectedAnyScalarRoundTripValue(value))
    }
  }

  !values
    .map { Pair(it, expectedAnyScalarRoundTripValue(it)) }
    .map { allValues.contains(it.first) || allValues.contains(it.second) }
    .reduce { acc, contained -> acc && contained }
}

fun Arb.Companion.callerSdkType(): Arb<CallerSdkType> = arbitrary {
  if (Arb.boolean().bind()) CallerSdkType.Base else CallerSdkType.Generated
}

fun Arb.Companion.tag(): Arb<String> = arbitrary {
  "tag" + Arb.string(size = 10, Codepoint.alphanumeric()).bind()
}
