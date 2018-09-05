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

package com.google.firebase.firestore.model.mutation;

import com.google.firebase.firestore.model.FieldPath;

/** A field path and the operation to perform upon it. */
public final class FieldTransform {

  private final FieldPath fieldPath;
  private final TransformOperation operation;

  public FieldTransform(FieldPath fieldPath, TransformOperation operation) {
    this.fieldPath = fieldPath;
    this.operation = operation;
  }

  public FieldPath getFieldPath() {
    return fieldPath;
  }

  public TransformOperation getOperation() {
    return operation;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FieldTransform that = (FieldTransform) o;

    if (!fieldPath.equals(that.fieldPath)) {
      return false;
    }
    return operation.equals(that.operation);
  }

  @Override
  public int hashCode() {
    int result = fieldPath.hashCode();
    result = 31 * result + operation.hashCode();
    return result;
  }
}
