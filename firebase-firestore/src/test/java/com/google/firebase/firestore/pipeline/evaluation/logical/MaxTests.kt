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

package com.google.firebase.firestore.pipeline.evaluation.logical

import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.Expression.Companion.array
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.logicalMaximum
import com.google.firebase.firestore.pipeline.Expression.Companion.map
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MaxTests {
  private val errorExpr1 = Expression.error("error-1")
  private val errorExpr2 = Expression.error("error-2")
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  private data class VariadicValueTestCase(
    val inputs: List<Expression>,
    val expected: Expression,
  )
  private data class EqualValues(val left: Expression, val right: Expression)

  private val generalCases =
    listOf(
      // Testing the relative priority of different types, following TypeComparator.
      // Boolean(2) < Number(3) < String(6) < Array(14) < Map(16). Null/Unset always have the
      // lowest priority.
      VariadicValueTestCase(listOf(constant(true), nullValue()), constant(true)),
      VariadicValueTestCase(listOf(nullValue(), constant(true)), constant(true)), // for UNSET
      VariadicValueTestCase(listOf(constant(0L), constant(false)), constant(0L)),
      VariadicValueTestCase(listOf(constant(0.0), constant(true)), constant(0.0)),
      VariadicValueTestCase(listOf(constant(""), constant(0L)), constant("")),
      VariadicValueTestCase(listOf(constant(0.0), constant("foo")), constant("foo")),
      VariadicValueTestCase(listOf(array(2, 3), constant("foo")), array(2, 3)),
      VariadicValueTestCase(
        listOf(map(emptyMap<String, Any>()), array(emptyList<Any>())),
        map(emptyMap<String, Any>())
      ),
      VariadicValueTestCase(
        listOf(array(emptyList<Any>()), map(emptyMap<String, Any>())),
        map(emptyMap<String, Any>())
      ),

      // Testing numeric comparisons are equal across types.
      VariadicValueTestCase(listOf(constant(1.0), constant(2L)), constant(2L)),
      VariadicValueTestCase(listOf(constant(1.1), constant(1L)), constant(1.1)),
      VariadicValueTestCase(listOf(constant(-20), constant(4.24)), constant(4.24)),
      VariadicValueTestCase(listOf(constant(1L), constant(2.0), constant(3L)), constant(3L)),
      VariadicValueTestCase(listOf(constant(2.5), constant(2.6)), constant(2.6)), // Decimal128
      VariadicValueTestCase(
        listOf(constant(Double.NEGATIVE_INFINITY), constant(Long.MIN_VALUE)),
        constant(Long.MIN_VALUE)
      ),
      VariadicValueTestCase(
        listOf(constant(Double.POSITIVE_INFINITY), constant(Long.MAX_VALUE)),
        constant(Double.POSITIVE_INFINITY)
      ),

      // Testing comparisons within the same type.
      VariadicValueTestCase(listOf(constant(true), constant(false)), constant(true)),
      VariadicValueTestCase(listOf(constant(1L), constant(0L)), constant(1L)),
      VariadicValueTestCase(listOf(constant(1), constant(2), constant(3)), constant(3)),
      VariadicValueTestCase(listOf(constant(-0.4), constant(0.0)), constant(0.0)),
      VariadicValueTestCase(listOf(constant("b"), constant("a")), constant("b")),
      VariadicValueTestCase(listOf(constant("b"), constant("aaaa")), constant("b")),
      VariadicValueTestCase(listOf(nullValue(), nullValue()), nullValue()),
      VariadicValueTestCase(listOf(nullValue(), nullValue()), nullValue()), // for UNSET

      // List comparison is based on the comparison of the first elements, or size as a
      // tie-breaker.
      VariadicValueTestCase(listOf(array(2), array(1)), array(2)),
      VariadicValueTestCase(listOf(array(2), array(1, 1, 2, 3, 4)), array(2)),
      VariadicValueTestCase(listOf(array(2, 3), array(2, 2)), array(2, 3)),
      VariadicValueTestCase(listOf(array(2), array(2, -10)), array(2, -10)),

      // Map comparison is based on the comparison of the smallest keys and their values, or
      // size as a tie-breaker.
      VariadicValueTestCase(
        listOf(map(mapOf("b" to 1)), map(mapOf("a" to 10))),
        map(mapOf("b" to 1))
      ),
      VariadicValueTestCase(
        listOf(map(mapOf("b" to 1)), map(mapOf("b" to 0))),
        map(mapOf("b" to 1))
      ),
      VariadicValueTestCase(
        listOf(map(mapOf("b" to 1, "c" to 2)), map(mapOf("a" to 3, "b" to 5))),
        map(mapOf("b" to 1, "c" to 2))
      ),
      VariadicValueTestCase(
        listOf(map(mapOf("b" to 1, "a" to 1)), map(mapOf("a" to 3, "b" to 0))),
        map(mapOf("a" to 3, "b" to 0))
      ),
      VariadicValueTestCase(
        listOf(map(mapOf("b" to 1, "a" to 2)), map(mapOf("b" to 1))),
        map(mapOf("b" to 1))
      ),
      VariadicValueTestCase(
        listOf(map(mapOf("b" to 1, "c" to 2)), map(mapOf("b" to 1))),
        map(mapOf("b" to 1, "c" to 2))
      ),

      // Testing across different value types
      VariadicValueTestCase(
        listOf(array(2, 3), constant(2), map(mapOf("2" to 2, "3" to 3))),
        map(mapOf("2" to 2, "3" to 3))
      ),
      VariadicValueTestCase(listOf(constant("a"), constant("b"), constant("c")), constant("c")),
      VariadicValueTestCase(listOf(constant(1L), constant("1"), constant(0L)), constant("1")),
      VariadicValueTestCase(listOf(constant(Double.NaN), constant(0L)), constant(0L)),
      VariadicValueTestCase(listOf(constant(Double.NaN), constant(1)), constant(1)),
      VariadicValueTestCase(listOf(nullValue(), constant(1L)), constant(1L)),
      VariadicValueTestCase(listOf(nullValue(), constant(1L)), constant(1L)), // for UNSET
      VariadicValueTestCase(listOf(nullValue(), constant(12)), constant(12)),
      VariadicValueTestCase(listOf(nullValue(), constant(12)), constant(12)), // for UNSET
    )

  private val equalCases =
    listOf(
      EqualValues(constant(1.0), constant(1)),
      EqualValues(constant(1L), constant(1.0)),
      EqualValues(constant(1), constant(1.0)),
      EqualValues(constant(-0.0), constant(0.0)),
      EqualValues(constant(0L), constant(-0.0)),
      EqualValues(constant(1), constant(1.0)), // Decimal128.fromString("1.0")
      EqualValues(constant(-1), constant(-1L)),
      EqualValues(constant(Double.NaN), constant(Double.NaN)), // Decimal128.NAN
      EqualValues(
        constant(Double.NEGATIVE_INFINITY),
        constant(Double.NEGATIVE_INFINITY)
      ), // Decimal128.NEGATIVE_INFINITY
      EqualValues(
        constant(Double.POSITIVE_INFINITY),
        constant(Double.POSITIVE_INFINITY)
      ), // Decimal128.POSITIVE_INFINITY
      EqualValues(constant(-0.0), constant(-0.0)), // Decimal128.NEGATIVE_ZERO
      EqualValues(constant(0.0), constant(0.0)), // Decimal128.POSITIVE_ZERO
      EqualValues(array(2), array(2.0)),
      EqualValues(map(mapOf("a" to 2)), map(mapOf("a" to 2.0))),
    )

  @Test
  fun `max with general values`() {
    generalCases.forEach { (inputs, expected) ->
      val expr = logicalMaximum(inputs[0], *inputs.subList(1, inputs.size).toTypedArray())
      assertEvaluatesTo(
        evaluate(expr, emptyDoc),
        evaluate(expected, emptyDoc),
        "Max(${inputs.joinToString()}) should be $expected"
      )
    }
  }

  @Test
  fun `max on equal values returns first input`() {
    equalCases.forEach { (left, right) ->
      assertEvaluatesTo(
        evaluate(logicalMaximum(left, right), emptyDoc),
        evaluate(left, emptyDoc),
        "Max($left, $right) should be $left"
      )
      assertEvaluatesTo(
        evaluate(logicalMaximum(right, left), emptyDoc),
        evaluate(right, emptyDoc),
        "Max($right, $left) should be $right"
      )
    }
  }

  @Test
  fun `one argument evaluates to error`() {
    val result = evaluate(logicalMaximum(constant(1L)), emptyDoc)
    assertEvaluatesToError(result, "1")
  }

  @Test
  fun `error value isError`() {
    val result = evaluate(logicalMaximum(errorExpr1, constant(1L)), emptyDoc)
    assertEvaluatesToError(result, "error-1")
  }

  @Test
  fun `value error isError`() {
    val result = evaluate(logicalMaximum(constant(1L), errorExpr2), emptyDoc)
    assertEvaluatesToError(result, "error-2")
  }

  @Test
  fun `error error isError`() {
    val result = evaluate(logicalMaximum(errorExpr1, errorExpr2), emptyDoc)
    assertEvaluatesToError(result, "error-1")
  }
}
