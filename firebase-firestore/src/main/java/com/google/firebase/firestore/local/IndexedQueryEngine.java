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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.FieldFilter;
import com.google.firebase.firestore.core.Filter;
import com.google.firebase.firestore.core.Filter.Operator;
import com.google.firebase.firestore.core.IndexRange;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldPath;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.Values;
import com.google.firebase.firestore.util.Assert;
import com.google.firestore.v1.Value;
import java.util.Arrays;
import java.util.List;

/**
 * An indexed implementation of {@link QueryEngine} which performs fairly efficient queries.
 *
 * <p>{@code IndexedQueryEngine} performs only one index lookup and picks an index to use based on
 * an estimate of a query's filter or orderBy selectivity.
 *
 * <p>For queries with filters, {@code IndexedQueryEngine} distinguishes between two categories of
 * query filters: High selectivity filters are expected to return a lower number of results from the
 * index, while low selectivity filters only marginally prune the search space.
 *
 * <p>We determine the best filter to use based on the combination of two static rules, which take
 * into account both the operator and field values type.
 *
 * <p>For operators, this assignment is as follows:
 *
 * <ul>
 *   <li>HIGH_SELECTIVITY: '='
 *   <li>LOW_SELECTIVITY: '<', <=', '>=', '>'
 * </ul>
 *
 * <p>For field value types, this assignment is:
 *
 * <ul>
 *   <li>HIGH_SELECTIVITY: {@code BlobValue}, {@code DoubleValue}, {@code GeoPointValue}, {@code
 *       NumberValue}, {@code ReferenceValue}, {@code StringValue}, {@code TimestampValue}, {@code
 *       NullValue}
 *   <li>LOW_SELECTIVITY: {@code ArrayValue}, {@code MapValue}, {@code BooleanValue}
 * </ul>
 *
 * <p>Note that we consider {@code NullValue} a high selectivity filter as we only support equals
 * comparisons against 'null' and expect most data to be non-null.
 *
 * <p>In the absence of filters, {@code IndexedQueryEngine} performs an index lookup based on the
 * first explicitly specified field in the orderBy clause. Fields in an orderBy only match documents
 * that contains these fields and can hence optimize our lookups by providing some selectivity.
 *
 * <p>A full collection scan is therefore only needed when no filters or orderBy constraints are
 * specified.
 */
public class IndexedQueryEngine implements QueryEngine {

  private static final double HIGH_SELECTIVITY = 1.0;
  private static final double LOW_SELECTIVITY = 0.5;

  // ARRAY_VALUE and MAP_VALUE are currently considered low cardinality because we don't index
  // them uniquely.
  private static final List<Value.ValueTypeCase> lowCardinalityTypes =
      Arrays.asList(
          Value.ValueTypeCase.BOOLEAN_VALUE,
          Value.ValueTypeCase.ARRAY_VALUE,
          Value.ValueTypeCase.MAP_VALUE);

  private final SQLiteCollectionIndex collectionIndex;
  private LocalDocumentsView localDocuments;

  public IndexedQueryEngine(SQLiteCollectionIndex collectionIndex) {
    this.collectionIndex = collectionIndex;
  }

  @Override
  public void setLocalDocumentsView(LocalDocumentsView localDocuments) {
    this.localDocuments = localDocuments;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    hardAssert(localDocuments != null, "setLocalDocumentsView() not called");

    return query.isDocumentQuery()
        ? localDocuments.getDocumentsMatchingQuery(query, SnapshotVersion.NONE)
        : performCollectionQuery(query);
  }

  /** Executes the query using both indexes and post-filtering. */
  private ImmutableSortedMap<DocumentKey, Document> performCollectionQuery(Query query) {
    hardAssert(!query.isDocumentQuery(), "matchesCollectionQuery() called with document query.");

    IndexRange indexRange = extractBestIndexRange(query);
    ImmutableSortedMap<DocumentKey, Document> filteredResults;

    if (indexRange != null) {
      filteredResults = performQueryUsingIndex(query, indexRange);
    } else {
      hardAssert(
          query.getFilters().isEmpty(),
          "If there are any filters, we should be able to use an index.");
      // TODO: Call overlay.getCollectionDocuments(query.getPath()) and filter the
      // results (there may still be startAt/endAt bounds that apply).
      filteredResults = localDocuments.getDocumentsMatchingQuery(query, SnapshotVersion.NONE);
    }

    return filteredResults;
  }

  /**
   * Applies 'filter' to the index cursor, looks up the relevant documents from the local documents
   * view and returns all matches.
   */
  private ImmutableSortedMap<DocumentKey, Document> performQueryUsingIndex(
      Query query, IndexRange indexRange) {
    ImmutableSortedMap<DocumentKey, Document> results = DocumentCollections.emptyDocumentMap();
    IndexCursor cursor = collectionIndex.getCursor(query.getPath(), indexRange);
    try {
      while (cursor.next()) {
        Document document = (Document) localDocuments.getDocument(cursor.getDocumentKey());
        if (query.matches(document)) {
          results = results.insert(cursor.getDocumentKey(), document);
        }
      }
    } finally {
      cursor.close();
    }

    return results;
  }

  /**
   * Determines a single filter's selectivity by multiplying the implied selectivity of the filter
   * operator and the type of its operand.
   *
   * @return a number from 0.0 to 1.0 (inclusive), where higher numbers indicate higher selectivity
   */
  private static double estimateFilterSelectivity(Filter filter) {
    hardAssert(filter instanceof FieldFilter, "Filter type expected to be FieldFilter");
    FieldFilter fieldFilter = (FieldFilter) filter;
    Value filterValue = fieldFilter.getValue();
    if (Values.isNullValue(filterValue) || Values.isNanValue(filterValue)) {
      return HIGH_SELECTIVITY;
    } else {
      double operatorSelectivity =
          fieldFilter.getOperator().equals(Operator.EQUAL) ? HIGH_SELECTIVITY : LOW_SELECTIVITY;
      double typeSelectivity =
          lowCardinalityTypes.contains(fieldFilter.getValue().getValueTypeCase())
              ? LOW_SELECTIVITY
              : HIGH_SELECTIVITY;

      return typeSelectivity * operatorSelectivity;
    }
  }

  /**
   * Returns an optimized {@code IndexRange} for this query. The {@code IndexRange} is computed
   * based on the estimated selectivity of the query filters and orderBy constraints. If no filters
   * or orderBy constraints are specified, it returns null.
   */
  @Nullable
  @VisibleForTesting
  static IndexRange extractBestIndexRange(Query query) {
    // TODO: consider any startAt/endAt bounds on the query.

    double currentSelectivity = -1;

    if (!query.getFilters().isEmpty()) {
      Filter selectedFilter = null;
      for (Filter currentFilter : query.getFilters()) {
        double estimatedSelectivity = estimateFilterSelectivity(currentFilter);
        if (estimatedSelectivity > currentSelectivity) {
          selectedFilter = currentFilter;
          currentSelectivity = estimatedSelectivity;
        }
      }
      hardAssert(selectedFilter != null, "Filter should be defined");
      return convertFilterToIndexRange(selectedFilter);
    } else {
      // If there are no filters, use the first orderBy constraint when performing the index lookup.
      // This index lookup will remove results that do not contain the field we use for ordering.
      FieldPath orderPath = query.getOrderBy().get(0).getField();
      if (!orderPath.equals(FieldPath.KEY_PATH)) {
        return IndexRange.builder().setFieldPath(query.getOrderBy().get(0).getField()).build();
      }
    }

    return null;
  }

  /**
   * Creates an {@code IndexRange} that is guaranteed to capture all values that match the given
   * filter. The determined {@code IndexRange} is likely overselective and requires post-filtering.
   */
  private static IndexRange convertFilterToIndexRange(Filter filter) {
    IndexRange.Builder indexRange = IndexRange.builder().setFieldPath(filter.getField());
    if (filter instanceof FieldFilter) {
      FieldFilter fieldFilter = (FieldFilter) filter;
      Value filterValue = fieldFilter.getValue();
      switch (fieldFilter.getOperator()) {
        case EQUAL:
          indexRange.setStart(filterValue).setEnd(filterValue);
          break;
        case LESS_THAN_OR_EQUAL:
        case LESS_THAN:
          indexRange.setEnd(filterValue);
          break;
        case GREATER_THAN:
        case GREATER_THAN_OR_EQUAL:
          indexRange.setStart(filterValue);
          break;
        default:
          // TODO: Add support for ARRAY_CONTAINS.
          throw Assert.fail("Unexpected operator in query filter");
      }
    }
    return indexRange.build();
  }

  @Override
  public void handleDocumentChange(MaybeDocument oldDocument, MaybeDocument newDocument) {
    // TODO: Determine changed fields and make appropriate addEntry() / removeEntry()
    // on SQLiteCollectionIndex.
    throw new RuntimeException("Not yet implemented.");
  }
}
