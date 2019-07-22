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

package com.google.firebase.firestore.core;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.NullValue;
import com.google.firebase.firestore.model.value.ReferenceValue;
import com.google.firebase.firestore.util.Assert;
import java.util.Arrays;

/** Represents a filter to be applied to query. */
public class FieldFilter extends Filter {
  private final Operator operator;

  private final FieldValue value;

  private final FieldPath field;

  /**
   * Creates a new filter that compares fields and values. Only intended to be called from
   * Filter.create().
   */
  protected FieldFilter(FieldPath field, Operator operator, FieldValue value) {
    this.field = field;
    this.operator = operator;
    this.value = value;
  }

  public Operator getOperator() {
    return operator;
  }

  @Override
  public FieldPath getField() {
    return field;
  }

  public FieldValue getValue() {
    return value;
  }

  /**
   * Gets a Filter instance for the provided path, operator, and value.
   *
   * <p>Note that if the relation operator is EQUAL and the value is null or NaN, this will return
   * the appropriate NullFilter or NaNFilter class instead of a FieldFilter.
   */
  public static FieldFilter create(FieldPath path, Operator operator, FieldValue value) {
    if (path.isKeyField()) {
      if (operator == Operator.IN) {
        hardAssert(
            value instanceof ArrayValue,
            "Comparing on key with IN, but an array value was not a RefValue");
        return new KeyFieldInFilter(path, (ArrayValue) value);
      } else {
        hardAssert(
            value instanceof ReferenceValue,
            "Comparing on key, but filter value not a ReferenceValue");
        hardAssert(
            operator != Operator.ARRAY_CONTAINS && operator != Operator.ARRAY_CONTAINS_ANY,
            operator.toString() + "queries don't make sense on document keys");
        return new KeyFieldFilter(path, operator, (ReferenceValue) value);
      }
    } else if (value.equals(NullValue.nullValue())) {
      if (operator != Filter.Operator.EQUAL) {
        throw new IllegalArgumentException(
            "Invalid Query. Null supports only equality comparisons (via whereEqualTo()).");
      }
      return new FieldFilter(path, operator, value);
    } else if (value.equals(DoubleValue.NaN)) {
      if (operator != Filter.Operator.EQUAL) {
        throw new IllegalArgumentException(
            "Invalid Query. NaN supports only equality comparisons (via whereEqualTo()).");
      }
      return new FieldFilter(path, operator, value);
    } else if (operator == Operator.ARRAY_CONTAINS) {
      return new ArrayContainsFilter(path, value);
    } else if (operator == Operator.IN) {
      hardAssert(value instanceof ArrayValue, "IN filter has invalid value: " + value.toString());
      return new InFilter(path, (ArrayValue) value);
    } else if (operator == Operator.ARRAY_CONTAINS_ANY) {
      hardAssert(
          value instanceof ArrayValue,
          "ARRAY_CONTAINS_ANY filter has invalid value: " + value.toString());
      return new ArrayContainsAnyFilter(path, (ArrayValue) value);
    } else {
      return new FieldFilter(path, operator, value);
    }
  }

  @Override
  public boolean matches(Document doc) {
    FieldValue other = doc.getField(field);
    // Only compare types with matching backend order (such as double and int).
    return other != null
        && value.typeOrder() == other.typeOrder()
        && this.matchesComparison(other.compareTo(value));
  }

  protected boolean matchesComparison(int comp) {
    switch (operator) {
      case LESS_THAN:
        return comp < 0;
      case LESS_THAN_OR_EQUAL:
        return comp <= 0;
      case EQUAL:
        return comp == 0;
      case GREATER_THAN:
        return comp > 0;
      case GREATER_THAN_OR_EQUAL:
        return comp >= 0;
      default:
        throw Assert.fail("Unknown FieldFilter operator: %s", operator);
    }
  }

  public boolean isInequality() {
    return Arrays.asList(
            Operator.LESS_THAN,
            Operator.LESS_THAN_OR_EQUAL,
            Operator.GREATER_THAN,
            Operator.GREATER_THAN_OR_EQUAL)
        .contains(operator);
  }

  @Override
  public String getCanonicalId() {
    // TODO: Technically, this won't be unique if two values have the same description,
    // such as the int 3 and the string "3". So we should add the types in here somehow, too.
    return getField().canonicalString() + getOperator().toString() + getValue().toString();
  }

  @Override
  public String toString() {
    return field.canonicalString() + " " + operator + " " + value;
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof FieldFilter)) {
      return false;
    }
    FieldFilter other = (FieldFilter) o;
    return operator == other.operator && field.equals(other.field) && value.equals(other.value);
  }

  @Override
  public int hashCode() {
    int result = 37;
    result = 31 * result + operator.hashCode();
    result = 31 * result + field.hashCode();
    result = 31 * result + value.hashCode();
    return result;
  }
}
