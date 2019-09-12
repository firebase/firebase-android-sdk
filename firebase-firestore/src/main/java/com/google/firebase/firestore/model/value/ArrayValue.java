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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** A wrapper for Array values in Firestore */
public class ArrayValue extends FieldValue {

  private final List<FieldValue> internalValue;

  private ArrayValue(List<FieldValue> value) {
    internalValue = Collections.unmodifiableList(value);
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof ArrayValue) && internalValue.equals(((ArrayValue) o).internalValue);
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof ArrayValue) {
      ArrayValue other = (ArrayValue) o;
      int minLength = Math.min(internalValue.size(), other.internalValue.size());
      for (int i = 0; i < minLength; i++) {
        int cmp = internalValue.get(i).compareTo(((ArrayValue) o).internalValue.get(i));
        if (cmp != 0) {
          return cmp;
        }
      }
      return Util.compareIntegers(internalValue.size(), other.internalValue.size());
    } else {
      return defaultCompareTo(o);
    }
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_ARRAY;
  }

  @Override
  public List<Object> value() {
    // Recursively convert the array into the value that users will see in document snapshots.
    List<Object> res = new ArrayList<>(internalValue.size());
    for (FieldValue v : internalValue) {
      res.add(v.value());
    }
    return res;
  }

  public List<FieldValue> getInternalValue() {
    return internalValue;
  }

  public static ArrayValue fromList(List<FieldValue> list) {
    return new ArrayValue(list);
  }
}
