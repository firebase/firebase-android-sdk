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
import static com.google.firebase.firestore.util.Util.values;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.core.Target;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.Logger;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * The Firestore query engine.
 *
 * <p>Firestore queries can be executed in three modes. The Query Engine determines what mode to use
 * based on what data is persisted. The mode only determines the runtime complexity of the query -
 * the result set is equivalent across all implementations.
 *
 * <p>The Query engine will use indexed-based execution if a user has configured any index that can
 * be used to execute query (via {@link FirebaseFirestore#setIndexConfiguation}). Otherwise, the
 * engine will try to optimize the query by re-using a previously persisted query result. If that is
 * not possible, the query will be executed via a full collection scan.
 *
 * <p>Index-based execution is the default when available. The query engine supports partial indexed
 * execution and merges the result from the index lookup with documents that have not yet been
 * indexed. The index evaluation matches the backend's format and as such, the SDK can use indexing
 * for all queries that the backend supports.
 *
 * <p>If no index exists, the query engine tries to take advantage of the target document mapping in
 * the TargetCache. These mappings exists for all queries that have been synced with the backend at
 * least once and allow the query engine to only read documents that previously matched a query plus
 * any documents that were edited after the query was last listened to.
 *
 * <p>There are some cases where this specific optimization is not guaranteed to produce the same
 * results as full collection scans. In these cases, query processing falls back to full scans.
 * These cases are:
 *
 * <ol>
 *   <li>Limit queries where a document that matched the query previously no longer matches the
 *       query.
 *   <li>Limit queries where a document edit may cause the document to sort below another document
 *       that is in the local cache.
 *   <li>Queries that have never been CURRENT or free of limbo documents.
 * </ol>
 */
public class QueryEngine {
  private static final String LOG_TAG = "QueryEngine";

  private LocalDocumentsView localDocumentsView;
  private IndexManager indexManager;
  private boolean initialized;

  public void initialize(LocalDocumentsView localDocumentsView, IndexManager indexManager) {
    this.localDocumentsView = localDocumentsView;
    this.indexManager = indexManager;
    this.initialized = true;
  }

  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    hardAssert(initialized, "initialize() not called");

    ImmutableSortedMap<DocumentKey, Document> result =
        performQueryUsingIndex(query, query.toTarget());
    if (result != null) {
      return result;
    }

    result = performQueryUsingRemoteKeys(query, remoteKeys, lastLimboFreeSnapshotVersion);
    if (result != null) {
      return result;
    }

    return executeFullCollectionScan(query);
  }

  /**
   * Performs an indexed query that evaluates the query based on a collection's persisted index
   * values. Returns {@code null} if an index is not available.
   */
  private @Nullable ImmutableSortedMap<DocumentKey, Document> performQueryUsingIndex(
      Query query, Target target) {
    if (query.matchesAllDocuments()) {
      // Don't use index queries that can be executed by scanning the collection.
      return null;
    }

    if (!indexManager.canServeFromIndex(target)) {
      return null;
    }

    Set<DocumentKey> keys = indexManager.getDocumentsMatchingTarget(target);
    ImmutableSortedMap<DocumentKey, Document> indexedDocuments =
        localDocumentsView.getDocuments(keys);

    return appendRemainingResults(values(indexedDocuments), query, indexManager.minOffset(target));
  }

  /**
   * Performs a query based on the target's persisted query mapping. Returns {@code null} if the
   * mapping is not available or cannot be used.
   */
  private @Nullable ImmutableSortedMap<DocumentKey, Document> performQueryUsingRemoteKeys(
      Query query,
      ImmutableSortedSet<DocumentKey> remoteKeys,
      SnapshotVersion lastLimboFreeSnapshotVersion) {
    if (query.matchesAllDocuments()) {
      // Don't use index queries that can be executed by scanning the collection.
      return null;
    }

    if (lastLimboFreeSnapshotVersion.equals(SnapshotVersion.NONE)) {
      // Queries that have never seen a snapshot without limbo free documents should be run as a
      // full collection scan.
      return null;
    }

    ImmutableSortedMap<DocumentKey, Document> documents =
        localDocumentsView.getDocuments(remoteKeys);
    ImmutableSortedSet<Document> previousResults = applyQuery(query, documents);

    if ((query.hasLimitToFirst() || query.hasLimitToLast())
        && needsRefill(
            query.getLimitType(), previousResults, remoteKeys, lastLimboFreeSnapshotVersion)) {
      return null;
    }

    if (Logger.isDebugEnabled()) {
      Logger.debug(
          LOG_TAG,
          "Re-using previous result from %s to execute query: %s",
          lastLimboFreeSnapshotVersion.toString(),
          query.toString());
    }

    return appendRemainingResults(
        previousResults,
        query,
        IndexOffset.createSuccessor(
            lastLimboFreeSnapshotVersion, FieldIndex.INITIAL_LARGEST_BATCH_ID));
  }

  /** Applies the query filter and sorting to the provided documents. */
  private ImmutableSortedSet<Document> applyQuery(
      Query query, ImmutableSortedMap<DocumentKey, Document> documents) {
    // Sort the documents and re-apply the query filter since previously matching documents do not
    // necessarily still match the query.
    ImmutableSortedSet<Document> queryResults =
        new ImmutableSortedSet<>(Collections.emptyList(), query.comparator());
    for (Map.Entry<DocumentKey, Document> entry : documents) {
      Document document = entry.getValue();
      if (query.matches(document)) {
        queryResults = queryResults.insert(document);
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

  private ImmutableSortedMap<DocumentKey, Document> executeFullCollectionScan(Query query) {
    if (Logger.isDebugEnabled()) {
      Logger.debug(LOG_TAG, "Using full collection scan to execute query: %s", query.toString());
    }
    return localDocumentsView.getDocumentsMatchingQuery(query, IndexOffset.NONE);
  }

  /**
   * Combines the results from an indexed execution with the remaining documents that have not yet
   * been indexed.
   */
  private ImmutableSortedMap<DocumentKey, Document> appendRemainingResults(
      Iterable<Document> indexedResults, Query query, IndexOffset offset) {
    // Retrieve all results for documents that were updated since the offset.
    ImmutableSortedMap<DocumentKey, Document> remainingResults =
        localDocumentsView.getDocumentsMatchingQuery(query, offset);
    for (Document entry : indexedResults) {
      remainingResults = remainingResults.insert(entry.getKey(), entry);
    }
    return remainingResults;
  }
}
