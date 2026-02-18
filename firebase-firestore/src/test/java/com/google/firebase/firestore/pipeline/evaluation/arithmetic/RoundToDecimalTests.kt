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
import com.google.firebase.firestore.pipeline.Expression.Companion.roundToPrecision
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.pipeline.evaluation.MirroringTestCases
import org.junit.Test

internal class RoundToDecimalTests {

  @Test
  fun roundToPrecisionMirrorsError() {
    for ((name, left, right) in MirroringTestCases.BINARY_MIRROR_TEST_CASES) {
      val expr = roundToPrecision(left, right)
      assertEvaluatesToNull(evaluate(expr), "roundToPrecision($name)")
    }
  }

  @Test
  fun roundToDecimalFunctionTestOnZero() {
    assertThat(evaluate(roundToPrecision(constant(0L), constant(0L))).value)
      .isEqualTo(encodeValue(0L))
  }

  @Test
  fun roundToDecimalFunctionTestPositiveHalfwayRoundsUp() {
    assertThat(evaluate(roundToPrecision(constant(15.5), constant(0L))).value)
      .isEqualTo(encodeValue(16.0))
  }

  @Test
  fun roundToDecimalFunctionTestNegativeHalfwayRoundsDown() {
    assertThat(evaluate(roundToPrecision(constant(-15.5), constant(0L))).value)
      .isEqualTo(encodeValue(-16.0))
  }

  @Test
  fun roundToDecimalFunctionTestPositiveDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15.48924), constant(1))).value)
      .isEqualTo(encodeValue(15.5))
  }

  @Test
  fun roundToDecimalFunctionTestPositiveDecimalPlacesLong() {
    assertThat(evaluate(roundToPrecision(constant(-15.48924), constant(2L))).value)
      .isEqualTo(encodeValue(-15.49))
  }

  @Test
  fun roundToDecimalFunctionTestLargePositiveDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15.48924), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(15.48924))
  }

  @Test
  fun roundToDecimalFunctionTestNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15.48924), constant(-1))).value)
      .isEqualTo(encodeValue(20.0))
  }

  @Test
  fun roundToDecimalFunctionTestLargeNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15.48924), constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun roundToDecimalFunctionTestLargeNegativeDecimalPlacesNegativeValue() {
    assertThat(evaluate(roundToPrecision(constant(-15.48924), constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun roundToDecimalFunctionTestZeroDecimalPlacesRoundsWhole() {
    assertThat(evaluate(roundToPrecision(constant(-15.48924), constant(0L))).value)
      .isEqualTo(encodeValue(-15.0))
  }

  @Test
  fun roundToDecimalFunctionTestLargeValueAndDecimalPlaces() {
    assertThat(
        evaluate(roundToPrecision(constant(Double.MAX_VALUE), constant(Long.MAX_VALUE))).value
      )
      .isEqualTo(encodeValue(Double.MAX_VALUE))
  }

  @Test
  fun roundToDecimalFunctionTestLargeValueAndSmallDecimalPlaces() {
    assertThat(
        evaluate(roundToPrecision(constant(Double.MAX_VALUE), constant(-Long.MAX_VALUE))).value
      )
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun roundToDecimalFunctionTestRoundMaxDouble() {
    assertThat(evaluate(roundToPrecision(constant(Double.MAX_VALUE), constant(0))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE))
  }

  @Test
  fun roundToDecimalFunctionTestRoundMaxNegativeDouble() {
    assertThat(evaluate(roundToPrecision(constant(-Double.MAX_VALUE), constant(0))).value)
      .isEqualTo(encodeValue(-Double.MAX_VALUE))
  }

  @Test
  fun roundToDecimalFunctionTestRoundMaxDoubleOverflow() {
    assertThat(evaluate(roundToPrecision(constant(Double.MAX_VALUE), constant(-307))).isError)
      .isTrue()
  }

  @Test
  fun roundToDecimalFunctionTestRoundMaxNegativeDoubleOverflow() {
    assertThat(evaluate(roundToPrecision(constant(-Double.MAX_VALUE), constant(-307))).isError)
      .isTrue()
  }

  @Test
  fun roundToDecimalFunctionTestRoundLong() {
    assertThat(evaluate(roundToPrecision(constant(15L), constant(0))).value)
      .isEqualTo(encodeValue(15L))
  }

  @Test
  fun roundToDecimalFunctionTestRoundNegativeLong() {
    assertThat(evaluate(roundToPrecision(constant(-15L), constant(0))).value)
      .isEqualTo(encodeValue(-15L))
  }

  @Test
  fun roundToDecimalFunctionTestRoundLongNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15L), constant(-1))).value)
      .isEqualTo(encodeValue(20L))
  }

  @Test
  fun roundToDecimalFunctionTestRoundLongLargeNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15L), constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0L))
  }

  @Test
  fun roundToDecimalFunctionTestRoundLongOverflow() {
    assertThat(evaluate(roundToPrecision(constant(Long.MAX_VALUE), constant(-1))).isError).isTrue()
  }

  @Test
  fun roundToDecimalFunctionTestRoundLongNegativeOverflow() {
    assertThat(evaluate(roundToPrecision(constant(-Long.MAX_VALUE), constant(-1))).isError).isTrue()
  }

  @Test
  fun roundToDecimalFunctionTestRoundMaxLong() {
    assertThat(evaluate(roundToPrecision(constant(Long.MAX_VALUE), constant(0))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE))
  }

  @Test
  fun roundToDecimalFunctionTestRoundMinLong() {
    assertThat(evaluate(roundToPrecision(constant(-Long.MAX_VALUE), constant(0))).value)
      .isEqualTo(encodeValue(-Long.MAX_VALUE))
  }

  @Test
  fun roundToDecimalFunctionTestRoundLongPositiveDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15L), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(15L))
  }

  @Test
  fun roundToDecimalFunctionTestRoundInteger() {
    assertThat(evaluate(roundToPrecision(constant(15), constant(0))).value)
      .isEqualTo(encodeValue(15))
  }

  @Test
  fun roundToDecimalFunctionTestRoundNegativeInteger() {
    assertThat(evaluate(roundToPrecision(constant(-15), constant(0))).value)
      .isEqualTo(encodeValue(-15))
  }

  @Test
  fun roundToDecimalFunctionTestRoundIntegerNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15), constant(-1))).value)
      .isEqualTo(encodeValue(20))
  }

  @Test
  fun roundToDecimalFunctionTestRoundNegativeIntegerNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(-15), constant(-1))).value)
      .isEqualTo(encodeValue(-20))
  }

  @Test
  fun roundToDecimalFunctionTestRoundIntegerLargeNegativeDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15), constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0))
  }

  @Test
  fun roundToDecimalFunctionTestRoundIntegerOverflow() {
    assertThat(evaluate(roundToPrecision(constant(Long.MAX_VALUE), constant(-1))).isError).isTrue()
  }

  @Test
  fun roundToDecimalFunctionTestRoundIntegerPositiveDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(15))
  }

  @Test
  fun roundToDecimalFunctionTestRoundInfinity() {
    assertThat(
        evaluate(roundToPrecision(constant(Double.POSITIVE_INFINITY), constant(-Long.MAX_VALUE)))
          .value
      )
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun roundToDecimalFunctionTestRoundNaN() {
    assertThat(evaluate(roundToPrecision(constant(Double.NaN), constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun roundToDecimalFunctionTestMaxLongDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15.3924532), constant(Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(15.3924532))
  }

  @Test
  fun roundToDecimalFunctionTestMaxNegativeLongDecimalPlaces() {
    assertThat(evaluate(roundToPrecision(constant(15.3924532), constant(-Long.MAX_VALUE))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun roundToDecimalFunctionTestNearMinDouble() {
    assertThat(evaluate(roundToPrecision(constant(Double.MAX_VALUE - 1), constant(-10))).value)
      .isEqualTo(encodeValue(Double.MAX_VALUE - 1))
  }

  @Test
  fun roundToDecimalFunctionTestNearMaxDouble() {
    assertThat(evaluate(roundToPrecision(constant(-Double.MAX_VALUE + 1), constant(10))).value)
      .isEqualTo(encodeValue(-Double.MAX_VALUE + 1))
  }

  @Test
  fun roundToDecimalFunctionTestRoundUnknownValueType() {
    assertThat(evaluate(roundToPrecision(constant("foo"), constant(-Long.MAX_VALUE))).isError)
      .isTrue()
  }

  @Test
  fun roundToDecimalFunctionTestRoundInvalidDecimalPlacesType() {
    assertThat(evaluate(roundToPrecision(constant(15.3924532), constant("foo"))).isError).isTrue()
  }
}
