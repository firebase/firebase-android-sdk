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

package com.google.firebase.firestore.core;

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.NullValue;

/** Filter that matches NULL values. */
public class NullFilter extends Filter {
  private final FieldPath fieldPath;

  public NullFilter(FieldPath fieldPath) {
    this.fieldPath = fieldPath;
  }

  @Override
  public FieldPath getField() {
    return fieldPath;
  }

  @Override
  public boolean matches(Document doc) {
    FieldValue fieldValue = doc.getField(fieldPath);
    return fieldValue != null && fieldValue.equals(NullValue.nullValue());
  }

  @Override
  public String getCanonicalId() {
    return fieldPath.canonicalString() + " IS NULL";
  }

  @Override
  public String toString() {
    return getCanonicalId();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof NullFilter)) {
      return false;
    }
    NullFilter other = (NullFilter) o;
    return fieldPath.equals(other.fieldPath);
  }

  @Override
  public int hashCode() {
    int result = 37;
    result = 31 * result + fieldPath.hashCode();
    return result;
  }
}
