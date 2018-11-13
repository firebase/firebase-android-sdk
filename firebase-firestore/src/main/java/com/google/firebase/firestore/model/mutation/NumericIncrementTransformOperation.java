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

import static com.google.firebase.firestore.util.Assert.fail;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.IntegerValue;
import com.google.firebase.firestore.model.value.NumberValue;

/**
 * Implements the backend semantics for locally computed NUMERIC_ADD (increment) transforms.
 * Converts all field values to longs or doubles and resolves overflows to
 * Long.MAX_VALUE/Long.MIN_VALUE.
 */
public class NumericIncrementTransformOperation implements TransformOperation {
  private NumberValue operand;

  public NumericIncrementTransformOperation(NumberValue operand) {
    this.operand = operand;
  }

  @Override
  public FieldValue applyToLocalView(FieldValue previousValue, Timestamp localWriteTime) {
    // Return an integer value only if the previous value and the operand is an integer.
    if (previousValue instanceof IntegerValue && operand instanceof IntegerValue) {
      long sum = safeIncrement(((IntegerValue) previousValue).getInternalValue(), operandAsLong());
      return IntegerValue.valueOf(sum);
    } else if (previousValue instanceof IntegerValue) {
      double sum = ((IntegerValue) previousValue).getInternalValue() + operandAsDouble();
      return DoubleValue.valueOf(sum);
    } else if (previousValue instanceof DoubleValue) {
      double sum = ((DoubleValue) previousValue).getInternalValue() + operandAsDouble();
      return DoubleValue.valueOf(sum);
    }

    // If the existing value is not a number, use the value of the transform as the new base value.
    return operand;
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

  private double operandAsDouble() {
    if (operand instanceof DoubleValue) {
      return ((DoubleValue) operand).getInternalValue();
    } else if (operand instanceof IntegerValue) {
      return ((IntegerValue) operand).getInternalValue();
    } else {
      throw fail(
          "Expected 'operand' to be of Number type, but was "
              + operand.getClass().getCanonicalName());
    }
  }

  private long operandAsLong() {
    if (operand instanceof DoubleValue) {
      return (long) ((DoubleValue) operand).getInternalValue();
    } else if (operand instanceof IntegerValue) {
      return ((IntegerValue) operand).getInternalValue();
    } else {
      throw fail(
          "Expected 'operand' to be of Number type, but was "
              + operand.getClass().getCanonicalName());
    }
  }

  @Override
  public FieldValue applyToRemoteDocument(FieldValue previousValue, FieldValue transformResult) {
    return transformResult;
  }

  public FieldValue getOperand() {
    return operand;
  }

  @Override
  public boolean isIdempotent() {
    return false;
  }
}
