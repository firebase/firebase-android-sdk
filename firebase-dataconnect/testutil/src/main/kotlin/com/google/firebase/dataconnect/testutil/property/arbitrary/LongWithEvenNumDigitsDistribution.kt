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

@file:Suppress("UnusedReceiverParameter")

package com.google.firebase.dataconnect.testutil.property.arbitrary

import io.kotest.property.Arb
import io.kotest.property.arbitrary.choice
import io.kotest.property.arbitrary.long

/**
 * Returns an [Arb] identical to [Arb.Companion.nonNegativeLong] except that the values it produces
 * have an equal probability of having any given number of digits in its base-10 string
 * representation. This is useful for testing int values that get zero padded when they are small.
 */
fun Arb.Companion.nonNegativeLongWithEvenNumDigitsDistribution(): Arb<Long> =
  Arb.choice(rangeByNumDigits.map { Arb.long(it) })

private val rangeByNumDigits: List<LongRange> = buildList {
  add(0L..9L)
  add(10L..99L)
  add(100L..999L)
  add(1_000L..9_999L)
  add(10_000L..99_999L)
  add(100_000L..999_999L)
  add(1_000_000L..9_999_999L)
  add(10_000_000L..99_999_999L)
  add(100_000_000L..999_999_999L)
  add(1_000_000_000L..9_999_999_999L)
  add(10_000_000_000L..99_999_999_999L)
  add(100_000_000_000L..999_999_999_999L)
  add(1_000_000_000_000L..9_999_999_999_999L)
  add(10_000_000_000_000L..99_999_999_999_999L)
  add(100_000_000_000_000L..999_999_999_999_999L)
  add(1_000_000_000_000_000L..9_999_999_999_999_999L)
  add(10_000_000_000_000_000L..99_999_999_999_999_999L)
  add(100_000_000_000_000_000L..999_999_999_999_999_999L)
  add(1_000_000_000_000_000_000L..Long.MAX_VALUE)
}
