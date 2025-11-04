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
import com.google.firebase.firestore.pipeline.Expression.Companion.log
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test

internal class LogTests {
  @Test
  fun logFunctionTest() {
    assertThat(evaluate(log(constant(100.0), constant(10.0))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(log(constant(100), constant(10))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(log(constant(100L), constant(10L))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(log(constant(100.0), constant(0.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100.0), constant(-10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100.0), constant(1.0))).isError).isTrue()
    assertThat(evaluate(log(constant(0.0), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100), constant(1.0))).isError).isTrue()
    assertThat(evaluate(log(constant(-100.0), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant("foo"), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(100.0), constant("bar"))).isError).isTrue()
  }

  @Test
  fun logFunctionTestWithInfiniteSemantics() {
    assertThat(evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(0.0))).isError).isTrue()
    assertThat(
        evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .isError
      )
      .isTrue()
    assertThat(
        evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .isError
      )
      .isTrue()
    assertThat(evaluate(log(constant(Double.NEGATIVE_INFINITY), constant(10.0))).isError).isTrue()
    assertThat(evaluate(log(constant(0.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(log(constant(-10.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(log(constant(10.0), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(
        evaluate(log(constant(Double.POSITIVE_INFINITY), constant(Double.POSITIVE_INFINITY))).value
      )
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(0.01))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(0.99))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(1.1))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(log(constant(Double.POSITIVE_INFINITY), constant(10.0))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }
}
