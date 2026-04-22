/*
 * Copyright 2025 Google LLC
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

import com.google.firebase.dataconnect.testutil.Quadruple
import com.google.firebase.dataconnect.testutil.Quintuple
import io.kotest.assertions.print.print
import io.kotest.property.Arb
import io.kotest.property.arbitrary.bind

interface MultipleValues<T> : Iterable<T> {

  fun toList(): List<T>

  override fun iterator(): Iterator<T> = toList().iterator()
}

fun <T : Comparable<T>> MultipleValues<T>.sorted(): List<T> = toList().sorted()

data class TwoValues<T>(val value1: T, val value2: T) : MultipleValues<T> {
  override fun toString() =
    "TwoValues(value1=${value1.print().value}, value2=${value2.print().value})"

  override fun toList(): List<T> = listOf(value1, value2)
}

fun <T> List<T>.toTwoValues(): TwoValues<T> {
  require(size == 2) { "size is $size, but it must be exactly 2" }
  return TwoValues(get(0), get(1))
}

fun <T : Comparable<T>> TwoValues<T>.sorted(): TwoValues<T> =
  if (value1 <= value2) this else TwoValues(value2, value1)

data class ThreeValues<T>(val value1: T, val value2: T, val value3: T) : MultipleValues<T> {
  override fun toString() =
    "ThreeValues(" +
      "value1=${value1.print().value}, " +
      "value2=${value2.print().value}, " +
      "value3=${value3.print().value})"

  override fun toList(): List<T> = listOf(value1, value2, value3)
}

fun <T : Comparable<T>> ThreeValues<T>.sorted(): ThreeValues<T> {
  if (value1 <= value2 && value2 <= value3) {
    return this
  }
  val values = mutableListOf(value1, value2, value3)
  values.sort()
  return ThreeValues(values[0], values[1], values[2])
}

fun <T> List<T>.toThreeValues(): ThreeValues<T> {
  require(size == 3) { "size is $size, but it must be exactly 3" }
  return ThreeValues(get(0), get(1), get(2))
}

data class FourValues<T>(val value1: T, val value2: T, val value3: T, val value4: T) :
  MultipleValues<T> {
  override fun toString() =
    "FourValues(" +
      "value1=${value1.print().value}, " +
      "value2=${value2.print().value}, " +
      "value3=${value3.print().value}, " +
      "value4=${value4.print().value})"

  override fun toList(): List<T> = listOf(value1, value2, value3, value4)
}

fun <T> List<T>.toFourValues(): FourValues<T> {
  require(size == 4) { "size is $size, but it must be exactly 4" }
  return FourValues(get(0), get(1), get(2), get(3))
}

fun <T : Comparable<T>> FourValues<T>.sorted(): FourValues<T> {
  if (value1 <= value2 && value2 <= value3 && value3 <= value4) {
    return this
  }
  val values = mutableListOf(value1, value2, value3, value4)
  values.sort()
  return FourValues(values[0], values[1], values[2], values[3])
}

data class FiveValues<T>(
  val value1: T,
  val value2: T,
  val value3: T,
  val value4: T,
  val value5: T
) : MultipleValues<T> {
  override fun toString() =
    "FiveValues(" +
      "value1=${value1.print().value}, " +
      "value2=${value2.print().value}, " +
      "value3=${value3.print().value}, " +
      "value4=${value4.print().value}, " +
      "value5=${value5.print().value})"

  override fun toList(): List<T> = listOf(value1, value2, value3, value4, value5)
}

fun <T> List<T>.toFiveValues(): FiveValues<T> {
  require(size == 5) { "size is $size, but it must be exactly 5" }
  return FiveValues(get(0), get(1), get(2), get(3), get(4))
}

fun <T : Comparable<T>> FiveValues<T>.sorted(): FiveValues<T> {
  if (value1 <= value2 && value2 <= value3 && value3 <= value4 && value4 < value5) {
    return this
  }
  val values = mutableListOf(value1, value2, value3, value4, value5)
  values.sort()
  return FiveValues(values[0], values[1], values[2], values[3], values[4])
}

fun <T> Arb.Companion.twoValues(arb: Arb<T>): Arb<TwoValues<T>> =
  bind(arb, arb) { value1, value2 -> TwoValues(value1, value2) }

fun <T> Arb<T>.pair(): Arb<TwoValues<T>> = Arb.twoValues(this)

fun <T> Arb.Companion.threeValues(arb: Arb<T>): Arb<ThreeValues<T>> =
  bind(arb, arb, arb) { value1, value2, value3 -> ThreeValues(value1, value2, value3) }

fun <T> Arb<T>.triple(): Arb<ThreeValues<T>> = Arb.threeValues(this)

fun <T> Arb.Companion.fourValues(arb: Arb<T>): Arb<FourValues<T>> =
  bind(arb, arb, arb, arb) { value1, value2, value3, value4 ->
    FourValues(value1, value2, value3, value4)
  }

fun <T> Arb<T>.quadruple(): Arb<FourValues<T>> = Arb.fourValues(this)

fun <T> Arb.Companion.fiveValues(arb: Arb<T>): Arb<FiveValues<T>> =
  bind(arb, arb, arb, arb, arb) { value1, value2, value3, value4, value5 ->
    FiveValues(value1, value2, value3, value4, value5)
  }

fun <T> Arb<T>.quintuple(): Arb<FiveValues<T>> = Arb.fiveValues(this)

fun <A, B, C, D> Arb.Companion.quadruple(
  a: Arb<A>,
  b: Arb<B>,
  c: Arb<C>,
  d: Arb<D>
): Arb<Quadruple<A, B, C, D>> = Arb.bind(a, b, c, d, ::Quadruple)

fun <A, B, C, D, E> Arb.Companion.quintuple(
  a: Arb<A>,
  b: Arb<B>,
  c: Arb<C>,
  d: Arb<D>,
  e: Arb<E>
): Arb<Quintuple<A, B, C, D, E>> = Arb.bind(a, b, c, d, e, ::Quintuple)
