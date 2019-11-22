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

import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.Filter.Operator;
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.util.Assert;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * Encapsulates all the query attributes we support in the SDK. It can be run against the
 * LocalStore, as well as be converted to a {@code Target} to query the RemoteStore results.
 */
public final class Query {

  public enum LimitType {
    LIMIT_TO_FIRST,
    LIMIT_TO_LAST
  }

  /**
   * Creates and returns a new Query.
   *
   * @param path The path to the collection to be queried over.
   * @return A new instance of the Query.
   */
  public static Query atPath(ResourcePath path) {
    return new Query(path, /*collectionGroup=*/ null);
  }

  private static final OrderBy KEY_ORDERING_ASC =
      OrderBy.getInstance(OrderBy.Direction.ASCENDING, FieldPath.KEY_PATH);
  private static final OrderBy KEY_ORDERING_DESC =
      OrderBy.getInstance(Direction.DESCENDING, FieldPath.KEY_PATH);

  private final List<OrderBy> explicitSortOrder;

  private List<OrderBy> memoizedOrderBy;

  // The corresponding Target of this Query instance.
  private @Nullable Target memoizedTarget;

  private final List<Filter> filters;

  private final ResourcePath path;

  private final @Nullable String collectionGroup;

  private final long limit;
  private final LimitType limitType;

  private final @Nullable Bound startAt;
  private final @Nullable Bound endAt;

  /** Initializes a Query with all of its components directly. */
  public Query(
      ResourcePath path,
      @Nullable String collectionGroup,
      List<Filter> filters,
      List<OrderBy> explicitSortOrder,
      long limit,
      LimitType limitType,
      @Nullable Bound startAt,
      @Nullable Bound endAt) {
    this.path = path;
    this.collectionGroup = collectionGroup;
    this.explicitSortOrder = explicitSortOrder;
    this.filters = filters;
    this.limit = limit;
    this.limitType = limitType;
    this.startAt = startAt;
    this.endAt = endAt;
  }

  /**
   * Initializes a Query with a path and (optional) collectionGroup. Path must currently be empty in
   * the case of a collection group query.
   */
  public Query(ResourcePath path, @Nullable String collectionGroup) {
    this(
        path,
        collectionGroup,
        Collections.emptyList(),
        Collections.emptyList(),
        Target.NO_LIMIT,
        LimitType.LIMIT_TO_FIRST,
        null,
        null);
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

  /** Returns true if this is a collection group query. */
  public boolean isCollectionGroupQuery() {
    return collectionGroup != null;
  }

  /**
   * Returns true if this query does not specify any query constraints that could remove results.
   */
  public boolean matchesAllDocuments() {
    return filters.isEmpty()
        && limit == Target.NO_LIMIT
        && startAt == null
        && endAt == null
        && (getExplicitOrderBy().isEmpty()
            || (getExplicitOrderBy().size() == 1 && getFirstOrderByField().isKeyField()));
  }

  /** The filters on the documents returned by the query. */
  public List<Filter> getFilters() {
    return filters;
  }

  /**
   * The maximum number of results to return. If there is no limit on the query, then this will
   * cause an assertion failure.
   */
  public long getLimitToFirst() {
    hardAssert(hasLimitToFirst(), "Called getLimitToFirst when no limit was set");
    return limit;
  }

  public boolean hasLimitToFirst() {
    return limitType == LimitType.LIMIT_TO_FIRST && limit != Target.NO_LIMIT;
  }

  /**
   * The maximum number of last-matching results to return. If there is no limit on the query, then
   * this will cause an assertion failure.
   */
  public long getLimitToLast() {
    hardAssert(hasLimitToLast(), "Called getLimitToLast when no limit was set");
    return limit;
  }

  public boolean hasLimitToLast() {
    return limitType == LimitType.LIMIT_TO_LAST && limit != Target.NO_LIMIT;
  }

  public LimitType getLimitType() {
    hardAssert(hasLimitToLast() || hasLimitToFirst(), "Called getLimitType when no limit was set");
    return limitType;
  }

  /** An optional bound to start the query at. */
  public @Nullable Bound getStartAt() {
    return startAt;
  }

  /** An optional bound to end the query at. */
  public @Nullable Bound getEndAt() {
    return endAt;
  }

  /** Returns the first field in an order-by constraint, or null if none. */
  public FieldPath getFirstOrderByField() {
    if (explicitSortOrder.isEmpty()) {
      return null;
    }
    return explicitSortOrder.get(0).getField();
  }

  /** Returns the field of the first filter on this Query that's an inequality, or null if none. */
  @Nullable
  public FieldPath inequalityField() {
    for (Filter filter : filters) {
      if (filter instanceof FieldFilter) {
        FieldFilter fieldfilter = (FieldFilter) filter;
        if (fieldfilter.isInequality()) {
          return fieldfilter.getField();
        }
      }
    }
    return null;
  }

  /**
   * Checks if any of the provided filter operators are included in the query and returns the first
   * one that is, or null if none are.
   */
  @Nullable
  public Operator findFilterOperator(List<Operator> operators) {
    for (Filter filter : filters) {
      if (filter instanceof FieldFilter) {
        Operator filterOp = ((FieldFilter) filter).getOperator();
        if (operators.contains(filterOp)) {
          return filterOp;
        }
      }
    }
    return null;
  }

  /**
   * Creates a new Query with an additional filter.
   *
   * @param filter The predicate to filter by.
   * @return the new Query.
   */
  public Query filter(Filter filter) {
    hardAssert(!isDocumentQuery(), "No filter is allowed for document query");
    FieldPath newInequalityField = null;
    if (filter instanceof FieldFilter && ((FieldFilter) filter).isInequality()) {
      newInequalityField = filter.getField();
    }

    FieldPath queryInequalityField = inequalityField();
    Assert.hardAssert(
        queryInequalityField == null
            || newInequalityField == null
            || queryInequalityField.equals(newInequalityField),
        "Query must only have one inequality field");

    Assert.hardAssert(
        explicitSortOrder.isEmpty()
            || newInequalityField == null
            || explicitSortOrder.get(0).field.equals(newInequalityField),
        "First orderBy must match inequality field");

    List<Filter> updatedFilter = new ArrayList<>(filters);
    updatedFilter.add(filter);
    return new Query(
        path, collectionGroup, updatedFilter, explicitSortOrder, limit, limitType, startAt, endAt);
  }

  /**
   * Creates a new Query with an additional ordering constraint.
   *
   * @param order The key and direction to order by.
   * @return the new Query.
   */
  public Query orderBy(OrderBy order) {
    hardAssert(!isDocumentQuery(), "No ordering is allowed for document query");
    if (explicitSortOrder.isEmpty()) {
      FieldPath inequality = inequalityField();
      if (inequality != null && !inequality.equals(order.field)) {
        throw Assert.fail("First orderBy must match inequality field");
      }
    }
    List<OrderBy> updatedSortOrder = new ArrayList<>(explicitSortOrder);
    updatedSortOrder.add(order);
    return new Query(
        path, collectionGroup, filters, updatedSortOrder, limit, limitType, startAt, endAt);
  }

  /**
   * Returns a new Query with the given limit on how many results can be returned.
   *
   * @param limit The maximum number of results to return. If {@code limit == NO_LIMIT}, then no
   *     limit is applied. Otherwise, if {@code limit <= 0}, behavior is unspecified.
   */
  public Query limitToFirst(long limit) {
    return new Query(
        path,
        collectionGroup,
        filters,
        explicitSortOrder,
        limit,
        LimitType.LIMIT_TO_FIRST,
        startAt,
        endAt);
  }

  /**
   * Returns a new Query with the given limit on how many last-matching results can be returned.
   *
   * @param limit The maximum number of results to return. If {@code limit == NO_LIMIT}, then no
   *     limit is applied. Otherwise, if {@code limit <= 0}, behavior is unspecified.
   */
  public Query limitToLast(long limit) {
    return new Query(
        path,
        collectionGroup,
        filters,
        explicitSortOrder,
        limit,
        LimitType.LIMIT_TO_LAST,
        startAt,
        endAt);
  }

  /**
   * Creates a new Query starting at the provided bound.
   *
   * @param bound The bound to start this query at.
   * @return the new Query.
   */
  public Query startAt(Bound bound) {
    return new Query(
        path, collectionGroup, filters, explicitSortOrder, limit, limitType, bound, endAt);
  }

  /**
   * Creates a new Query ending at the provided bound.
   *
   * @param bound The bound to end this query at.
   * @return the new Query.
   */
  public Query endAt(Bound bound) {
    return new Query(
        path, collectionGroup, filters, explicitSortOrder, limit, limitType, startAt, bound);
  }

  /**
   * Helper to convert a collection group query into a collection query at a specific path. This is
   * used when executing collection group queries, since we have to split the query into a set of
   * collection queries at multiple paths.
   */
  public Query asCollectionQueryAtPath(ResourcePath path) {
    return new Query(
        path,
        /*collectionGroup=*/ null,
        filters,
        explicitSortOrder,
        limit,
        limitType,
        startAt,
        endAt);
  }

  /**
   * Returns the list of ordering constraints that were explicitly requested on the query by the
   * user.
   *
   * <p>Note that the actual query performed might add additional sort orders to match the behavior
   * of the backend.
   */
  public List<OrderBy> getExplicitOrderBy() {
    return explicitSortOrder;
  }

  /**
   * Returns the full list of ordering constraints on the query.
   *
   * <p>This might include additional sort orders added implicitly to match the backend behavior.
   */
  public List<OrderBy> getOrderBy() {
    if (memoizedOrderBy == null) {
      FieldPath inequalityField = inequalityField();
      FieldPath firstOrderByField = getFirstOrderByField();
      if (inequalityField != null && firstOrderByField == null) {
        // In order to implicitly add key ordering, we must also add the inequality filter field for
        // it to be a valid query. Note that the default inequality field and key ordering is
        // ascending.
        if (inequalityField.isKeyField()) {
          this.memoizedOrderBy = Collections.singletonList(KEY_ORDERING_ASC);
        } else {
          memoizedOrderBy =
              Arrays.asList(
                  OrderBy.getInstance(Direction.ASCENDING, inequalityField), KEY_ORDERING_ASC);
        }
      } else {
        List<OrderBy> res = new ArrayList<>();
        boolean foundKeyOrdering = false;
        for (OrderBy explicit : explicitSortOrder) {
          res.add(explicit);
          if (explicit.getField().equals(FieldPath.KEY_PATH)) {
            foundKeyOrdering = true;
          }
        }
        if (!foundKeyOrdering) {
          // The direction of the implicit key ordering always matches the direction of the last
          // explicit sort order
          Direction lastDirection =
              explicitSortOrder.size() > 0
                  ? explicitSortOrder.get(explicitSortOrder.size() - 1).getDirection()
                  : Direction.ASCENDING;
          res.add(lastDirection.equals(Direction.ASCENDING) ? KEY_ORDERING_ASC : KEY_ORDERING_DESC);
        }
        memoizedOrderBy = res;
      }
    }
    return memoizedOrderBy;
  }

  private boolean matchesPathAndCollectionGroup(Document doc) {
    ResourcePath docPath = doc.getKey().getPath();
    if (collectionGroup != null) {
      // NOTE: this.path is currently always empty since we don't expose Collection
      // Group queries rooted at a document path yet.
      return doc.getKey().hasCollectionId(collectionGroup) && path.isPrefixOf(docPath);
    } else if (DocumentKey.isDocumentKey(path)) {
      return path.equals(docPath);
    } else {
      return path.isPrefixOf(docPath) && path.length() == docPath.length() - 1;
    }
  }

  private boolean matchesFilters(Document doc) {
    for (Filter filter : filters) {
      if (!filter.matches(doc)) {
        return false;
      }
    }
    return true;
  }

  /** A document must have a value for every ordering clause in order to show up in the results. */
  private boolean matchesOrderBy(Document doc) {
    for (OrderBy order : explicitSortOrder) {
      // order by key always matches
      if (!order.getField().equals(FieldPath.KEY_PATH) && (doc.getField(order.field) == null)) {
        return false;
      }
    }
    return true;
  }

  /** Makes sure a document is within the bounds, if provided. */
  private boolean matchesBounds(Document doc) {
    if (startAt != null && !startAt.sortsBeforeDocument(getOrderBy(), doc)) {
      return false;
    }
    if (endAt != null && endAt.sortsBeforeDocument(getOrderBy(), doc)) {
      return false;
    }
    return true;
  }

  /** Returns true if the document matches the constraints of this query. */
  public boolean matches(Document doc) {
    return matchesPathAndCollectionGroup(doc)
        && matchesOrderBy(doc)
        && matchesFilters(doc)
        && matchesBounds(doc);
  }

  /** Returns a comparator that will sort documents according to this Query's sort order. */
  public Comparator<Document> comparator() {
    return new QueryComparator(getOrderBy());
  }

  private static class QueryComparator implements Comparator<Document> {
    private final List<OrderBy> sortOrder;

    QueryComparator(List<OrderBy> order) {
      boolean hasKeyOrdering = false;
      for (OrderBy orderBy : order) {
        hasKeyOrdering = hasKeyOrdering || orderBy.getField().equals(FieldPath.KEY_PATH);
      }
      if (!hasKeyOrdering) {
        throw new IllegalArgumentException("QueryComparator needs to have a key ordering");
      }
      this.sortOrder = order;
    }

    @Override
    public int compare(Document doc1, Document doc2) {
      for (OrderBy order : sortOrder) {
        int comp = order.compare(doc1, doc2);
        if (comp != 0) {
          return comp;
        }
      }
      return 0;
    }
  }

  /** @return A {@code Target} instance this query will be mapped to in backend and local store. */
  public Target toTarget() {
    if (this.memoizedTarget == null) {
      if (this.limitType == LimitType.LIMIT_TO_FIRST) {
        this.memoizedTarget =
            new Target(
                this.getPath(),
                this.getCollectionGroup(),
                this.getFilters(),
                this.getOrderBy(),
                this.limit,
                this.getStartAt(),
                this.getEndAt());
      } else {
        // Flip the orderBy directions since we want the last results
        ArrayList<OrderBy> newOrderBy = new ArrayList<>();
        for (OrderBy orderBy : this.getOrderBy()) {
          Direction dir =
              orderBy.getDirection() == Direction.DESCENDING
                  ? Direction.ASCENDING
                  : Direction.DESCENDING;
          newOrderBy.add(OrderBy.getInstance(dir, orderBy.getField()));
        }

        // We need to swap the cursors to match the now-flipped query ordering.
        Bound newStartAt =
            this.endAt != null ? new Bound(this.endAt.getPosition(), !this.endAt.isBefore()) : null;
        Bound newEndAt =
            this.startAt != null
                ? new Bound(this.startAt.getPosition(), !this.startAt.isBefore())
                : null;

        this.memoizedTarget =
            new Target(
                this.getPath(),
                this.getCollectionGroup(),
                this.getFilters(),
                newOrderBy,
                this.limit,
                newStartAt,
                newEndAt);
      }
    }

    return this.memoizedTarget;
  }

  /**
   * Returns a canonical string representing this query. This should match the iOS and Android
   * canonical ids for a query exactly.
   */
  // TODO(wuandy): This is now only used in tests and SpecTestCase. Maybe we can delete it?
  public String getCanonicalId() {
    return this.toTarget().getCanonicalId() + "|lt:" + limitType;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    Query query = (Query) o;

    if (this.limitType != query.limitType) {
      return false;
    }

    return this.toTarget().equals(query.toTarget());
  }

  @Override
  public int hashCode() {
    return 31 * this.toTarget().hashCode() + limitType.hashCode();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Query(target=");
    builder.append(this.toTarget().toString());
    builder.append(";limitType=");
    builder.append(this.limitType.toString());
    builder.append(")");
    return builder.toString();
  }
}
