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

import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.List;

/** A wrapper for Array values in Firestore */
public class ArrayValue extends FieldValue {
  ArrayValue(Value value) {
    super(value);
  }

  public static ArrayValue fromList(List<FieldValue> list) {
    com.google.firestore.v1.ArrayValue.Builder builder =
        com.google.firestore.v1.ArrayValue.newBuilder();
    for (FieldValue value : list) {
      builder.addValues(value.getProto());
    }
    return new ArrayValue(Value.newBuilder().setArrayValue(builder).build());
  }

  /** Converts all elements to a list of FieldValues. */
  public List<FieldValue> getValues() {
    List<FieldValue> result = new ArrayList<>();
    for (Value element : internalValue.getArrayValue().getValuesList()) {
      result.add(FieldValue.valueOf(element));
    }
    return result;
  }

  /**
   * Returns true if this ArrayValue contains the specified element.
   *
   * <p>This method creates fewer temporary objects than invoking `getValues().contains()` (if the
   * value is contained in the backing ArrayValue).
   */
  public boolean contains(FieldValue value) {
    for (Value element : internalValue.getArrayValue().getValuesList()) {
      if (value.equals(FieldValue.valueOf(element))) {
        return true;
      }
    }
    return false;
  }
}
