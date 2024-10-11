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

import java.util.Date
import java.util.GregorianCalendar
import java.util.TimeZone

object EdgeCases {

  val numbers: List<Double> =
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

  val strings: List<String> = listOf("")

  val booleans: List<Boolean> = listOf(true, false)

  val primitives: List<Any> = numbers + strings + booleans

  val lists: List<List<Any?>> = buildList {
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

  val maps: List<Map<String, Any?>> = buildList {
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

  val anyScalars: List<Any?> = primitives + lists + maps + listOf(null)

  object Dates {
    const val MIN_YEAR = 1583
    const val MAX_YEAR = 9999

    val MIN: Date
      get() = dateFromYearMonthDayUTC(MIN_YEAR, 1, 1)
    val MIN_DATE_AND_STRING: DateAndString
      get() = DateAndString(MIN, "$MIN_YEAR-01-01")

    val MAX: Date
      get() = dateFromYearMonthDayUTC(MAX_YEAR, 12, 31)
    val MAX_DATE_AND_STRING: DateAndString
      get() = DateAndString(MAX, "$MAX_YEAR-12-31")

    val ZERO: Date
      get() = GregorianCalendar(TimeZone.getTimeZone("UTC")).apply { timeInMillis = 0 }.time
    val ZERO_DATE_AND_STRING: DateAndString
      get() = DateAndString(ZERO, "1970-01-01")
  }

  val dates: List<Date> = listOf(Dates.MIN, Dates.MAX, Dates.ZERO)

  val dateAndStrings: List<DateAndString> =
    listOf(
      Dates.MIN_DATE_AND_STRING,
      Dates.MAX_DATE_AND_STRING,
      Dates.ZERO_DATE_AND_STRING,
    )

  val dateAndStringOffDayBoundary: List<DateAndString> =
    listOf(
      DateAndString(
        Date(Dates.MIN_DATE_AND_STRING.date.time + 1),
        Dates.MIN_DATE_AND_STRING.string
      ),
      DateAndString(
        Date(Dates.MAX_DATE_AND_STRING.date.time + 1),
        Dates.MAX_DATE_AND_STRING.string
      ),
      DateAndString(
        Date(Dates.ZERO_DATE_AND_STRING.date.time + 1),
        Dates.ZERO_DATE_AND_STRING.string
      ),
      DateAndString(Date(Dates.ZERO_DATE_AND_STRING.date.time - 1), "1969-12-31"),
    )
}
