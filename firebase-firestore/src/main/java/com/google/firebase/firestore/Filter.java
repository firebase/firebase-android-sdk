// Copyright 2021 Google LLC
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
import com.google.firebase.firestore.core.FieldFilter;

/** @hide */
@RestrictTo(RestrictTo.Scope.LIBRARY)
public class Filter {
  private final FieldPath field;
  private final FieldFilter.Operator operator;
  private final Object value;

  private Filter(@NonNull FieldPath field, FieldFilter.Operator operator, Object value) {
    this.field = field;
    this.operator = operator;
    this.value = value;
  }

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public FieldPath getField() {
    return field;
  }

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public FieldFilter.Operator getOperator() {
    return operator;
  }

  /** @hide */
  @RestrictTo(RestrictTo.Scope.LIBRARY)
  public Object getValue() {
    return value;
  }

  @NonNull
  public static Filter equalTo(@NonNull String field, @Nullable Object value) {
    return equalTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter equalTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.EQUAL, value);
  }

  @NonNull
  public static Filter notEqualTo(@NonNull String field, @Nullable Object value) {
    return notEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter notEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.NOT_EQUAL, value);
  }

  @NonNull
  public static Filter greaterThan(@NonNull String field, @Nullable Object value) {
    return greaterThan(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter greaterThan(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.GREATER_THAN, value);
  }

  @NonNull
  public static Filter greaterThanOrEqualTo(@NonNull String field, @Nullable Object value) {
    return greaterThanOrEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter greaterThanOrEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.GREATER_THAN_OR_EQUAL, value);
  }

  @NonNull
  public static Filter lessThan(@NonNull String field, @Nullable Object value) {
    return lessThan(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter lessThan(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.LESS_THAN, value);
  }

  @NonNull
  public static Filter lessThanOrEqualTo(@NonNull String field, @Nullable Object value) {
    return lessThanOrEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter lessThanOrEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.LESS_THAN_OR_EQUAL, value);
  }

  @NonNull
  public static Filter arrayContains(@NonNull String field, @Nullable Object value) {
    return arrayContains(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter arrayContains(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.ARRAY_CONTAINS, value);
  }

  @NonNull
  public static Filter arrayContainsAny(@NonNull String field, @Nullable Object value) {
    return arrayContainsAny(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter arrayContainsAny(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.ARRAY_CONTAINS_ANY, value);
  }

  @NonNull
  public static Filter inArray(@NonNull String field, @Nullable Object value) {
    return inArray(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter inArray(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.IN, value);
  }

  @NonNull
  public static Filter notInArray(@NonNull String field, @Nullable Object value) {
    return notInArray(FieldPath.fromDotSeparatedPath(field), value);
  }

  @NonNull
  public static Filter notInArray(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new Filter(fieldPath, FieldFilter.Operator.NOT_IN, value);
  }
}
