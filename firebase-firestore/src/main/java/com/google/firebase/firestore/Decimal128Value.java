// Copyright 2025 Google LLC
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

package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import java.util.Objects;

/** Represents a 128-bit decimal type in Firestore documents. */
public final class Decimal128Value {
  public final String stringValue;
  final Quadruple value;

  public Decimal128Value(@NonNull String val) {
    this.stringValue = val;
    this.value = Quadruple.fromString(val);
  }

  /**
   * Returns true if this Decimal128Value is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this Decimal128Value is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }

    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }

    Quadruple otherValue = ((Decimal128Value) obj).value;

    // Firestore considers +0 and -0 to be equal.
    if (this.value.isZero() && otherValue.isZero()) {
      return true;
    }
    return this.value.compareTo(otherValue) == 0;
  }

  @Override
  public int hashCode() {
    // Since +0 and -0 are considered equal, they should have the same hash code.
    if (this.value.isZero()) {
      return Objects.hash(Quadruple.POSITIVE_ZERO);
    }
    return this.value.hashCode();
  }

  @Override
  public String toString() {
    return "Decimal128Value{value=" + this.stringValue + "}";
  }
}
