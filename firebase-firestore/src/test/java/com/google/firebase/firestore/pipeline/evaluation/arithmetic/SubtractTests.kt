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
import com.google.firebase.firestore.pipeline.Expression.Companion.subtract
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class SubtractTests {
  @Test
  fun subtractMirrorsErrors() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = subtract(left, right)
      assertEvaluatesToNull(evaluate(expr), "subtract($name)")
    }
  }

  @Test
  fun subtractFunctionTestWithBasicNumerics() {
    assertThat(evaluate(subtract(constant(1L), constant(2L))).value).isEqualTo(encodeValue(-1L))
    assertThat(evaluate(subtract(constant(1L), constant(2.5))).value).isEqualTo(encodeValue(-1.5))
    assertThat(evaluate(subtract(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(-1.0))
    assertThat(evaluate(subtract(constant(1.0), constant(2.0))).value).isEqualTo(encodeValue(-1.0))
  }

  @Test
  fun subtractFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(subtract(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(subtract(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(subtract(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun subtractFunctionTestWithDoubleLongSubtractionOverflow() {
    assertThat(evaluate(subtract(constant(Long.MIN_VALUE), constant(1.0))).value)
      .isEqualTo(encodeValue(-9.223372036854776E18))
    assertThat(evaluate(subtract(constant(Long.MIN_VALUE.toDouble()), constant(100L))).value)
      .isEqualTo(encodeValue(-9.223372036854776E18))
  }

  @Test
  fun subtractFunctionTestWithDoubleSubtractionOverflow() {
    assertThat(evaluate(subtract(constant(-Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(subtract(constant(Double.MAX_VALUE), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun subtractFunctionTestWithLongSubtractionOverflow() {
    assertThat(evaluate(subtract(constant(Long.MIN_VALUE), constant(1L))).isError).isTrue()
    assertThat(evaluate(subtract(constant(Long.MAX_VALUE), constant(-1L))).isError).isTrue()
  }

  @Test
  fun subtractFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(subtract(constant(1L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(1.0), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(-9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(-Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(subtract(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun subtractFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(subtract(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun subtractFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(subtract(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(subtract(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun subtractFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(subtract(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(subtract(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun subtractFunctionTestWithPositiveInfinityNegativeInfinity() {
    assertThat(
        evaluate(subtract(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(
        evaluate(subtract(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }
}
