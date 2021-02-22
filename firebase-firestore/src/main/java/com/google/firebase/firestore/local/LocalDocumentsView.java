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

import androidx.annotation.VisibleForTesting;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * A readonly view of the local state of all documents we're tracking (i.e. we have a cached version
 * in remoteDocumentCache or local mutations for the document). The view is computed by applying the
 * mutations in the MutationQueue to the RemoteDocumentCache.
 */
// TODO: Turn this into the UnifiedDocumentCache / whatever.
class LocalDocumentsView {

  private final RemoteDocumentCache remoteDocumentCache;
  private final MutationQueue mutationQueue;
  private final IndexManager indexManager;

  LocalDocumentsView(
      RemoteDocumentCache remoteDocumentCache,
      MutationQueue mutationQueue,
      IndexManager indexManager) {
    this.remoteDocumentCache = remoteDocumentCache;
    this.mutationQueue = mutationQueue;
    this.indexManager = indexManager;
  }

  @VisibleForTesting
  RemoteDocumentCache getRemoteDocumentCache() {
    return remoteDocumentCache;
  }

  @VisibleForTesting
  MutationQueue getMutationQueue() {
    return mutationQueue;
  }

  @VisibleForTesting
  IndexManager getIndexManager() {
    return indexManager;
  }

  /**
   * Returns the the local view of the document identified by {@code key}.
   *
   * @return Local view of the document or an invalid document if we don't have any cached state for
   *     it.
   */
  MutableDocument getDocument(DocumentKey key) {
    List<MutationBatch> batches = mutationQueue.getAllMutationBatchesAffectingDocumentKey(key);
    return getDocument(key, batches);
  }

  // Internal version of {@code getDocument} that allows reusing batches.
  private MutableDocument getDocument(DocumentKey key, List<MutationBatch> inBatches) {
    MutableDocument document = remoteDocumentCache.get(key);
    for (MutationBatch batch : inBatches) {
      batch.applyToLocalView(document);
    }

    return document;
  }

  // Returns the view of the given {@code docs} as they would appear after applying all mutations in
  // the given {@code batches}.
  private void applyLocalMutationsToDocuments(
          Map<DocumentKey, MutableDocument> docs, List<MutationBatch> batches) {
    for (Map.Entry<DocumentKey, MutableDocument> base : docs.entrySet()) {
      for (MutationBatch batch : batches) {
        batch.applyToLocalView(base.getValue());
      }
    }
  }

  /**
   * Gets the local view of the documents identified by {@code keys}.
   *
   * <p>If we don't have cached state for a document in {@code keys}, a NoDocument will be stored
   * for that key in the resulting set.
   */
  ImmutableSortedMap<DocumentKey, MutableDocument> getDocuments(Iterable<DocumentKey> keys) {
    Map<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(keys);
    return getLocalViewOfDocuments(docs);
  }

  /**
   * Similar to {@code #getDocuments}, but creates the local view from the given {@code baseDocs}
   * without retrieving documents from the local store.
   */
  ImmutableSortedMap<DocumentKey, MutableDocument> getLocalViewOfDocuments(
      Map<DocumentKey, MutableDocument> docs) {
    ImmutableSortedMap<DocumentKey, MutableDocument> results = emptyDocumentMap();

    List<MutationBatch> batches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKeys(docs.keySet());
    applyLocalMutationsToDocuments(docs, batches);
    for (Map.Entry<DocumentKey, MutableDocument> entry : docs.entrySet()) {
      results = results.insert(entry.getKey(), entry.getValue());
    }
    return results;
  }

  // TODO: The Querying implementation here should move 100% to the query engines.
  // Instead, we should just provide a getCollectionDocuments() method here that return all the
  // documents in a given collection so that query engine can do that and then filter in
  // memory.

  /**
   * Performs a query against the local view of all documents.
   *
   * @param query The query to match documents against.
   * @param sinceReadTime If not set to SnapshotVersion.MIN, return only documents that have been
   *     read since this snapshot version (exclusive).
   */
  ImmutableSortedMap<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
      Query query, SnapshotVersion sinceReadTime) {
    ResourcePath path = query.getPath();
    if (query.isDocumentQuery()) {
      return getDocumentsMatchingDocumentQuery(path);
    } else if (query.isCollectionGroupQuery()) {
      return getDocumentsMatchingCollectionGroupQuery(query, sinceReadTime);
    } else {
      return getDocumentsMatchingCollectionQuery(query, sinceReadTime);
    }
  }

  /** Performs a simple document lookup for the given path. */
  private ImmutableSortedMap<DocumentKey, MutableDocument> getDocumentsMatchingDocumentQuery(
      ResourcePath path) {
    ImmutableSortedMap<DocumentKey, MutableDocument> result = emptyDocumentMap();
    // Just do a simple document lookup.
    MutableDocument doc = getDocument(DocumentKey.fromPath(path));
    if (doc.isFoundDocument()) {
      result = result.insert(doc.getKey(), doc);
    }
    return result;
  }

  private ImmutableSortedMap<DocumentKey, MutableDocument> getDocumentsMatchingCollectionGroupQuery(
      Query query, SnapshotVersion sinceReadTime) {
    hardAssert(
        query.getPath().isEmpty(),
        "Currently we only support collection group queries at the root.");
    String collectionId = query.getCollectionGroup();
    ImmutableSortedMap<DocumentKey, MutableDocument> results = emptyDocumentMap();
    List<ResourcePath> parents = indexManager.getCollectionParents(collectionId);

    // Perform a collection query against each parent that contains the collectionId and
    // aggregate the results.
    for (ResourcePath parent : parents) {
      Query collectionQuery = query.asCollectionQueryAtPath(parent.append(collectionId));
      ImmutableSortedMap<DocumentKey, MutableDocument> collectionResults =
          getDocumentsMatchingCollectionQuery(collectionQuery, sinceReadTime);
      for (Map.Entry<DocumentKey, MutableDocument> docEntry : collectionResults) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
      }
    }
    return results;
  }

  /** Queries the remote documents and overlays mutations. */
  private ImmutableSortedMap<DocumentKey, MutableDocument> getDocumentsMatchingCollectionQuery(
      Query query, SnapshotVersion sinceReadTime) {
    ImmutableSortedMap<DocumentKey, MutableDocument> results =
        remoteDocumentCache.getAllDocumentsMatchingQuery(query, sinceReadTime);

    List<MutationBatch> matchingBatches = mutationQueue.getAllMutationBatchesAffectingQuery(query);

    results = addMissingBaseDocuments(matchingBatches, results);

    for (MutationBatch batch : matchingBatches) {
      for (Mutation mutation : batch.getMutations()) {
        // Only process documents belonging to the collection.
        if (!query.getPath().isImmediateParentOf(mutation.getKey().getPath())) {
          continue;
        }

        DocumentKey key = mutation.getKey();
        MutableDocument document = results.get(key);
        if (document == null) {
          document = new MutableDocument(key); // Create invalid document to apply mutations on top of
          results = results.insert(key, document);
        }
        mutation.applyToLocalView(document, batch.getLocalWriteTime());
        if (!document.isFoundDocument()) {
          results = results.remove(key);
        }
      }
    }

    // Finally, filter out any documents that don't actually match the query.
    for (Map.Entry<DocumentKey, MutableDocument> docEntry : results) {
      if (!query.matches(docEntry.getValue())) {
        results = results.remove(docEntry.getKey());
      }
    }

    return results;
  }

  /**
   * It is possible that a {@code PatchMutation} can make a document match a query, even if the
   * version in the {@code RemoteDocumentCache} is not a match yet (waiting for server to ack). To
   * handle this, we find all document keys affected by the {@code PatchMutation}s that are not in
   * {@code existingDocs} yet, and back fill them via {@code remoteDocumentCache.getAll}, otherwise
   * those {@code PatchMutation}s will be ignored because no base document can be found, and lead to
   * missing results for the query.
   */
  private ImmutableSortedMap<DocumentKey, MutableDocument> addMissingBaseDocuments(
      List<MutationBatch> matchingBatches, ImmutableSortedMap<DocumentKey, MutableDocument> existingDocs) {
    HashSet<DocumentKey> missingDocKeys = new HashSet<>();
    for (MutationBatch batch : matchingBatches) {
      for (Mutation mutation : batch.getMutations()) {
        if (mutation instanceof PatchMutation && !existingDocs.containsKey(mutation.getKey())) {
          missingDocKeys.add(mutation.getKey());
        }
      }
    }

    ImmutableSortedMap<DocumentKey, MutableDocument> mergedDocs = existingDocs;
    Map<DocumentKey, MutableDocument> missingDocs = remoteDocumentCache.getAll(missingDocKeys);
    for (Map.Entry<DocumentKey, MutableDocument> entry : missingDocs.entrySet()) {
      if (entry.getValue().isFoundDocument()) {
        mergedDocs = mergedDocs.insert(entry.getKey(), entry.getValue());
      }
    }

    return mergedDocs;
  }
}
