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
import com.google.firebase.firestore.pipeline.Expression.Companion.exp
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import kotlin.math.E
import org.junit.Test

internal class ExpTests {
  @Test
  fun expMirrorsErrors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(exp(testCase.input)), "exp(${'$'}{testCase.name})")
    }
  }

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
    assertThat(evaluate(exp(constant(Double.MAX_VALUE))).isError).isTrue()
  }

  @Test
  fun expFunctionTestWithUnsupportedType() {
    assertThat(evaluate(exp(constant("foo"))).isError).isTrue()
  }
}
