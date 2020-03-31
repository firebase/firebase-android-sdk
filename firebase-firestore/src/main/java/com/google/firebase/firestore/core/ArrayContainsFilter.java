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

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;

/** A Filter that implements the array-contains operator. */
public class ArrayContainsFilter extends FieldFilter {
  ArrayContainsFilter(FieldPath field, Value value) {
    super(field, Operator.ARRAY_CONTAINS, value);
  }

  @Override
  public boolean matches(Document doc) {
    Value other = doc.getField(getField());
    return Values.isArray(other) && Values.contains(other.getArrayValue(), getValue());
  }
}
