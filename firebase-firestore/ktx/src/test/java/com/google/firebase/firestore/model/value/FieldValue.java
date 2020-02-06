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

package com.google.firebase.firestore.model.value;

import androidx.annotation.NonNull;
import com.google.firestore.v1.Value;

/**
 * Represents a FieldValue that is backed by a single Firestore V1 Value proto and implements
 * Firestore's Value semantics for ordering and equality.
 */
// TODO(mrschmidt): Drop this class
public class FieldValue implements Comparable<FieldValue> {
  final Value internalValue;

  public FieldValue(Value value) {
    this.internalValue = value;
  }

  /** Returns Firestore Value Protobuf that backs this FieldValuee */
  public Value getProto() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    } else if (o instanceof FieldValue) {
      return ProtoValues.equals(internalValue, ((FieldValue) o).internalValue);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(@NonNull FieldValue other) {
    return ProtoValues.compare(internalValue, other.internalValue);
  }

  @Override
  public String toString() {
    return ProtoValues.canonicalId(internalValue);
  }
}
