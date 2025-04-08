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

/** Represents a 32-bit integer type in Firestore documents. */
public final class Int32Value {
  public final int value;

  public Int32Value(int value) {
    this.value = value;
  }

  /**
   * Returns true if this Int32Value is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this Int32Value is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof Int32Value)) {
      return false;
    }
    Int32Value other = (Int32Value) obj;
    return value == other.value;
  }

  @Override
  public int hashCode() {
    return value;
  }

  @Override
  public String toString() {
    return "Int32Value{value=" + value + "}";
  }
}
