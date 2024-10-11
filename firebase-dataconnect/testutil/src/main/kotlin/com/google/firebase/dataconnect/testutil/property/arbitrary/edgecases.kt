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

object EdgeCases {
  object anyScalar
}

val EdgeCases.anyScalar.numbers: List<Double>
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

val EdgeCases.anyScalar.strings: List<String>
  get() = listOf("")

val EdgeCases.anyScalar.booleans: List<Boolean>
  get() = listOf(true, false)

val EdgeCases.anyScalar.primitives: List<Any>
  get() = numbers + strings + booleans

val EdgeCases.anyScalar.lists: List<List<Any?>>
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

val EdgeCases.anyScalar.maps: List<Map<String, Any?>>
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

val EdgeCases.anyScalar.all: List<Any?>
  get() = primitives + lists + maps + listOf(null)
