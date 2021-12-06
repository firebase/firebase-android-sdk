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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.model.Document;
import com.google.firestore.v1.StructuredQuery;
import com.google.firestore.v1.StructuredQuery.CompositeFilter.Operator;
import java.util.ArrayList;
import java.util.List;

interface FieldFilterCondition {
  boolean check(FieldFilter field);
}

/** Represents a filter that is the conjunction or disjunction of other filters. */
public class CompositeFilter extends Filter {
  private final List<Filter> filters;
  //  private final boolean isAnd;
  private final Operator operator;

  public CompositeFilter(List<Filter> filters, Operator operator) {
    this.filters = filters;
    this.operator = operator;
  }

  public List<Filter> getFilters() {
    return filters;
  }

  public Operator getOperator() {
    return operator;
  }

  public boolean isAnd() {
    return operator == Operator.AND;
  }

  public boolean isOr() {
    // TODO(ehsann): Replace with Operator.OR.
    return operator == Operator.OPERATOR_UNSPECIFIED;
  }

  /**
   * Returns true if this is an AND filter and all the filters in this composite filter are
   * FieldFilters (i.e. it does not contain any other composite filters).
   */
  public boolean isFlatAndFilter() {
    return isAnd() && !containsCompositeFilters();
  }

  /**
   * Returns true if this composite filter contains any other composite filter. Returns false
   * otherwise.
   */
  public boolean containsCompositeFilters() {
    for (Filter filter : filters) {
      if (filter instanceof CompositeFilter) {
        return true;
      }
    }
    return false;
  }

  /**
   * Performs a depth-first search to find and return the first FieldFilter in the composite filter
   * that satisfies the condition. Returns null if none of the FieldFilters satisfy the condition.
   */
  public FieldFilter firstFieldFilterWhere(FieldFilterCondition condition) {
    for (Filter filter : filters) {
      if (filter instanceof FieldFilter && condition.check(((FieldFilter) filter))) {
        return (FieldFilter) filter;
      } else if (filter instanceof CompositeFilter) {
        FieldFilter found = ((CompositeFilter) filter).firstFieldFilterWhere(condition);
        if (found != null) {
          return found;
        }
      }
    }
    return null;
  }

  /**
   * Returns the first inequality filter contained within this composite filter. Returns null if it
   * does not contain any inequalities.
   */
  public FieldFilter getInequalityFilter() {
    return firstFieldFilterWhere(f -> f.isInequality());
  }

  @Override
  public boolean matches(Document doc) {
    if (isAnd()) {
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
    for (Filter filter : filters) {
      if (builder.length() == 0) {
        builder.append(isAnd() ? "and(" : "or(");
      } else {
        builder.append(",");
      }
      builder.append(filter.getCanonicalId());
    }
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

  /**
   * Returns a new composite filter that contains all filter from `this` plus all the given filters
   */
  public CompositeFilter addFilters(List<Filter> otherFilters) {
    List<Filter> mergedFilters = new ArrayList<>(filters);
    mergedFilters.addAll(otherFilters);
    return new CompositeFilter(mergedFilters, operator);
  }

  /**
   * Returns a new composite filter that contains all filters from `this` plus the given field
   * filter
   */
  public CompositeFilter addFilter(FieldFilter other) {
    List<Filter> mergedFilters = new ArrayList<>(filters);
    mergedFilters.add(other);
    return new CompositeFilter(mergedFilters, operator);
  }

  /**
   * Applies the associativity property to this filter to get a new filter and returns it.
   *
   * <p>For Example, it turns this: OR(A, OR(B, OR(C, D, OR(E, F), AND(G, OR(H, I))))
   *
   * <p>Into this: OR(A, B, C, D, E, F, AND(G, OR(H, I))
   */
  @NonNull
  @Override
  public Filter applyAssociativity() {
    // Associativity for AND(X) should result in X.
    if (filters.size() == 1) {
      return filters.get(0).applyAssociativity();
    }

    // Associativity applied to a flat composite filter results in itself.
    if (!containsCompositeFilters()) {
      return this;
    }

    // First apply associativity to all subfilters. This will in turn recursively apply
    // associativity to all nested composite filters and field filters.
    List<Filter> updatedFilters = new ArrayList<>();
    for (Filter subfilter : filters) {
      updatedFilters.add(subfilter.applyAssociativity());
    }

    // Take out components of composite subfilters where possible.
    List<Filter> newSubfilters = new ArrayList<>();
    for (Filter subfilter : updatedFilters) {
      if (subfilter instanceof FieldFilter) {
        newSubfilters.add(subfilter);
      } else if (subfilter instanceof CompositeFilter) {
        CompositeFilter compositeSubfilter = (CompositeFilter) subfilter;
        if (compositeSubfilter.getOperator() == operator) {
          // Filter: (A | (B | C))
          // compositeSubfilter: (B | C)
          // Result: (A | B | C)
          newSubfilters.addAll(compositeSubfilter.getFilters());
        } else {
          // Filter: (A | (B & C))
          // compositeSubfilter: (B & C)
          // Result: (A | (B & C))
          newSubfilters.add(subfilter);
        }
      }
    }
    if (newSubfilters.size() == 1) {
      return newSubfilters.get(0);
    }
    return new CompositeFilter(newSubfilters, operator);
  }

  /**
   * Performs "AND distribution" between `this` filter and the given filter (`other`).
   *
   * <p>Example: This: (A | B) , Other: (C | D). Result: (A & C) | (A & D) | (B & C) & (B & D)
   *
   * <p>Example: This: (A | B) , Other: (C & D). Result: (A & C & D) | (B & C & D)
   *
   * <p>Example: This: (A | B) , Other: (C). Result: (A & C) | (B & C)
   */
  @NonNull
  @Override
  public Filter applyDistribution(@Nullable Filter other) {
    hardAssert(filters.size() > 0, "Found an empty composite filter");

    // Distribute(AND(X), Y) == Distribute(X, Y).
    if (filters.size() == 1) {
      return filters.get(0).applyDistribution(other);
    }

    // Distribute(OR(X, Y) , Z) == Distribute(Z, OR(X, Y)) == OR(AND(Z,X), AND(Z,Y))
    if (other instanceof FieldFilter) {
      return other.applyDistribution(this);
    }

    hardAssert(
        other instanceof CompositeFilter, "Only FieldFilter and CompositeFilter are supported.");

    // If they are both AND filters, this is a merge.
    // Example: Distribute( AND(A, B), AND(C, D) ) == AND(A, B, C, D).
    CompositeFilter compositeOther = (CompositeFilter) other;
    if (this.isAnd() && compositeOther.isAnd()) {
      return this.addFilters(compositeOther.getFilters());
    }

    // Note that if both are OR filters, we should not merge them.
    // Example: Distribute( OR(A, B), OR(C, D) ) == OR(AND(A,C), AND(A,D), AND(B,C), AND(B,D)).
    // If either filter is an OR filter, we should iterate over its subfilters and distribute them.
    CompositeFilter lhs = this.isOr() ? this : compositeOther;
    CompositeFilter rhs = this.isOr() ? compositeOther : this;
    // lhs is guaranteed to be an OR filter.
    List<Filter> newFilters = new ArrayList<>();
    for (Filter subfilter : lhs.getFilters()) {
      newFilters.add(subfilter.applyDistribution(rhs));
    }
    if (newFilters.size() == 1) {
      return newFilters.get(0);
    }
    return new CompositeFilter(
            newFilters, StructuredQuery.CompositeFilter.Operator.OPERATOR_UNSPECIFIED)
        .applyAssociativity();
  }

  /**
   * Given a composite filter, returns its DNF representation.
   *
   * <p>A filter in DNF form falls into one of the following categories:
   *
   * <p>(1) a field filter. Example: A
   *
   * <p>(2) a flat AND filter. Example: (A & B & C)
   *
   * <p>(3) a flat OR filter. Example: (A | B | C)
   *
   * <p>(4) OR filter of #1/#2. Example: A | (B & C & D) | E | (F & G)
   */
  public Filter computeDnf() {
    if (filters.size() == 1) {
      return filters.get(0).computeDnf();
    }

    List<Filter> result = new ArrayList<>();
    for (Filter subfilter : filters) {
      result.add(subfilter.computeDnf());
    }

    Filter newFilter = new CompositeFilter(result, operator).applyAssociativity();

    // Covers Case (1), (2), and (3).
    if (newFilter instanceof FieldFilter
        || (newFilter instanceof CompositeFilter
            && !((CompositeFilter) newFilter).containsCompositeFilters())) {
      return newFilter;
    }

    if (isOr()) {
      // The top-level logic in `newFilter` is `OR`.
      // All the subfilters in `newFilter` that were composite filters have also been transformed to
      // DNF (which is an OR). The result can be achieved by applying the associative property.
      return newFilter;
    }

    // If `newFilter` is an AND filter, we need to perform distribution.
    hardAssert(
        newFilter instanceof CompositeFilter,
        "Only FieldFilters and CompositeFilters are supported.");
    CompositeFilter newCompositeFilter = (CompositeFilter) newFilter;
    hardAssert(
        newCompositeFilter.getFilters().size() > 1,
        "Single-filter composite filters should have been handled already.");
    Filter runningResult = newCompositeFilter.getFilters().get(0);
    for (int i = 1; i < newCompositeFilter.getFilters().size(); ++i) {
      Filter curFilter = newCompositeFilter.getFilters().get(i);
      runningResult = runningResult.applyDistribution(curFilter);
    }
    return runningResult.applyAssociativity();
  }
}
