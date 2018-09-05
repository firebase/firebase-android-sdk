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
import com.google.firebase.firestore.model.value.DoubleValue;
import com.google.firebase.firestore.model.value.FieldValue;
import com.google.firebase.firestore.model.value.NullValue;

/** Interface used for all query filters. */
public abstract class Filter {
  public enum Operator {
    LESS_THAN("<"),
    LESS_THAN_OR_EQUAL("<="),
    EQUAL("=="),
    GREATER_THAN(">"),
    GREATER_THAN_OR_EQUAL(">="),
    ARRAY_CONTAINS("array_contains");

    private final String text;

    Operator(String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  /**
   * Gets a Filter instance for the provided path, operator, and value.
   *
   * <p>Note that if the relation operator is EQUAL and the value is null or NaN, this will return
   * the appropriate NullFilter or NaNFilter class instead of a RelationFilter.
   */
  public static Filter create(FieldPath path, Operator operator, FieldValue value) {
    if (value.equals(NullValue.nullValue())) {
      if (operator != Filter.Operator.EQUAL) {
        throw new IllegalArgumentException(
            "Invalid Query. You can only perform equality comparisons on null (via "
                + "whereEqualTo()).");
      }
      return new NullFilter(path);
    } else if (value.equals(DoubleValue.NaN)) {
      if (operator != Filter.Operator.EQUAL) {
        throw new IllegalArgumentException(
            "Invalid Query. You can only perform equality comparisons on NaN (via "
                + "whereEqualTo()).");
      }
      return new NaNFilter(path);
    } else {
      return new RelationFilter(path, operator, value);
    }
  }

  /** Returns the field the Filter operates over. */
  public abstract FieldPath getField();

  /** Returns true if a document matches the filter. */
  public abstract boolean matches(Document doc);

  /** A unique ID identifying the filter; used when serializing queries. */
  public abstract String getCanonicalId();
}
