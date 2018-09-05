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

package com.google.firebase.firestore.model.value;

/** A wrapper for float/double values in Firestore. */
public final class DoubleValue extends NumberValue {
  public static final DoubleValue NaN = new DoubleValue(Double.NaN);

  private final double internalValue;

  private DoubleValue(Double val) {
    internalValue = val;
  }

  public static DoubleValue valueOf(Double val) {
    if (Double.isNaN(val)) {
      return NaN;
    } else {
      return new DoubleValue(val);
    }
  }

  @Override
  public Double value() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    // NOTE: DoubleValue and IntegerValue instances may compareTo() the same,
    // but that doesn't make them equal via equals().

    // NOTE: equals() should compare NaN equal to itself and -0.0 not equal to 0.0.
    return o instanceof DoubleValue
        && Double.doubleToLongBits(internalValue)
            == Double.doubleToLongBits(((DoubleValue) o).internalValue);
  }

  @Override
  public int hashCode() {
    long bits = Double.doubleToLongBits(internalValue);
    return (int) (bits ^ (bits >>> 32));
  }

  // NOTE: compareTo() is implemented in NumberValue.

  public double getInternalValue() {
    return internalValue;
  }
}
