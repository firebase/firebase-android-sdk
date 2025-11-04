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
import com.google.firebase.firestore.pipeline.Expression.Companion.pow
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class PowTests {
  @Test
  fun powFunctionTest() {
    assertThat(evaluate(pow(constant(2), constant(3))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2L), constant(3))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2.0), constant(3))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2), constant(3L))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2L), constant(3L))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2.0), constant(3L))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2), constant(3.0))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2L), constant(3.0))).value).isEqualTo(encodeValue(8.0))
    assertThat(evaluate(pow(constant(2.0), constant(3.0))).value).isEqualTo(encodeValue(8.0))

    assertThat(evaluate(pow(constant(2), constant(-3))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2L), constant(-3))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2.0), constant(-3))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2), constant(-3L))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2L), constant(-3L))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2.0), constant(-3L))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2), constant(-3.0))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2L), constant(-3.0))).value).isEqualTo(encodeValue(1.0 / 8.0))
    assertThat(evaluate(pow(constant(2.0), constant(-3.0))).value).isEqualTo(encodeValue(1.0 / 8.0))

    assertThat(evaluate(pow(constant(-2), constant(-3))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2L), constant(-3))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2.0), constant(-3))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2), constant(-3L))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2L), constant(-3L))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2.0), constant(-3L))).value)
      .isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2), constant(-3.0))).value).isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2L), constant(-3.0))).value)
      .isEqualTo(encodeValue(-1.0 / 8.0))
    assertThat(evaluate(pow(constant(-2.0), constant(-3.0))).value)
      .isEqualTo(encodeValue(-1.0 / 8.0))

    assertThat(evaluate(pow(constant(1.0), constant(2))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(2.5))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(-2))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(-2L))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(-2.5))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(Double.NaN))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(1.0), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(0), constant(2))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(0), constant(2L))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(0), constant(2.5))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(-0.0), constant(2))).value).isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(2), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(2L), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(2.5), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-2), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-2L), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-2.5), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(Double.NaN), constant(0))).value).isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(0))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(0))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(2.0), constant(Integer.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2.0), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2L), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(2.0), constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2.0), constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2.0), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2), constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2), constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(Integer.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(Long.MIN_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(-Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun powFunctionTestWithInfiniteSemantics() {
    assertThat(evaluate(pow(constant(-1), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(-1), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(pow(constant(-1.0), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))

    assertThat(evaluate(pow(constant(0.5), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(0.0), constant(Double.NEGATIVE_INFINITY))).isError).isTrue()
    assertThat(evaluate(pow(constant(-0.0), constant(Double.NEGATIVE_INFINITY))).isError).isTrue()

    assertThat(evaluate(pow(constant(2), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(2.5), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(0.5), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(2), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(2.5), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(-2))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(-2L))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(-2.5))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3.0))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(2))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(2L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(2.0))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.NEGATIVE_INFINITY), constant(3.1))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))

    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(-2))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(-2L))).value)
      .isEqualTo(encodeValue(0.0))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(-0.5))).value)
      .isEqualTo(encodeValue(0.0))

    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(3))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(3L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(pow(constant(Double.POSITIVE_INFINITY), constant(0.5))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun powFunctionTestWithErrorSemantics() {
    assertThat(evaluate(pow(constant(-1), constant(3.1))).isError).isTrue()
    assertThat(evaluate(pow(constant(-1L), constant(3.1))).isError).isTrue()
    assertThat(evaluate(pow(constant(-0.5), constant(3.1))).isError).isTrue()

    assertThat(evaluate(pow(constant(0), constant(-2))).isError).isTrue()
    assertThat(evaluate(pow(constant(0), constant(-2L))).isError).isTrue()
    assertThat(evaluate(pow(constant(0), constant(-2.5))).isError).isTrue()
    assertThat(evaluate(pow(constant(-0.0), constant(-2))).isError).isTrue()

    assertThat(evaluate(pow(constant(Double.NaN), constant(3))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(3L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(3.1))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(-3))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(-3L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(Double.NaN), constant(-3.1))).value)
      .isEqualTo(encodeValue(Double.NaN))

    assertThat(evaluate(pow(constant(2), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(2L), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(2.5), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(0), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-0.0), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-2), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-2L), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(pow(constant(-2.5), constant(Double.NaN))).value)
      .isEqualTo(encodeValue(Double.NaN))

    assertThat(evaluate(pow(constant("abc"), constant(3))).isError).isTrue()
    assertThat(evaluate(pow(constant(3L), constant("abc"))).isError).isTrue()
  }
}
