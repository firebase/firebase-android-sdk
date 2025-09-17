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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.util.ImmutableCollection;
import com.google.firebase.firestore.util.ImmutableHashMap;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

/** In-memory cache of remote documents. */
final class MemoryRemoteDocumentCache implements RemoteDocumentCache {

  /**
   * Underlying cache of documents and their read times.
   * <p>
   * A {@link TreeMap} is used instead of {@link HashMap} because the sorting properties allow
   * {@link #getDocumentsMatchingQuery} to be implemented more efficiently.
   */
  private final TreeMap<DocumentKey, Document> docs = new TreeMap<>();

  /** Manages the collection group index. */
  private IndexManager indexManager;

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
    docs.put(document.getKey(), document.mutableCopy().setReadTime(readTime));
    indexManager.addToCollectionParentIndex(document.getKey().getCollectionPath());
  }

  @Override
  public void removeAll(ImmutableCollection<DocumentKey> keys) {
    hardAssert(indexManager != null, "setIndexManager() not called");

    ImmutableHashMap.Builder<DocumentKey, Document> deletedDocs = new ImmutableHashMap.Builder<>();
    for (DocumentKey key : keys) {
      docs.remove(key);
      deletedDocs.put(key, MutableDocument.newNoDocument(key, SnapshotVersion.NONE));
    }
    indexManager.updateIndexEntries(deletedDocs.build());
  }

  @Override
  public MutableDocument get(DocumentKey key) {
    Document doc = docs.get(key);
    return doc != null ? doc.mutableCopy() : MutableDocument.newInvalidDocument(key);
  }

  @Override
  public HashMap<DocumentKey, MutableDocument> getAll(ImmutableCollection<DocumentKey> keys) {
    HashMap<DocumentKey, MutableDocument> result = new HashMap<>();
    for (DocumentKey key : keys) {
      result.put(key, get(key));
    }
    return result;
  }

  @Override
  public HashMap<DocumentKey, MutableDocument> getAll(
      String collectionGroup, IndexOffset offset, int limit) {
    // This method should only be called from the IndexBackfiller if SQLite is enabled.
    throw new UnsupportedOperationException("getAll(String, IndexOffset, int) is not supported.");
  }

  @Override
  public HashMap<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
      Query query,
      IndexOffset offset,
      @NonNull ImmutableCollection<DocumentKey> mutatedKeys,
      @Nullable QueryContext context) {
    HashMap<DocumentKey, MutableDocument> result = new HashMap<>();

    // Documents are ordered by key, so we can use a prefix scan to narrow down the documents
    // we need to match the query against.
    DocumentKey prefix = DocumentKey.fromPath(query.getPath().append(""));

    for (Map.Entry<DocumentKey, Document> entry : docs.tailMap(prefix).entrySet()) {
      DocumentKey key = entry.getKey();
      Document doc = entry.getValue();

      if (!query.getPath().isPrefixOf(key.getPath())) {
        // We are now scanning the next collection. Abort.
        break;
      }

      if (key.getPath().length() > query.getPath().length() + 1) {
        // Exclude entries from subcollections.
        continue;
      }

      if (IndexOffset.fromDocument(doc).compareTo(offset) <= 0) {
        // The document sorts before the offset.
        continue;
      }

      if (!mutatedKeys.contains(doc.getKey()) && !query.matches(doc)) {
        continue;
      }

      result.put(doc.getKey(), doc.mutableCopy());
    }

    return result;
  }

  @Override
  public HashMap<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
      Query query, IndexOffset offset, @NonNull ImmutableCollection<DocumentKey> mutatedKeys) {
    return getDocumentsMatchingQuery(query, offset, mutatedKeys, /*context*/ null);
  }

  Iterable<Document> getDocuments() {
    return docs.values();
  }

  long getByteSize(LocalSerializer serializer) {
    long count = 0;
    for (Document doc : docs.values()) {
      count += serializer.encodeMaybeDocument(doc).getSerializedSize();
    }
    return count;
  }
}
