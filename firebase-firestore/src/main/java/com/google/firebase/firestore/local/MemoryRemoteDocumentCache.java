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
import static com.google.firebase.firestore.util.Assert.hardAssert;

import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MaybeDocument;
import com.google.firebase.firestore.model.ResourcePath;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.annotation.Nullable;

/** In-memory cache of remote documents. */
final class MemoryRemoteDocumentCache implements RemoteDocumentCache {

  /** Underlying cache of documents. */
  private ImmutableSortedMap<DocumentKey, MaybeDocument> docs;

  private final MemoryPersistence persistence;
  private StatsCollector statsCollector;

  MemoryRemoteDocumentCache(MemoryPersistence persistence, StatsCollector statsCollector) {
    docs = emptyMaybeDocumentMap();
    this.statsCollector = statsCollector;
    this.persistence = persistence;
  }

  @Override
  public void add(MaybeDocument document) {
    docs = docs.insert(document.getKey(), document);

    persistence.getIndexManager().addToCollectionParentIndex(document.getKey().getPath().popLast());
  }

  @Override
  public void remove(DocumentKey key) {
    statsCollector.recordRowsDeleted(STATS_TAG, 1);
    docs = docs.remove(key);
  }

  @Nullable
  @Override
  public MaybeDocument get(DocumentKey key) {
    statsCollector.recordRowsRead(STATS_TAG, 1);
    return docs.get(key);
  }

  @Override
  public Map<DocumentKey, MaybeDocument> getAll(Iterable<DocumentKey> keys) {
    Map<DocumentKey, MaybeDocument> result = new HashMap<>();

    for (DocumentKey key : keys) {
      // Make sure each key has a corresponding entry, which is null in case the document is not
      // found.
      result.put(key, get(key));
    }

    statsCollector.recordRowsRead(STATS_TAG, result.size());
    return result;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getAllDocumentsMatchingQuery(Query query) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");
    ImmutableSortedMap<DocumentKey, Document> result = emptyDocumentMap();

    // Documents are ordered by key, so we can use a prefix scan to narrow down the documents
    // we need to match the query against.
    ResourcePath queryPath = query.getPath();
    DocumentKey prefix = DocumentKey.fromPath(queryPath.append(""));
    Iterator<Map.Entry<DocumentKey, MaybeDocument>> iterator = docs.iteratorFrom(prefix);

    int rowsRead = 0;

    while (iterator.hasNext()) {
      Map.Entry<DocumentKey, MaybeDocument> entry = iterator.next();

      ++rowsRead;

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

    statsCollector.recordRowsRead(STATS_TAG, rowsRead);

    return result;
  }

  ImmutableSortedMap<DocumentKey, MaybeDocument> getDocuments() {
    return docs;
  }

  /**
   * Returns an estimate of the number of bytes used to store the given document key in memory. This
   * is only an estimate and includes the size of the segments of the path, but not any object
   * overhead or path separators.
   */
  private static long getKeySize(DocumentKey key) {
    ResourcePath path = key.getPath();
    long count = 0;
    for (int i = 0; i < path.length(); i++) {
      // Strings in java are utf-16, each character is two bytes in memory
      count += path.getSegment(i).length() * 2;
    }
    return count;
  }

  long getByteSize(LocalSerializer serializer) {
    long count = 0;
    for (Map.Entry<DocumentKey, MaybeDocument> entry : docs) {
      count += getKeySize(entry.getKey());
      count += serializer.encodeMaybeDocument(entry.getValue()).getSerializedSize();
    }
    return count;
  }
}
