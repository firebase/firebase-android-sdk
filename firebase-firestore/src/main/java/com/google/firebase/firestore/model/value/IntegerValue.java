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

/** A wrapper for integer/long values in Firestore. */
public final class IntegerValue extends NumberValue {
  private final long internalValue;

  private IntegerValue(Long val) {
    super();
    internalValue = val;
  }

  public static IntegerValue valueOf(Long val) {
    return new IntegerValue(val);
  }

  @Override
  public Long value() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    // NOTE: DoubleValue and IntegerValue instances may compareTo() the same,
    // but that doesn't make them equal via equals().
    return o instanceof IntegerValue && internalValue == ((IntegerValue) o).internalValue;
  }

  @Override
  public int hashCode() {
    return (int) (internalValue ^ (internalValue >>> 32));
  }

  // NOTE: compareTo() is implemented in NumberValue.

  public long getInternalValue() {
    return internalValue;
  }
}
