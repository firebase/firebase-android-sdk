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
import com.google.firebase.firestore.pipeline.Expression.Companion.floor
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class FloorTests {

  @Test
  fun floorMirrorsErrors() {
    for (testCase in MirroringTestCases.UNARY_MIRROR_TEST_CASES) {
      assertEvaluatesToNull(evaluate(floor(testCase.input)), "floor(${'$'}{testCase.name})")
    }
  }

  @Test
  fun floorFunctionTestWithInteger() {
    assertThat(evaluate(floor(constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(Integer.MIN_VALUE.toLong()))
    assertThat(evaluate(floor(constant(-15))).value).isEqualTo(encodeValue(-15L))
    assertThat(evaluate(floor(constant(0))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(floor(constant(15))).value).isEqualTo(encodeValue(15L))
    assertThat(evaluate(floor(constant(Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Integer.MAX_VALUE.toLong()))
  }

  @Test
  fun floorFunctionTestWithLong() {
    assertThat(evaluate(floor(constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(Long.MIN_VALUE))
    assertThat(evaluate(floor(constant(-15L))).value).isEqualTo(encodeValue(-15L))
    assertThat(evaluate(floor(constant(0L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(floor(constant(15L))).value).isEqualTo(encodeValue(15L))
    assertThat(evaluate(floor(constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE))
  }

  @Test
  fun floorFunctionTestWithDouble() {
    assertThat(evaluate(floor(constant(-15.0))).value).isEqualTo(encodeValue(-15.0))
    assertThat(evaluate(floor(constant(-0.4))).value).isEqualTo(encodeValue(-1.0))
    assertThat(evaluate(floor(constant(0.0))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(floor(constant(0.4))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(floor(constant(-0.0))).value).isEqualTo(encodeValue(-0.0))
    assertThat(evaluate(floor(constant(15.0))).value).isEqualTo(encodeValue(15.0))
    assertThat(evaluate(floor(constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE))
  }

  @Test
  fun floorFunctionTestWithNaN() {
    assertThat(evaluate(floor(constant(Double.NaN))).value).isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun floorFunctionTestWithInfinity() {
    assertThat(evaluate(floor(constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(floor(constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun floorFunctionTestWithUnsupportedType() {
    assertThat(evaluate(floor(constant("foo"))).isError).isTrue()
  }
}
