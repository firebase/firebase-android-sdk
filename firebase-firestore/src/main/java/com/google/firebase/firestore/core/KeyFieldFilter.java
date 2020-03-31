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
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;

/** Filter that matches on key fields (i.e. '__name__'). */
public class KeyFieldFilter extends FieldFilter {
  private final DocumentKey key;

  KeyFieldFilter(FieldPath field, Operator operator, Value value) {
    super(field, operator, value);
    hardAssert(Values.isReferenceValue(value), "KeyFieldFilter expects a ReferenceValue");
    key = DocumentKey.fromName(getValue().getReferenceValue());
  }

  @Override
  public boolean matches(Document doc) {
    int comparator = doc.getKey().compareTo(key);
    return this.matchesComparison(comparator);
  }
}
