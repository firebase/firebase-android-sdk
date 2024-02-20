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
import com.google.firebase.firestore.core.OrderBy.Direction;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.SortedSet;
import java.util.TreeSet;

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

  private List<OrderBy> memoizedNormalizedOrderBys;

  /** The corresponding `Target` of this `Query` instance, for use with non-aggregate queries. */
  private @Nullable Target memoizedTarget;

  /**
   * The corresponding `Target` of this `Query` instance, for use with aggregate queries. Unlike
   * targets for non-aggregate queries, aggregate query targets do not contain normalized order-bys,
   * they only contain explicit order-bys.
   */
  private @Nullable Target memoizedAggregateTarget;

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
            || (getExplicitOrderBy().size() == 1
                && getExplicitOrderBy().get(0).field.isKeyField()));
  }

  /** The filters on the documents returned by the query. */
  public List<Filter> getFilters() {
    return filters;
  }

  /** The maximum number of results to return or {@link Target#NO_LIMIT} if there is no limit. */
  public long getLimit() {
    return limit;
  }

  public boolean hasLimit() {
    return limit != Target.NO_LIMIT;
  }

  public LimitType getLimitType() {
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

  /** Returns the sorted set of inequality filter fields used in this query. */
  public SortedSet<FieldPath> getInequalityFilterFields() {
    SortedSet<FieldPath> result = new TreeSet<FieldPath>();

    for (Filter filter : getFilters()) {
      for (FieldFilter subFilter : filter.getFlattenedFilters()) {
        if (subFilter.isInequality()) {
          result.add(subFilter.getField());
        }
      }
    }

    return result;
  }

  /**
   * Creates a new Query with an additional filter.
   *
   * @param filter The predicate to filter by.
   * @return the new Query.
   */
  public Query filter(Filter filter) {
    hardAssert(!isDocumentQuery(), "No filter is allowed for document query");

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
   * Returns the full list of ordering constraints on the query. This might include additional sort
   * orders added implicitly to match the backend behavior.
   *
   * <p>This method is marked as synchronized because it modifies the internal state in some cases.
   *
   * <p>The returned list is unmodifiable, to prevent ConcurrentModificationExceptions, if one
   * thread is iterating the list and one thread is modifying the list.
   */
  public synchronized List<OrderBy> getNormalizedOrderBy() {
    if (memoizedNormalizedOrderBys == null) {
      List<OrderBy> res = new ArrayList<>();
      HashSet<String> fieldsNormalized = new HashSet<String>();

      /** Any explicit order by fields should be added as is. */
      for (OrderBy explicit : explicitSortOrder) {
        res.add(explicit);
        fieldsNormalized.add(explicit.field.canonicalString());
      }

      /** The order of the implicit ordering always matches the last explicit order by. */
      Direction lastDirection =
          explicitSortOrder.size() > 0
              ? explicitSortOrder.get(explicitSortOrder.size() - 1).getDirection()
              : Direction.ASCENDING;

      /**
       * Any inequality fields not explicitly ordered should be implicitly ordered in a
       * lexicographical order. When there are multiple inequality filters on the same field, the
       * field should be added only once. Note: `SortedSet<FieldPath>` sorts the key field before
       * other fields. However, we want the key field to be sorted last.
       */
      SortedSet<FieldPath> inequalityFields = getInequalityFilterFields();
      for (FieldPath field : inequalityFields) {
        if (!fieldsNormalized.contains(field.canonicalString()) && !field.isKeyField()) {
          res.add(OrderBy.getInstance(lastDirection, field));
        }
      }

      /** Add the document key field to the last if it is not explicitly ordered. */
      if (!fieldsNormalized.contains(FieldPath.KEY_PATH.canonicalString())) {
        res.add(lastDirection.equals(Direction.ASCENDING) ? KEY_ORDERING_ASC : KEY_ORDERING_DESC);
      }

      memoizedNormalizedOrderBys = Collections.unmodifiableList(res);
    }
    return memoizedNormalizedOrderBys;
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
    // We must use `getNormalizedOrderBy()` to get the list of all orderBys (both implicit and
    // explicit).
    // Note that for OR queries, orderBy applies to all disjunction terms and implicit orderBys must
    // be taken into account. For example, the query "a > 1 || b==1" has an implicit "orderBy a" due
    // to the inequality, and is evaluated as "a > 1 orderBy a || b==1 orderBy a".
    // A document with content of {b:1} matches the filters, but does not match the orderBy because
    // it's missing the field 'a'.
    for (OrderBy order : getNormalizedOrderBy()) {
      // order by key always matches
      if (!order.getField().equals(FieldPath.KEY_PATH) && (doc.getField(order.field) == null)) {
        return false;
      }
    }
    return true;
  }

  /** Makes sure a document is within the bounds, if provided. */
  private boolean matchesBounds(Document doc) {
    if (startAt != null && !startAt.sortsBeforeDocument(getNormalizedOrderBy(), doc)) {
      return false;
    }
    if (endAt != null && !endAt.sortsAfterDocument(getNormalizedOrderBy(), doc)) {
      return false;
    }
    return true;
  }

  /** Returns true if the document matches the constraints of this query. */
  public boolean matches(Document doc) {
    return doc.isFoundDocument()
        && matchesPathAndCollectionGroup(doc)
        && matchesOrderBy(doc)
        && matchesFilters(doc)
        && matchesBounds(doc);
  }

  /** Returns a comparator that will sort documents according to this Query's sort order. */
  public Comparator<Document> comparator() {
    return new QueryComparator(getNormalizedOrderBy());
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

  /**
   * This method is marked as synchronized because it modifies the internal state in some cases.
   *
   * @return A {@code Target} instance this query will be mapped to in backend and local store.
   */
  public synchronized Target toTarget() {
    if (this.memoizedTarget == null) {
      memoizedTarget = toTarget(getNormalizedOrderBy());
    }
    return this.memoizedTarget;
  }

  private synchronized Target toTarget(List<OrderBy> orderBys) {
    if (this.limitType == LimitType.LIMIT_TO_FIRST) {
      return new Target(
          this.getPath(),
          this.getCollectionGroup(),
          this.getFilters(),
          orderBys,
          this.limit,
          this.getStartAt(),
          this.getEndAt());
    } else {
      // Flip the orderBy directions since we want the last results
      ArrayList<OrderBy> newOrderBy = new ArrayList<>();
      for (OrderBy orderBy : orderBys) {
        Direction dir =
            orderBy.getDirection() == Direction.DESCENDING
                ? Direction.ASCENDING
                : Direction.DESCENDING;
        newOrderBy.add(OrderBy.getInstance(dir, orderBy.getField()));
      }

      // We need to swap the cursors to match the now-flipped query ordering.
      Bound newStartAt =
          this.endAt != null ? new Bound(this.endAt.getPosition(), this.endAt.isInclusive()) : null;
      Bound newEndAt =
          this.startAt != null
              ? new Bound(this.startAt.getPosition(), this.startAt.isInclusive())
              : null;

      return new Target(
          this.getPath(),
          this.getCollectionGroup(),
          this.getFilters(),
          newOrderBy,
          this.limit,
          newStartAt,
          newEndAt);
    }
  }

  /**
   * This method is marked as synchronized because it modifies the internal state in some cases.
   *
   * @return A {@code Target} instance this query will be mapped to in backend and local store, for
   *     use within an aggregate query.
   */
  public synchronized Target toAggregateTarget() {
    if (this.memoizedAggregateTarget == null) {
      memoizedAggregateTarget = toTarget(explicitSortOrder);
    }
    return this.memoizedAggregateTarget;
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
