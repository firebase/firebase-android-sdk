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
import java.util.Set;

/**
 * Provides a set of fields that can be used to partially patch a document. The FieldMask is used in
 * conjunction with ObjectValue.
 *
 * <p>Examples: foo - Overwrites foo entirely with the provided value. If foo is not present in the
 * companion ObjectValue, the field is deleted. foo.bar - Overwrites only the field bar of the
 * object foo. If foo is not an object, foo is replaced with an object containing foo.
 */
public final class FieldMask {
  public static FieldMask fromSet(Set<FieldPath> mask) {
    return new FieldMask(mask);
  }

  private final Set<FieldPath> mask;

  private FieldMask(Set<FieldPath> mask) {
    this.mask = mask;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    FieldMask fieldMask = (FieldMask) o;
    return mask.equals(fieldMask.mask);
  }

  @Override
  public String toString() {
    return "FieldMask{mask=" + mask.toString() + "}";
  }

  /**
   * Verifies that 'fieldPath' is included by at least one field in this field mask.
   *
   * <p>This is an O(n) operation, where 'n' is the size of the field mask.
   */
  public boolean covers(FieldPath fieldPath) {
    for (FieldPath fieldMaskPath : this.mask) {
      if (fieldMaskPath.isPrefixOf(fieldPath)) {
        return true;
      }
    }

    return false;
  }

  @Override
  public int hashCode() {
    return mask.hashCode();
  }

  public Set<FieldPath> getMask() {
    return mask;
  }
}
