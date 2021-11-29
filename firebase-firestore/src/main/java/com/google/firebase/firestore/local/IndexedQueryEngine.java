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
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.CompositeFilter;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Logger;
import java.util.Map;
import java.util.Set;

/** An indexed implementation of {@link QueryEngine}. */
public class IndexedQueryEngine implements QueryEngine {

  private static final String LOG_TAG = "IndexedQueryEngine";

  private IndexManager indexManager;
  private LocalDocumentsView localDocuments;

  public IndexedQueryEngine() {
    hardAssert(Persistence.INDEXING_SUPPORT_ENABLED, "Indexing support not enbabled");
  }

  @Override
  public void setLocalDocumentsView(LocalDocumentsView localDocuments) {
    this.localDocuments = localDocuments;
  }

  @Override
  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
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
    hardAssert(localDocuments != null, "setLocalDocumentsView() not called");
    hardAssert(indexManager != null, "setIndexManager() not called");

    // Queries that match all documents don't benefit from index-based lookups.
    if (query.matchesAllDocuments() || query.containsCompositeFilters()) {
      return executeFullCollectionScan(query);
    }

    Target target = query.toTarget();

    if (target.getDnf().size() == 0) {
      // There are no filters.
      return performCollectionQueryForFilter(query, target, null);
    }

    ImmutableSortedMap<DocumentKey, Document> result =
        ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
    for (CompositeFilter andFilter : target.getDnf()) {
      // Each filter in the DNF must be an AND filter.
      hardAssert(andFilter.isAnd(), "Found an OR filter in the DNF");
      for (Map.Entry<DocumentKey, Document> entry :
          performCollectionQueryForFilter(query, target, andFilter)) {
        result = result.insert(entry.getKey(), entry.getValue());
      }
    }

    // If there's no limit constraint, we can return all the results.
    // If the DNF contained only 1 term, the SQLite query has enforced the limit, and we can return
    // all the results.
    if (target.getLimit() == -1 || target.getDnf().size() == 1) {
      return result;
    }

    // If there's a limit constraint, we should perform all branches of the query (as done above),
    // collect the results in a sorted map (as done above), and we need to limit the results here.
    long counter = 0;
    ImmutableSortedMap<DocumentKey, Document> limitedResult =
        ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
    for (Map.Entry<DocumentKey, Document> entry : result) {
      if (counter == target.getLimit()) {
        break;
      }
      limitedResult = limitedResult.insert(entry.getKey(), entry.getValue());
      counter++;
    }
    return limitedResult;
  }

  /**
   * Executes a sub-query using both indexes and post-filtering. It only applies the given andFilter
   * that is part of the query logic. `andFilter` can be null if the query contains no filters.
   */
  private ImmutableSortedMap<DocumentKey, Document> performCollectionQueryForFilter(
      Query query, Target target, @Nullable CompositeFilter andFilter) {
    FieldIndex fieldIndex = indexManager.getFieldIndex(target, andFilter);
    if (fieldIndex != null) {
      // If there is an index, use the index to execute the query up to its last update time.
      // Results that have not yet been written to the index get merged into the result.
      Set<DocumentKey> keys =
          indexManager.getDocumentsMatchingTarget(fieldIndex, target, andFilter);
      ImmutableSortedMap<DocumentKey, Document> indexedDocuments =
          localDocuments.getDocuments(keys);
      ImmutableSortedMap<DocumentKey, Document> additionalDocuments =
          localDocuments.getDocumentsMatchingQuery(query, fieldIndex.getIndexState().getReadTime());
      for (Map.Entry<DocumentKey, Document> entry : additionalDocuments) {
        indexedDocuments = indexedDocuments.insert(entry.getKey(), entry.getValue());
      }
      return indexedDocuments;
    } else {
      return executeFullCollectionScan(query);
    }
  }

  private ImmutableSortedMap<DocumentKey, Document> executeFullCollectionScan(Query query) {
    if (Logger.isDebugEnabled()) {
      Logger.debug(LOG_TAG, "Using full collection scan to execute query: %s", query.toString());
    }
    return localDocuments.getDocumentsMatchingQuery(query, SnapshotVersion.NONE);
  }
}
