// Copyright 2018 Google LLC
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

package com.google.firebase.firestore.model.mutation;

import static com.google.firebase.firestore.model.Values.isDecimal128Value;
import static com.google.firebase.firestore.model.Values.isDouble;
import static com.google.firebase.firestore.model.Values.isInt32Value;
import static com.google.firebase.firestore.model.Values.isInteger;
import static com.google.firebase.firestore.util.Assert.fail;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;

/**
 * Implements the backend semantics for locally computed NUMERIC_ADD (increment) transforms.
 * Converts all field values to longs or doubles and resolves overflows to
 * Long.MAX_VALUE/Long.MIN_VALUE.
 */
public class NumericIncrementTransformOperation extends NumericTransformOperation {
  public NumericIncrementTransformOperation(Value operand) {
    super(operand);
  }

  @Override
  public Value computeBaseValue(@Nullable Value previousValue) {
    return Values.isNumber(previousValue)
        ? previousValue
        : Value.newBuilder().setIntegerValue(0).build();
  }

  @Override
  public Value applyToLocalView(@Nullable Value previousValue, Timestamp localWriteTime) {
    Value baseValue = computeBaseValue(previousValue);

    // If either is Decimal128, approximate sum as double and return Decimal128 Value.
    if (isDecimal128Value(baseValue) || isDecimal128Value(operand)) {
      double baseDouble = Values.getDouble(baseValue);
      double operandDouble = operandAsDouble();
      double sum = baseDouble + operandDouble;
      return decimal128Value(sum);
    }

    // If baseValue is Int32Value:
    if (isInt32Value(baseValue)) {
      int baseInt =
          (int)
              baseValue.getMapValue().getFieldsOrThrow(Values.RESERVED_INT32_KEY).getIntegerValue();
      if (isDouble(operand)) {
        double sum = baseInt + operand.getDoubleValue();
        return Value.newBuilder().setDoubleValue(sum).build();
      } else if (isInteger(operand)) {
        long sum = safeIncrement(baseInt, operand.getIntegerValue());
        return Value.newBuilder().setIntegerValue(sum).build();
      } else if (isInt32Value(operand)) {
        int operandInt =
            (int)
                operand.getMapValue().getFieldsOrThrow(Values.RESERVED_INT32_KEY).getIntegerValue();
        int sum = safeIncrementInt32(baseInt, operandInt);
        return Values.getInt32(sum);
      } else {
        throw fail("Unexpected operand type: " + operand);
      }
    }

    // Return an integer value if baseValue is integer and operand is integer or Int32Value.
    if (isInteger(baseValue) && (isInteger(operand) || isInt32Value(operand))) {
      long sum = safeIncrement(baseValue.getIntegerValue(), operandAsLong());
      return Value.newBuilder().setIntegerValue(sum).build();
    } else if (isInteger(baseValue)) {
      double sum = baseValue.getIntegerValue() + operandAsDouble();
      return Value.newBuilder().setDoubleValue(sum).build();
    } else {
      double sum = baseValue.getDoubleValue() + operandAsDouble();
      return Value.newBuilder().setDoubleValue(sum).build();
    }
  }

  private static Value decimal128Value(double sum) {
    if (Double.isNaN(sum)) {
      return Values.getDecimal128("NaN");
    }
    if (sum == Double.POSITIVE_INFINITY) {
      return Values.getDecimal128("Infinity");
    }
    if (sum == Double.NEGATIVE_INFINITY) {
      return Values.getDecimal128("-Infinity");
    }
    if (Double.doubleToRawLongBits(sum) == Double.doubleToRawLongBits(-0.0)) {
      return Values.getDecimal128("-0");
    }
    if ((long) sum == sum) {
      return Values.getDecimal128(String.valueOf((long) sum));
    }
    return Values.getDecimal128(Double.toString(sum));
  }

  /**
   * Implementation of Java 8's `addExact()` that resolves positive and negative numeric overflows
   * to Long.MAX_VALUE or Long.MIN_VALUE respectively (instead of throwing an ArithmeticException).
   */
  private long safeIncrement(long x, long y) {
    long r = x + y;

    // See "Hacker's Delight" 2-12: Overflow if both arguments have the opposite sign of the result
    if (((x ^ r) & (y ^ r)) >= 0) {
      return r;
    }

    if (r >= 0L) {
      return Long.MIN_VALUE;
    } else {
      return Long.MAX_VALUE;
    }
  }

  private int safeIncrementInt32(int x, int y) {
    int r = x + y;

    // Overflow if both arguments have the opposite sign of the result
    if (((x ^ r) & (y ^ r)) >= 0) {
      return r;
    }

    if (r >= 0) {
      return Integer.MIN_VALUE;
    } else {
      return Integer.MAX_VALUE;
    }
  }
}
