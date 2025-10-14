// Copyright 2025 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.model.Values.encodeValue // Returns com.google.protobuf.Value
import com.google.firebase.firestore.pipeline.Expression.Companion.abs
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.ceil
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.divide
import com.google.firebase.firestore.pipeline.Expression.Companion.exp
import com.google.firebase.firestore.pipeline.Expression.Companion.floor
import com.google.firebase.firestore.pipeline.Expression.Companion.ln
import com.google.firebase.firestore.pipeline.Expression.Companion.log
import com.google.firebase.firestore.pipeline.Expression.Companion.log10
import com.google.firebase.firestore.pipeline.Expression.Companion.mod
import com.google.firebase.firestore.pipeline.Expression.Companion.multiply
import com.google.firebase.firestore.pipeline.Expression.Companion.pow
import com.google.firebase.firestore.pipeline.Expression.Companion.round
import com.google.firebase.firestore.pipeline.Expression.Companion.sqrt
import com.google.firebase.firestore.pipeline.Expression.Companion.subtract
import kotlin.math.E
import org.junit.Test

internal class ArithmeticTests {

  @Test
  fun addFunctionTestWithBasicNumerics() {
    assertThat(evaluate(add(constant(1L), constant(2L))).value).isEqualTo(encodeValue(3L))
    assertThat(evaluate(add(constant(1L), constant(2.5))).value).isEqualTo(encodeValue(3.5))
    assertThat(evaluate(add(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(3.0))
    assertThat(evaluate(add(constant(1.0), constant(2.0))).value).isEqualTo(encodeValue(3.0))
  }

  @Test
  fun addFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(add(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(add(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(add(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun addFunctionTestWithDoubleLongAdditionOverflow() {
    val longMaxAsDoublePlusOne = Long.MAX_VALUE.toDouble() + 1.0
    assertThat(evaluate(add(constant(Long.MAX_VALUE), constant(1.0))).value)
      .isEqualTo(encodeValue(longMaxAsDoublePlusOne))

    val intermediate = longMaxAsDoublePlusOne
    assertThat(evaluate(add(constant(intermediate), constant(100L))).value)
      .isEqualTo(encodeValue(intermediate + 100.0))
  }

  @Test
  fun addFunctionTestWithDoubleAdditionOverflow() {
    assertThat(evaluate(add(constant(Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(add(constant(-Double.MAX_VALUE), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun addFunctionTestWithSumPosAndNegInfinityReturnNaN() {
    assertThat(
        evaluate(add(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY))).value
      )
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun addFunctionTestWithLongAdditionOverflow() {
    assertThat(evaluate(add(constant(Long.MAX_VALUE), constant(1L))).isError).isTrue()
    assertThat(evaluate(add(constant(Long.MIN_VALUE), constant(-1L))).isError).isTrue()
    assertThat(evaluate(add(constant(1L), constant(Long.MAX_VALUE))).isError).isTrue()
  }

  @Test
  fun addFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(add(constant(1L), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(1.0), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(-9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(
        evaluate(add(constant(-Double.MAX_VALUE), constant(nanVal))).value
      ) // Corresponds to C++ std::numeric_limits<double>::lowest()
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun addFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(add(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun addFunctionTestWithMultiArgument() {
    assertThat(evaluate(add(add(constant(1L), constant(2L)), constant(3L))).value)
      .isEqualTo(encodeValue(6L))
    assertThat(evaluate(add(add(constant(1.0), constant(2L)), constant(3L))).value)
      .isEqualTo(encodeValue(6.0))
  }

  // --- Subtract Tests (Ported) ---
  @Test
  fun subtractFunctionTestWithBasicNumerics() {
    assertThat(evaluate(subtract(constant(1L), constant(2L))).value).isEqualTo(encodeValue(-1L))
    assertThat(evaluate(subtract(constant(1L), constant(2.5))).value).isEqualTo(encodeValue(-1.5))
    assertThat(evaluate(subtract(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(-1.0))
    assertThat(evaluate(subtract(constant(1.0), constant(2.0))).value).isEqualTo(encodeValue(-1.0))
  }

  @Test
  fun subtractFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(subtract(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(subtract(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(subtract(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun subtractFunctionTestWithDoubleSubtractionOverflow() {
    assertThat(evaluate(subtract(constant(-Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(subtract(constant(Double.MAX_VALUE), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun subtractFunctionTestWithLongSubtractionOverflow() {
    assertThat(evaluate(subtract(constant(Long.MIN_VALUE), constant(1L))).isError).isTrue()
    assertThat(evaluate(subtract(constant(Long.MAX_VALUE), constant(-1L))).isError).isTrue()
  }

  @Test
  fun subtractFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(subtract(constant(1L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(1.0), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(-9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(-Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun subtractFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(subtract(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun subtractFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(subtract(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(subtract(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun subtractFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(subtract(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(subtract(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun subtractFunctionTestWithPositiveInfinityNegativeInfinity() {
    assertThat(
        evaluate(subtract(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(
        evaluate(subtract(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  // --- Multiply Tests (Ported) ---
  @Test
  fun multiplyFunctionTestWithBasicNumerics() {
    assertThat(evaluate(multiply(constant(1L), constant(2L))).value).isEqualTo(encodeValue(2L))
    assertThat(evaluate(multiply(constant(3L), constant(2.5))).value).isEqualTo(encodeValue(7.5))
    assertThat(evaluate(multiply(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(multiply(constant(1.32), constant(2.0))).value).isEqualTo(encodeValue(2.64))
  }

  @Test
  fun multiplyFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(multiply(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(multiply(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(multiply(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithDoubleLongMultiplicationOverflow() {
    assertThat(evaluate(multiply(constant(Long.MAX_VALUE), constant(100.0))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE.toDouble() * 100.0))
    assertThat(evaluate(multiply(constant(Long.MAX_VALUE), constant(100L))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithDoubleMultiplicationOverflow() {
    assertThat(evaluate(multiply(constant(Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(multiply(constant(-Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithLongMultiplicationOverflow() {
    assertThat(evaluate(multiply(constant(Long.MAX_VALUE), constant(10L))).isError).isTrue()
    assertThat(evaluate(multiply(constant(Long.MIN_VALUE), constant(10L))).isError).isTrue()
    assertThat(evaluate(multiply(constant(-10L), constant(Long.MAX_VALUE))).isError).isTrue()
    assertThat(evaluate(multiply(constant(-10L), constant(Long.MIN_VALUE))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(multiply(constant(1L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(1.0), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(-9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(-Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun multiplyFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(multiply(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(multiply(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(multiply(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(multiply(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(multiply(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithPositiveInfinityNegativeInfinityReturnsNegativeInfinity() {
    assertThat(
        evaluate(multiply(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(
        evaluate(multiply(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithMultiArgument() {
    assertThat(evaluate(multiply(multiply(constant(1L), constant(2L)), constant(3L))).value)
      .isEqualTo(encodeValue(6L))
    assertThat(evaluate(multiply(constant(1.0), multiply(constant(2L), constant(3L)))).value)
      .isEqualTo(encodeValue(6.0))
  }

  // --- Divide Tests (Ported) ---
  @Test
  fun divideFunctionTestWithBasicNumerics() {
    assertThat(evaluate(divide(constant(10L), constant(2L))).value).isEqualTo(encodeValue(5L))
    assertThat(evaluate(divide(constant(10L), constant(2.0))).value).isEqualTo(encodeValue(5.0))
    assertThat(evaluate(divide(constant(10.0), constant(3L))).value)
      .isEqualTo(encodeValue(10.0 / 3.0))
    assertThat(evaluate(divide(constant(10.0), constant(7.0))).value)
      .isEqualTo(encodeValue(10.0 / 7.0))
  }

  @Test
  fun divideFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(divide(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(divide(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(divide(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun divideFunctionTestWithLongDivision() {
    assertThat(evaluate(divide(constant(10L), constant(3L))).value).isEqualTo(encodeValue(3L))
    assertThat(evaluate(divide(constant(-10L), constant(3L))).value).isEqualTo(encodeValue(-3L))
    assertThat(evaluate(divide(constant(10L), constant(-3L))).value).isEqualTo(encodeValue(-3L))
    assertThat(evaluate(divide(constant(-10L), constant(-3L))).value).isEqualTo(encodeValue(3L))
  }

  @Test
  fun divideFunctionTestWithDoubleDivisionOverflow() {
    assertThat(evaluate(divide(constant(Double.MAX_VALUE), constant(0.5))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(divide(constant(-Double.MAX_VALUE), constant(0.5))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun divideFunctionTestWithByZero() {
    assertThat(evaluate(divide(constant(1L), constant(0L))).isError).isTrue()
    assertThat(evaluate(divide(constant(1.1), constant(0.0))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(divide(constant(1.1), constant(-0.0))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(divide(constant(0.0), constant(0.0))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun divideFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(divide(constant(1L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(1L))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(1.0), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(1.0))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun divideFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(divide(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun divideFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(divide(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(divide(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun divideFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(divide(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(divide(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun divideFunctionTestWithPositiveInfinityNegativeInfinityReturnsNan() {
    assertThat(
        evaluate(divide(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(
        evaluate(divide(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NaN))
  }

  // --- Mod Tests (Ported) ---
  @Test
  fun modFunctionTestWithDivisorZero() {
    assertThat(evaluate(mod(constant(42L), constant(0L))).isError).isTrue()
    assertThat(evaluate(mod(constant(42.0), constant(0.0))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(42.0), constant(-0.0))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun modFunctionTestWithDividendZeroReturnsZero() {
    assertThat(evaluate(mod(constant(0L), constant(42L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(0.0), constant(42.0))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(mod(constant(-0.0), constant(42.0))).value).isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun modFunctionTestWithLongPositivePositive() {
    assertThat(evaluate(mod(constant(10L), constant(3L))).value).isEqualTo(encodeValue(1L))
  }

  @Test
  fun modFunctionTestWithLongNegativeNegative() {
    assertThat(evaluate(mod(constant(-10L), constant(-3L))).value).isEqualTo(encodeValue(-1L))
  }

  @Test
  fun modFunctionTestWithLongPositiveNegative() {
    assertThat(evaluate(mod(constant(10L), constant(-3L))).value).isEqualTo(encodeValue(1L))
  }

  @Test
  fun modFunctionTestWithLongNegativePositive() {
    assertThat(evaluate(mod(constant(-10L), constant(3L))).value).isEqualTo(encodeValue(-1L))
  }

  @Test
  fun modFunctionTestWithDoublePositivePositive() {
    // 10.5 % 3.0 is exactly 1.5
    assertThat(evaluate(mod(constant(10.5), constant(3.0))).value).isEqualTo(encodeValue(1.5))
  }

  @Test
  fun modFunctionTestWithDoubleNegativeNegative() {
    val resultValue = evaluate(mod(constant(-7.3), constant(-1.8))).value
    assertThat(resultValue?.doubleValue).isWithin(1e-9).of(-0.1)
  }

  @Test
  fun modFunctionTestWithDoublePositiveNegative() {
    val resultValue = evaluate(mod(constant(9.8), constant(-2.5))).value
    assertThat(resultValue?.doubleValue).isWithin(1e-9).of(2.3)
  }

  @Test
  fun modFunctionTestWithDoubleNegativePositive() {
    val resultValue = evaluate(mod(constant(-7.5), constant(2.3))).value
    assertThat(resultValue?.doubleValue).isWithin(1e-9).of(-0.6)
  }

  @Test
  fun modFunctionTestWithLongPerfectlyDivisible() {
    assertThat(evaluate(mod(constant(10L), constant(5L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(-10L), constant(5L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(10L), constant(-5L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(-10L), constant(-5L))).value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun modFunctionTestWithDoublePerfectlyDivisible() {
    assertThat(evaluate(mod(constant(10.0), constant(2.5))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(mod(constant(10.0), constant(-2.5))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(mod(constant(-10.0), constant(2.5))).value).isEqualTo(encodeValue(-0.0))
    assertThat(evaluate(mod(constant(-10.0), constant(-2.5))).value).isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun modFunctionTestWithNonNumericsReturnError() {
    assertThat(evaluate(mod(constant(10L), constant("1"))).isError).isTrue()
    assertThat(evaluate(mod(constant("1"), constant(10L))).isError).isTrue()
    assertThat(evaluate(mod(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun modFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(mod(constant(1L), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(1.0), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun modFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(mod(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun modFunctionTestWithNumberPosInfinityReturnSelf() {
    assertThat(evaluate(mod(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(mod(constant(42.123), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(42.123))
    assertThat(evaluate(mod(constant(-99.9), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(-99.9))
  }

  @Test
  fun modFunctionTestWithPosInfinityNumberReturnNaN() {
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(42.123))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(-99.9))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun modFunctionTestWithNumberNegInfinityReturnSelf() {
    assertThat(evaluate(mod(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(mod(constant(42.123), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(42.123))
    assertThat(evaluate(mod(constant(-99.9), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(-99.9))
  }

  @Test
  fun modFunctionTestWithNegInfinityNumberReturnNaN() {
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(42.123))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(-99.9))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun modFunctionTestWithPosAndNegInfinityReturnNaN() {
    assertThat(
        evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY))).value
      )
      .isEqualTo(encodeValue(Double.NaN))
  }

  // --- Abs Tests ---
  @Test
  fun absFunctionTestWithLong() {
    assertThat(evaluate(abs(constant(-42L))).value).isEqualTo(encodeValue(42L))
    assertThat(evaluate(abs(constant(42L))).value).isEqualTo(encodeValue(42L))
  }

  @Test
  fun absFunctionTestWithLongZero() {
    assertThat(evaluate(abs(constant(0L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(abs(constant(-0L))).value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun absFunctionTestWithLongMinValue() {
    assertThat(evaluate(abs(constant(Long.MIN_VALUE))).isError).isTrue()
  }

  @Test
  fun absFunctionTestWithLongMaxValue() {
    assertThat(evaluate(abs(constant(Long.MAX_VALUE))).value).isEqualTo(encodeValue(Long.MAX_VALUE))
    assertThat(evaluate(abs(constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE))
  }

  @Test
  fun absFunctionTestWithDouble() {
    assertThat(evaluate(abs(constant(-42.1))).value).isEqualTo(encodeValue(42.1))
    assertThat(evaluate(abs(constant(42.1))).value).isEqualTo(encodeValue(42.1))
  }

  @Test
  fun absFunctionTestWithDoubleZero() {
    assertThat(evaluate(abs(constant(-0.0))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(abs(constant(0.0))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun absFunctionTestWithDoubleMinMaxValue() {
    assertThat(evaluate(abs(constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE))
    assertThat(evaluate(abs(constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE))
    assertThat(evaluate(abs(constant(Double.MIN_VALUE))).value)
      .isEqualTo(encodeValue(Double.MIN_VALUE))
    assertThat(evaluate(abs(constant(-Double.MIN_VALUE))).value)
      .isEqualTo(encodeValue(Double.MIN_VALUE))
  }

  @Test
  fun absFunctionTestWithInfinity() {
    assertThat(evaluate(abs(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(abs(constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun absFunctionTestWithNaN() {
    assertThat(evaluate(abs(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun absFunctionTestWithNonNumeric() {
    assertThat(evaluate(abs(constant("1"))).isError).isTrue()
  }

  // --- Exp Tests ---
  @Test
  fun expFunctionTestWithDouble() {
    assertThat(evaluate(exp(constant(2.0))).value).isEqualTo(encodeValue(kotlin.math.exp(2.0)))
  }

  @Test
  fun expFunctionTestWithInteger() {
    assertThat(evaluate(exp(constant(2))).value).isEqualTo(encodeValue(kotlin.math.exp(2.0)))
  }

  @Test
  fun expFunctionTestWithLong() {
    assertThat(evaluate(exp(constant(2L))).value).isEqualTo(encodeValue(kotlin.math.exp(2.0)))
  }

  @Test
  fun expFunctionTestWithZero() {
    assertThat(evaluate(exp(constant(0))).value).isEqualTo(encodeValue(1.0))
  }

  @Test
  fun expFunctionTestWithNegativeZero() {
    assertThat(evaluate(exp(constant(-0.0))).value).isEqualTo(encodeValue(1.0))
  }

  @Test
  fun expFunctionTestWithNegative() {
    assertThat(evaluate(exp(constant(-1))).value).isEqualTo(encodeValue(1 / E))
  }

  @Test
  fun expFunctionTestWithInfinity() {
    assertThat(evaluate(exp(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun expFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(exp(constant(Double.NEGATIVE_INFINITY))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun expFunctionTestWithNaN() {
    assertThat(evaluate(exp(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun expFunctionTestWithNegativeConstant() {
    assertThat(evaluate(exp(constant(-16.0))).value).isEqualTo(encodeValue(kotlin.math.exp(-16.0)))
  }

  @Test
  fun expFunctionTestWithDoubleOverflow() {
    assertThat(evaluate(exp(constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun expFunctionTestWithUnsupportedType() {
    assertThat(evaluate(exp(constant("foo"))).isError).isTrue()
  }

  // --- Ln Tests ---
  @Test
  fun lnFunctionTestWithDouble() {
    assertThat(evaluate(ln(constant(kotlin.math.exp(16.0)))).value).isEqualTo(encodeValue(16.0))
  }

  @Test
  fun lnFunctionTestWithInteger() {
    assertThat(evaluate(ln(constant(1))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun lnFunctionTestWithLong() {
    assertThat(evaluate(ln(constant(1L))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun lnFunctionTestWithZero() {
    assertThat(evaluate(ln(constant(0))).value).isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun lnFunctionTestWithNegativeZero() {
    assertThat(evaluate(ln(constant(-0.0))).value).isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun lnFunctionTestWithNegative() {
    assertThat(evaluate(ln(constant(-1))).isError).isTrue()
  }

  @Test
  fun lnFunctionTestWithInfinity() {
    assertThat(evaluate(ln(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun lnFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(ln(constant(Double.NEGATIVE_INFINITY))).isError).isTrue()
  }

  @Test
  fun lnFunctionTestWithNegativeConstant() {
    assertThat(evaluate(ln(constant(-16.0))).isError).isTrue()
  }

  @Test
  fun lnFunctionTestWithUnsupportedType() {
    assertThat(evaluate(ln(constant("foo"))).isError).isTrue()
  }

  // --- Log Tests ---
  @Test
  fun logFunctionTest() {
    assertThat(evaluate(log(constant(100.0), constant(10.0))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(log(constant(100), constant(10))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(log(constant(100L), constant(10L))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(log(constant(100.0), constant(0.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100.0), constant(-10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100.0), constant(1.0))).isError).isTrue()
    assertThat(evaluate(log(constant(0.0), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100), constant(1.0))).isError).isTrue()
    assertThat(evaluate(log(constant(-100.0), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant("foo"), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100.0), constant("bar"))).isError).isTrue()
  }

  @Test
  fun logFunctionTestWithInfiniteSemantics() {
    assertThat(evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(0.0))).isError).isTrue()
    assertThat(
        evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .isError
      )
      .isTrue()
    assertThat(
        evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .isError
      )
      .isTrue()
    assertThat(evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(0.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(log(constant(-10.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(log(constant(10.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(
        evaluate(log(constant(Double.POSITIVE_INFINITY), constant(Double.POSITIVE_INFINITY))).value
      )
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(0.01))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(0.99))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(1.1))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(10.0))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  // --- Log10 Tests ---
  @Test
  fun log10FunctionTestWithDouble() {
    assertThat(evaluate(log10(constant(100.0))).value).isEqualTo(encodeValue(2.0))
  }

  @Test
  fun log10FunctionTestWithInteger() {
    assertThat(evaluate(log10(constant(100))).value).isEqualTo(encodeValue(2.0))
  }

  @Test
  fun log10FunctionTestWithLong() {
    assertThat(evaluate(log10(constant(100L))).value).isEqualTo(encodeValue(2.0))
  }

  @Test
  fun log10FunctionTestWithZero() {
    assertThat(evaluate(log10(constant(0))).value).isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun log10FunctionTestWithNegativeZero() {
    assertThat(evaluate(log10(constant(-0.0))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun log10FunctionTestWithNegative() {
    assertThat(evaluate(log10(constant(-1))).isError).isTrue()
  }

  @Test
  fun log10FunctionTestWithInfinity() {
    assertThat(evaluate(log10(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun log10FunctionTestWithNegativeInfinity() {
    assertThat(evaluate(log10(constant(Double.NEGATIVE_INFINITY))).isError).isTrue()
  }

  @Test
  fun log10FunctionTestWithNegativeConstant() {
    assertThat(evaluate(log10(constant(-16.0))).isError).isTrue()
  }

  @Test
  fun log10FunctionTestWithUnsupportedType() {
    assertThat(evaluate(log10(constant("foo"))).isError).isTrue()
  }

  // --- Ceil Tests ---
  @Test
  fun ceilFunctionTestWithInteger() {
    assertThat(evaluate(ceil(constant(15))).value).isEqualTo(encodeValue(15L))
  }

  @Test
  fun ceilFunctionTestWithNegativeInteger() {
    assertThat(evaluate(ceil(constant(-1))).value).isEqualTo(encodeValue(-1L))
  }

  @Test
  fun ceilFunctionTestWithLong() {
    assertThat(evaluate(ceil(constant(15L))).value).isEqualTo(encodeValue(15L))
  }

  @Test
  fun ceilFunctionTestWithNegativeLong() {
    assertThat(evaluate(ceil(constant(-1L))).value).isEqualTo(encodeValue(-1L))
  }

  @Test
  fun ceilFunctionTestWithDouble() {
    assertThat(evaluate(ceil(constant(15.1))).value).isEqualTo(encodeValue(16.0))
  }

  @Test
  fun ceilFunctionTestWithNegativeDouble() {
    assertThat(evaluate(ceil(constant(-1.1))).value).isEqualTo(encodeValue(-1.0))
  }

  @Test
  fun ceilFunctionTestWithDoubleWholeNumber() {
    assertThat(evaluate(ceil(constant(15.0))).value).isEqualTo(encodeValue(15.0))
  }

  @Test
  fun ceilFunctionTestWithInvalidType() {
    assertThat(evaluate(ceil(constant("invalid"))).isError).isTrue()
  }

  @Test
  fun ceilFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(ceil(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun ceilFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(ceil(constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun ceilFunctionTestWithNaN() {
    assertThat(evaluate(ceil(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun ceilFunctionToNegativeZero() {
    assertThat(evaluate(ceil(constant(-0.4))).value).isEqualTo(encodeValue(-0.0))
  }

  // --- Floor Tests ---
  @Test
  fun floorFunctionTestWithInteger() {
    assertThat(evaluate(floor(constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(Integer.MIN_VALUE.toLong()))
    assertThat(evaluate(floor(constant(-15))).value).isEqualTo(encodeValue(-15L))
    assertThat(evaluate(floor(constant(0))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(floor(constant(15))).value).isEqualTo(encodeValue(15L))
    assertThat(evaluate(floor(constant(Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Integer.MAX_VALUE.toLong()))
  }

  @Test
  fun floorFunctionTestWithLong() {
    assertThat(evaluate(floor(constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(Long.MIN_VALUE))
    assertThat(evaluate(floor(constant(-15L))).value).isEqualTo(encodeValue(-15L))
    assertThat(evaluate(floor(constant(0L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(floor(constant(15L))).value).isEqualTo(encodeValue(15L))
    assertThat(evaluate(floor(constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE))
  }

  @Test
  fun floorFunctionTestWithDouble() {
    assertThat(evaluate(floor(constant(-15.0))).value).isEqualTo(encodeValue(-15.0))
    assertThat(evaluate(floor(constant(-0.4))).value).isEqualTo(encodeValue(-1.0))
    assertThat(evaluate(floor(constant(0.0))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(floor(constant(0.4))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(floor(constant(-0.0))).value).isEqualTo(encodeValue(-0.0))
    assertThat(evaluate(floor(constant(15.0))).value).isEqualTo(encodeValue(15.0))
    assertThat(evaluate(floor(constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE))
  }

  @Test
  fun floorFunctionTestWithNaN() {
    assertThat(evaluate(floor(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun floorFunctionTestWithInfinity() {
    assertThat(evaluate(floor(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(floor(constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun floorFunctionTestWithUnsupportedType() {
    assertThat(evaluate(floor(constant("foo"))).isError).isTrue()
  }

  // --- Pow Tests ---
  @Test
  fun powFunctionTest() {
    assertThat(evaluate(pow(constant(2), constant(3))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2L), constant(3))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2.0), constant(3))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2), constant(3L))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2L), constant(3L))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2.0), constant(3L))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2), constant(3.0))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2L), constant(3.0))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2.0), constant(3.0))).value).isEqualTo(encodeValue(8.0))

    assertThat(evaluate(pow(constant(2), constant(-3))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2L), constant(-3))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2.0), constant(-3))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2), constant(-3L))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2L), constant(-3L))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2.0), constant(-3L))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2), constant(-3.0))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2L), constant(-3.0))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2.0), constant(-3.0))).value).isEqualTo(encodeValue(1.0 / 8.0))

    assertThat(evaluate(pow(constant(-2), constant(-3))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2L), constant(-3))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2.0), constant(-3))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2), constant(-3L))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2L), constant(-3L))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2.0), constant(-3L))).value)
      .isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2), constant(-3.0))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2L), constant(-3.0))).value)
      .isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2.0), constant(-3.0))).value)
      .isEqualTo(encodeValue(-1.0 / 8.0))

    assertThat(evaluate(pow(constant(1.0), constant(2))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(2.5))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(-2))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(-2L))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(-2.5))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(Double.NaN))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(0), constant(2))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(0), constant(2L))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(0), constant(2.5))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(-0.0), constant(2))).value).isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(2), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(2L), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(2.5), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-2), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-2L), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-2.5), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(Double.NaN), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(0))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(0))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(2.0), constant(Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2.0), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2L), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(2.0), constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2.0), constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2.0), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2), constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2), constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun powFunctionTestWithInfiniteSemantics() {
    assertThat(evaluate(pow(constant(-1), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(-1), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1.0), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(0.5), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(0.0), constant(Double.NEGATIVE_INFINITY))).isError).isTrue()
    assertThat(evaluate(pow(constant(-0.0), constant(Double.NEGATIVE_INFINITY))).isError).isTrue()

    assertThat(evaluate(pow(constant(2), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2.5), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(0.5), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(2), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2.5), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(-2))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(-2L))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(-2.5))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3.0))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(2))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(2L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(2.0))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3.1))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(-2))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(-2L))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(-0.5))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(3))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(3L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(0.5))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun powFunctionTestWithErrorSemantics() {
    assertThat(evaluate(pow(constant(-1), constant(3.1))).isError).isTrue()
    assertThat(evaluate(pow(constant(-1L), constant(3.1))).isError).isTrue()
    assertThat(evaluate(pow(constant(-0.5), constant(3.1))).isError).isTrue()

    assertThat(evaluate(pow(constant(0), constant(-2))).isError).isTrue()
    assertThat(evaluate(pow(constant(0), constant(-2L))).isError).isTrue()
    assertThat(evaluate(pow(constant(0), constant(-2.5))).isError).isTrue()
    assertThat(evaluate(pow(constant(-0.0), constant(-2))).isError).isTrue()

    assertThat(evaluate(pow(constant(Double.NaN), constant(3))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(3L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(3.1))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(-3))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(-3L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(-3.1))).value)
      .isEqualTo(encodeValue(Double.NaN))

    assertThat(evaluate(pow(constant(2), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(2L), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(2.5), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(0), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-0.0), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-2), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-2L), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-2.5), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))

    assertThat(evaluate(pow(constant("abc"), constant(3))).isError).isTrue()
    assertThat(evaluate(pow(constant(3L), constant("abc"))).isError).isTrue()
  }

  // --- Round Tests ---
  @Test
  fun roundFunctionTest() {
    assertThat(evaluate(round(constant(15.48924))).value).isEqualTo(encodeValue(15.0))
  }

  @Test
  fun roundFunctionTestWithZero() {
    assertThat(evaluate(round(constant(0L))).value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun roundFunctionTestPositiveHalfway() {
    assertThat(evaluate(round(constant(15.5))).value).isEqualTo(encodeValue(16.0))
  }

  @Test
  fun roundFunctionTestNegativeHalfway() {
    assertThat(evaluate(round(constant(-15.5))).value).isEqualTo(encodeValue(-16.0))
  }

  @Test
  fun roundFunctionTestWithMaxDouble() {
    assertThat(evaluate(round(constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE))
  }

  @Test
  fun roundFunctionTestWithMaxNegativeDouble() {
    assertThat(evaluate(round(constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(-Double.MAX_VALUE))
  }

  @Test
  fun roundFunctionTestWithLong() {
    assertThat(evaluate(round(constant(0L))).value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun roundFunctionTestWithMaxLong() {
    assertThat(evaluate(round(constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE))
  }

  @Test
  fun roundFunctionTestWithMaxLongNegative() {
    assertThat(evaluate(round(constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(-Long.MAX_VALUE))
  }

  @Test
  fun roundFunctionTestWithInteger() {
    assertThat(evaluate(round(constant(15))).value).isEqualTo(encodeValue(15L))
  }

  @Test
  fun roundFunctionTestWithMaxInteger() {
    assertThat(evaluate(round(constant(Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Integer.MAX_VALUE.toLong()))
  }

  @Test
  fun roundFunctionTestWithMaxIntegerNegative() {
    assertThat(evaluate(round(constant(-Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue((-Integer.MAX_VALUE).toLong()))
  }

  @Test
  fun roundFunctionTestWithInfinity() {
    assertThat(evaluate(round(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun roundFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(round(constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun roundFunctionTestWithNaN() {
    assertThat(evaluate(round(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun roundFunctionTestWithZeroDouble() {
    assertThat(evaluate(round(constant(0.0))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun roundFunctionTestWithNegativeZero() {
    assertThat(evaluate(round(constant(-0.0))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun roundFunctionTestWithUnknownValueType() {
    assertThat(evaluate(round(constant("foo"))).isError).isTrue()
  }

  // --- Sqrt Tests ---
  @Test
  fun sqrtFunctionTestWithInteger() {
    assertThat(evaluate(sqrt(constant(16))).value).isEqualTo(encodeValue(4.0))
  }

  @Test
  fun sqrtFunctionTestWithNegativeInteger() {
    assertThat(evaluate(sqrt(constant(-16))).isError).isTrue()
  }

  @Test
  fun sqrtFunctionTestWithLong() {
    assertThat(evaluate(sqrt(constant(16L))).value).isEqualTo(encodeValue(4.0))
  }

  @Test
  fun sqrtFunctionTestWithNegativeLong() {
    assertThat(evaluate(sqrt(constant(-16L))).isError).isTrue()
  }

  @Test
  fun sqrtFunctionTestWithDouble() {
    assertThat(evaluate(sqrt(constant(16.0))).value).isEqualTo(encodeValue(4.0))
  }

  @Test
  fun sqrtFunctionTestWithNegativeDouble() {
    assertThat(evaluate(sqrt(constant(-16.0))).isError).isTrue()
  }

  @Test
  fun sqrtFunctionTestWithZeroDouble() {
    assertThat(evaluate(sqrt(constant(0.0))).value).isEqualTo(encodeValue(0.0))
  }

  @Test
  fun sqrtFunctionTestWithNegativeZeroDouble() {
    assertThat(evaluate(sqrt(constant(-0.0))).value).isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun sqrtFunctionTestWithInfinity() {
    assertThat(evaluate(sqrt(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun sqrtFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(sqrt(constant(Double.NEGATIVE_INFINITY))).isError).isTrue()
  }

  @Test
  fun sqrtFunctionTestWithNaN() {
    assertThat(evaluate(sqrt(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun sqrtFunctionTestWithUnsupportedType() {
    assertThat(evaluate(sqrt(constant("foo"))).isError).isTrue()
  }
}
