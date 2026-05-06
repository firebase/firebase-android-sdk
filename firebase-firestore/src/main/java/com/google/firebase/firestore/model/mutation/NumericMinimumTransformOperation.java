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

import static com.google.firebase.firestore.model.Values.isInteger;

import androidx.annotation.Nullable;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;

/**
 * Implements the backend semantics for locally computed NUMERIC_MIN (minimum) transforms.
 */
public class NumericMinimumTransformOperation extends NumericTransformOperation {
  public NumericMinimumTransformOperation(Value operand) {
    super(operand);
  }

  @Override
  public Value applyToLocalView(@Nullable Value previousValue, Timestamp localWriteTime) {
    if (!Values.isNumber(previousValue)) {
      return operand;
    }

    // Return an integer value only if the previous value and the operand is an integer.
    if (isInteger(previousValue) && isInteger(operand)) {
      long min = Math.min(previousValue.getIntegerValue(), operandAsLong());
      return Value.newBuilder().setIntegerValue(min).build();
    } else {
      double prevDouble =
          isInteger(previousValue)
              ? previousValue.getIntegerValue()
              : previousValue.getDoubleValue();
      double operDouble = operandAsDouble();

      if (Double.isNaN(prevDouble)) {
        return previousValue;
      }
      if (Double.isNaN(operDouble)) {
        return operand;
      }

      double min = Math.min(prevDouble, operDouble);
      return Value.newBuilder().setDoubleValue(min).build();
    }
  }
}
