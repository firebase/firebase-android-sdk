// Copyright 2019 Google LLC
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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.ReferenceValue;

public class KeyFieldInFilter extends FieldFilter {
  KeyFieldInFilter(FieldPath field, ArrayValue value) {
    super(field, Operator.IN, value);
    ArrayValue arrayValue = (ArrayValue) getValue();
    for (FieldValue refValue : arrayValue.getInternalValue()) {
      hardAssert(
          refValue instanceof ReferenceValue,
          "Comparing on key with IN, but an array value was not a ReferenceValue");
    }
  }

  @Override
  public boolean matches(Document doc) {
    ArrayValue arrayValue = (ArrayValue) getValue();
    for (FieldValue refValue : arrayValue.getInternalValue()) {
      if (doc.getKey().equals(((ReferenceValue) refValue).value())) {
        return true;
      }
    }
    return false;
  }
}
