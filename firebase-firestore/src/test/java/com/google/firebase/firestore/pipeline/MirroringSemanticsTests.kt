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

package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.pipeline.Expression.Companion.abs
import com.google.firebase.firestore.pipeline.Expression.Companion.add
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expression.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expression.Companion.byteLength
import com.google.firebase.firestore.pipeline.Expression.Companion.charLength
import com.google.firebase.firestore.pipeline.Expression.Companion.constant
import com.google.firebase.firestore.pipeline.Expression.Companion.divide
import com.google.firebase.firestore.pipeline.Expression.Companion.endsWith
import com.google.firebase.firestore.pipeline.Expression.Companion.equal
import com.google.firebase.firestore.pipeline.Expression.Companion.equalAny
import com.google.firebase.firestore.pipeline.Expression.Companion.exp
import com.google.firebase.firestore.pipeline.Expression.Companion.field
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThan
import com.google.firebase.firestore.pipeline.Expression.Companion.greaterThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.isNan
import com.google.firebase.firestore.pipeline.Expression.Companion.isNotNan
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThan
import com.google.firebase.firestore.pipeline.Expression.Companion.lessThanOrEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.like
import com.google.firebase.firestore.pipeline.Expression.Companion.ln
import com.google.firebase.firestore.pipeline.Expression.Companion.log
import com.google.firebase.firestore.pipeline.Expression.Companion.log10
import com.google.firebase.firestore.pipeline.Expression.Companion.mod
import com.google.firebase.firestore.pipeline.Expression.Companion.multiply
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqual
import com.google.firebase.firestore.pipeline.Expression.Companion.notEqualAny
import com.google.firebase.firestore.pipeline.Expression.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expression.Companion.pow
import com.google.firebase.firestore.pipeline.Expression.Companion.regexContains
import com.google.firebase.firestore.pipeline.Expression.Companion.regexMatch
import com.google.firebase.firestore.pipeline.Expression.Companion.reverse
import com.google.firebase.firestore.pipeline.Expression.Companion.sqrt
import com.google.firebase.firestore.pipeline.Expression.Companion.startsWith
import com.google.firebase.firestore.pipeline.Expression.Companion.stringConcat
import com.google.firebase.firestore.pipeline.Expression.Companion.stringContains
import com.google.firebase.firestore.pipeline.Expression.Companion.subtract
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixMicros
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixMillis
import com.google.firebase.firestore.pipeline.Expression.Companion.timestampToUnixSeconds
import com.google.firebase.firestore.pipeline.Expression.Companion.toLower
import com.google.firebase.firestore.pipeline.Expression.Companion.toUpper
import com.google.firebase.firestore.pipeline.Expression.Companion.trim
import com.google.firebase.firestore.pipeline.Expression.Companion.unixMicrosToTimestamp
import com.google.firebase.firestore.pipeline.Expression.Companion.unixMillisToTimestamp
import com.google.firebase.firestore.pipeline.Expression.Companion.unixSecondsToTimestamp
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class MirroringSemanticsTests {

  private val NULL_INPUT = nullValue()
  // Error: Integer division by zero
  private val ERROR_INPUT = divide(constant(1L), constant(0L))
  // Unset: Field that doesn't exist in the default test document
  private val UNSET_INPUT = field("non-existent-field")
  // Valid: A simple valid input for binary tests
  private val VALID_INPUT = constant(42L)

  private enum class ExpectedOutcome {
    NULL,
    ERROR
  }

  private data class UnaryTestCase(
    val inputExpr: Expression,
    val expectedOutcome: ExpectedOutcome,
    val description: String
  )

  private data class BinaryTestCase(
    val left: Expression,
    val right: Expression,
    val expectedOutcome: ExpectedOutcome,
    val description: String
  )

  @Test
  fun `unary function input mirroring`() {
    val unaryFunctionBuilders =
      listOf<Pair<String, (Expression) -> Expression>>(
        "abs" to { v -> abs(v) },
        "exp" to { v -> exp(v) },
        "ln" to { v -> ln(v) },
        "log10" to { v -> log10(v) },
        "sqrt" to { v -> sqrt(v) },
        "isNan" to { v -> isNan(v) },
        "isNotNan" to { v -> isNotNan(v) },
        "arrayLength" to { v -> arrayLength(v) },
        "reverse" to { v -> reverse(v) },
        "charLength" to { v -> charLength(v) },
        "byteLength" to { v -> byteLength(v) },
        "toLower" to { v -> toLower(v) },
        "toUpper" to { v -> toUpper(v) },
        "trim" to { v -> trim(v) },
        "unixMicrosToTimestamp" to { v -> unixMicrosToTimestamp(v) },
        "timestampToUnixMicros" to { v -> timestampToUnixMicros(v) },
        "unixMillisToTimestamp" to { v -> unixMillisToTimestamp(v) },
        "timestampToUnixMillis" to { v -> timestampToUnixMillis(v) },
        "unixSecondsToTimestamp" to { v -> unixSecondsToTimestamp(v) },
        "timestampToUnixSeconds" to { v -> timestampToUnixSeconds(v) }
      )

    val testCases =
      listOf(
        UnaryTestCase(NULL_INPUT, ExpectedOutcome.NULL, "NULL"),
        UnaryTestCase(ERROR_INPUT, ExpectedOutcome.ERROR, "ERROR"),
        // Unary ops expect resolved args, so UNSET should lead to an error during evaluation.
        UnaryTestCase(UNSET_INPUT, ExpectedOutcome.ERROR, "UNSET")
      )

    for ((funcName, builder) in unaryFunctionBuilders) {
      for (testCase in testCases) {
        val exprToEvaluate = builder(testCase.inputExpr)
        val result = evaluate(exprToEvaluate) // Assumes default document context

        when (testCase.expectedOutcome) {
          ExpectedOutcome.NULL ->
            assertEvaluatesToNull(result, "Function: %s, Input: %s", funcName, testCase.description)
          ExpectedOutcome.ERROR ->
            assertEvaluatesToError(
              result,
              "Function: %s, Input: %s",
              funcName,
              testCase.description
            )
        }
      }
    }
  }

  @Test
  fun `binary function input mirroring`() {
    val binaryFunctionBuilders =
      listOf<Pair<String, (Expression, Expression) -> Expression>>(
        // Arithmetic (Variadic, base is binary)
        "add" to { v1, v2 -> add(v1, v2) },
        "subtract" to { v1, v2 -> subtract(v1, v2) },
        "multiply" to { v1, v2 -> multiply(v1, v2) },
        "divide" to { v1, v2 -> divide(v1, v2) },
        "mod" to { v1, v2 -> mod(v1, v2) },
        "log" to { v1, v2 -> log(v1, v2) },
        "pow" to { v1, v2 -> pow(v1, v2) },
        // Comparison
        "eq" to { v1, v2 -> equal(v1, v2) },
        "neq" to { v1, v2 -> notEqual(v1, v2) },
        "lt" to { v1, v2 -> lessThan(v1, v2) },
        "lte" to { v1, v2 -> lessThanOrEqual(v1, v2) },
        "gt" to { v1, v2 -> greaterThan(v1, v2) },
        "gte" to { v1, v2 -> greaterThanOrEqual(v1, v2) },
        // Array
        "arrayContains" to { v1, v2 -> arrayContains(v1, v2) },
        "arrayContainsAll" to { v1, v2 -> arrayContainsAll(v1, v2) },
        "arrayContainsAny" to { v1, v2 -> arrayContainsAny(v1, v2) },
        "eqAny" to { v1, v2 -> equalAny(v1, v2) }, // Maps to EqAnyExpr
        "notEqAny" to { v1, v2 -> notEqualAny(v1, v2) }, // Maps to NotEqAnyExpr
        // String
        "like" to { v1, v2 -> like(v1, v2) },
        "regexContains" to { v1, v2 -> regexContains(v1, v2) },
        "regexMatch" to { v1, v2 -> regexMatch(v1, v2) },
        "strContains" to { v1, v2 -> stringContains(v1, v2) }, // Maps to StrContainsExpr
        "startsWith" to { v1, v2 -> startsWith(v1, v2) },
        "endsWith" to { v1, v2 -> endsWith(v1, v2) },
        "strConcat" to { v1, v2 -> stringConcat(v1, v2) } // Maps to StrConcatExpr
        // TODO(b/351084804): mapGet is not implemented yet
        )

    val testCases =
      listOf(
        // Rule 1: NULL, NULL -> NULL (for most ops, some like eq(NULL,NULL) might be NULL)
        BinaryTestCase(NULL_INPUT, NULL_INPUT, ExpectedOutcome.NULL, "NULL, NULL -> NULL"),
        // Rule 2: Error/Unset propagation
        BinaryTestCase(NULL_INPUT, ERROR_INPUT, ExpectedOutcome.ERROR, "NULL, ERROR -> ERROR"),
        BinaryTestCase(ERROR_INPUT, NULL_INPUT, ExpectedOutcome.ERROR, "ERROR, NULL -> ERROR"),
        BinaryTestCase(NULL_INPUT, UNSET_INPUT, ExpectedOutcome.ERROR, "NULL, UNSET -> ERROR"),
        BinaryTestCase(UNSET_INPUT, NULL_INPUT, ExpectedOutcome.ERROR, "UNSET, NULL -> ERROR"),
        BinaryTestCase(ERROR_INPUT, ERROR_INPUT, ExpectedOutcome.ERROR, "ERROR, ERROR -> ERROR"),
        BinaryTestCase(ERROR_INPUT, UNSET_INPUT, ExpectedOutcome.ERROR, "ERROR, UNSET -> ERROR"),
        BinaryTestCase(UNSET_INPUT, ERROR_INPUT, ExpectedOutcome.ERROR, "UNSET, ERROR -> ERROR"),
        BinaryTestCase(UNSET_INPUT, UNSET_INPUT, ExpectedOutcome.ERROR, "UNSET, UNSET -> ERROR"),
        BinaryTestCase(VALID_INPUT, ERROR_INPUT, ExpectedOutcome.ERROR, "VALID, ERROR -> ERROR"),
        BinaryTestCase(ERROR_INPUT, VALID_INPUT, ExpectedOutcome.ERROR, "ERROR, VALID -> ERROR"),
        BinaryTestCase(VALID_INPUT, UNSET_INPUT, ExpectedOutcome.ERROR, "VALID, UNSET -> ERROR"),
        BinaryTestCase(UNSET_INPUT, VALID_INPUT, ExpectedOutcome.ERROR, "UNSET, VALID -> ERROR")
      )

    for ((funcName, builder) in binaryFunctionBuilders) {
      for (testCase in testCases) {
        val exprToEvaluate = builder(testCase.left, testCase.right)
        val result = evaluate(exprToEvaluate) // Assumes default document context

        when (testCase.expectedOutcome) {
          ExpectedOutcome.NULL ->
            assertEvaluatesToNull(result, "Function: %s, Case: %s", funcName, testCase.description)
          ExpectedOutcome.ERROR ->
            assertEvaluatesToError(result, "Function: %s, Case: %s", funcName, testCase.description)
        }
      }
    }
  }
}
