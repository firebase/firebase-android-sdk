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
 * A query engine that takes advantage of the target document mapping in the TargetCache. Query
 * execution is optimized by only reading the documents that previously matched a query plus any
 * documents that were edited after the query was last listened to.
 *
 * <p>There are some cases where this optimization is not guaranteed to produce the same results as
 * full collection scans. In these cases, query processing falls back to full scans. These cases
 * are:
 *
 * <ol>
 *   <li>Limit queries where a document that matched the query previously no longer matches the
 *       query.
 *   <li>Limit queries where a document edit may cause the document to sort below another document
 *       that is in the local cache.
 *   <li>Queries that have never been CURRENT or free of limbo documents.
 * </ol>
 */
public class DefaultQueryEngine implements QueryEngine {
  private static final String LOG_TAG = "DefaultQueryEngine";

  private LocalDocumentsView localDocumentsView;

  @Override
  public void setLocalDocumentsView(LocalDocumentsView localDocuments) {
    this.localDocumentsView = localDocuments;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    hardAssert(localDocumentsView != null, "setLocalDocumentsView() not called");

    // Queries that match all documents don't benefit from using key-based lookups. It is more
    // efficient to scan all documents in a collection, rather than to perform individual lookups.
    if (query.matchesAllDocuments()) {
      return executeFullCollectionScan(query);
    }

    // Queries that have never seen a snapshot without limbo free documents should also be run as a
    // full collection scan.
    if (lastLimboFreeSnapshotVersion.equals(SnapshotVersion.NONE)) {
      return executeFullCollectionScan(query);
    }

    ImmutableSortedMap<DocumentKey, MaybeDocument> documents =
        localDocumentsView.getDocuments(remoteKeys);
    ImmutableSortedSet<Document> previousResults = applyQuery(query, documents);

    if ((query.hasLimitToFirst() || query.hasLimitToLast())
        && needsRefill(
            query.getLimitType(), previousResults, remoteKeys, lastLimboFreeSnapshotVersion)) {
      return executeFullCollectionScan(query);
    }

    if (Logger.isDebugEnabled()) {
      Logger.debug(
          LOG_TAG,
          "Re-using previous result from %s to execute query: %s",
          lastLimboFreeSnapshotVersion.toString(),
          query.toString());
    }

    // Retrieve all results for documents that were updated since the last limbo-document free
    // remote snapshot.
    ImmutableSortedMap<DocumentKey, Document> updatedResults =
        localDocumentsView.getDocumentsMatchingQuery(query, lastLimboFreeSnapshotVersion);

    // We merge `previousResults` into `updateResults`, since `updateResults` is already a
    // ImmutableSortedMap. If a document is contained in both lists, then its contents are the same.
    for (Document result : previousResults) {
      updatedResults = updatedResults.insert(result.getKey(), result);
    }

    return updatedResults;
  }

  /** Applies the query filter and sorting to the provided documents. */
  private ImmutableSortedSet<Document> applyQuery(
      Query query, ImmutableSortedMap<DocumentKey, MaybeDocument> documents) {
    // Sort the documents and re-apply the query filter since previously matching documents do not
    // necessarily still match the query.
    ImmutableSortedSet<Document> queryResults =
        new ImmutableSortedSet<>(Collections.emptyList(), query.comparator());
    for (Map.Entry<DocumentKey, MaybeDocument> entry : documents) {
      MaybeDocument maybeDoc = entry.getValue();
      if (maybeDoc instanceof Document && query.matches((Document) maybeDoc)) {
        Document doc = (Document) maybeDoc;
        queryResults = queryResults.insert(doc);
      }
    }
    return queryResults;
  }

  /**
   * Determines if a limit query needs to be refilled from cache, making it ineligible for
   * index-free execution.
   *
   * @param limitType The type of limit query for refill calculation.
   * @param sortedPreviousResults The documents that matched the query when it was last
   *     synchronized, sorted by the query's comparator.
   * @param remoteKeys The document keys that matched the query at the last snapshot.
   * @param limboFreeSnapshotVersion The version of the snapshot when the query was last
   *     synchronized.
   */
  private boolean needsRefill(
      Query.LimitType limitType,
      ImmutableSortedSet<Document> sortedPreviousResults,
      ImmutableSortedSet<DocumentKey> remoteKeys,
      SnapshotVersion limboFreeSnapshotVersion) {
    // The query needs to be refilled if a previously matching document no longer matches.
    if (remoteKeys.size() != sortedPreviousResults.size()) {
      return true;
    }

    // Limit queries are not eligible for index-free query execution if there is a potential that an
    // older document from cache now sorts before a document that was previously part of the limit.
    // This, however, can only happen if the document at the edge of the limit goes out of limit. If
    // a document that is not the limit boundary sorts differently, the boundary of the limit itself
    // did not change and documents from cache will continue to be "rejected" by this boundary.
    // Therefore, we can ignore any modifications that don't affect the last document.
    Document documentAtLimitEdge =
        limitType == Query.LimitType.LIMIT_TO_FIRST
            ? sortedPreviousResults.getMaxEntry()
            : sortedPreviousResults.getMinEntry();
    if (documentAtLimitEdge == null) {
      // We don't need to refill the query if there were already no documents.
      return false;
    }
    return documentAtLimitEdge.hasPendingWrites()
        || documentAtLimitEdge.getVersion().compareTo(limboFreeSnapshotVersion) > 0;
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
