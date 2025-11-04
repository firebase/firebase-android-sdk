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
import com.google.firebase.firestore.pipeline.Expression.Companion.abs
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class AbsTests {
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
}
