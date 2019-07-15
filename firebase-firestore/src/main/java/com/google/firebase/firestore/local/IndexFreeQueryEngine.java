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

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.Map;
import javax.annotation.Nullable;

public class IndexFreeQueryEngine implements QueryEngine {
  private final LocalDocumentsView localDocumentsView;
  private final QueryCache queryCache;

  public IndexFreeQueryEngine(LocalDocumentsView localDocumentsView, QueryCache queryCache) {
    this.localDocumentsView = localDocumentsView;
    this.queryCache = queryCache;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query, @Nullable QueryData queryData) {
    if (isSynced(queryData) && !matchesAllDocuments(query)) {
      // Retrieve all results for documents that were updated since the last remote snapshot.
      ImmutableSortedMap<DocumentKey, Document> docs =
          localDocumentsView.getDocumentsMatchingQuery(query, queryData.getSnapshotVersion());

      // Merge with the documents that matched the query per the last remote snapshot.
      ImmutableSortedSet<DocumentKey> remoteKeys =
          queryCache.getMatchingKeysForTargetId(queryData.getTargetId());
      ImmutableSortedMap<DocumentKey, MaybeDocument> previousResults =
          localDocumentsView.getDocuments(remoteKeys);
      for (Map.Entry<DocumentKey, MaybeDocument> entry : previousResults) {
        MaybeDocument maybeDoc = entry.getValue();
        // Apply the query filter since previously matching documents do not necessarily still
        // match the query.
        if (maybeDoc instanceof Document && query.matches((Document) maybeDoc)) {
          docs = docs.insert(entry.getKey(), (Document) maybeDoc);
        }
      }
      return docs;
    } else {
      return localDocumentsView.getDocumentsMatchingQuery(query, SnapshotVersion.NONE);
    }
  }

  @Override
  public void handleDocumentChange(MaybeDocument oldDocument, MaybeDocument newDocument) {
    // No indexes to update.
  }

  private boolean isSynced(@Nullable QueryData queryData) {
    return queryData != null && queryData.isSynced();
  }

  private static boolean matchesAllDocuments(Query query) {
    return query.getFilters().isEmpty()
        && !query.hasLimit()
        && query.getEndAt() == null
        && query.getStartAt() == null;
  }
}
