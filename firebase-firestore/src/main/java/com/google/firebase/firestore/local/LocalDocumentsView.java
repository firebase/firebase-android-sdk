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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.MutationBatchResult;
import com.google.firebase.firestore.model.mutation.MutationResult;
import com.google.firebase.firestore.util.Logger;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A readonly view of the local state of all documents we're tracking (i.e. we have a cached version
 * in remoteDocumentCache or local mutations for the document). The view is computed by applying the
 * mutations in the MutationQueue to the RemoteDocumentCache.
 */
// TODO(overlay): Turn this into the UnifiedDocumentCache / whatever.
class LocalDocumentsView {

  private final RemoteDocumentCache remoteDocumentCache;
  private final LocalDocumentCache localDocumentCache;
  private final MutationQueue mutationQueue;
  private final IndexManager indexManager;

  LocalDocumentsView(
      RemoteDocumentCache remoteDocumentCache,
      MutationQueue mutationQueue,
      IndexManager indexManager,
      LocalDocumentCache localDocumentCache) {
    this.remoteDocumentCache = remoteDocumentCache;
    this.mutationQueue = mutationQueue;
    this.indexManager = indexManager;
    this.localDocumentCache = localDocumentCache;
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
   * @return Local view of the document or a an invalid document if we don't have any cached state
   *     for it.
   */
  Document getDocument(DocumentKey key) {
    MutableDocument local = localDocumentCache.get(key);
    MutableDocument remote = remoteDocumentCache.get(key);
    SnapshotVersion version = remote.getVersion();
    if (local != null) {
      version = local.getVersion().compareTo(version) > 0 ? local.getVersion() : version;
      return local.setVersion(version);
    }
    return remote;
  }

  // Applies the given {@code batches} to the given {@code docs}. The the result of application is
  // returned in a new Map, with both the original doc and the applied doc (if applied).
  private Map<DocumentKey, DocumentBaseAndFinal> applyLocalMutationsToDocuments(
      Map<DocumentKey, MutableDocument> docs, List<MutationBatch> batches) {
    Map<DocumentKey, DocumentBaseAndFinal> result = new HashMap<>();
    for (Map.Entry<DocumentKey, MutableDocument> base : docs.entrySet()) {
      MutableDocument baseDoc = base.getValue().clone();
      DocumentBaseAndFinal baseAndFinal = new DocumentBaseAndFinal();
      baseAndFinal.baseDoc = base.getValue();

      boolean changed = false;
      for (MutationBatch batch : batches) {
        boolean applied = batch.applyToLocalView(baseDoc);
        if (applied && !changed) {
          changed = true;
        }
      }

      if (changed) {
        baseAndFinal.finalDoc = baseDoc;
      }
      result.put(base.getKey(), baseAndFinal);
    }

    return result;
  }

  /**
   * Gets the local view of the documents identified by {@code keys}.
   *
   * <p>If we don't have cached state for a document in {@code keys}, a NoDocument will be stored
   * for that key in the resulting set.
   */
  ImmutableSortedMap<DocumentKey, Document> getDocuments(Iterable<DocumentKey> keys) {
    Map<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(keys);
    Map<DocumentKey, MutableDocument> local_docs = localDocumentCache.getAll(keys);
    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();

    for (Map.Entry<DocumentKey, MutableDocument> entry : docs.entrySet()) {
      if (local_docs.containsKey(entry.getKey())) {
        MutableDocument localDoc = local_docs.get(entry.getKey());
        SnapshotVersion version = entry.getValue().getVersion();
        if (localDoc.getVersion().compareTo(version) > 0) {
          version = localDoc.getVersion();
        }
        results = results.insert(entry.getKey(), localDoc.setVersion(version));
      } else {
        results = results.insert(entry.getKey(), entry.getValue());
      }
    }
    return results;
  }

  public void saveLocalDocuments(ImmutableSortedMap<DocumentKey, Document> documents) {
    for (Map.Entry<DocumentKey, Document> entry : documents) {
      // TODO(Overlay): This cast is ugly.
      localDocumentCache.add((MutableDocument) entry.getValue());
    }
  }

  public void repopulateCache(Set<DocumentKey> keys) {
    Map<DocumentKey, MutableDocument> existingDocs = remoteDocumentCache.getAll(keys);

    List<MutationBatch> batches = mutationQueue.getAllMutationBatchesAffectingDocumentKeys(keys);
    // TODO(Overlay): This is running for all changed docs X all batches that might affect..pretty
    // wasteful.
    Map<DocumentKey, DocumentBaseAndFinal> localDocs =
        applyLocalMutationsToDocuments(existingDocs, batches);

    for (Map.Entry<DocumentKey, DocumentBaseAndFinal> entry : localDocs.entrySet()) {
      if (entry.getValue().finalDoc != null) {
        localDocumentCache.add((MutableDocument) entry.getValue().finalDoc);
      } else {
        localDocumentCache.remove(entry.getKey());
      }
    }
  }

  public LocalDocumentCache getLocalDocumentCache() {
    return this.localDocumentCache;
  }

  // TODO(Overlay): Better name, and better place.
  private static final class DocumentBaseAndFinal {
    MutableDocument baseDoc = null;
    MutableDocument finalDoc = null;

    MutableDocument getDocument() {
      return finalDoc == null ? baseDoc : finalDoc;
    }
  }

  /**
   * Populates the remote document cache with documents from backend or a bundle. Returns the
   * document changes resulting from applying those documents.
   *
   * <p>Note: this function will use `documentVersions` if it is defined. When it is not defined, it
   * resorts to `globalVersion`.
   *
   * @param docsToPopulate Documents to be applied.
   * @param documentVersions A DocumentKey-to-SnapshotVersion map if documents have their own read
   *     time.
   * @param globalVersion A SnapshotVersion representing the read time if all documents have the
   *     same read time.
   * @return A map representing the new view of the changed documents.
   */
  Map<DocumentKey, MutableDocument> populateDocumentChanges(
      Map<DocumentKey, MutableDocument> docsToPopulate,
      @Nullable Map<DocumentKey, SnapshotVersion> documentVersions,
      SnapshotVersion globalVersion) {
    Map<DocumentKey, MutableDocument> changedDocsView = new HashMap<>();

    // Each loop iteration only affects its "own" doc, so it's safe to get all the remote
    // documents in advance in a single call.
    Map<DocumentKey, MutableDocument> existingDocs =
        remoteDocumentCache.getAll(docsToPopulate.keySet());

    List<MutationBatch> batches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKeys(docsToPopulate.keySet());
    // TODO(Overlay): This is running for all changed docs X all batches that might affect..pretty
    // wasteful.
    Map<DocumentKey, DocumentBaseAndFinal> localDocs =
        applyLocalMutationsToDocuments(docsToPopulate, batches);

    for (Map.Entry<DocumentKey, DocumentBaseAndFinal> entry : localDocs.entrySet()) {
      DocumentKey key = entry.getKey();
      MutableDocument doc = entry.getValue().baseDoc;
      MutableDocument localDocView = entry.getValue().finalDoc;
      MutableDocument existingDoc = existingDocs.get(key);
      SnapshotVersion readTime =
          documentVersions != null ? documentVersions.get(key) : globalVersion;

      // Note: The order of the steps below is important, since we want to ensure that
      // rejected limbo resolutions (which fabricate NoDocuments with SnapshotVersion.NONE)
      // never add documents to cache.
      if (doc.isNoDocument() && doc.getVersion().equals(SnapshotVersion.NONE)) {
        // NoDocuments with SnapshotVersion.NONE are used in manufactured events. We remove
        // these documents from cache since we lost access.
        remoteDocumentCache.remove(doc.getKey());
        localDocumentCache.remove(doc.getKey());
        changedDocsView.put(key, doc);
      } else if (!existingDoc.isValidDocument()
          || doc.getVersion().compareTo(existingDoc.getVersion()) > 0
          || (doc.getVersion().compareTo(existingDoc.getVersion()) == 0
              && existingDoc.hasPendingWrites())) {
        hardAssert(
            !SnapshotVersion.NONE.equals(readTime),
            "Cannot add a document when the remote version is zero");
        remoteDocumentCache.add(doc, readTime);
        if (localDocView != null) {
          localDocumentCache.add(localDocView);
        } else {
          // TODO(Overlay): this is wrong. It should be when there are no mutations to the document.
          // The mutations can cancel each other, leading to localDocView == null, but it is still
          // inconsistent.
          localDocumentCache.remove(key);
        }

        changedDocsView.put(key, localDocView == null ? doc : localDocView);
      } else {
        Logger.debug(
            "LocalStore",
            "Ignoring outdated watch update for %s." + "Current version: %s  Watch version: %s",
            key,
            existingDoc.getVersion(),
            doc.getVersion());
      }
    }
    return changedDocsView;
  }

  void applyWriteToRemoteDocuments(MutationBatchResult batchResult) {
    MutationBatch batch = batchResult.getBatch();
    Set<DocumentKey> docKeys = batch.getKeys();
    mutationQueue.removeMutationBatch(batch);
    for (DocumentKey docKey : docKeys) {
      MutableDocument doc = remoteDocumentCache.get(docKey);
      SnapshotVersion ackVersion = batchResult.getDocVersions().get(docKey);
      hardAssert(ackVersion != null, "docVersions should contain every doc in the write.");

      if (doc.getVersion().compareTo(ackVersion) < 0) {
        batch.applyToRemoteDocument(doc, batchResult);
        if (doc.isValidDocument()) {
          remoteDocumentCache.add(doc, batchResult.getCommitVersion());
        }
        localDocumentCache.setMutationFlags(doc);
        if (doc.isUnknownDocument()) {
          localDocumentCache.add(doc);
        }
      } else {
        // TODO(Overlay): This is a hack to pass test. The proper way to solve the mutation flag
        // issue is probably
        // save a last-batch-id in the local document cache.
        // if last-batch-id==batchResult.id, we clear the flags.
        // if last-batch-id>batchResult.id, we add a `hasCommittedMutation`.
        // last-batch-id<batchResult.id is an error case.
        localDocumentCache.setMutationFlags(doc);
      }
    }

    // TODO(Overlay): Optimization:
    // 1. Scheduled a timed task to batch processing mutation acknowledgements..from remote store
    // probably.
    for (MutationResult result : batchResult.getMutationResults()) {
      if (result.getTransformResults() != null && !result.getTransformResults().isEmpty()) {
        repopulateCache(docKeys);
        break;
      }
    }
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
  ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
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
  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingDocumentQuery(
      ResourcePath path) {
    ImmutableSortedMap<DocumentKey, Document> result = emptyDocumentMap();
    // Just do a simple document lookup.
    Document doc = getDocument(DocumentKey.fromPath(path));
    if (doc.isFoundDocument()) {
      result = result.insert(doc.getKey(), doc);
    }
    return result;
  }

  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingCollectionGroupQuery(
      Query query, SnapshotVersion sinceReadTime) {
    hardAssert(
        query.getPath().isEmpty(),
        "Currently we only support collection group queries at the root.");
    String collectionId = query.getCollectionGroup();
    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();
    List<ResourcePath> parents = indexManager.getCollectionParents(collectionId);

    // Perform a collection query against each parent that contains the collectionId and
    // aggregate the results.
    for (ResourcePath parent : parents) {
      Query collectionQuery = query.asCollectionQueryAtPath(parent.append(collectionId));
      ImmutableSortedMap<DocumentKey, Document> collectionResults =
          getDocumentsMatchingCollectionQuery(collectionQuery, sinceReadTime);
      for (Map.Entry<DocumentKey, Document> docEntry : collectionResults) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
      }
    }
    return results;
  }

  /** Queries the remote documents and overlays mutations. */
  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingCollectionQuery(
      Query query, SnapshotVersion sinceReadTime) {
    ImmutableSortedMap<DocumentKey, MutableDocument> remoteDocuments =
        remoteDocumentCache.getAllDocumentsMatchingQuery(query, sinceReadTime);
    ImmutableSortedMap<DocumentKey, MutableDocument> localMatches =
        localDocumentCache.getAllDocumentsMatchingQuery(query);

    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();
    for (Map.Entry<DocumentKey, MutableDocument> localEntry : localMatches) {
      if (remoteDocuments.containsKey(localEntry.getKey())) {
        SnapshotVersion version = remoteDocuments.get(localEntry.getKey()).getVersion();
        if (localEntry.getValue().getVersion().compareTo(version) > 0) {
          version = localEntry.getValue().getVersion();
        }
        results = results.insert(localEntry.getKey(), localEntry.getValue().setVersion(version));
        remoteDocuments = remoteDocuments.remove(localEntry.getKey());
      } else {
        results = results.insert(localEntry.getKey(), localEntry.getValue());
      }
    }

    for (Map.Entry<DocumentKey, MutableDocument> docEntry : remoteDocuments) {
      results = results.insert(docEntry.getKey(), docEntry.getValue());
    }

    return results;
  }
}
