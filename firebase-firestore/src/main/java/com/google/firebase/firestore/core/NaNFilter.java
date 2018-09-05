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
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;

/** Filter that matches NaN (not-a-number) fields. */
public class NaNFilter extends Filter {
  private final FieldPath fieldPath;

  public NaNFilter(FieldPath fieldPath) {
    this.fieldPath = fieldPath;
  }

  @Override
  public FieldPath getField() {
    return fieldPath;
  }

  @Override
  public boolean matches(Document doc) {
    FieldValue fieldValue = doc.getField(fieldPath);
    return fieldValue != null && fieldValue.equals(DoubleValue.NaN);
  }

  @Override
  public String getCanonicalId() {
    return fieldPath.canonicalString() + " IS NaN";
  }

  @Override
  public String toString() {
    return getCanonicalId();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof NaNFilter)) {
      return false;
    }
    NaNFilter other = (NaNFilter) o;
    return fieldPath.equals(other.fieldPath);
  }

  @Override
  public int hashCode() {
    int result = 41;
    result = 31 * result + fieldPath.hashCode();
    return result;
  }
}
