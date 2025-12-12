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
import com.google.firebase.firestore.pipeline.Expression.Companion.log10
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class Log10Tests {
  @Test
  fun log10MirrorsErrors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(log10(testCase.input)), "log10(${'$'}{testCase.name})")
    }
  }

  @Test
  fun log10FunctionTestWithNaN() {
    assertThat(evaluate(log10(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

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
    assertThat(evaluate(log10(constant(0))).isError).isTrue()
  }

  @Test
  fun log10FunctionTestWithNegativeZero() {
    assertThat(evaluate(log10(constant(-0.0))).isError).isTrue()
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
}
