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
import com.google.firebase.firestore.pipeline.Expression.Companion.sqrt
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class SqrtTests {
  @Test
  fun sqrtMirrorsErrors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(sqrt(testCase.input)), "sqrt(${'$'}{testCase.name})")
    }
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
