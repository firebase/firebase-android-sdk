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
import com.google.firebase.firestore.model.NoDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;

/**
 * A readonly view of the local state of all documents we're tracking (i.e. we have a cached version
 * in remoteDocumentCache or local mutations for the document). The view is computed by applying the
 * mutations in the MutationQueue to the RemoteDocumentCache.
 */
// TODO: Turn this into the UnifiedDocumentCache / whatever.
final class LocalDocumentsView {

  private final RemoteDocumentCache remoteDocumentCache;
  private final MutationQueue mutationQueue;

  LocalDocumentsView(RemoteDocumentCache remoteDocumentCache, MutationQueue mutationQueue) {
    this.remoteDocumentCache = remoteDocumentCache;
    this.mutationQueue = mutationQueue;
  }

  /**
   * Returns the the local view of the document identified by {@code key}.
   *
   * @return Local view of the document or null if we don't have any cached state for it.
   */
  @Nullable
  MaybeDocument getDocument(DocumentKey key) {
    List<MutationBatch> batches = mutationQueue.getAllMutationBatchesAffectingDocumentKey(key);
    return getDocument(key, batches);
  }

  // Internal version of {@code getDocument} that allows reusing batches.
  @Nullable
  private MaybeDocument getDocument(DocumentKey key, List<MutationBatch> inBatches) {
    @Nullable MaybeDocument document = remoteDocumentCache.get(key);
    for (MutationBatch batch : inBatches) {
      document = batch.applyToLocalView(key, document);
    }

    return document;
  }

  /**
   * Gets the local view of the documents identified by {@code keys}.
   *
   * <p>If we don't have cached state for a document in {@code keys}, a NoDocument will be stored
   * for that key in the resulting set.
   */
  ImmutableSortedMap<DocumentKey, MaybeDocument> getDocuments(Iterable<DocumentKey> keys) {
    ImmutableSortedMap<DocumentKey, MaybeDocument> results = emptyMaybeDocumentMap();

    List<MutationBatch> batches = mutationQueue.getAllMutationBatchesAffectingDocumentKeys(keys);
    for (DocumentKey key : keys) {
      // TODO: PERF: Consider fetching all remote documents at once rather than
      // one-by-one.
      MaybeDocument maybeDoc = getDocument(key, batches);
      // TODO: Don't conflate missing / deleted.
      if (maybeDoc == null) {
        maybeDoc = new NoDocument(key, SnapshotVersion.NONE, /*hasCommittedMutations=*/ false);
      }
      results = results.insert(key, maybeDoc);
    }
    return results;
  }

  // TODO: The Querying implementation here should move 100% to SimpleQueryEngine.
  // Instead, we should just provide a getCollectionDocuments() method here that return all the
  // documents in a given collection so that SimpleQueryEngine can do that and then filter in
  // memory.

  /** Performs a query against the local view of all documents. */
  ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(Query query) {
    ResourcePath path = query.getPath();
    if (DocumentKey.isDocumentKey(path)) {
      return getDocumentsMatchingDocumentQuery(path);
    } else {
      return getDocumentsMatchingCollectionQuery(query);
    }
  }

  /** Performs a simple document lookup for the given path. */
  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingDocumentQuery(
      ResourcePath path) {
    ImmutableSortedMap<DocumentKey, Document> result = emptyDocumentMap();
    // Just do a simple document lookup.
    MaybeDocument doc = getDocument(DocumentKey.fromPath(path));
    if (doc instanceof Document) {
      result = result.insert(doc.getKey(), (Document) doc);
    }
    return result;
  }

  /** Queries the remote documents and overlays mutations. */
  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingCollectionQuery(
      Query query) {
    ImmutableSortedMap<DocumentKey, Document> results =
        remoteDocumentCache.getAllDocumentsMatchingQuery(query);

    List<MutationBatch> matchingBatches = mutationQueue.getAllMutationBatchesAffectingQuery(query);
    for (MutationBatch batch : matchingBatches) {
      for (Mutation mutation : batch.getMutations()) {
        // Only process documents belonging to the collection.
        if (!query.getPath().isImmediateParentOf(mutation.getKey().getPath())) {
          continue;
        }

        DocumentKey key = mutation.getKey();
        MaybeDocument baseDoc = results.get(key);
        MaybeDocument mutatedDoc =
            mutation.applyToLocalView(baseDoc, baseDoc, batch.getLocalWriteTime());
        if (mutatedDoc instanceof Document) {
          results = results.insert(key, (Document) mutatedDoc);
        } else {
          results = results.remove(key);
        }
      }
    }

    // Finally, filter out any documents that don't actually match the query.
    for (Map.Entry<DocumentKey, Document> docEntry : results) {
      if (!query.matches(docEntry.getValue())) {
        results = results.remove(docEntry.getKey());
      }
    }

    return results;
  }
}
