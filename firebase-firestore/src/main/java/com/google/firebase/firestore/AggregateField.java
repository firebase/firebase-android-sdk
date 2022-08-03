// Copyright 2022 Google LLC
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
import java.util.Objects;

public abstract class AggregateField {

  private AggregateField() {}

  @NonNull
  public static CountAggregateField count() {
    return new CountAggregateField();
  }

  @NonNull
  public static MinAggregateField min(@NonNull String field) {
    return min(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static MinAggregateField min(@NonNull FieldPath field) {
    return new MinAggregateField(field);
  }

  @NonNull
  public static MaxAggregateField max(@NonNull String field) {
    return max(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static MaxAggregateField max(@NonNull FieldPath field) {
    return new MaxAggregateField(field);
  }

  @NonNull
  public static AverageAggregateField average(@NonNull String field) {
    return average(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static AverageAggregateField average(@NonNull FieldPath field) {
    return new AverageAggregateField(field);
  }

  @NonNull
  public static SumAggregateField sum(@NonNull String field) {
    return sum(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static SumAggregateField sum(@NonNull FieldPath field) {
    return new SumAggregateField(field);
  }

  @NonNull
  public static FirstAggregateField first(@NonNull String field) {
    return first(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static FirstAggregateField first(@NonNull FieldPath field) {
    return new FirstAggregateField(field);
  }

  @NonNull
  public static LastAggregateField last(@NonNull String field) {
    return last(FieldPath.fromDotSeparatedPath(field));
  }

  @NonNull
  public static LastAggregateField last(@NonNull FieldPath field) {
    return new LastAggregateField(field);
  }

  @Override
  public abstract boolean equals(Object obj);

  @Override
  public abstract int hashCode();

  @Override
  public abstract String toString();

  public static final class CountAggregateField extends AggregateField {

    @Nullable private Integer upTo;

    CountAggregateField() {}

    CountAggregateField(@Nullable Integer upTo) {
      this.upTo = upTo;
    }

    public CountAggregateField upTo(int upTo) {
      if (upTo < 0) {
        throw new IllegalArgumentException("upTo==" + upTo);
      }
      return new CountAggregateField(upTo);
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      CountAggregateField other = (CountAggregateField) obj;
      return Objects.equals(upTo, other.upTo);
    }

    @Override
    public int hashCode() {
      return Objects.hash("COUNT", upTo);
    }

    @Override
    public String toString() {
      if (upTo == null) {
        return "COUNT";
      } else {
        return "COUNT(upTo=" + upTo + ")";
      }
    }
  }

  public static final class MinAggregateField extends AggregateField {

    @NonNull private FieldPath field;

    MinAggregateField(@NonNull FieldPath field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      return field.equals(((MinAggregateField) obj).field);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "MIN(" + field.toString() + ")";
    }
  }

  public static final class MaxAggregateField extends AggregateField {

    @NonNull private FieldPath field;

    MaxAggregateField(@NonNull FieldPath field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      return field.equals(((MaxAggregateField) obj).field);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "MAX(" + field.toString() + ")";
    }
  }

  public static final class AverageAggregateField extends AggregateField {

    @NonNull private FieldPath field;

    AverageAggregateField(@NonNull FieldPath field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      return field.equals(((AverageAggregateField) obj).field);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "AVERAGE(" + field.toString() + ")";
    }
  }

  public static final class SumAggregateField extends AggregateField {

    @NonNull private FieldPath field;

    SumAggregateField(@NonNull FieldPath field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      return field.equals(((SumAggregateField) obj).field);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "SUM(" + field.toString() + ")";
    }
  }

  public static final class FirstAggregateField extends AggregateField {

    @NonNull private FieldPath field;

    FirstAggregateField(@NonNull FieldPath field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      return field.equals(((FirstAggregateField) obj).field);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "FIRST(" + field.toString() + ")";
    }
  }

  public static final class LastAggregateField extends AggregateField {

    @NonNull private FieldPath field;

    LastAggregateField(@NonNull FieldPath field) {
      this.field = field;
    }

    @Override
    public boolean equals(Object obj) {
      if (obj == null || obj.getClass() != this.getClass()) {
        return false;
      }
      return field.equals(((LastAggregateField) obj).field);
    }

    @Override
    public int hashCode() {
      return toString().hashCode();
    }

    @Override
    public String toString() {
      return "LAST(" + field.toString() + ")";
    }
  }
}
