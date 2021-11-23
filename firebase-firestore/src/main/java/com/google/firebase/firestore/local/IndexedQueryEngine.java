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

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
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
    if (query.matchesAllDocuments()) {
      return executeFullCollectionScan(query);
    }

    Target target = query.toTarget();

    FieldIndex fieldIndex = indexManager.getFieldIndex(target);
    // TODO(indexing): Fall back to DefaultQueryEngine if index-free queries is available
    if (fieldIndex != null) {
      // If there is an index, use the index to execute the query up to its last update time.
      // Results that have not yet been written to the index get merged into the result.
      Set<DocumentKey> keys = indexManager.getDocumentsMatchingTarget(fieldIndex, target);
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
