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
import com.google.firebase.firestore.model.DocumentCollections;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A query engine that takes advantage of the target document mapping in the QueryCache. The
 * IndexFreeQueryEngine optimizes query execution reducing the number of documents scanned to the
 * documents that previously matched a query plus any documents plus any documents that were edited
 * after the query was last listened to.
 *
 * There are some cases where Index-Free queries are not guaranteed to produce to the same results
 * as a full collection scan. In this case, the IndexFreeQueryEngine falls back to a full query
 * processing. These cases are
 *
 * - Limit queries where a document that matched the query at the last remote snapshot no longer
 *   matches the query. In this case, we have to scan all local documents since a document that were
 *   sent to us as part of a different query result may now fall into the limit.
 * - Limit queries that include edits that happened after the last remote snapshot (both
 *   latency-compensated and committed). Even if an edited document continues to match the query
 *   constraints, an edit may cause a document to sort below another document that is in the local
 *   cache.
 * - Queries where the last snapshot contained Limbo documents. While a Limbo document is not part
 *   of the backend result set, we need to include Limbo documents in local views to ensure
 *   consistency between different Query views. If any previous query snapshot contained no limbo
 *   documents, we can use the older snapshot version for Index-Free processing.
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
    // efficient to scan all documents in a collection.
    if (query.matchesAllDocuments()) {
      return executeFullQuery(query);
    }

    // Queries that have never seen a snapshot without limbo free documents should also be run as a
    // full collection scan.
    if (queryData == null
        || queryData.getLastLimboFreeSnapshotVersion().equals(SnapshotVersion.NONE)) {
      return executeFullQuery(query);
    }

    ImmutableSortedMap<DocumentKey, Document> result =
        executeIndexFreeQuery(query, queryData, remoteKeys);

    return result != null ? result : executeFullQuery(query);
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

    // Limit queries are not eligible for index-free query execution if any document was modified
    // after we received the last query snapshot. This makes sure that we can re-populate the limit
    // if an older document from cache sorts before the modified document.
    if (query.hasLimit()
        && containsUpdatesSinceSnapshotVersion(previousResults, queryData.getSnapshotVersion())) {
      return null;
    }

    ImmutableSortedMap<DocumentKey, Document> docs = DocumentCollections.emptyDocumentMap();

    // Re-Apply the query filter since previously matching documents do not necessarily still
    // match the query.
    for (Map.Entry<DocumentKey, MaybeDocument> entry : previousResults) {
      MaybeDocument maybeDoc = entry.getValue();
      if (maybeDoc instanceof Document && query.matches((Document) maybeDoc)) {
        Document doc = (Document) maybeDoc;
        docs = docs.insert(entry.getKey(), doc);
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

    docs = docs.insertAll(updatedResults);

    return docs;
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

  private ImmutableSortedMap<DocumentKey, Document> executeFullQuery(Query query) {
    return localDocumentsView.getDocumentsMatchingQuery(query, SnapshotVersion.NONE);
  }
}
