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
import com.google.firebase.firestore.pipeline.Expression.Companion.ceil
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class CeilTests {
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
}
