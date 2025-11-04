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
import com.google.firebase.firestore.pipeline.Expression.Companion.multiply
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class MultiplyTests {
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
}
