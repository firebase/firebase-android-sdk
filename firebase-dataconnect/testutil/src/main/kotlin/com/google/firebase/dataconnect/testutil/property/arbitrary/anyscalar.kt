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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import com.google.firebase.dataconnect.testutil.expectedAnyScalarRoundTripValue
import io.kotest.property.Arb
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.arbitrary.boolean
import io.kotest.property.arbitrary.choose
import io.kotest.property.arbitrary.double
import io.kotest.property.arbitrary.filter
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.of
import io.kotest.property.arbitrary.string

object AnyScalarArb {

  fun number(): Arb<Double> = Arb.double()

  fun string(): Arb<String> = DataConnectArb.string(1..40)

  fun list(): Arb<List<Any?>> = arbitrary {
    val size = Arb.int(1..3).bind()
    List(size) { any().bind() }
  }

  fun map(): Arb<Map<String, Any?>> = arbitrary {
    buildMap {
      val size = Arb.int(1..3).bind()
      repeat(size) { put(string().bind(), AnyScalarArb.any().bind()) }
    }
  }

  fun any(): Arb<Any?> =
    arbitrary(edgecases = EdgeCases.anyScalar.all) {
      Arb.choose(
          2 to Arb.boolean(),
          2 to number(),
          2 to string(),
          2 to Arb.of(null),
          1 to list(),
          1 to map(),
        )
        .bind()
    }
}

object AnyScalarEdgeCases {

  val numbers: List<Double>
    get() =
      listOf(
        -1.0,
        -Double.MIN_VALUE,
        -0.0,
        0.0,
        Double.MIN_VALUE,
        1.0,
        Double.NEGATIVE_INFINITY,
        Double.NaN,
        Double.POSITIVE_INFINITY
      )

  val strings: List<String>
    get() = listOf("")

  val booleans: List<Boolean>
    get() = listOf(true, false)

  val primitives: List<Any>
    get() = numbers + strings + booleans

  val lists: List<List<Any?>>
    get() = buildList {
      add(emptyList())
      add(listOf(null))
      add(listOf(emptyList<Nothing>()))
      add(listOf(emptyMap<Nothing, Nothing>()))
      add(listOf(listOf(null)))
      add(listOf(mapOf("bansj8ayck" to emptyList<Nothing>())))
      add(listOf(mapOf("mjstqe4bt4" to listOf(null))))
      add(primitives)
      add(listOf(primitives))
      add(listOf(mapOf("hw888awmnr" to primitives)))
      add(listOf(mapOf("29vphvjzpr" to listOf(primitives))))
      for (primitiveEdgeCase in primitives) {
        add(listOf(primitiveEdgeCase))
        add(listOf(listOf(primitiveEdgeCase)))
        add(listOf(mapOf("me74x5fqgy" to listOf(primitiveEdgeCase))))
        add(listOf(mapOf("v2rj5cmhsm" to listOf(listOf(primitiveEdgeCase)))))
      }
    }

  val maps: List<Map<String, Any?>>
    get() = buildList {
      add(emptyMap())
      add(mapOf("" to null))
      add(mapOf("fzjfmcrqwe" to emptyMap<Nothing, Nothing>()))
      add(mapOf("g3a2sgytnd" to emptyList<Nothing>()))
      add(mapOf("qywfwqnb6p" to mapOf("84gszc54nh" to null)))
      add(mapOf("zeb85c3xbr" to mapOf("t6mzt385km" to emptyMap<Nothing, Nothing>())))
      add(mapOf("ew85krxvmv" to mapOf("w8a2myv5yj" to emptyList<Nothing>())))
      add(mapOf("k3ytrrk2n6" to mapOf("hncgdwa2wt" to primitives)))
      add(mapOf("yr2xpxczd8" to mapOf("s76y7jh9wa" to mapOf("g28wzy56k4" to primitives))))
      add(
        buildMap {
          for (primitiveEdgeCase in primitives) {
            put("pn9a9nz8b3_$primitiveEdgeCase", primitiveEdgeCase)
          }
        }
      )
      for (primitiveEdgeCase in primitives) {
        add(mapOf("yq7j7n72tc" to primitiveEdgeCase))
        add(mapOf("qsdbfeygnf" to mapOf("33rsz2mjpr" to primitiveEdgeCase)))
        add(mapOf("kyjkx5epga" to listOf(primitiveEdgeCase)))
      }
    }

  val all: List<Any?>
    get() = primitives + lists + maps + listOf(null)
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
