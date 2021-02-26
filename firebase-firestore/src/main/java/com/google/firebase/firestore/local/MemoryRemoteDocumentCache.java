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

import static com.google.firebase.firestore.model.DocumentCollections.emptyMutableDocumentMap;
import static com.google.firebase.firestore.util.Assert.hardAssert;

import android.util.Pair;
import androidx.annotation.NonNull;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** In-memory cache of remote documents. */
final class MemoryRemoteDocumentCache implements RemoteDocumentCache {

  /** Underlying cache of documents and their read times. */
  private ImmutableSortedMap<DocumentKey, Pair<MutableDocument, SnapshotVersion>> docs;

  private final MemoryPersistence persistence;

  MemoryRemoteDocumentCache(MemoryPersistence persistence) {
    docs = ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
    this.persistence = persistence;
  }

  @Override
  public void add(MutableDocument document, SnapshotVersion readTime) {
    hardAssert(
        !readTime.equals(SnapshotVersion.NONE),
        "Cannot add document to the RemoteDocumentCache with a read time of zero");
    docs = docs.insert(document.getKey(), new Pair<>(document.clone(), readTime));

    persistence.getIndexManager().addToCollectionParentIndex(document.getKey().getPath().popLast());
  }

  @Override
  public void remove(DocumentKey key) {
    docs = docs.remove(key);
  }

  @Override
  public MutableDocument get(DocumentKey key) {
    Pair<MutableDocument, SnapshotVersion> entry = docs.get(key);
    return entry != null ? entry.first.clone() : MutableDocument.newInvalidDocument(key);
  }

  @Override
  public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> keys) {
    Map<DocumentKey, MutableDocument> result = new HashMap<>();
    for (DocumentKey key : keys) {
      result.put(key, get(key));
    }
    return result;
  }

  @Override
  public ImmutableSortedMap<DocumentKey, MutableDocument> getAllDocumentsMatchingQuery(
      Query query, SnapshotVersion sinceReadTime) {
    hardAssert(
        !query.isCollectionGroupQuery(),
        "CollectionGroup queries should be handled in LocalDocumentsView");
    ImmutableSortedMap<DocumentKey, MutableDocument> result = emptyMutableDocumentMap();

    // Documents are ordered by key, so we can use a prefix scan to narrow down the documents
    // we need to match the query against.
    ResourcePath queryPath = query.getPath();
    DocumentKey prefix = DocumentKey.fromPath(queryPath.append(""));
    Iterator<Map.Entry<DocumentKey, Pair<MutableDocument, SnapshotVersion>>> iterator =
        docs.iteratorFrom(prefix);

    while (iterator.hasNext()) {
      Map.Entry<DocumentKey, Pair<MutableDocument, SnapshotVersion>> entry = iterator.next();

      DocumentKey key = entry.getKey();
      if (!queryPath.isPrefixOf(key.getPath())) {
        break;
      }

      MutableDocument doc = entry.getValue().first;
      if (!doc.isFoundDocument()) {
        continue;
      }

      SnapshotVersion readTime = entry.getValue().second;
      if (readTime.compareTo(sinceReadTime) <= 0) {
        continue;
      }

      if (!query.matches(doc)) {
        continue;
      }

      result = result.insert(doc.getKey(), doc.clone());
    }

    return result;
  }

  Iterable<MutableDocument> getDocuments() {
    return new DocumentIterable();
  }

  long getByteSize(LocalSerializer serializer) {
    long count = 0;
    for (MutableDocument doc : new DocumentIterable()) {
      count += serializer.encodeMaybeDocument(doc).getSerializedSize();
    }
    return count;
  }

  /**
   * A proxy that exposes an iterator over the current set of documents in the RemoteDocumentCache.
   */
  private class DocumentIterable implements Iterable<MutableDocument> {
    @NonNull
    @Override
    public Iterator<MutableDocument> iterator() {
      Iterator<Map.Entry<DocumentKey, Pair<MutableDocument, SnapshotVersion>>> iterator =
          MemoryRemoteDocumentCache.this.docs.iterator();
      return new Iterator<MutableDocument>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public MutableDocument next() {
          return iterator.next().getValue().first;
        }
      };
    }
  }
}
