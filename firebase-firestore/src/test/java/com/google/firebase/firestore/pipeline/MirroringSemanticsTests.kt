package com.google.firebase.firestore.pipeline

import com.google.firebase.firestore.pipeline.Expr.Companion.add
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContains
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAll
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayContainsAny
import com.google.firebase.firestore.pipeline.Expr.Companion.arrayLength
import com.google.firebase.firestore.pipeline.Expr.Companion.byteLength
import com.google.firebase.firestore.pipeline.Expr.Companion.charLength
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.divide
import com.google.firebase.firestore.pipeline.Expr.Companion.endsWith
import com.google.firebase.firestore.pipeline.Expr.Companion.eq
import com.google.firebase.firestore.pipeline.Expr.Companion.eqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.field
import com.google.firebase.firestore.pipeline.Expr.Companion.gt
import com.google.firebase.firestore.pipeline.Expr.Companion.gte
import com.google.firebase.firestore.pipeline.Expr.Companion.isNan
import com.google.firebase.firestore.pipeline.Expr.Companion.isNotNan
import com.google.firebase.firestore.pipeline.Expr.Companion.like
import com.google.firebase.firestore.pipeline.Expr.Companion.lt
import com.google.firebase.firestore.pipeline.Expr.Companion.lte
import com.google.firebase.firestore.pipeline.Expr.Companion.mod
import com.google.firebase.firestore.pipeline.Expr.Companion.multiply
import com.google.firebase.firestore.pipeline.Expr.Companion.neq
import com.google.firebase.firestore.pipeline.Expr.Companion.notEqAny
import com.google.firebase.firestore.pipeline.Expr.Companion.nullValue
import com.google.firebase.firestore.pipeline.Expr.Companion.regexContains
import com.google.firebase.firestore.pipeline.Expr.Companion.regexMatch
import com.google.firebase.firestore.pipeline.Expr.Companion.reverse
import com.google.firebase.firestore.pipeline.Expr.Companion.startsWith
import com.google.firebase.firestore.pipeline.Expr.Companion.strConcat
import com.google.firebase.firestore.pipeline.Expr.Companion.strContains
import com.google.firebase.firestore.pipeline.Expr.Companion.subtract
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampToUnixMicros
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampToUnixMillis
import com.google.firebase.firestore.pipeline.Expr.Companion.timestampToUnixSeconds
import com.google.firebase.firestore.pipeline.Expr.Companion.toLower
import com.google.firebase.firestore.pipeline.Expr.Companion.toUpper
import com.google.firebase.firestore.pipeline.Expr.Companion.trim
import com.google.firebase.firestore.pipeline.Expr.Companion.unixMicrosToTimestamp
import com.google.firebase.firestore.pipeline.Expr.Companion.unixMillisToTimestamp
import com.google.firebase.firestore.pipeline.Expr.Companion.unixSecondsToTimestamp
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
    val inputExpr: Expr,
    val expectedOutcome: ExpectedOutcome,
    val description: String
  )

  private data class BinaryTestCase(
    val left: Expr,
    val right: Expr,
    val expectedOutcome: ExpectedOutcome,
    val description: String
  )

  @Test
  fun `unary function input mirroring`() {
    val unaryFunctionBuilders =
      listOf<Pair<String, (Expr) -> Expr>>(
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
      listOf<Pair<String, (Expr, Expr) -> Expr>>(
        // Arithmetic (Variadic, base is binary)
        "add" to { v1, v2 -> add(v1, v2) },
        "subtract" to { v1, v2 -> subtract(v1, v2) },
        "multiply" to { v1, v2 -> multiply(v1, v2) },
        "divide" to { v1, v2 -> divide(v1, v2) },
        "mod" to { v1, v2 -> mod(v1, v2) },
        // Comparison
        "eq" to { v1, v2 -> eq(v1, v2) },
        "neq" to { v1, v2 -> neq(v1, v2) },
        "lt" to { v1, v2 -> lt(v1, v2) },
        "lte" to { v1, v2 -> lte(v1, v2) },
        "gt" to { v1, v2 -> gt(v1, v2) },
        "gte" to { v1, v2 -> gte(v1, v2) },
        // Array
        "arrayContains" to { v1, v2 -> arrayContains(v1, v2) },
        "arrayContainsAll" to { v1, v2 -> arrayContainsAll(v1, v2) },
        "arrayContainsAny" to { v1, v2 -> arrayContainsAny(v1, v2) },
        "eqAny" to { v1, v2 -> eqAny(v1, v2) }, // Maps to EqAnyExpr
        "notEqAny" to { v1, v2 -> notEqAny(v1, v2) }, // Maps to NotEqAnyExpr
        // String
        "like" to { v1, v2 -> like(v1, v2) },
        "regexContains" to { v1, v2 -> regexContains(v1, v2) },
        "regexMatch" to { v1, v2 -> regexMatch(v1, v2) },
        "strContains" to { v1, v2 -> strContains(v1, v2) }, // Maps to StrContainsExpr
        "startsWith" to { v1, v2 -> startsWith(v1, v2) },
        "endsWith" to { v1, v2 -> endsWith(v1, v2) },
        "strConcat" to { v1, v2 -> strConcat(v1, v2) } // Maps to StrConcatExpr
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
