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
import com.google.firebase.firestore.pipeline.Expression.Companion.ln
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class LnTests {
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
}
