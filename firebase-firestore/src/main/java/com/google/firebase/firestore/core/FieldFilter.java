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

import static com.google.firebase.firestore.util.Preconditions.checkNotNull;

import androidx.annotation.NonNull;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.util.Assert;
import com.google.firebase.firestore.util.Util;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import java.util.Arrays;
import java.util.List;

/** Represents a single-field filter to be applied to query. */
public class FieldFilter extends Filter {
  public enum Operator {
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    EQUAL("=="),
    NOT_EQUAL("!="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    ARRAY_CONTAINS("array_contains"),
    ARRAY_CONTAINS_ANY("array_contains_any"),
    IN("in"),
    NOT_IN("not_in");

    private final String text;

    Operator(String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  private final Operator operator;

  private final Value value;

  private final FieldPath field;

  private final Object valueObject;

  /**
   * Creates a new filter that compares fields and values. Only intended to be called from
   * Filter.create().
   */
  protected FieldFilter(FieldPath field, Operator operator, Object valueObject) {
    this.field = field;
    this.operator = operator;
    this.valueObject = valueObject;
    this.value = null;
  }

  /**
   * Creates a new filter that compares fields and values. Only intended to be called from
   * Filter.create(). This is a more specialized constructor that takes a parsed Value.
   */
  protected FieldFilter(FieldPath field, Operator operator, Value value) {
    this.field = field;
    this.operator = operator;
    this.value = value;
    this.valueObject = null;
  }

  public Operator getOperator() {
    return operator;
  }

  public FieldPath getField() {
    return field;
  }

  public Value getValue() {
    return value;
  }

  /**
   * Gets a Filter instance for the provided path, operator, and value.
   *
   * <p>Note that if the relation operator is EQUAL and the value is null or NaN, this will return
   * the appropriate NullFilter or NaNFilter class instead of a FieldFilter.
   */
  public static FieldFilter create(
      @NonNull FieldPath path, @NonNull Operator operator, Object valueObject) {
    checkNotNull(path, "Provided field path must not be null.");
    checkNotNull(operator, "Provided op must not be null.");

    // If we have a value that has not been parsed, store it and return a FieldFilter.
    if (!(valueObject instanceof Value)) {
      return new FieldFilter(path, operator, valueObject);
    }

    Value value = (Value) valueObject;

    if (path.isKeyField()) {
      if (operator == Operator.IN) {
        return new KeyFieldInFilter(path, value);
      } else if (operator == Operator.NOT_IN) {
        return new KeyFieldNotInFilter(path, value);
      } else {
        if (operator == Operator.ARRAY_CONTAINS || operator == Operator.ARRAY_CONTAINS_ANY) {
          throw new IllegalArgumentException(
              "Invalid query. You can't perform '"
                  + operator.toString()
                  + "' queries on FieldPath.documentId().");
        }
        return new KeyFieldFilter(path, operator, value);
      }
    } else if (operator == Operator.ARRAY_CONTAINS) {
      return new ArrayContainsFilter(path, value);
    } else if (operator == Operator.IN) {
      return new InFilter(path, value);
    } else if (operator == Operator.ARRAY_CONTAINS_ANY) {
      return new ArrayContainsAnyFilter(path, value);
    } else if (operator == Operator.NOT_IN) {
      return new NotInFilter(path, value);
    } else {
      return new FieldFilter(path, operator, value);
    }
  }

  /** Validates that the value passed into a disjunctive filter satisfies all array requirements. */
  protected void validateDisjunctiveFilterElements(Object valueObject, Operator op) {
    if (!(valueObject instanceof List) || ((List) valueObject).size() == 0) {
      throw new IllegalArgumentException(
          "Invalid Query. A non-empty array is required for '" + op.toString() + "' filters.");
    }
    if (((List) valueObject).size() > 10) {
      throw new IllegalArgumentException(
          "Invalid Query. '"
              + op.toString()
              + "' filters support a maximum of 10 elements in the value array.");
    }
  }

  /**
   * Parses the given documentIdValue into a ReferenceValue, throwing appropriate errors if the
   * value is anything other than a DocumentReference or String, or if the string is malformed.
   */
  protected Value parseDocumentIdValue(
      Object documentIdValue, FirebaseFirestore firestore, Query query) {
    if (documentIdValue instanceof String) {
      String documentId = (String) documentIdValue;
      if (documentId.isEmpty()) {
        throw new IllegalArgumentException(
            "Invalid query. When querying with FieldPath.documentId() you must provide a valid "
                + "document ID, but it was an empty string.");
      }
      if (!query.isCollectionGroupQuery() && documentId.contains("/")) {
        throw new IllegalArgumentException(
            "Invalid query. When querying a collection by FieldPath.documentId() you must "
                + "provide a plain document ID, but '"
                + documentId
                + "' contains a '/' character.");
      }
      ResourcePath path = query.getPath().append(ResourcePath.fromString(documentId));
      if (!DocumentKey.isDocumentKey(path)) {
        throw new IllegalArgumentException(
            "Invalid query. When querying a collection group by FieldPath.documentId(), the "
                + "value provided must result in a valid document path, but '"
                + path
                + "' is not because it has an odd number of segments ("
                + path.length()
                + ").");
      }
      return Values.refValue(firestore.getDatabaseId(), DocumentKey.fromPath(path));
    } else if (documentIdValue instanceof DocumentReference) {
      DocumentReference ref = (DocumentReference) documentIdValue;
      return Values.refValue(firestore.getDatabaseId(), ref.getKey());
    } else {
      throw new IllegalArgumentException(
          "Invalid query. When querying with FieldPath.documentId() you must provide a valid "
              + "String or DocumentReference, but it was of type: "
              + Util.typeName(documentIdValue));
    }
  }

  /**
   * If a `Value` has not been set for this filter, `valueObject` object will be parsed and a new
   * filter with this `Value` will be returned. Otherwise returns the filter itself.
   */
  public FieldFilter parseValue(Query query, FirebaseFirestore firestore) {
    // If `parseValue` is called multiple times, we only need to do the parsing the first time.
    if (value != null) {
      return this;
    }

    Value parsedValue;
    if (field.isKeyField()) {
      if (operator == Operator.IN || operator == Operator.NOT_IN) {
        validateDisjunctiveFilterElements(valueObject, operator);
        ArrayValue.Builder referenceList = ArrayValue.newBuilder();
        for (Object arrayValue : (List) valueObject) {
          referenceList.addValues(parseDocumentIdValue(arrayValue, firestore, query));
        }
        parsedValue = Value.newBuilder().setArrayValue(referenceList).build();
      } else {
        parsedValue = parseDocumentIdValue(valueObject, firestore, query);
      }
    } else {
      if (operator == Operator.IN
          || operator == Operator.NOT_IN
          || operator == Operator.ARRAY_CONTAINS_ANY) {
        validateDisjunctiveFilterElements(valueObject, operator);
      }
      parsedValue =
          firestore
              .getUserDataReader()
              .parseQueryValue(valueObject, operator == Operator.IN || operator == Operator.NOT_IN);
    }
    return FieldFilter.create(field, operator, parsedValue);
  }

  /** Applies this filter to the given query */
  @Override
  public Query apply(Query query, FirebaseFirestore firestore) {
    FieldFilter parsedFilter = parseValue(query, firestore);
    return query.filter(parsedFilter);
  }

  @Override
  public boolean matches(Document doc) {
    Value other = doc.getField(field);
    // Types do not have to match in NOT_EQUAL filters.
    if (operator == Operator.NOT_EQUAL) {
      return other != null && this.matchesComparison(Values.compare(other, value));
    }
    // Only compare types with matching backend order (such as double and int).
    return other != null
        && Values.typeOrder(other) == Values.typeOrder(value)
        && this.matchesComparison(Values.compare(other, value));
  }

  protected boolean matchesComparison(int comp) {
    switch (operator) {
      case LESS_THAN:
        return comp < 0;
      case LESS_THAN_OR_EQUAL:
        return comp <= 0;
      case EQUAL:
        return comp == 0;
      case NOT_EQUAL:
        return comp != 0;
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
            Operator.GREATER_THAN_OR_EQUAL,
            Operator.NOT_EQUAL,
            Operator.NOT_IN)
        .contains(operator);
  }

  @Override
  public String getCanonicalId() {
    // TODO: Technically, this won't be unique if two values have the same description,
    // such as the int 3 and the string "3". So we should add the types in here somehow, too.
    return getField().canonicalString() + getOperator().toString() + Values.canonicalId(getValue());
  }

  @Override
  public String toString() {
    return field.canonicalString() + " " + operator + " " + Values.canonicalId(value);
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
