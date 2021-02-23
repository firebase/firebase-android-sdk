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

import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.util.Assert;
import com.google.firestore.v1.Value;

/** Represents a sort order for a Firestore Query */
public class OrderBy {
  /** The direction of the ordering */
  public enum Direction {
    ASCENDING(1),
    DESCENDING(-1);

    private final int comparisonModifier;

    Direction(int comparisonModifier) {
      this.comparisonModifier = comparisonModifier;
    }

    int getComparisonModifier() {
      return comparisonModifier;
    }
  }

  public static OrderBy getInstance(Direction direction, FieldPath path) {
    return new OrderBy(direction, path);
  }

  public Direction getDirection() {
    return direction;
  }

  public FieldPath getField() {
    return field;
  }

  private final Direction direction;
  final FieldPath field;

  private OrderBy(Direction direction, FieldPath field) {
    this.direction = direction;
    this.field = field;
  }

  int compare(Document d1, Document d2) {
    if (field.equals(FieldPath.KEY_PATH)) {
      return direction.getComparisonModifier() * d1.getKey().compareTo(d2.getKey());
    } else {
      Value v1 = d1.getField(field);
      Value v2 = d2.getField(field);
      Assert.hardAssert(
          v1 != null && v2 != null, "Trying to compare documents on fields that don't exist.");
      return direction.getComparisonModifier() * Values.compare(v1, v2);
    }
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof OrderBy)) {
      return false;
    }

    OrderBy other = (OrderBy) o;
    return direction == other.direction && field.equals(other.field);
  }

  @Override
  public int hashCode() {
    int result = 29;
    result = 31 * result + direction.hashCode();
    result = 31 * result + field.hashCode();
    return result;
  }

  @Override
  public String toString() {
    return (direction == Direction.ASCENDING ? "" : "-") + field.canonicalString();
  }
}
