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

import static com.google.firebase.firestore.core.FieldFilter.Operator.ARRAY_CONTAINS;
import static com.google.firebase.firestore.core.FieldFilter.Operator.ARRAY_CONTAINS_ANY;
import static com.google.firebase.firestore.model.Values.MAX_VALUE;
import static com.google.firebase.firestore.model.Values.MIN_VALUE;
import static com.google.firebase.firestore.model.Values.lowerBoundCompare;
import static com.google.firebase.firestore.model.Values.upperBoundCompare;

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.Values;
import com.google.firestore.v1.Value;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;

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

  /** Returns the field filters that target the given field path. */
  private List<FieldFilter> getFieldFiltersForPath(FieldPath path) {
    List<FieldFilter> result = new ArrayList<>();
    for (Filter filter : filters) {
      if ((filter instanceof FieldFilter) && (((FieldFilter) filter).getField()).equals(path)) {
        result.add((FieldFilter) filter);
      }
    }
    return result;
  }

  /**
   * Returns the values that are used in ARRAY_CONTAINS or ARRAY_CONTAINS_ANY filters. Returns
   * {@code null} if there are no such filters.
   */
  public @Nullable List<Value> getArrayValues(FieldIndex fieldIndex) {
    @Nullable FieldIndex.Segment segment = fieldIndex.getArraySegment();
    if (segment == null) return null;

    for (FieldFilter fieldFilter : getFieldFiltersForPath(segment.getFieldPath())) {
      switch (fieldFilter.getOperator()) {
        case ARRAY_CONTAINS_ANY:
          return fieldFilter.getValue().getArrayValue().getValuesList();
        case ARRAY_CONTAINS:
          return Collections.singletonList(fieldFilter.getValue());
      }
    }

    return null;
  }

  /**
   * Returns the list of values that are used in != or NOT_IN filters. Returns {@code null} if there
   * are no such filters.
   */
  public @Nullable Collection<Value> getNotInValues(FieldIndex fieldIndex) {
    LinkedHashMap<FieldPath, Value> values = new LinkedHashMap<>();
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      for (FieldFilter fieldFilter : getFieldFiltersForPath(segment.getFieldPath())) {
        switch (fieldFilter.getOperator()) {
          case EQUAL:
          case IN:
            // Encode equality prefix, which is encoded in the index value before the inequality
            // (for example `a == 'a' && b != 'b'` is encoded to `value != 'ab'`).
            values.put(segment.getFieldPath(), fieldFilter.getValue());
            break;
          case NOT_IN:
          case NOT_EQUAL:
            values.put(segment.getFieldPath(), fieldFilter.getValue());
            return values.values(); // NotIn/NotEqual is always a suffix
        }
      }
    }

    return null;
  }

  /**
   * Returns a lower bound of field values that can be used as a starting point to scan the index
   * defined by {@code fieldIndex}. Returns {@link Values#MIN_VALUE} if no lower bound exists.
   */
  public Bound getLowerBound(FieldIndex fieldIndex) {
    List<Value> values = new ArrayList<>();
    boolean inclusive = true;

    // For each segment, retrieve a lower bound if there is a suitable filter or startAt.
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      Pair<Value, Boolean> segmentBound =
          segment.getKind().equals(FieldIndex.Segment.Kind.ASCENDING)
              ? getAscendingBound(segment, startAt)
              : getDescendingBound(segment, startAt);

      values.add(segmentBound.first);
      inclusive &= segmentBound.second;
    }

    return new Bound(values, inclusive);
  }

  /**
   * Returns an upper bound of field values that can be used as an ending point when scanning the
   * index defined by {@code fieldIndex}. Returns {@link Values#MAX_VALUE} if no upper bound exists.
   */
  public Bound getUpperBound(FieldIndex fieldIndex) {
    List<Value> values = new ArrayList<>();
    boolean inclusive = true;

    // For each segment, retrieve an upper bound if there is a suitable filter or endAt.
    for (FieldIndex.Segment segment : fieldIndex.getDirectionalSegments()) {
      Pair<Value, Boolean> segmentBound =
          segment.getKind().equals(FieldIndex.Segment.Kind.ASCENDING)
              ? getDescendingBound(segment, endAt)
              : getAscendingBound(segment, endAt);

      values.add(segmentBound.first);
      inclusive &= segmentBound.second;
    }

    return new Bound(values, inclusive);
  }

  /**
   * Returns the value for an ascending bound of `segment`.
   *
   * @param segment The segment to get the value for.
   * @param bound A bound to restrict the index range.
   * @return a Pair with a nullable Value and a boolean indicating whether the bound is inclusive
   */
  private Pair<Value, Boolean> getAscendingBound(
      FieldIndex.Segment segment, @Nullable Bound bound) {
    Value segmentValue = MIN_VALUE;
    boolean segmentInclusive = true;

    // Process all filters to find a value for the current field segment
    for (FieldFilter fieldFilter : getFieldFiltersForPath(segment.getFieldPath())) {
      Value filterValue = MIN_VALUE;
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
        case NOT_IN:
          filterValue = Values.MIN_VALUE;
          break;
        default:
          // Remaining filters cannot be used as bound.
      }

      if (lowerBoundCompare(segmentValue, segmentInclusive, filterValue, filterInclusive) < 0) {
        segmentValue = filterValue;
        segmentInclusive = filterInclusive;
      }
    }

    // If there is an additional bound, compare the values against the existing range to see if we
    // can narrow the scope.
    if (bound != null) {
      for (int i = 0; i < orderBys.size(); ++i) {
        OrderBy orderBy = this.orderBys.get(i);
        if (orderBy.getField().equals(segment.getFieldPath())) {
          Value cursorValue = bound.getPosition().get(i);
          if (lowerBoundCompare(segmentValue, segmentInclusive, cursorValue, bound.isInclusive())
              < 0) {
            segmentValue = cursorValue;
            segmentInclusive = bound.isInclusive();
          }
          break;
        }
      }
    }

    return new Pair<>(segmentValue, segmentInclusive);
  }

  /**
   * Returns the value for a descending bound of `segment`.
   *
   * @param segment The segment to get the value for.
   * @param bound A bound to restrict the index range.
   * @return a Pair with a nullable Value and a boolean indicating whether the bound is inclusive
   */
  private Pair<Value, Boolean> getDescendingBound(
      FieldIndex.Segment segment, @Nullable Bound bound) {
    Value segmentValue = MAX_VALUE;
    boolean segmentInclusive = true;

    // Process all filters to find a value for the current field segment
    for (FieldFilter fieldFilter : getFieldFiltersForPath(segment.getFieldPath())) {
      Value filterValue = MAX_VALUE;
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
        case NOT_IN:
          filterValue = Values.MAX_VALUE;
          break;
        default:
          // Remaining filters cannot be used as bound.
      }

      if (upperBoundCompare(segmentValue, segmentInclusive, filterValue, filterInclusive) > 0) {
        segmentValue = filterValue;
        segmentInclusive = filterInclusive;
      }
    }

    // If there is an additional bound, compare the values against the existing range to see if we
    // can narrow the scope.
    if (bound != null) {
      for (int i = 0; i < orderBys.size(); ++i) {
        OrderBy orderBy = this.orderBys.get(i);
        if (orderBy.getField().equals(segment.getFieldPath())) {
          Value cursorValue = bound.getPosition().get(i);
          if (upperBoundCompare(segmentValue, segmentInclusive, cursorValue, bound.isInclusive())
              > 0) {
            segmentValue = cursorValue;
            segmentInclusive = bound.isInclusive();
          }
          break;
        }
      }
    }

    return new Pair<>(segmentValue, segmentInclusive);
  }

  public List<OrderBy> getOrderBy() {
    return this.orderBys;
  }

  /** Returns the order of the document key component. */
  public Direction getKeyOrder() {
    return this.orderBys.get(this.orderBys.size() - 1).getDirection();
  }

  /** Returns the number of segments of a perfect index for this target. */
  public int getSegmentCount() {
    Set<FieldPath> fields = new HashSet<>();
    boolean hasArraySegment = false;
    for (Filter filter : filters) {
      for (FieldFilter subFilter : filter.getFlattenedFilters()) {
        // __name__ is not an explicit segment of any index, so we don't need to count it.
        if (subFilter.getField().isKeyField()) {
          continue;
        }

        // ARRAY_CONTAINS or ARRAY_CONTAINS_ANY filters must be counted separately. For instance,
        // it is possible to have an index for "a ARRAY a ASC". Even though these are on the same
        // field, they should be counted as two separate segments in an index.
        if (subFilter.getOperator().equals(ARRAY_CONTAINS)
            || subFilter.getOperator().equals(ARRAY_CONTAINS_ANY)) {
          hasArraySegment = true;
        } else {
          fields.add(subFilter.getField());
        }
      }
    }
    for (OrderBy orderBy : orderBys) {
      // __name__ is not an explicit segment of any index, so we don't need to count it.
      if (!orderBy.getField().isKeyField()) {
        fields.add(orderBy.getField());
      }
    }
    return fields.size() + (hasArraySegment ? 1 : 0);
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
}
