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
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Logger;
import java.util.Collections;
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
  private static final String LOG_TAG = "IndexFreeQueryEngine";

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

    ImmutableSortedSet<Document> previousResults = getSortedPreviousResults(query, remoteKeys);

    if (query.hasLimit()
        && needsRefill(previousResults, remoteKeys, queryData.getLastLimboFreeSnapshotVersion())) {
      return executeFullCollectionScan(query);
    }

    if (Logger.isDebugEnabled()) {
      Logger.debug(
          LOG_TAG,
          "Re-using previous result from %s to execute query: %s",
          queryData.getLastLimboFreeSnapshotVersion().toString(),
          query.toString());
    }

    // Retrieve all results for documents that were updated since the last limbo-document free
    // remote snapshot.
    ImmutableSortedMap<DocumentKey, Document> updatedResults =
        localDocumentsView.getDocumentsMatchingQuery(
            query, queryData.getLastLimboFreeSnapshotVersion());
    for (Document result : previousResults) {
      updatedResults = updatedResults.insert(result.getKey(), result);
    }

    return updatedResults;
  }

  /**
   * Returns the documents for the specified remote keys if they still match the query, sorted by
   * the query's comparator.
   */
  private ImmutableSortedSet<Document> getSortedPreviousResults(
      Query query, ImmutableSortedSet<DocumentKey> remoteKeys) {
    // Fetch the documents that matched the query at the last snapshot.
    ImmutableSortedMap<DocumentKey, MaybeDocument> previousResults =
        localDocumentsView.getDocuments(remoteKeys);

    // Sort the documents and re-apply the query filter since previously matching documents do not
    // necessarily still match the query.
    ImmutableSortedSet<Document> results =
        new ImmutableSortedSet<>(Collections.emptyList(), query.comparator());
    for (Map.Entry<DocumentKey, MaybeDocument> entry : previousResults) {
      MaybeDocument maybeDoc = entry.getValue();
      if (maybeDoc instanceof Document && query.matches((Document) maybeDoc)) {
        Document doc = (Document) maybeDoc;
        results = results.insert(doc);
      }
    }
    return results;
  }

  /**
   * Determines if a limit query needs to be refilled from cache, making it ineligible for
   * index-free execution.
   *
   * @param sortedPreviousResults The documents that matched the query when it was last
   *     synchronized, sorted by the query's comparator.
   * @param remoteKeys The document keys that matched the query at the last snapshot.
   * @param limboFreeSnapshotVersion The version of the snapshot when the query was last
   *     synchronized.
   */
  private boolean needsRefill(
      ImmutableSortedSet<Document> sortedPreviousResults,
      ImmutableSortedSet<DocumentKey> remoteKeys,
      SnapshotVersion limboFreeSnapshotVersion) {
    // The query needs to be refilled if a previously matching document no longer matches.
    if (remoteKeys.size() != sortedPreviousResults.size()) {
      return true;
    }

    // We don't need to find a better match from cache if no documents matched the query.
    if (sortedPreviousResults.isEmpty()) {
      return false;
    }

    // Limit queries are not eligible for index-free query execution if there is a potential that an
    // older document from cache now sorts before a document that was previously part of the limit.
    // This, however, can only happen if the last document of the limit sorts lower than it did when
    // the query was last synchronized. If a document that is not the limit boundary sorts
    // differently, the boundary of the limit itself did not change and documents from cache will
    // continue to be "rejected" by this boundary. Therefore, we can ignore any modifications that
    // don't affect the last document.
    Document lastDocumentInLimit = sortedPreviousResults.getMaxEntry();
    return lastDocumentInLimit.hasPendingWrites()
        || lastDocumentInLimit.getVersion().compareTo(limboFreeSnapshotVersion) > 0;
  }

  @Override
  public void handleDocumentChange(MaybeDocument oldDocument, MaybeDocument newDocument) {
    // No indexes to update.
  }

  private ImmutableSortedMap<DocumentKey, Document> executeFullCollectionScan(Query query) {
    if (Logger.isDebugEnabled()) {
      Logger.debug(LOG_TAG, "Using full collection scan to execute query: %s", query.toString());
    }
    return localDocumentsView.getDocumentsMatchingQuery(query, SnapshotVersion.NONE);
  }
}
