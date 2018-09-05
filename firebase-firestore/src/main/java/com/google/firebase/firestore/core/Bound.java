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
import com.google.firebase.firestore.model.value.FieldValue;
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

  /** Whether this bound is just before or just after the provided position */
  private final boolean before;

  /** The index position of this bound */
  private final List<FieldValue> position;

  public Bound(List<FieldValue> position, boolean before) {
    this.position = position;
    this.before = before;
  }

  public List<FieldValue> getPosition() {
    return position;
  }

  public boolean isBefore() {
    return before;
  }

  public String canonicalString() {
    // TODO: Make this collision robust.
    StringBuilder builder = new StringBuilder();
    if (before) {
      builder.append("b:");
    } else {
      builder.append("a:");
    }
    for (FieldValue indexComponent : position) {
      builder.append(indexComponent.toString());
    }
    return builder.toString();
  }

  /** Returns true if a document sorts before a bound using the provided sort order. */
  public boolean sortsBeforeDocument(List<OrderBy> orderBy, Document document) {
    hardAssert(position.size() <= orderBy.size(), "Bound has more components than query's orderBy");
    int comparison = 0;
    for (int i = 0; i < position.size(); i++) {
      OrderBy orderByComponent = orderBy.get(i);
      FieldValue component = position.get(i);
      if (orderByComponent.field.equals(FieldPath.KEY_PATH)) {
        Object refValue = component.value();
        hardAssert(
            refValue instanceof DocumentKey,
            "Bound has a non-key value where the key path is being used %s",
            component);
        comparison = ((DocumentKey) refValue).compareTo(document.getKey());
      } else {
        FieldValue docValue = document.getField(orderByComponent.getField());
        hardAssert(
            docValue != null, "Field should exist since document matched the orderBy already.");
        comparison = component.compareTo(docValue);
      }

      if (orderByComponent.getDirection().equals(Direction.DESCENDING)) {
        comparison = comparison * -1;
      }

      if (comparison != 0) {
        break;
      }
    }

    return before ? comparison <= 0 : comparison < 0;
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

    return before == bound.before && position.equals(bound.position);
  }

  @Override
  public int hashCode() {
    int result = (before ? 1 : 0);
    result = 31 * result + position.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return "Bound{before=" + before + ", position=" + position + '}';
  }
}
