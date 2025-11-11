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

package com.google.firebase.firestore.pipeline.evaluation.arithmetic

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.model.Values.encodeValue
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.mod
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class ModTests {
  @Test
  fun modFunctionTestWithMirrorError() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = mod(left, right)
      assertEvaluatesToNull(evaluate(expr), "mod($name)")
    }
  }

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
    assertThat(evaluate(mod(constant(Long.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Long.MIN_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Double.MIN_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
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
}
