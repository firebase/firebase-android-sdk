// Copyright 2024 Google LLC
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
import androidx.annotation.Nullable;
import java.util.Arrays;

/**
 * Represent a vector type in Firestore documents.
 * Create an instance with {@link FieldValue#vector(double[])}.
 */
public class VectorValue {
  private final double[] values;

  VectorValue(@Nullable double[] values) {
    this.values = (values == null) ? new double[] {} : values.clone();
  }

  /**
   * Returns a representation of the vector as an array of doubles.
   *
   * @return A representation of the vector as an array of doubles
   */
  @NonNull
  public double[] toArray() {
    return this.values.clone();
  }

  /**
   * Returns true if this VectorValue is equal to the provided object.
   *
   * @param obj The object to compare against.
   * @return Whether this VectorValue is equal to the provided object.
   */
  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null || getClass() != obj.getClass()) {
      return false;
    }
    VectorValue otherArray = (VectorValue) obj;
    return Arrays.equals(this.values, otherArray.values);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(values);
  }
}
