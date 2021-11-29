// Copyright 2019 Google LLC
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

import static com.google.firebase.firestore.model.Values.max;
import static com.google.firebase.firestore.model.Values.min;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.Filter;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.ArrayValue;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A Target represents the WatchTarget representation of a Query, which is used by the LocalStore
 * and the RemoteStore to keep track of and to execute backend queries. While multiple Queries can
 * map to the same Target, each Target maps to a single WatchTarget in RemoteStore and a single
 * TargetData entry in persistence.
 */
public final class Target {
  public static final long NO_LIMIT = -1;

  private @Nullable String memoizedCanonicalId;

  private final List<OrderBy> orderBys;
  private final List<Filter> filters;

  private final ResourcePath path;

  private final @Nullable String collectionGroup;

  private final long limit;

  private final @Nullable Bound startAt;
  private final @Nullable Bound endAt;

  // DNF (Disjunctive Normal Form) representation of this target's filters.
  // Each element in this list is an AND filter of FieldFilters.
  // The query result is the disjunction (OR) of each of these AND filters.
  private final List<CompositeFilter> dnf;

  /**
   * Initializes a Target with a path and additional query constraints. Path must currently be empty
   * if this is a collection group query.
   *
   * <p>NOTE: In general, you should prefer to construct Target from {@code Query.toTarget} instead
   * of using this constructor, because Query provides an implicit {@code orderBy} property and
   * flips the orderBy constraints for limitToLast() queries.
   */
  public Target(
      ResourcePath path,
      @Nullable String collectionGroup,
      List<Filter> filters,
      List<OrderBy> orderBys,
      long limit,
      @Nullable Bound startAt,
      @Nullable Bound endAt) {
    this.path = path;
    this.collectionGroup = collectionGroup;
    this.orderBys = orderBys;
    this.filters = filters;
    this.limit = limit;
    this.startAt = startAt;
    this.endAt = endAt;
    this.dnf = computeDnf();
  }

  /** The base path of the query. */
  public ResourcePath getPath() {
    return path;
  }

  /** An optional collection group within which to query. */
  public @Nullable String getCollectionGroup() {
    return collectionGroup;
  }

  /** Returns true if this Query is for a specific document. */
  public boolean isDocumentQuery() {
    return DocumentKey.isDocumentKey(path) && collectionGroup == null && filters.isEmpty();
  }

  /** The filters on the documents returned by the query. */
  public List<Filter> getFilters() {
    return filters;
  }

  /** The AND filters in the DNF. */
  public List<CompositeFilter> getDnf() {
    return dnf;
  }

  /** The maximum number of results to return. Returns -1 if there is no limit on the query. */
  public long getLimit() {
    return limit;
  }

  public boolean hasLimit() {
    return limit != NO_LIMIT;
  }

  /** An optional bound to start the query at. */
  public @Nullable Bound getStartAt() {
    return startAt;
  }

  /** An optional bound to end the query at. */
  public @Nullable Bound getEndAt() {
    return endAt;
  }

  /**
   * Returns the values that are used in ARRAY_CONTAINS or ARRAY_CONTAINS_ANY filters in the given
   * AND filter's components. Returns {@code null} if there are no such filters.
   */
  public @Nullable List<Value> getArrayValuesForFilter(
      FieldIndex fieldIndex, @Nullable CompositeFilter andFilter) {
    if (andFilter == null) return null;
    @Nullable FieldIndex.Segment segment = fieldIndex.getArraySegment();
    if (segment == null) return null;

    for (Filter filter : andFilter.getFilters()) {
      if (filter instanceof FieldFilter
          && ((FieldFilter) filter).getField().equals(segment.getFieldPath())) {
        FieldFilter fieldFilter = (FieldFilter) filter;
        switch (fieldFilter.getOperator()) {
          case ARRAY_CONTAINS_ANY:
            return fieldFilter.getValue().getArrayValue().getValuesList();
          case ARRAY_CONTAINS:
            return Collections.singletonList(fieldFilter.getValue());
        }
      }
    }

    return null;
  }

  /**
   * Returns the list of values that are used in != or NOT_IN filters in the given AND filter's
   * components. Returns {@code null} if there are no such filters.
   */
  public @Nullable List<Value> getNotInValuesForFilter(
      FieldIndex fieldIndex, @Nullable CompositeFilter andFilter) {
    if (andFilter == null) return null;
    List<Value> values = new ArrayList<>();

    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      for (Filter filter : andFilter.getFilters()) {
        if (filter instanceof FieldFilter
            && ((FieldFilter) filter).getField().equals(segment.getFieldPath())) {
          FieldFilter fieldFilter = (FieldFilter) filter;
          switch (fieldFilter.getOperator()) {
            case EQUAL:
            case IN:
              // Encode equality prefix, which is encoded in the index value before the inequality
              // (e.g. `a == 'a' && b != 'b' is encoded to 'value != ab').
              values.add(fieldFilter.getValue());
              break;
            case NOT_IN:
            case NOT_EQUAL:
              // NotIn/NotEqual is always a suffix
              values.add(fieldFilter.getValue());
              return values;
          }
        }
      }
    }

    return null;
  }

  /**
   * Returns a lower bound of field values that can be used as a starting point to scan the index
   * defined by {@code fieldIndex} for the given AND filter's components. Returns {@code null} if no
   * lower bound exists.
   */
  @Nullable
  public Bound getLowerBoundForFilter(FieldIndex fieldIndex, @Nullable CompositeFilter andFilter) {
    List<Value> values = new ArrayList<>();
    boolean inclusive = true;

    // For each segment, retrieve a lower bound if there is a suitable filter or startAt.
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      Value segmentValue = null;
      boolean segmentInclusive = true;

      // Process all filters to find a value for the current field segment
      if (andFilter != null) {
        for (Filter filter : andFilter.getFilters()) {
          if (filter instanceof FieldFilter
              && ((FieldFilter) filter).getField().equals(segment.getFieldPath())) {
            FieldFilter fieldFilter = (FieldFilter) filter;
            Value filterValue = null;
            boolean filterInclusive = true;

            switch (fieldFilter.getOperator()) {
              case LESS_THAN:
              case LESS_THAN_OR_EQUAL:
                filterValue = Values.getLowerBound(fieldFilter.getValue().getValueTypeCase());
                break;
              case EQUAL:
              case IN:
              case GREATER_THAN_OR_EQUAL:
                filterValue = fieldFilter.getValue();
                break;
              case GREATER_THAN:
                filterValue = fieldFilter.getValue();
                filterInclusive = false;
                break;
              case NOT_EQUAL:
                filterValue = Values.MIN_VALUE;
                break;
              case NOT_IN:
                {
                  ArrayValue.Builder arrayValue = ArrayValue.newBuilder();
                  for (int i = 0;
                      i < fieldFilter.getValue().getArrayValue().getValuesCount();
                      ++i) {
                    arrayValue.addValues(Values.MIN_VALUE);
                  }
                  filterValue = Value.newBuilder().setArrayValue(arrayValue).build();
                  break;
                }
              default:
                // Remaining filters cannot be used as lower bounds.
            }

            if (max(segmentValue, filterValue) == filterValue) {
              segmentValue = filterValue;
              segmentInclusive = filterInclusive;
            }
          }
        }
      }

      // If there is a startAt bound, compare the values against the existing boundary to see
      // if we can narrow the scope.
      if (startAt != null) {
        for (int i = 0; i < orderBys.size(); ++i) {
          OrderBy orderBy = this.orderBys.get(i);
          if (orderBy.getField().equals(segment.getFieldPath())) {
            Value cursorValue = startAt.getPosition().get(i);
            if (max(segmentValue, cursorValue) == cursorValue) {
              segmentValue = cursorValue;
              segmentInclusive = startAt.isInclusive();
            }
            break;
          }
        }
      }

      if (segmentValue == null) {
        // No lower bound exists
        return null;
      }

      values.add(segmentValue);
      inclusive &= segmentInclusive;
    }

    return new Bound(values, inclusive);
  }

  /**
   * Returns an upper bound of field values that can be used as an ending point when scanning the
   * index defined by {@code fieldIndex} for the given AND filter's components. Returns {@code null}
   * if no upper bound exists.
   */
  public @Nullable Bound getUpperBoundForFilter(
      FieldIndex fieldIndex, @Nullable CompositeFilter andFilter) {
    List<Value> values = new ArrayList<>();
    boolean inclusive = true;

    // For each segment, retrieve an upper bound if there is a suitable filter or endAt.
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      @Nullable Value segmentValue = null;
      boolean segmentInclusive = true;

      // Process all filters to find a value for the current field segment
      if (andFilter != null) {
        for (Filter filter : andFilter.getFilters()) {
          if (filter instanceof FieldFilter
              && ((FieldFilter) filter).getField().equals(segment.getFieldPath())) {
            FieldFilter fieldFilter = (FieldFilter) filter;
            Value filterValue = null;
            boolean filterInclusive = true;

            switch (fieldFilter.getOperator()) {
              case GREATER_THAN_OR_EQUAL:
              case GREATER_THAN:
                filterValue = Values.getUpperBound(fieldFilter.getValue().getValueTypeCase());
                filterInclusive = false;
                break;
              case EQUAL:
              case IN:
              case LESS_THAN_OR_EQUAL:
                filterValue = fieldFilter.getValue();
                break;
              case LESS_THAN:
                filterValue = fieldFilter.getValue();
                filterInclusive = false;
                break;
              case NOT_EQUAL:
                filterValue = Values.MAX_VALUE;
                break;
              case NOT_IN:
                {
                  ArrayValue.Builder arrayValue = ArrayValue.newBuilder();
                  for (int i = 0;
                      i < fieldFilter.getValue().getArrayValue().getValuesCount();
                      ++i) {
                    arrayValue.addValues(Values.MAX_VALUE);
                  }
                  filterValue = Value.newBuilder().setArrayValue(arrayValue).build();
                  break;
                }
              default:
                // Remaining filters cannot be used as upper bounds.
            }

            if (min(segmentValue, filterValue) == filterValue) {
              segmentValue = filterValue;
              segmentInclusive = filterInclusive;
            }
          }
        }
      }

      // If there is an endAt bound, compare the values against the existing boundary to see
      // if we can narrow the scope.
      if (endAt != null) {
        for (int i = 0; i < orderBys.size(); ++i) {
          OrderBy orderBy = this.orderBys.get(i);
          if (orderBy.getField().equals(segment.getFieldPath())) {
            Value cursorValue = endAt.getPosition().get(i);
            if (min(segmentValue, cursorValue) == cursorValue) {
              segmentValue = cursorValue;
              segmentInclusive = endAt.isInclusive();
            }
            break;
          }
        }
      }

      if (segmentValue == null) {
        // No upper bound exists
        return null;
      }

      values.add(segmentValue);
      inclusive &= segmentInclusive;
    }

    if (values.isEmpty()) {
      return null;
    }

    return new Bound(values, inclusive);
  }

  public List<OrderBy> getOrderBy() {
    return this.orderBys;
  }

  /** Returns a canonical string representing this target. */
  public String getCanonicalId() {
    if (memoizedCanonicalId != null) {
      return memoizedCanonicalId;
    }

    StringBuilder builder = new StringBuilder();
    builder.append(getPath().canonicalString());

    if (collectionGroup != null) {
      builder.append("|cg:");
      builder.append(collectionGroup);
    }

    // Add filters.
    builder.append("|f:");
    for (Filter filter : getFilters()) {
      builder.append(filter.getCanonicalId());
    }

    // Add order by.
    builder.append("|ob:");
    for (OrderBy orderBy : getOrderBy()) {
      builder.append(orderBy.getField().canonicalString());
      builder.append(orderBy.getDirection().equals(OrderBy.Direction.ASCENDING) ? "asc" : "desc");
    }

    // Add limit.
    if (hasLimit()) {
      builder.append("|l:");
      builder.append(getLimit());
    }

    if (startAt != null) {
      builder.append("|lb:");
      builder.append(startAt.isInclusive() ? "b:" : "a:");
      builder.append(startAt.positionString());
    }

    if (endAt != null) {
      builder.append("|ub:");
      builder.append(endAt.isInclusive() ? "a:" : "b:");
      builder.append(endAt.positionString());
    }

    memoizedCanonicalId = builder.toString();
    return memoizedCanonicalId;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Target target = (Target) o;

    if (collectionGroup != null
        ? !collectionGroup.equals(target.collectionGroup)
        : target.collectionGroup != null) {
      return false;
    }
    if (limit != target.limit) {
      return false;
    }
    if (!orderBys.equals(target.orderBys)) {
      return false;
    }
    if (!filters.equals(target.filters)) {
      return false;
    }
    if (!path.equals(target.path)) {
      return false;
    }
    if (startAt != null ? !startAt.equals(target.startAt) : target.startAt != null) {
      return false;
    }
    return endAt != null ? endAt.equals(target.endAt) : target.endAt == null;
  }

  @Override
  public int hashCode() {
    int result = orderBys.hashCode();
    result = 31 * result + (collectionGroup != null ? collectionGroup.hashCode() : 0);
    result = 31 * result + filters.hashCode();
    result = 31 * result + path.hashCode();
    result = 31 * result + (int) (limit ^ (limit >>> 32));
    result = 31 * result + (startAt != null ? startAt.hashCode() : 0);
    result = 31 * result + (endAt != null ? endAt.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Query(");
    builder.append(path.canonicalString());
    if (collectionGroup != null) {
      builder.append(" collectionGroup=");
      builder.append(collectionGroup);
    }
    if (!filters.isEmpty()) {
      builder.append(" where ");
      for (int i = 0; i < filters.size(); i++) {
        if (i > 0) {
          builder.append(" and ");
        }
        builder.append(filters.get(i));
      }
    }

    if (!orderBys.isEmpty()) {
      builder.append(" order by ");
      for (int i = 0; i < orderBys.size(); i++) {
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(orderBys.get(i));
      }
    }

    builder.append(")");
    return builder.toString();
  }

  private List<CompositeFilter> computeDnf() {
    List<CompositeFilter> result = new ArrayList<>();
    if (filters.isEmpty()) return result;

    // For the simple case of traditional queries, return 1 filter.
    CompositeFilter filter = new CompositeFilter(filters, true);
    if (filter.isFlatAndFilter()) {
      result.add(filter);
      return result;
    }

    // TODO(ehsann): Implement DNF transform.

    return result;
  }
}
