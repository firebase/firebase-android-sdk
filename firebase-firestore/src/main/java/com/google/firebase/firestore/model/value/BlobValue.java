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

import com.google.firebase.firestore.Blob;

/** A wrapper for blob values in Firestore. */
public class BlobValue extends FieldValue {
  private final Blob internalValue;

  private BlobValue(Blob blob) {
    internalValue = blob;
  }

  @Override
  public int typeOrder() {
    return TYPE_ORDER_BLOB;
  }

  @Override
  public Blob value() {
    return internalValue;
  }

  @Override
  public boolean equals(Object o) {
    return (o instanceof BlobValue) && internalValue.equals(((BlobValue) o).internalValue);
  }

  @Override
  public int hashCode() {
    return internalValue.hashCode();
  }

  @Override
  public int compareTo(FieldValue o) {
    if (o instanceof BlobValue) {
      return internalValue.compareTo(((BlobValue) o).internalValue);
    } else {
      return defaultCompareTo(o);
    }
  }

  public static BlobValue valueOf(Blob blob) {
    return new BlobValue(blob);
  }
}
