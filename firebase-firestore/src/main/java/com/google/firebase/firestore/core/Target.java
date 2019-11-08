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

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.List;

/**
 * A Target represents the WatchTarget representation of a Query, which is used by the LocalStore
 * and the RemoteStore to keep track of and to execute backend queries. While multiple Queries can
 * map to the same Target, each Target maps to a single WatchTarget in RemoteStore and a single
 * TargetData entry in persistence.
 */
public final class Target {
  public static final long NO_LIMIT = -1;

  private @Nullable String memoizedCannonicalId;

  private final List<OrderBy> orderBy;
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
   * <p>NOTE: you should always construct Target from {@code Query.toTarget} instead of using this
   * constructor, because Query provides an implicit {@code orderBy} property.
   */
  Target(
      ResourcePath path,
      @Nullable String collectionGroup,
      List<Filter> filters,
      List<OrderBy> orderBy,
      long limit,
      @Nullable Bound startAt,
      @Nullable Bound endAt) {
    this.path = path;
    this.collectionGroup = collectionGroup;
    this.orderBy = orderBy;
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

  /**
   * The maximum number of results to return. If there is no limit on the query, then this will
   * cause an assertion failure.
   */
  public long getLimit() {
    hardAssert(hasLimit(), "Called getLimit when no limit was set");
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

  public List<OrderBy> getOrderBy() {
    return this.orderBy;
  }

  /** Returns a canonical string representing this target. */
  public String getCanonicalId() {
    if (memoizedCannonicalId != null) {
      return memoizedCannonicalId;
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
      builder.append(orderBy.getDirection().equals(Direction.ASCENDING) ? "asc" : "desc");
    }

    // Add limit.
    if (hasLimit()) {
      builder.append("|l:");
      builder.append(getLimit());
    }

    if (startAt != null) {
      builder.append("|lb:");
      builder.append(startAt.canonicalString());
    }

    if (endAt != null) {
      builder.append("|ub:");
      builder.append(endAt.canonicalString());
    }

    memoizedCannonicalId = builder.toString();
    return memoizedCannonicalId;
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
    if (!orderBy.equals(target.orderBy)) {
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
    int result = orderBy.hashCode();
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
        builder.append(filters.get(i).toString());
      }
    }

    if (!orderBy.isEmpty()) {
      builder.append(" order by ");
      for (int i = 0; i < orderBy.size(); i++) {
        if (i > 0) {
          builder.append(", ");
        }
        builder.append(orderBy.get(i));
      }
    }

    builder.append(")");
    return builder.toString();
  }
}
