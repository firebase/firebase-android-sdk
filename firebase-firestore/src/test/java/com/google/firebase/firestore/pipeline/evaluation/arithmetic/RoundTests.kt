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
import com.google.firebase.firestore.pipeline.Expression.Companion.round
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class RoundTests {

  @Test
  fun roundMirrorsErrors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(round(testCase.input)), "round(${'$'}{testCase.name})")
    }
  }

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
    assertThat(evaluate(round(constant(15))).value).isEqualTo(encodeValue(15))
  }

  @Test
  fun roundFunctionTestWithMaxInteger() {
    assertThat(evaluate(round(constant(Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Integer.MAX_VALUE))
  }

  @Test
  fun roundFunctionTestWithMaxIntegerNegative() {
    assertThat(evaluate(round(constant(-Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(-Integer.MAX_VALUE))
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
}
