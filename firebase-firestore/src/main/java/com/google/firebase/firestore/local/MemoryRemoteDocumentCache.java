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

import static com.google.firebase.firestore.model.DocumentCollections.emptyDocumentMap;
import static com.google.firebase.firestore.model.DocumentCollections.emptyMaybeDocumentMap;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;

/** In-memory cache of remote documents. */
final class MemoryRemoteDocumentCache implements RemoteDocumentCache {

  /** Underlying cache of documents. */
  private ImmutableSortedMap<DocumentKey, MaybeDocument> docs;

  MemoryRemoteDocumentCache() {
    docs = emptyMaybeDocumentMap();
  }

  @Override
  public void add(MaybeDocument document) {
    docs = docs.insert(document.getKey(), document);
  }

  @Override
  public void remove(DocumentKey key) {
    docs = docs.remove(key);
  }

  @Nullable
  @Override
  public MaybeDocument get(DocumentKey key) {
    return docs.get(key);
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getAllDocumentsMatchingQuery(Query query) {
    ImmutableSortedMap<DocumentKey, Document> result = emptyDocumentMap();

    // Documents are ordered by key, so we can use a prefix scan to narrow down the documents
    // we need to match the query against.
    ResourcePath queryPath = query.getPath();
    DocumentKey prefix = DocumentKey.fromPath(queryPath.append(""));
    Iterator<Map.Entry<DocumentKey, MaybeDocument>> iterator = docs.iteratorFrom(prefix);
    while (iterator.hasNext()) {
      Map.Entry<DocumentKey, MaybeDocument> entry = iterator.next();
      DocumentKey key = entry.getKey();
      if (!queryPath.isPrefixOf(key.getPath())) {
        break;
      }

      MaybeDocument maybeDoc = entry.getValue();
      if (!(maybeDoc instanceof Document)) {
        continue;
      }

      Document doc = (Document) maybeDoc;
      if (query.matches(doc)) {
        result = result.insert(doc.getKey(), doc);
      }
    }

    return result;
  }

  ImmutableSortedMap<DocumentKey, MaybeDocument> getDocuments() {
    return docs;
  }
}
