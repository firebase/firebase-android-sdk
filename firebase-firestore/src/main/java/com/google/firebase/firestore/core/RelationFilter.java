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
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.value.ArrayValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.util.Assert;

/** Represents a filter to be applied to query. */
public class RelationFilter extends Filter {
  private final Operator operator;

  private final FieldValue value;

  private final FieldPath field;

  /**
   * Creates a new filter that compares fields and values. Only intended to be called from
   * Filter.create().
   */
  RelationFilter(FieldPath field, Operator operator, FieldValue value) {
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

  @Override
  public boolean matches(Document doc) {
    if (this.field.isKeyField()) {
      Object refValue = value.value();
      hardAssert(
          refValue instanceof DocumentKey, "Comparing on key, but filter value not a DocumentKey");
      hardAssert(
          operator != Operator.ARRAY_CONTAINS,
          "ARRAY_CONTAINS queries don't make sense on document keys.");
      int comparison = DocumentKey.comparator().compare(doc.getKey(), (DocumentKey) refValue);
      return matchesComparison(comparison);
    } else {
      FieldValue value = doc.getField(field);
      return value != null && matchesValue(doc.getField(field));
    }
  }

  private boolean matchesValue(FieldValue other) {
    if (operator == Operator.ARRAY_CONTAINS) {
      return other instanceof ArrayValue && ((ArrayValue) other).getInternalValue().contains(value);
    } else {
      // Only compare types with matching backend order (such as double and int).
      return value.typeOrder() == other.typeOrder()
          && matchesComparison(other.compareTo(this.value));
    }
  }

  private boolean matchesComparison(int comp) {
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
        throw Assert.fail("Unknown operator: %s", operator);
    }
  }

  public boolean isInequality() {
    return operator != Operator.EQUAL && operator != Operator.ARRAY_CONTAINS;
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
    if (o == null || !(o instanceof RelationFilter)) {
      return false;
    }
    RelationFilter other = (RelationFilter) o;
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
