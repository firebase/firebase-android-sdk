// Copyright 2026 Google LLC
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

import static com.google.firebase.firestore.model.Values.isDouble;
import static com.google.firebase.firestore.model.Values.isInteger;
import static com.google.firebase.firestore.util.Assert.fail;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;

/**
 * // Implements the backend semantics for locally computed numeric transforms.
 * // Base class for increment, minimum, and maximum transforms.
 */
public abstract class NumericTransformOperation implements TransformOperation {
  protected final Value operand;

  public NumericTransformOperation(Value operand) {
    hardAssert(Values.isNumber(operand), "NumericTransformOperation expects a NumberValue operand");
    this.operand = operand;
  }

  @Override
  public Value applyToRemoteDocument(@Nullable Value previousValue, Value transformResult) {
    return transformResult;
  }

  public Value getOperand() {
    return operand;
  }

  /**
   * Inspects the provided value, returning the provided value if it is already a NumberValue,
   * otherwise returning a coerced IntegerValue of 0.
   */
  @Override
  public Value computeBaseValue(@Nullable Value previousValue) {
    return Values.isNumber(previousValue)
        ? previousValue
        : Value.newBuilder().setIntegerValue(0).build();
  }

  protected double operandAsDouble() {
    if (isDouble(operand)) {
      return operand.getDoubleValue();
    } else if (isInteger(operand)) {
      return operand.getIntegerValue();
    } else {
      throw fail(
          "Expected 'operand' to be of Number type, but was "
              + operand.getClass().getCanonicalName());
    }
  }

  protected long operandAsLong() {
    if (isDouble(operand)) {
      return (long) operand.getDoubleValue();
    } else if (isInteger(operand)) {
      return operand.getIntegerValue();
    } else {
      throw fail(
          "Expected 'operand' to be of Number type, but was "
              + operand.getClass().getCanonicalName());
    }
  }

  @Override
  @SuppressWarnings("EqualsGetClass")
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    NumericTransformOperation that = (NumericTransformOperation) o;
    return operand.equals(that.operand);
  }

  @Override
  public int hashCode() {
    int result = getClass().hashCode();
    result = 31 * result + operand.hashCode();
    return result;
  }
}
