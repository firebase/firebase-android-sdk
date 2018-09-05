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

import com.google.firebase.firestore.util.Util;

/** A wrapper for boolean value in Firestore. */
public class BooleanValue extends FieldValue {
  private static final BooleanValue TRUE_VALUE = new BooleanValue(Boolean.TRUE);
  private static final BooleanValue FALSE_VALUE = new BooleanValue(Boolean.FALSE);

  private final boolean internalValue;

  private BooleanValue(Boolean b) {
    internalValue = b;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_BOOLEAN;
  }

  @Override
  public Boolean value() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    // Since we create shared instances for true / false, we can use reference equality.
    return this == o;
  }

  @Override
  public int hashCode() {
    return internalValue ? 1 : 0;
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof BooleanValue) {
      return Util.compareBooleans(internalValue, ((BooleanValue) o).internalValue);
    } else {
      return defaultCompareTo(o);
    }
  }

  public static BooleanValue valueOf(Boolean b) {
    return b ? TRUE_VALUE : FALSE_VALUE;
  }
}
