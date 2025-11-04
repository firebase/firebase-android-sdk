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
import com.google.firebase.firestore.pipeline.Expression.Companion.divide
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class DivideTests {
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
}
