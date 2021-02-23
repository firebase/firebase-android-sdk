// Copyright 2020 Google LLC
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
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;

/** A Filter that implements the not-in operator. */
public class NotInFilter extends FieldFilter {
  NotInFilter(FieldPath field, Value value) {
    super(field, Operator.NOT_IN, value);
    hardAssert(Values.isArray(value), "NotInFilter expects an ArrayValue");
  }

  @Override
  public boolean matches(Document doc) {
    if (Values.contains(getValue().getArrayValue(), Values.NULL_VALUE)) {
      return false;
    }
    Value other = doc.getField(getField());
    return other != null && !Values.contains(getValue().getArrayValue(), other);
  }
}
