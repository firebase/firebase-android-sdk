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
import com.google.firebase.firestore.core.FieldFilter.Operator;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * A {@code Filter} represents a restriction on one or more field values and can be used to refine
 * the results of a {@code Query}.
 */
public class Filter {
  static class UnaryFilter extends Filter {
    private final FieldPath field;
    private final Operator operator;
    private final Object value;

    public UnaryFilter(FieldPath field, Operator operator, @Nullable Object value) {
      this.field = field;
      this.operator = operator;
      this.value = value;
    }

    public FieldPath getField() {
      return field;
    }

    public Operator getOperator() {
      return operator;
    }

    @Nullable
    public Object getValue() {
      return value;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      UnaryFilter that = (UnaryFilter) o;

      return this.operator == that.operator
          && Objects.equals(this.field, that.field)
          && Objects.equals(this.value, that.value);
    }

    @Override
    public int hashCode() {
      int result = field != null ? field.hashCode() : 0;
      result = 31 * result + (operator != null ? operator.hashCode() : 0);
      result = 31 * result + (value != null ? value.hashCode() : 0);
      return result;
    }
  }

  static class CompositeFilter extends Filter {
    private final List<Filter> filters;
    private final com.google.firebase.firestore.core.CompositeFilter.Operator operator;

    public CompositeFilter(
        @NonNull List<Filter> filters,
        com.google.firebase.firestore.core.CompositeFilter.Operator operator) {
      this.filters = filters;
      this.operator = operator;
    }

    public List<Filter> getFilters() {
      return filters;
    }

    public com.google.firebase.firestore.core.CompositeFilter.Operator getOperator() {
      return operator;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      CompositeFilter that = (CompositeFilter) o;

      return this.operator == that.operator && Objects.equals(this.filters, that.filters);
    }

    @Override
    public int hashCode() {
      int result = filters != null ? filters.hashCode() : 0;
      result = 31 * result + (operator != null ? operator.hashCode() : 0);
      return result;
    }
  }

  /**
   * Creates a new filter for checking that the given field is equal to the given value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter equalTo(@NonNull String field, @Nullable Object value) {
    return equalTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given field is equal to the given value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter equalTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.EQUAL, value);
  }

  /**
   * Creates a new filter for checking that the given field is not equal to the given value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter notEqualTo(@NonNull String field, @Nullable Object value) {
    return notEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given field is not equal to the given value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter notEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.NOT_EQUAL, value);
  }

  /**
   * Creates a new filter for checking that the given field is greater than the given value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter greaterThan(@NonNull String field, @Nullable Object value) {
    return greaterThan(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given field is greater than the given value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter greaterThan(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.GREATER_THAN, value);
  }

  /**
   * Creates a new filter for checking that the given field is greater than or equal to the given
   * value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter greaterThanOrEqualTo(@NonNull String field, @Nullable Object value) {
    return greaterThanOrEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given field is greater than or equal to the given
   * value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter greaterThanOrEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.GREATER_THAN_OR_EQUAL, value);
  }

  /**
   * Creates a new filter for checking that the given field is less than the given value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter lessThan(@NonNull String field, @Nullable Object value) {
    return lessThan(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given field is less than the given value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter lessThan(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.LESS_THAN, value);
  }

  /**
   * Creates a new filter for checking that the given field is less than or equal to the given
   * value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter lessThanOrEqualTo(@NonNull String field, @Nullable Object value) {
    return lessThanOrEqualTo(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given field is less than or equal to the given
   * value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter lessThanOrEqualTo(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.LESS_THAN_OR_EQUAL, value);
  }

  /**
   * Creates a new filter for checking that the given array field contains the given value.
   *
   * @param field The field used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter arrayContains(@NonNull String field, @Nullable Object value) {
    return arrayContains(FieldPath.fromDotSeparatedPath(field), value);
  }

  /**
   * Creates a new filter for checking that the given array field contains the given value.
   *
   * @param fieldPath The field path used for the filter.
   * @param value The value used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter arrayContains(@NonNull FieldPath fieldPath, @Nullable Object value) {
    return new UnaryFilter(fieldPath, Operator.ARRAY_CONTAINS, value);
  }

  /**
   * Creates a new filter for checking that the given array field contains any of the given values.
   *
   * @param field The field used for the filter.
   * @param values The list of values used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter arrayContainsAny(
      @NonNull String field, @NonNull List<? extends Object> values) {
    return arrayContainsAny(FieldPath.fromDotSeparatedPath(field), values);
  }

  /**
   * Creates a new filter for checking that the given array field contains any of the given values.
   *
   * @param fieldPath The field path used for the filter.
   * @param values The list of values used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter arrayContainsAny(
      @NonNull FieldPath fieldPath, @NonNull List<? extends Object> values) {
    return new UnaryFilter(fieldPath, Operator.ARRAY_CONTAINS_ANY, values);
  }

  /**
   * Creates a new filter for checking that the given field equals any of the given values.
   *
   * @param field The field used for the filter.
   * @param values The list of values used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter inArray(@NonNull String field, @NonNull List<? extends Object> values) {
    return inArray(FieldPath.fromDotSeparatedPath(field), values);
  }

  /**
   * Creates a new filter for checking that the given field equals any of the given values.
   *
   * @param fieldPath The field path used for the filter.
   * @param values The list of values used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter inArray(
      @NonNull FieldPath fieldPath, @NonNull List<? extends Object> values) {
    return new UnaryFilter(fieldPath, Operator.IN, values);
  }

  /**
   * Creates a new filter for checking that the given field does not equal any of the given values.
   *
   * @param field The field path used for the filter.
   * @param values The list of values used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter notInArray(@NonNull String field, @NonNull List<? extends Object> values) {
    return notInArray(FieldPath.fromDotSeparatedPath(field), values);
  }

  /**
   * Creates a new filter for checking that the given field does not equal any of the given values.
   *
   * @param fieldPath The field path used for the filter.
   * @param values The list of values used for the filter.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter notInArray(
      @NonNull FieldPath fieldPath, @NonNull List<? extends Object> values) {
    return new UnaryFilter(fieldPath, Operator.NOT_IN, values);
  }

  /**
   * Creates a new filter that is a disjunction of the given filters. A disjunction filter includes
   * a document if it satisfies <em>any</em> of the given filters.
   *
   * @param filters The list of filters to perform a disjunction for.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter or(Filter... filters) {
    return new CompositeFilter(
        Arrays.asList(filters), com.google.firebase.firestore.core.CompositeFilter.Operator.OR);
  }

  /**
   * Creates a new filter that is a conjunction of the given filters. A conjunction filter includes
   * a document if it satisfies <em>all</em> of the given filters.
   *
   * @param filters The list of filters to perform a conjunction for.
   * @return The newly created filter.
   */
  @NonNull
  public static Filter and(Filter... filters) {
    return new CompositeFilter(
        Arrays.asList(filters), com.google.firebase.firestore.core.CompositeFilter.Operator.AND);
  }
}
