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

package com.google.firebase.firestore.local;

import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Map;

/**
 * A query engine that takes advantage of the target document mapping in the QueryCache. The
 * IndexFreeQueryEngine optimizes query execution by only reading the documents previously matched a
 * query plus any documents that were edited after the query was last listened to.
 *
 * <p>There are some cases where Index-Free queries are not guaranteed to produce to the same
 * results as full collection scans. In these case, the IndexFreeQueryEngine falls back to a full
 * query processing. These cases are:
 *
 * <ol>
 *   <li>Limit queries where a document that matched the query previously no longer matches the
 *       query. In this case, we have to scan all local documents since a document that was sent to
 *       us as part of a different query result may now fall into the limit.
 *   <li>Limit queries that include edits that occurred after the last remote snapshot (both
 *       latency-compensated or committed). Even if an edited document continues to match the query,
 *       an edit may cause a document to sort below another document that is in the local cache.
 *   <li>Queries where the last snapshot contained Limbo documents. Even though a Limbo document is
 *       not part of the backend result set, we need to include Limbo documents in local views to
 *       ensure consistency between different Query views. If there exists a previous query snapshot
 *       that contained no limbo documents, we can instead use the older snapshot version for
 *       Index-Free processing.
 * </ol>
 */
public class IndexFreeQueryEngine implements QueryEngine {
  private LocalDocumentsView localDocumentsView;

  @Override
  public void setLocalDocumentsView(LocalDocumentsView localDocuments) {
    this.localDocumentsView = localDocuments;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query, @Nullable QueryData queryData, ImmutableSortedSet<DocumentKey> remoteKeys) {
    hardAssert(localDocumentsView != null, "setLocalDocumentsView() not called");

    // Queries that match all document don't benefit from using IndexFreeQueries. It is more
    // efficient to scan all documents in a collection, rather than to perform individual lookups.
    if (query.matchesAllDocuments()) {
      return executeFullCollectionScan(query);
    }

    // Queries that have never seen a snapshot without limbo free documents should also be run as a
    // full collection scan.
    if (queryData == null
        || queryData.getLastLimboFreeSnapshotVersion().equals(SnapshotVersion.NONE)) {
      return executeFullCollectionScan(query);
    }

    ImmutableSortedMap<DocumentKey, Document> result =
        executeIndexFreeQuery(query, queryData, remoteKeys);

    return result != null ? result : executeFullCollectionScan(query);
  }

  /**
   * Attempts index-free query execution. Returns the set of query results on success, otherwise
   * returns null.
   */
  private @Nullable ImmutableSortedMap<DocumentKey, Document> executeIndexFreeQuery(
      Query query, QueryData queryData, ImmutableSortedSet<DocumentKey> remoteKeys) {
    // Fetch the documents that matched the query at the last snapshot.
    ImmutableSortedMap<DocumentKey, MaybeDocument> previousResults =
        localDocumentsView.getDocuments(remoteKeys);

    // Limit queries are not eligible for index-free query execution if any part of the result was
    // modified after we received the last query snapshot. This makes sure that we re-populate the
    // view with older documents that may sort before the modified document.
    if (query.hasLimit()
        && containsUpdatesSinceSnapshotVersion(previousResults, queryData.getSnapshotVersion())) {
      return null;
    }

    ImmutableSortedMap<DocumentKey, Document> results = DocumentCollections.emptyDocumentMap();

    // Re-apply the query filter since previously matching documents do not necessarily still
    // match the query.
    for (Map.Entry<DocumentKey, MaybeDocument> entry : previousResults) {
      MaybeDocument maybeDoc = entry.getValue();
      if (maybeDoc instanceof Document && query.matches((Document) maybeDoc)) {
        Document doc = (Document) maybeDoc;
        results = results.insert(entry.getKey(), doc);
      } else if (query.hasLimit()) {
        // Limit queries with documents that no longer match need to be re-filled from cache.
        return null;
      }
    }

    // Retrieve all results for documents that were updated since the last limbo-document free
    // remote snapshot.
    ImmutableSortedMap<DocumentKey, Document> updatedResults =
        localDocumentsView.getDocumentsMatchingQuery(
            query, queryData.getLastLimboFreeSnapshotVersion());

    results = results.insertAll(updatedResults);

    return results;
  }

  @Override
  public void handleDocumentChange(MaybeDocument oldDocument, MaybeDocument newDocument) {
    // No indexes to update.
  }

  private boolean containsUpdatesSinceSnapshotVersion(
      ImmutableSortedMap<DocumentKey, MaybeDocument> previousResults,
      SnapshotVersion sinceSnapshotVersion) {
    for (Map.Entry<DocumentKey, MaybeDocument> doc : previousResults) {
      if (doc.getValue().hasPendingWrites()
          || doc.getValue().getVersion().compareTo(sinceSnapshotVersion) > 0) {
        return true;
      }
    }

    return false;
  }

  private ImmutableSortedMap<DocumentKey, Document> executeFullCollectionScan(Query query) {
    return localDocumentsView.getDocumentsMatchingQuery(query, SnapshotVersion.NONE);
  }
}
