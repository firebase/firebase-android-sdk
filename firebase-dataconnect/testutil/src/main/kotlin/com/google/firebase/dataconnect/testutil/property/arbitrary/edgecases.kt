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

import com.google.firebase.dataconnect.testutil.MAX_SAFE_INTEGER
import java.util.UUID

object EdgeCases {
  val anyScalar: AnyScalarEdgeCases = AnyScalarEdgeCases
  val dates: DateEdgeCases = DateEdgeCases
  val javaTime: JavaTimeEdgeCases = JavaTimeEdgeCases

  val strings: List<String>
    get() = listOf("")

  val ints: List<Int>
    get() = listOf(0, 1, -1, Int.MAX_VALUE, Int.MIN_VALUE)

  val int64s: List<Long>
    get() =
      listOf(
        0,
        1,
        -1,
        Int.MAX_VALUE.toLong(),
        Int.MIN_VALUE.toLong(),
        Long.MAX_VALUE,
        Long.MIN_VALUE,
      )

  val floats: List<Double>
    get() = listOf(-0.0, 0.0, 1.0, -1.0, Double.MAX_VALUE, Double.MIN_VALUE, MAX_SAFE_INTEGER)

  val uuids: List<UUID>
    get() = listOf(UUID.fromString("00000000-0000-0000-0000-000000000000"))

  val booleans: List<Boolean>
    get() = listOf(true, false)
}
