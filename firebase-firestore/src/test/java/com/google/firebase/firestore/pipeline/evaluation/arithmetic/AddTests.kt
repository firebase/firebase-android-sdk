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
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class AddTests {
  @Test
  fun addMirrorsErrors() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = add(left, right)
      assertEvaluatesToNull(evaluate(expr), "add($name)")
    }
  }

  @Test
  fun addFunctionTestWithBasicNumerics() {
    assertThat(evaluate(add(constant(1L), constant(2L))).value).isEqualTo(encodeValue(3L))
    assertThat(evaluate(add(constant(1L), constant(2.5))).value).isEqualTo(encodeValue(3.5))
    assertThat(evaluate(add(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(3.0))
    assertThat(evaluate(add(constant(1.0), constant(2.0))).value).isEqualTo(encodeValue(3.0))
  }

  @Test
  fun addFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(add(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(add(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(add(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun addFunctionTestWithDoubleLongAdditionOverflow() {
    assertThat(evaluate(add(constant(Long.MAX_VALUE), constant(1.0))).value)
      .isEqualTo(encodeValue(9.223372036854776E18))
    assertThat(evaluate(add(constant(Long.MAX_VALUE.toDouble()), constant(100L))).value)
      .isEqualTo(encodeValue(9.223372036854776E18))
  }

  @Test
  fun addFunctionTestWithDoubleAdditionOverflow() {
    assertThat(evaluate(add(constant(Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(add(constant(-Double.MAX_VALUE), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun addFunctionTestWithSumPosAndNegInfinityReturnNaN() {
    assertThat(
        evaluate(add(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY))).value
      )
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun addFunctionTestWithLongAdditionOverflow() {
    assertThat(evaluate(add(constant(Long.MAX_VALUE), constant(1L))).isError).isTrue()
    assertThat(evaluate(add(constant(Long.MIN_VALUE), constant(-1L))).isError).isTrue()
    assertThat(evaluate(add(constant(1L), constant(Long.MAX_VALUE))).isError).isTrue()
  }

  @Test
  fun addFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(add(constant(1L), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(1.0), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Long.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Long.MIN_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.MIN_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun addFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(add(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun addFunctionTestWithMultiArgument() {
    assertThat(evaluate(add(add(constant(1L), constant(2L)), constant(3L))).value)
      .isEqualTo(encodeValue(6L))
    assertThat(evaluate(add(add(constant(1.0), constant(2L)), constant(3L))).value)
      .isEqualTo(encodeValue(6.0))
  }
}
