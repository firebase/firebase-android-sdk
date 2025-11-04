package com.google.firebase.firestore.pipeline.evaluation

import com.google.firebase.firestore.pipeline.Expression
import com.google.firebase.firestore.pipeline.assertEvaluatesToError
import com.google.firebase.firestore.pipeline.assertEvaluatesToNull
import com.google.firebase.firestore.pipeline.evaluate
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
internal class MirroringSemanticsTests {

  private val NULL_INPUT = Expression.Companion.nullValue()
  // Error: Integer division by zero
  private val ERROR_INPUT =
    Expression.Companion.divide(
      Expression.Companion.constant(1L),
      Expression.Companion.constant(0L)
    )
  // Unset: Field that doesn't exist in the default test document
  private val UNSET_INPUT = Expression.Companion.field("non-existent-field")
  // Valid: A simple valid input for binary tests
  private val VALID_INPUT = Expression.Companion.constant(42L)

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
        "abs" to { v -> Expression.Companion.abs(v) },
        "exp" to { v -> Expression.Companion.exp(v) },
        "ln" to { v -> Expression.Companion.ln(v) },
        "log10" to { v -> Expression.Companion.log10(v) },
        "sqrt" to { v -> Expression.Companion.sqrt(v) },
        "isNan" to { v -> Expression.Companion.isNan(v) },
        "isNotNan" to { v -> Expression.Companion.isNotNan(v) },
        "arrayLength" to { v -> Expression.Companion.arrayLength(v) },
        "vectorLength" to { v -> Expression.Companion.vectorLength(v) },
        "length" to { v -> Expression.Companion.length(v) },
        "reverse" to { v -> Expression.Companion.reverse(v) },
        "stringReverse" to { v -> Expression.Companion.stringReverse(v) },
        "charLength" to { v -> Expression.Companion.charLength(v) },
        "byteLength" to { v -> Expression.Companion.byteLength(v) },
        "toLower" to { v -> Expression.Companion.toLower(v) },
        "toUpper" to { v -> Expression.Companion.toUpper(v) },
        "trim" to { v -> Expression.Companion.trim(v) },
        "arrayReverse" to { v -> Expression.Companion.arrayReverse(v) },
        "unixMicrosToTimestamp" to { v -> Expression.Companion.unixMicrosToTimestamp(v) },
        "timestampToUnixMicros" to { v -> Expression.Companion.timestampToUnixMicros(v) },
        "unixMillisToTimestamp" to { v -> Expression.Companion.unixMillisToTimestamp(v) },
        "timestampToUnixMillis" to { v -> Expression.Companion.timestampToUnixMillis(v) },
        "unixSecondsToTimestamp" to { v -> Expression.Companion.unixSecondsToTimestamp(v) },
        "timestampToUnixSeconds" to { v -> Expression.Companion.timestampToUnixSeconds(v) }
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
        "add" to { v1, v2 -> Expression.Companion.add(v1, v2) },
        "subtract" to { v1, v2 -> Expression.Companion.subtract(v1, v2) },
        "multiply" to { v1, v2 -> Expression.Companion.multiply(v1, v2) },
        "divide" to { v1, v2 -> Expression.Companion.divide(v1, v2) },
        "mod" to { v1, v2 -> Expression.Companion.mod(v1, v2) },
        "log" to { v1, v2 -> Expression.Companion.log(v1, v2) },
        "pow" to { v1, v2 -> Expression.Companion.pow(v1, v2) },
        // Comparison
        "eq" to { v1, v2 -> Expression.Companion.equal(v1, v2) },
        "neq" to { v1, v2 -> Expression.Companion.notEqual(v1, v2) },
        "lt" to { v1, v2 -> Expression.Companion.lessThan(v1, v2) },
        "lte" to { v1, v2 -> Expression.Companion.lessThanOrEqual(v1, v2) },
        "gt" to { v1, v2 -> Expression.Companion.greaterThan(v1, v2) },
        "gte" to { v1, v2 -> Expression.Companion.greaterThanOrEqual(v1, v2) },
        // Array
        "arrayContains" to { v1, v2 -> Expression.Companion.arrayContains(v1, v2) },
        "arrayContainsAll" to { v1, v2 -> Expression.Companion.arrayContainsAll(v1, v2) },
        "arrayContainsAny" to { v1, v2 -> Expression.Companion.arrayContainsAny(v1, v2) },
        // TODO(pipeline): arrayConcat is correct, the rest need to be updated
        // "arrayConcat" to { v1, v2 -> arrayConcat(v1, v2) },
        "eqAny" to { v1, v2 -> Expression.Companion.equalAny(v1, v2) }, // Maps to EqAnyExpr
        "notEqAny" to
          { v1, v2 ->
            Expression.Companion.notEqualAny(v1, v2)
          }, // Maps to NotEqAnyExpr
        // String
        "like" to { v1, v2 -> Expression.Companion.like(v1, v2) },
        "regexContains" to { v1, v2 -> Expression.Companion.regexContains(v1, v2) },
        "regexMatch" to { v1, v2 -> Expression.Companion.regexMatch(v1, v2) },
        "strContains" to
          { v1, v2 ->
            Expression.Companion.stringContains(v1, v2)
          }, // Maps to StrContainsExpr
        "startsWith" to { v1, v2 -> Expression.Companion.startsWith(v1, v2) },
        "endsWith" to { v1, v2 -> Expression.Companion.endsWith(v1, v2) },
        "strConcat" to { v1, v2 -> Expression.Companion.stringConcat(v1, v2) },
        "join" to { v1, v2 -> Expression.Companion.join(v1, v2) },
        "cosineDistance" to { v1, v2 -> Expression.Companion.cosineDistance(v1, v2) },
        "dotProduct" to { v1, v2 -> Expression.Companion.dotProduct(v1, v2) },
        "euclideanDistance" to { v1, v2 -> Expression.Companion.euclideanDistance(v1, v2) },
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
