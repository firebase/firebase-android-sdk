// Copyright 2023 Google LLC
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
import androidx.annotation.RestrictTo;
import java.util.Objects;

// TODO(sumavg): Remove the `hide` and scope annotations.
/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public abstract class AggregateField {
  @Nullable private final FieldPath fieldPath;

  @NonNull private final String operator;

  @NonNull private final String alias;

  private AggregateField(@Nullable FieldPath fieldPath, @NonNull String operator) {
    this.fieldPath = fieldPath;
    this.operator = operator;

    // Use $operator_$field format if it's an aggregation of a specific field. For example: sum_foo.
    // Use $operator format if there's no field. For example: count.
    this.alias = operator + (fieldPath == null ? "" : "_" + fieldPath);
  }

  /**
   * Returns the field on which the aggregation takes place. Returns an empty string if there's no
   * field (e.g. for count).
   */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @NonNull
  public String getFieldPath() {
    return fieldPath == null ? "" : fieldPath.toString();
  }

  /** Returns the alias used internally for this aggregate field. */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @NonNull
  public String getAlias() {
    return alias;
  }

  /** Returns a string representation of this aggregation's operator. For example: "sum" */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  @NonNull
  public String getOperator() {
    return operator;
  }

  /**
   * Returns true if the given object is equal to this object. Two `AggregateField` objects are
   * considered equal if they have the same operator and operate on the same field.
   */
  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof AggregateField)) {
      return false;
    }

    AggregateField otherAggregateField = (AggregateField) other;
    if (fieldPath == null || otherAggregateField.fieldPath == null) {
      return fieldPath == null && otherAggregateField.fieldPath == null;
    }
    return operator.equals(otherAggregateField.getOperator())
        && getFieldPath().equals(otherAggregateField.getFieldPath());
  }

  /** Calculates and returns the hash code for this object. */
  @Override
  public int hashCode() {
    return Objects.hash(getOperator(), getFieldPath());
  }

  @NonNull
  public static CountAggregateField count() {
    return new CountAggregateField();
  }

  @NonNull
  public static SumAggregateField sum(@NonNull String field) {
    return new SumAggregateField(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static SumAggregateField sum(@NonNull FieldPath fieldPath) {
    return new SumAggregateField(fieldPath);
  }

  @NonNull
  public static AverageAggregateField average(@NonNull String field) {
    return new AverageAggregateField(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static AverageAggregateField average(@NonNull FieldPath fieldPath) {
    return new AverageAggregateField(fieldPath);
  }

  public static class CountAggregateField extends AggregateField {
    private CountAggregateField() {
      super(null, "count");
    }
  }

  public static class SumAggregateField extends AggregateField {
    private SumAggregateField(@NonNull FieldPath fieldPath) {
      super(fieldPath, "sum");
    }
  }

  public static class AverageAggregateField extends AggregateField {
    private AverageAggregateField(@NonNull FieldPath fieldPath) {
      super(fieldPath, "average");
    }
  }
}
