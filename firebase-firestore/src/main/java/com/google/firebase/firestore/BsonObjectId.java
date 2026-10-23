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

/** Represents a BSON ObjectId type in Firestore documents. */
public final class BsonObjectId {
  public final String value;

  /**
   * Constructor that creates a new BSON ObjectId value with the given value.
   *
   * @param oid The 24-character hex string representing the ObjectId.
   */
  public BsonObjectId(@NonNull String oid) {
    this.value = oid;
  }

  /**
   * Returns true if this BsonObjectId is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this BsonObjectId is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof BsonObjectId)) {
      return false;
    }
    BsonObjectId other = (BsonObjectId) obj;
    return value.equals(other.value);
  }

  @Override
  public int hashCode() {
    return value.hashCode();
  }

  @Override
  public String toString() {
    return "BsonObjectId{value='" + value + "'}";
  }
}
