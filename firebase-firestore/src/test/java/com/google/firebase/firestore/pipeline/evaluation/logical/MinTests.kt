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
import com.google.firebase.firestore.pipeline.Expression.Companion.error
import com.google.firebase.firestore.pipeline.Expression.Companion.logicalMinimum
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.assertEvaluatesTo
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.evaluate
import com.google.firebase.firestore.testutil.TestUtilKtx.doc
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MinTests {
  private val emptyDoc = doc("coll/docEmpty", 1, emptyMap())

  private data class VariadicValueTestCase(
    val inputs: List<Expression>,
    val expected: Expression,
    val description: String = ""
  )
  private data class EqualValues(val left: Expression, val right: Expression)

  private val generalCases =
    listOf(
      // Testing the relative priority of different types, following TypeComparator.
      // Boolean < Number < String < Array < Map. Null always has the lowest
      // priority.
      VariadicValueTestCase(listOf(constant(true), nullValue()), constant(true)),
      VariadicValueTestCase(listOf(nullValue(), constant(true)), constant(true)),
      VariadicValueTestCase(listOf(constant(0L), constant(false)), constant(false)),
      VariadicValueTestCase(listOf(constant(0.0), constant(true)), constant(true)),
      VariadicValueTestCase(listOf(constant(""), constant(0L)), constant(0L)),
      VariadicValueTestCase(listOf(constant(0.0), constant("foo")), constant(0.0)),
      VariadicValueTestCase(listOf(array(2, 3), constant("foo")), constant("foo")),
      VariadicValueTestCase(
        listOf(Expression.map(mapOf<String, Any>()), array(listOf<Any>())),
        array(listOf<Any>())
      ),
      VariadicValueTestCase(
        listOf(array(listOf<Any>()), Expression.map(mapOf<String, Any>())),
        array(listOf<Any>())
      ),

      // Testing numeric comparisons are equal across types.
      VariadicValueTestCase(listOf(constant(1.0), constant(2L)), constant(1.0)),
      VariadicValueTestCase(listOf(constant(1.1), constant(1L)), constant(1L)),
      VariadicValueTestCase(listOf(constant(-20), constant(4.24)), constant(-20)),
      VariadicValueTestCase(listOf(constant(1L), constant(2.0), constant(3L)), constant(1L)),
      VariadicValueTestCase(listOf(constant(2.5), constant(2.6)), constant(2.5)),
      VariadicValueTestCase(
        listOf(constant(Double.NEGATIVE_INFINITY), constant(Long.MIN_VALUE)),
        constant(Double.NEGATIVE_INFINITY)
      ),
      VariadicValueTestCase(
        listOf(constant(Double.POSITIVE_INFINITY), constant(Long.MAX_VALUE)),
        constant(Long.MAX_VALUE)
      ),

      // Testing comparisons within the same type.
      VariadicValueTestCase(listOf(constant(true), constant(false)), constant(false)),
      VariadicValueTestCase(listOf(constant(1L), constant(0L)), constant(0L)),
      VariadicValueTestCase(listOf(constant(-0.4), constant(0.0)), constant(-0.4)),
      VariadicValueTestCase(listOf(constant(1), constant(2), constant(3)), constant(1)),
      VariadicValueTestCase(listOf(constant("b"), constant("a")), constant("a")),
      VariadicValueTestCase(listOf(constant("b"), constant("aaaa")), constant("aaaa")),
      VariadicValueTestCase(listOf(nullValue(), nullValue()), nullValue()),
      VariadicValueTestCase(listOf(nullValue(), nullValue()), nullValue()), // for UNSET

      // List comparison is based on the comparison of the first elements, or size as a
      // tie-breaker.
      VariadicValueTestCase(listOf(array(listOf(2)), array(listOf(1))), array(listOf(1))),
      VariadicValueTestCase(
        listOf(array(listOf(2)), array(listOf(1, 1, 2, 3, 4))),
        array(listOf(1, 1, 2, 3, 4))
      ),
      VariadicValueTestCase(listOf(array(listOf(2, 3)), array(listOf(2, 2))), array(listOf(2, 2))),
      VariadicValueTestCase(listOf(array(listOf(2)), array(listOf(2, -10))), array(listOf(2))),

      // Map comparison is based on the comparison of the smallest keys and their values, or
      // size as a tie-breaker.
      VariadicValueTestCase(
        listOf(Expression.map(mapOf("b" to 1)), Expression.map(mapOf("a" to 10))),
        Expression.map(mapOf("a" to 10))
      ),
      VariadicValueTestCase(
        listOf(Expression.map(mapOf("b" to 1)), Expression.map(mapOf("b" to 0))),
        Expression.map(mapOf("b" to 0))
      ),
      VariadicValueTestCase(
        listOf(
          Expression.map(mapOf("b" to 1, "c" to 2)),
          Expression.map(mapOf("a" to 3, "b" to 5))
        ),
        Expression.map(mapOf("a" to 3, "b" to 5))
      ),
      VariadicValueTestCase(
        listOf(
          Expression.map(mapOf("b" to 1, "a" to 1)),
          Expression.map(mapOf("a" to 3, "b" to 0))
        ),
        Expression.map(mapOf("b" to 1, "a" to 1))
      ),
      VariadicValueTestCase(
        listOf(Expression.map(mapOf("b" to 1, "a" to 2)), Expression.map(mapOf("b" to 1))),
        Expression.map(mapOf("b" to 1, "a" to 2))
      ),
      VariadicValueTestCase(
        listOf(Expression.map(mapOf("b" to 1, "c" to 2)), Expression.map(mapOf("b" to 1))),
        Expression.map(mapOf("b" to 1))
      ),

      // Testing across different value types
      VariadicValueTestCase(
        listOf(array(listOf(2, 3)), constant(2), Expression.map(mapOf("2" to 2, "3" to 3))),
        constant(2)
      ),
      VariadicValueTestCase(listOf(constant("a"), constant("b"), constant("c")), constant("a")),
      VariadicValueTestCase(listOf(constant(1L), constant("1"), constant(0L)), constant(0L)),
      VariadicValueTestCase(listOf(constant(Double.NaN), constant(0L)), constant(Double.NaN)),
      VariadicValueTestCase(listOf(constant(Double.NaN), constant(1)), constant(Double.NaN)),
      VariadicValueTestCase(listOf(nullValue(), constant(1L)), constant(1L)),
      VariadicValueTestCase(listOf(nullValue(), constant(1L)), constant(1L)), // for UNSET
      VariadicValueTestCase(listOf(nullValue(), constant(12)), constant(12)),
      VariadicValueTestCase(listOf(nullValue(), constant(12)), constant(12))
    )

  private val equalCases =
    listOf(
      EqualValues(constant(1.0), constant(1)),
      EqualValues(constant(1L), constant(1.0)),
      EqualValues(constant(1), constant(1.0)),
      EqualValues(constant(-0.0), constant(0.0)),
      EqualValues(constant(0L), constant(-0.0)),
      EqualValues(constant(1), constant(1.0)), // Decimal128
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
      EqualValues(array(listOf(2)), array(listOf(2.0))),
      EqualValues(Expression.map(mapOf("a" to 2)), Expression.map(mapOf("a" to 2.0)))
    )

  @Test
  fun `min with general cases`() {
    generalCases.forEach { (inputs, expected, description) ->
      val expr = logicalMinimum(inputs[0], *inputs.subList(1, inputs.size).toTypedArray())
      assertEvaluatesTo(
        evaluate(expr, emptyDoc),
        evaluate(expected, emptyDoc),
        "Min(${inputs.joinToString()}) should be $expected. $description"
      )
    }
  }

  // Testing that the result of calling min on two equal values always results in the first value
  // being returned.
  @Test
  fun `min on equal values returns first input`() {
    equalCases.forEach { (left, right) ->
      assertEvaluatesTo(
        evaluate(logicalMinimum(left, right), emptyDoc),
        evaluate(left, emptyDoc),
        "Min(${left}, ${right}) should be ${left}"
      )
      assertEvaluatesTo(
        evaluate(logicalMinimum(right, left), emptyDoc),
        evaluate(right, emptyDoc),
        "Min(${right}, ${left}) should be ${right}"
      )
    }
  }

  @Test
  fun `one argument throws`() {
    assertEvaluatesToError(evaluate(logicalMinimum(constant(1L)), emptyDoc), "minimum(1)")
  }

  @Test
  fun `error value isError`() {
    assertEvaluatesToError(
      evaluate(logicalMinimum(error("error-1"), constant(1L)), emptyDoc),
      "minimum(error, 1)"
    )
  }

  @Test
  fun `value error isError`() {
    assertEvaluatesToError(
      evaluate(logicalMinimum(constant(1L), error("error-2")), emptyDoc),
      "minimum(1, error)"
    )
  }

  @Test
  fun `error error isError`() {
    assertEvaluatesToError(
      evaluate(logicalMinimum(error("error-1"), error("error-2")), emptyDoc),
      "minimum(error, error)"
    )
  }
}
