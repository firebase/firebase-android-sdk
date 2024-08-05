package com.google.firebase.firestore;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import java.io.Serializable;
import java.util.Arrays;

public class VectorValue implements Serializable {
  private final double[] values;

  VectorValue(@Nullable double[] values) {
    if (values == null) this.values = new double[] {};
    else this.values = values.clone();
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
