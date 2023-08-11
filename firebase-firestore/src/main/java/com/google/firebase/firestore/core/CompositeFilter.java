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

package com.google.firebase.firestore.core;

import android.text.TextUtils;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.util.Function;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Represents a filter that is the conjunction or disjunction of other filters. */
public class CompositeFilter extends Filter {
  public enum Operator {
    AND("and"),
    OR("or");

    private final String text;

    Operator(String text) {
      this.text = text;
    }

    @Override
    public String toString() {
      return text;
    }
  }

  private final List<Filter> filters;
  private final Operator operator;

  // Memoized list of all field filters that can be found by traversing the tree of filters
  // contained in this composite filter.
  private List<FieldFilter> memoizedFlattenedFilters;

  public CompositeFilter(List<Filter> filters, Operator operator) {
    this.filters = new ArrayList<>(filters);
    this.operator = operator;
  }

  @Override
  public List<Filter> getFilters() {
    return Collections.unmodifiableList(filters);
  }

  public Operator getOperator() {
    return operator;
  }

  @Override
  public List<FieldFilter> getFlattenedFilters() {
    if (memoizedFlattenedFilters != null) {
      return Collections.unmodifiableList(memoizedFlattenedFilters);
    }
    memoizedFlattenedFilters = new ArrayList<>();
    for (Filter subfilter : filters) {
      memoizedFlattenedFilters.addAll(subfilter.getFlattenedFilters());
    }
    return Collections.unmodifiableList(memoizedFlattenedFilters);
  }

  public boolean isConjunction() {
    return operator == Operator.AND;
  }

  public boolean isDisjunction() {
    return operator == Operator.OR;
  }

  /**
   * Returns true if this filter is a conjunction of field filters only. Returns false otherwise.
   */
  public boolean isFlatConjunction() {
    return isFlat() && isConjunction();
  }

  /**
   * Returns true if this filter does not contain any composite filters. Returns false otherwise.
   */
  public boolean isFlat() {
    for (Filter filter : filters) {
      if (filter instanceof CompositeFilter) {
        return false;
      }
    }
    return true;
  }

  /**
   * Returns a new composite filter that contains all filter from `this` plus all the given filters.
   */
  public CompositeFilter withAddedFilters(List<Filter> otherFilters) {
    List<Filter> mergedFilters = new ArrayList<>(filters);
    mergedFilters.addAll(otherFilters);
    return new CompositeFilter(mergedFilters, operator);
  }

  /**
   * Performs a depth-first search to find and return the first FieldFilter in the composite filter
   * that satisfies the condition. Returns {@code null} if none of the FieldFilters satisfy the
   * condition.
   */
  @Nullable
  private FieldFilter findFirstMatchingFilter(Function<FieldFilter, Boolean> condition) {
    for (FieldFilter filter : getFlattenedFilters()) {
      if (condition.apply(filter)) {
        return filter;
      }
    }
    return null;
  }

  @Override
  public boolean matches(Document doc) {
    if (isConjunction()) {
      // For conjunctions, all filters must match, so return false if any filter doesn't match.
      for (Filter filter : filters) {
        if (!filter.matches(doc)) {
          return false;
        }
      }
      return true;
    } else {
      // For disjunctions, at least one filter should match.
      for (Filter filter : filters) {
        if (filter.matches(doc)) {
          return true;
        }
      }
      return false;
    }
  }

  @Override
  public String getCanonicalId() {
    StringBuilder builder = new StringBuilder();

    // Older SDK versions use an implicit AND operation between their filters. In the new SDK
    // versions, the developer may use an explicit AND filter. To stay consistent with the old
    // usages, we add a special case to ensure the canonical ID for these two are the same.
    // For example: `col.whereEquals("a", 1).whereEquals("b", 2)` should have the same canonical ID
    // as `col.where(and(equals("a",1), equals("b",2)))`.
    if (isFlatConjunction()) {
      for (Filter filter : filters) {
        builder.append(filter.getCanonicalId());
      }
      return builder.toString();
    }

    builder.append(operator.toString() + "(");
    builder.append(TextUtils.join(",", filters));
    builder.append(")");
    return builder.toString();
  }

  @Override
  public String toString() {
    return getCanonicalId();
  }

  @Override
  public boolean equals(Object o) {
    if (o == null || !(o instanceof CompositeFilter)) {
      return false;
    }
    CompositeFilter other = (CompositeFilter) o;
    // Note: This comparison requires order of filters in the list to be the same, and it does not
    // remove duplicate subfilters from each composite filter. It is therefore way less expensive.
    return operator == other.operator && filters.equals(other.filters);
  }

  @Override
  public int hashCode() {
    int result = 37;
    result = 31 * result + operator.hashCode();
    result = 31 * result + filters.hashCode();
    return result;
  }
}
