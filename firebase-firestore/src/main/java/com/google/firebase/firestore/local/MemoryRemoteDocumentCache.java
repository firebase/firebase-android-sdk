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
import static com.google.firebase.firestore.util.Assert.hardAssert;

import androidx.annotation.NonNull;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/** In-memory cache of remote documents. */
final class MemoryRemoteDocumentCache implements RemoteDocumentCache {

  /** Underlying cache of documents and their read times. */
  private ImmutableSortedMap<DocumentKey, Document> docs;
  /** Manages the collection group index. */
  private IndexManager indexManager;
  /** The latest read time of any document in the cache. */
  private SnapshotVersion latestReadTime;

  MemoryRemoteDocumentCache() {
    docs = emptyDocumentMap();
    latestReadTime = SnapshotVersion.NONE;
  }

  @Override
  public void setIndexManager(IndexManager indexManager) {
    this.indexManager = indexManager;
  }

  @Override
  public void add(MutableDocument document, SnapshotVersion readTime) {
    hardAssert(indexManager != null, "setIndexManager() not called");
    hardAssert(
        !readTime.equals(SnapshotVersion.NONE),
        "Cannot add document to the RemoteDocumentCache with a read time of zero");
    docs = docs.insert(document.getKey(), document.mutableCopy().withReadTime(readTime));
    latestReadTime = readTime.compareTo(latestReadTime) > 0 ? readTime : latestReadTime;

    indexManager.addToCollectionParentIndex(document.getKey().getCollectionPath());
  }

  @Override
  public void remove(DocumentKey key) {
    docs = docs.remove(key);
  }

  @Override
  public MutableDocument get(DocumentKey key) {
    Document doc = docs.get(key);
    return doc != null ? doc.mutableCopy() : MutableDocument.newInvalidDocument(key);
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
  public Map<DocumentKey, MutableDocument> getAll(
      String collectionGroup, IndexOffset offset, int limit) {
    // This method should only be called from the IndexBackfiller if SQLite is enabled.
    throw new UnsupportedOperationException("getAll(String, IndexOffset, int) is not supported.");
  }

  @Override
  public Map<DocumentKey, MutableDocument> getAll(ResourcePath collection, IndexOffset offset) {
    Map<DocumentKey, MutableDocument> result = new HashMap<>();

    // Documents are ordered by key, so we can use a prefix scan to narrow down the documents
    // we need to match the query against.
    DocumentKey prefix = DocumentKey.fromPath(collection.append(""));
    Iterator<Map.Entry<DocumentKey, Document>> iterator = docs.iteratorFrom(prefix);

    while (iterator.hasNext()) {
      Map.Entry<DocumentKey, Document> entry = iterator.next();
      Document doc = entry.getValue();

      DocumentKey key = entry.getKey();
      if (!collection.isPrefixOf(key.getPath())) {
        // We are now scanning the next collection. Abort.
        break;
      }

      if (key.getPath().length() > collection.length() + 1) {
        // Exclude entries from subcollections.
        continue;
      }

      if (IndexOffset.fromDocument(doc).compareTo(offset) <= 0) {
        // The document sorts before the offset.
        continue;
      }

      result.put(doc.getKey(), doc.mutableCopy());
    }

    return result;
  }

  @Override
  public SnapshotVersion getLatestReadTime() {
    return latestReadTime;
  }

  Iterable<Document> getDocuments() {
    return new DocumentIterable();
  }

  long getByteSize(LocalSerializer serializer) {
    long count = 0;
    for (Document doc : new DocumentIterable()) {
      count += serializer.encodeMaybeDocument(doc).getSerializedSize();
    }
    return count;
  }

  /**
   * A proxy that exposes an iterator over the current set of documents in the RemoteDocumentCache.
   */
  private class DocumentIterable implements Iterable<Document> {
    @NonNull
    @Override
    public Iterator<Document> iterator() {
      Iterator<Map.Entry<DocumentKey, Document>> iterator =
          MemoryRemoteDocumentCache.this.docs.iterator();
      return new Iterator<Document>() {
        @Override
        public boolean hasNext() {
          return iterator.hasNext();
        }

        @Override
        public Document next() {
          return iterator.next().getValue();
        }
      };
    }
  }
}
