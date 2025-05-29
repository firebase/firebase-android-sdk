package com.google.firebase.firestore.pipeline

import com.google.common.truth.Truth.assertThat
import com.google.firebase.firestore.model.Values.encodeValue // Returns com.google.protobuf.Value
import com.google.firebase.firestore.pipeline.Expr.Companion.add
import com.google.firebase.firestore.pipeline.Expr.Companion.constant
import com.google.firebase.firestore.pipeline.Expr.Companion.divide
import com.google.firebase.firestore.pipeline.Expr.Companion.mod
import com.google.firebase.firestore.pipeline.Expr.Companion.multiply
import com.google.firebase.firestore.pipeline.Expr.Companion.subtract
import org.junit.Test

internal class ArithmeticTests {

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
    val longMaxAsDoublePlusOne = Long.MAX_VALUE.toDouble() + 1.0
    assertThat(evaluate(add(constant(Long.MAX_VALUE), constant(1.0))).value)
      .isEqualTo(encodeValue(longMaxAsDoublePlusOne))

    val intermediate = longMaxAsDoublePlusOne
    assertThat(evaluate(add(constant(intermediate), constant(100L))).value)
      .isEqualTo(encodeValue(intermediate + 100.0))
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
    assertThat(evaluate(add(constant(9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(-9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(add(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(
        evaluate(add(constant(-Double.MAX_VALUE), constant(nanVal))).value
      ) // Corresponds to C++ std::numeric_limits<double>::lowest()
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

  // --- Subtract Tests (Ported) ---
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

  // --- Multiply Tests (Ported) ---
  @Test
  fun multiplyFunctionTestWithBasicNumerics() {
    assertThat(evaluate(multiply(constant(1L), constant(2L))).value).isEqualTo(encodeValue(2L))
    assertThat(evaluate(multiply(constant(3L), constant(2.5))).value).isEqualTo(encodeValue(7.5))
    assertThat(evaluate(multiply(constant(1.0), constant(2L))).value).isEqualTo(encodeValue(2.0))
    assertThat(evaluate(multiply(constant(1.32), constant(2.0))).value).isEqualTo(encodeValue(2.64))
  }

  @Test
  fun multiplyFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(multiply(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(multiply(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(multiply(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithDoubleLongMultiplicationOverflow() {
    assertThat(evaluate(multiply(constant(Long.MAX_VALUE), constant(100.0))).value)
      .isEqualTo(encodeValue(Long.MAX_VALUE.toDouble() * 100.0))
    assertThat(evaluate(multiply(constant(Long.MAX_VALUE), constant(100L))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithDoubleMultiplicationOverflow() {
    assertThat(evaluate(multiply(constant(Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(multiply(constant(-Double.MAX_VALUE), constant(Double.MAX_VALUE))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithLongMultiplicationOverflow() {
    assertThat(evaluate(multiply(constant(Long.MAX_VALUE), constant(10L))).isError).isTrue()
    assertThat(evaluate(multiply(constant(Long.MIN_VALUE), constant(10L))).isError).isTrue()
    assertThat(evaluate(multiply(constant(-10L), constant(Long.MAX_VALUE))).isError).isTrue()
    assertThat(evaluate(multiply(constant(-10L), constant(Long.MIN_VALUE))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(multiply(constant(1L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(1.0), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(-9007199254740991L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(-Double.MAX_VALUE), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(multiply(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun multiplyFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(multiply(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun multiplyFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(multiply(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(multiply(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(multiply(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(multiply(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithPositiveInfinityNegativeInfinityReturnsNegativeInfinity() {
    assertThat(
        evaluate(multiply(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(
        evaluate(multiply(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun multiplyFunctionTestWithMultiArgument() {
    assertThat(evaluate(multiply(multiply(constant(1L), constant(2L)), constant(3L))).value)
      .isEqualTo(encodeValue(6L))
    assertThat(evaluate(multiply(constant(1.0), multiply(constant(2L), constant(3L)))).value)
      .isEqualTo(encodeValue(6.0))
  }

  // --- Divide Tests (Ported) ---
  @Test
  fun divideFunctionTestWithBasicNumerics() {
    assertThat(evaluate(divide(constant(10L), constant(2L))).value).isEqualTo(encodeValue(5L))
    assertThat(evaluate(divide(constant(10L), constant(2.0))).value).isEqualTo(encodeValue(5.0))
    assertThat(evaluate(divide(constant(10.0), constant(3L))).value)
      .isEqualTo(encodeValue(10.0 / 3.0))
    assertThat(evaluate(divide(constant(10.0), constant(7.0))).value)
      .isEqualTo(encodeValue(10.0 / 7.0))
  }

  @Test
  fun divideFunctionTestWithBasicNonNumerics() {
    assertThat(evaluate(divide(constant(1L), constant("1"))).isError).isTrue()
    assertThat(evaluate(divide(constant("1"), constant(1.0))).isError).isTrue()
    assertThat(evaluate(divide(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun divideFunctionTestWithLongDivision() {
    assertThat(evaluate(divide(constant(10L), constant(3L))).value).isEqualTo(encodeValue(3L))
    assertThat(evaluate(divide(constant(-10L), constant(3L))).value).isEqualTo(encodeValue(-3L))
    assertThat(evaluate(divide(constant(10L), constant(-3L))).value).isEqualTo(encodeValue(-3L))
    assertThat(evaluate(divide(constant(-10L), constant(-3L))).value).isEqualTo(encodeValue(3L))
  }

  @Test
  fun divideFunctionTestWithDoubleDivisionOverflow() {
    assertThat(evaluate(divide(constant(Double.MAX_VALUE), constant(0.5))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(divide(constant(-Double.MAX_VALUE), constant(0.5))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
  }

  @Test
  fun divideFunctionTestWithByZero() {
    assertThat(evaluate(divide(constant(1L), constant(0L))).isError).isTrue()
    assertThat(evaluate(divide(constant(1.1), constant(0.0))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(divide(constant(1.1), constant(-0.0))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(divide(constant(0.0), constant(0.0))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun divideFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(divide(constant(1L), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(1L))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(1.0), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(1.0))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(divide(constant(nanVal), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun divideFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(divide(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun divideFunctionTestWithPositiveInfinity() {
    assertThat(evaluate(divide(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.POSITIVE_INFINITY))
    assertThat(evaluate(divide(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(0.0))
  }

  @Test
  fun divideFunctionTestWithNegativeInfinity() {
    assertThat(evaluate(divide(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NEGATIVE_INFINITY))
    assertThat(evaluate(divide(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun divideFunctionTestWithPositiveInfinityNegativeInfinityReturnsNan() {
    assertThat(
        evaluate(divide(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(
        evaluate(divide(constant(Double.NEGATIVE_INFINITY), constant(Double.POSITIVE_INFINITY)))
          .value
      )
      .isEqualTo(encodeValue(Double.NaN))
  }

  // --- Mod Tests (Ported) ---
  @Test
  fun modFunctionTestWithDivisorZero() {
    assertThat(evaluate(mod(constant(42L), constant(0L))).isError).isTrue()
    assertThat(evaluate(mod(constant(42.0), constant(0.0))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(42.0), constant(-0.0))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun modFunctionTestWithDividendZeroReturnsZero() {
    assertThat(evaluate(mod(constant(0L), constant(42L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(0.0), constant(42.0))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(mod(constant(-0.0), constant(42.0))).value).isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun modFunctionTestWithLongPositivePositive() {
    assertThat(evaluate(mod(constant(10L), constant(3L))).value).isEqualTo(encodeValue(1L))
  }

  @Test
  fun modFunctionTestWithLongNegativeNegative() {
    assertThat(evaluate(mod(constant(-10L), constant(-3L))).value).isEqualTo(encodeValue(-1L))
  }

  @Test
  fun modFunctionTestWithLongPositiveNegative() {
    assertThat(evaluate(mod(constant(10L), constant(-3L))).value).isEqualTo(encodeValue(1L))
  }

  @Test
  fun modFunctionTestWithLongNegativePositive() {
    assertThat(evaluate(mod(constant(-10L), constant(3L))).value).isEqualTo(encodeValue(-1L))
  }

  @Test
  fun modFunctionTestWithDoublePositivePositive() {
    // 10.5 % 3.0 is exactly 1.5
    assertThat(evaluate(mod(constant(10.5), constant(3.0))).value).isEqualTo(encodeValue(1.5))
  }

  @Test
  fun modFunctionTestWithDoubleNegativeNegative() {
    val resultValue = evaluate(mod(constant(-7.3), constant(-1.8))).value
    assertThat(resultValue?.doubleValue).isWithin(1e-9).of(-0.1)
  }

  @Test
  fun modFunctionTestWithDoublePositiveNegative() {
    val resultValue = evaluate(mod(constant(9.8), constant(-2.5))).value
    assertThat(resultValue?.doubleValue).isWithin(1e-9).of(2.3)
  }

  @Test
  fun modFunctionTestWithDoubleNegativePositive() {
    val resultValue = evaluate(mod(constant(-7.5), constant(2.3))).value
    assertThat(resultValue?.doubleValue).isWithin(1e-9).of(-0.6)
  }

  @Test
  fun modFunctionTestWithLongPerfectlyDivisible() {
    assertThat(evaluate(mod(constant(10L), constant(5L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(-10L), constant(5L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(10L), constant(-5L))).value).isEqualTo(encodeValue(0L))
    assertThat(evaluate(mod(constant(-10L), constant(-5L))).value).isEqualTo(encodeValue(0L))
  }

  @Test
  fun modFunctionTestWithDoublePerfectlyDivisible() {
    assertThat(evaluate(mod(constant(10.0), constant(2.5))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(mod(constant(10.0), constant(-2.5))).value).isEqualTo(encodeValue(0.0))
    assertThat(evaluate(mod(constant(-10.0), constant(2.5))).value).isEqualTo(encodeValue(-0.0))
    assertThat(evaluate(mod(constant(-10.0), constant(-2.5))).value).isEqualTo(encodeValue(-0.0))
  }

  @Test
  fun modFunctionTestWithNonNumericsReturnError() {
    assertThat(evaluate(mod(constant(10L), constant("1"))).isError).isTrue()
    assertThat(evaluate(mod(constant("1"), constant(10L))).isError).isTrue()
    assertThat(evaluate(mod(constant("1"), constant("1"))).isError).isTrue()
  }

  @Test
  fun modFunctionTestWithNanNumberReturnNaN() {
    val nanVal = Double.NaN
    assertThat(evaluate(mod(constant(1L), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(1.0), constant(nanVal))).value).isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(nanVal))).value)
      .isEqualTo(encodeValue(nanVal))
  }

  @Test
  fun modFunctionTestWithNanNotNumberTypeReturnError() {
    assertThat(evaluate(mod(constant(Double.NaN), constant("hello world"))).isError).isTrue()
  }

  @Test
  fun modFunctionTestWithNumberPosInfinityReturnSelf() {
    assertThat(evaluate(mod(constant(1L), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(mod(constant(42.123), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(42.123))
    assertThat(evaluate(mod(constant(-99.9), constant(Double.POSITIVE_INFINITY))).value)
      .isEqualTo(encodeValue(-99.9))
  }

  @Test
  fun modFunctionTestWithPosInfinityNumberReturnNaN() {
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(42.123))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(-99.9))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun modFunctionTestWithNumberNegInfinityReturnSelf() {
    assertThat(evaluate(mod(constant(1L), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(1.0))
    assertThat(evaluate(mod(constant(42.123), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(42.123))
    assertThat(evaluate(mod(constant(-99.9), constant(Double.NEGATIVE_INFINITY))).value)
      .isEqualTo(encodeValue(-99.9))
  }

  @Test
  fun modFunctionTestWithNegInfinityNumberReturnNaN() {
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(1L))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(42.123))).value)
      .isEqualTo(encodeValue(Double.NaN))
    assertThat(evaluate(mod(constant(Double.NEGATIVE_INFINITY), constant(-99.9))).value)
      .isEqualTo(encodeValue(Double.NaN))
  }

  @Test
  fun modFunctionTestWithPosAndNegInfinityReturnNaN() {
    assertThat(
        evaluate(mod(constant(Double.POSITIVE_INFINITY), constant(Double.NEGATIVE_INFINITY))).value
      )
      .isEqualTo(encodeValue(Double.NaN))
  }
}
