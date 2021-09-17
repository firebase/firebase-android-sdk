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

import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;
import java.util.List;

/**
 * Represents a bound of a query.
 *
 * <p>The bound is specified with the given components representing a position and whether it's just
 * before or just after the position (relative to whatever the query order is).
 *
 * <p>The position represents a logical index position for a query. It's a prefix of values for the
 * (potentially implicit) order by clauses of a query.
 *
 * <p>Bound provides a function to determine whether a document comes before or after a bound. This
 * is influenced by whether the position is just before or just after the provided values.
 */
public final class Bound {

  /**
   * Whether this bound includes the provided position (e.g. for {#code startAt()} or {#code
   * endAt()})
   */
  private final boolean inclusive;

  /** The index position of this bound */
  private final List<Value> position;

  public Bound(List<Value> position, boolean inclusive) {
    this.position = position;
    this.inclusive = inclusive;
  }

  public List<Value> getPosition() {
    return position;
  }

  /**
   * Whether the bound includes the documents that lie directly on the bound. Returns {@code true}
   * for {@code startAt()} and {@code endAt()} and false for {@code startAfter()} and {@code
   * endBefore()}.
   */
  public boolean isInclusive() {
    return inclusive;
  }

  public String positionString() {
    // TODO: Make this collision robust.
    StringBuilder builder = new StringBuilder();
    boolean first = true;
    for (Value indexComponent : position) {
      if (!first) {
        builder.append(",");
      }
      first = false;
      builder.append(Values.canonicalId(indexComponent));
    }
    return builder.toString();
  }

  /** Returns true if a document sorts before a bound using the provided sort order. */
  public boolean sortsBeforeDocument(List<OrderBy> orderBy, Document document) {
    int comparison = compareToDocument(orderBy, document);
    return inclusive ? comparison <= 0 : comparison < 0;
  }

  /** Returns true if a document sorts after a bound using the provided sort order. */
  public boolean sortsAfterDocument(List<OrderBy> orderBy, Document document) {
    int comparison = compareToDocument(orderBy, document);
    return inclusive ? comparison >= 0 : comparison > 0;
  }

  private int compareToDocument(List<OrderBy> orderBy, Document document) {
    hardAssert(position.size() <= orderBy.size(), "Bound has more components than query's orderBy");
    int comparison = 0;
    for (int i = 0; i < position.size(); i++) {
      OrderBy orderByComponent = orderBy.get(i);
      Value component = position.get(i);
      if (orderByComponent.field.equals(FieldPath.KEY_PATH)) {
        hardAssert(
            Values.isReferenceValue(component),
            "Bound has a non-key value where the key path is being used %s",
            component);
        comparison =
            DocumentKey.fromName(component.getReferenceValue()).compareTo(document.getKey());
      } else {
        Value docValue = document.getField(orderByComponent.getField());
        hardAssert(
            docValue != null, "Field should exist since document matched the orderBy already.");
        comparison = Values.compare(component, docValue);
      }

      if (orderByComponent.getDirection().equals(Direction.DESCENDING)) {
        comparison = comparison * -1;
      }

      if (comparison != 0) {
        break;
      }
    }
    return comparison;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Bound bound = (Bound) o;

    return inclusive == bound.inclusive && position.equals(bound.position);
  }

  @Override
  public int hashCode() {
    int result = (inclusive ? 1 : 0);
    result = 31 * result + position.hashCode();
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Bound(inclusive=");
    builder.append(inclusive);
    builder.append(", position=");
    for (int i = 0; i < position.size(); i++) {
      if (i > 0) {
        builder.append(" and ");
      }
      builder.append(Values.canonicalId(position.get(i)));
    }
    builder.append(")");
    return builder.toString();
  }
}
