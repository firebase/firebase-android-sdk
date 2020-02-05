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
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.value.ProtoValues;
import com.google.firestore.v1.Value;

public class KeyFieldInFilter extends FieldFilter {
  KeyFieldInFilter(FieldPath field, Value value) {
    super(field, Operator.IN, value);
    hardAssert(ProtoValues.isArray(value), "KeyFieldInFilter expects an ArrayValue");
    for (Value element : value.getArrayValue().getValuesList()) {
      hardAssert(
          ProtoValues.isReferenceValue(element),
          "Comparing on key with IN, but an array value was not a ReferenceValue");
    }
  }

  @Override
  public boolean matches(Document doc) {
    for (Value refValue : getValue().getArrayValue().getValuesList()) {
      DocumentKey referencedKey = DocumentKey.fromName(refValue.getReferenceValue());
      if (doc.getKey().equals(referencedKey)) {
        return true;
      }
    }
    return false;
  }
}
