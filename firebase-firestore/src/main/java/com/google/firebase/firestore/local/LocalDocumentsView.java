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
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A readonly view of the local state of all documents we're tracking (i.e. we have a cached version
 * in remoteDocumentCache or local mutations for the document). The view is computed by applying the
 * mutations in the MutationQueue to the RemoteDocumentCache.
 */
// TODO: Turn this into the UnifiedDocumentCache / whatever.
class LocalDocumentsView {

  private final RemoteDocumentCache remoteDocumentCache;
  private final MutationQueue mutationQueue;
  private final DocumentOverlayCache documentOverlayCache;
  private final IndexManager indexManager;

  LocalDocumentsView(
      RemoteDocumentCache remoteDocumentCache,
      MutationQueue mutationQueue,
      DocumentOverlayCache documentOverlayCache,
      IndexManager indexManager) {
    this.remoteDocumentCache = remoteDocumentCache;
    this.mutationQueue = mutationQueue;
    this.documentOverlayCache = documentOverlayCache;
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
  DocumentOverlayCache getDocumentOverlayCache() {
    return documentOverlayCache;
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
    if (Persistence.OVERLAY_SUPPORT_ENABLED) {
      Mutation overlay = documentOverlayCache.getOverlay(key);
      MutableDocument fromOverlay = remoteDocumentCache.get(key);
      if (overlay != null) {
        overlay.applyToLocalView(fromOverlay, null, Timestamp.now());
      }

      // TODO(Overlay): Remove below and just return `fromOverlay`.
      List<MutationBatch> batches = mutationQueue.getAllMutationBatchesAffectingDocumentKey(key);
      Document fromMutationQueue = getDocument(key, batches);
      hardAssert(
          fromOverlay.equals(fromMutationQueue),
          "Document from overlay does not match mutation queue");

      return fromOverlay;
    }
    List<MutationBatch> batches = mutationQueue.getAllMutationBatchesAffectingDocumentKey(key);
    return getDocument(key, batches);
  }

  // Internal version of {@code getDocument} that allows reusing batches.
  private Document getDocument(DocumentKey key, List<MutationBatch> inBatches) {
    MutableDocument document = remoteDocumentCache.get(key);
    for (MutationBatch batch : inBatches) {
      batch.applyToLocalView(document);
    }
    return document;
  }

  /**
   * Applies the given {@code batches} to the given {@code docs}. The docs are updated to reflect
   * the contents of the mutations.
   *
   * <p>Returns a {@link DocumentKey} to {@link FieldMask} map, representing the fields mutated for
   * each document. This is useful to build overlays.
   */
  private Map<DocumentKey, FieldMask> applyLocalMutationsToDocuments(
      Map<DocumentKey, MutableDocument> docs, List<MutationBatch> batches) {
    Map<DocumentKey, FieldMask> changedMasks = new HashMap<>();
    for (Map.Entry<DocumentKey, MutableDocument> base : docs.entrySet()) {
      FieldMask mask = null;
      for (MutationBatch batch : batches) {
        mask = batch.applyToLocalView(base.getValue(), mask);
      }
      changedMasks.put(base.getKey(), mask);
    }

    return changedMasks;
  }

  /**
   * Gets the local view of the documents identified by {@code keys}.
   *
   * <p>If we don't have cached state for a document in {@code keys}, a NoDocument will be stored
   * for that key in the resulting set.
   */
  ImmutableSortedMap<DocumentKey, Document> getDocuments(Iterable<DocumentKey> keys) {
    Map<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(keys);
    return getLocalViewOfDocuments(docs, new HashSet<>());
  }

  /**
   * Similar to {@link #getDocuments}, but creates the local view from the given {@code baseDocs}
   * without retrieving documents from the local store.
   *
   * @param docs The documents to apply local mutations to get the local views.
   * @param existenceStateChanged The set of document keys whose existence state is changed. This is
   *     useful to determine if some documents overlay needs to be recalculated.
   */
  ImmutableSortedMap<DocumentKey, Document> getLocalViewOfDocuments(
      Map<DocumentKey, MutableDocument> docs, Set<DocumentKey> existenceStateChanged) {
    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();
    if (Persistence.OVERLAY_SUPPORT_ENABLED) {
      Map<DocumentKey, MutableDocument> recalculateDocuments = new HashMap<>();
      for (Map.Entry<DocumentKey, MutableDocument> entry : docs.entrySet()) {
        Mutation overlay = documentOverlayCache.getOverlay(entry.getKey());
        // Recalculate an overlay if the document's existence state is changed due to a remote
        // event *and* the overlay is a PatchMutation. This is because document existence state
        // can change if some patch mutation's preconditions are met.
        // NOTE: we recalculate when `overlay` is null as well, because there might be a patch
        // mutation whose precondition does not match before the change (hence overlay==null),
        // but would now match.
        if (existenceStateChanged.contains(entry.getKey())
            && (overlay == null || overlay instanceof PatchMutation)) {
          recalculateDocuments.put(entry.getKey(), docs.get(entry.getKey()));
        } else if (overlay != null) {
          overlay.applyToLocalView(entry.getValue(), null, Timestamp.now());
        }
      }

      recalculateAndSaveOverlays(recalculateDocuments);
    } else {
      List<MutationBatch> batches =
          mutationQueue.getAllMutationBatchesAffectingDocumentKeys(docs.keySet());
      applyLocalMutationsToDocuments(docs, batches);
    }

    for (Map.Entry<DocumentKey, MutableDocument> entry : docs.entrySet()) {
      results = results.insert(entry.getKey(), entry.getValue());
    }
    return results;
  }

  private void recalculateAndSaveOverlays(Map<DocumentKey, MutableDocument> docs) {
    List<MutationBatch> batches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKeys(docs.keySet());

    Map<DocumentKey, FieldMask> masks = new HashMap<>();
    // A reverse lookup map from batch id to the documents within that batch.
    TreeMap<Integer, Set<DocumentKey>> documentsByBatchId = new TreeMap<>();

    // Apply mutations from mutation queue to the documents, collecting batch id and field masks
    // along the way.
    for (MutationBatch batch : batches) {
      for (DocumentKey key : batch.getKeys()) {
        FieldMask mask = batch.applyToLocalView(docs.get(key), masks.get(key));
        masks.put(key, mask);
        int batchId = batch.getBatchId();
        if (!documentsByBatchId.containsKey(batchId)) {
          documentsByBatchId.put(batchId, new HashSet<>());
        }
        documentsByBatchId.get(batchId).add(key);
      }
    }

    Set<DocumentKey> processed = new HashSet<>();
    // Iterate in descending order of batch ids, skip documents that are already saved.
    for (Map.Entry<Integer, Set<DocumentKey>> entry :
        documentsByBatchId.descendingMap().entrySet()) {
      Map<DocumentKey, Mutation> overlays = new HashMap<>();
      for (DocumentKey key : entry.getValue()) {
        if (!processed.contains(key)) {
          overlays.put(key, Mutation.calculateOverlayMutation(docs.get(key), masks.get(key)));
          processed.add(key);
        }
      }
      documentOverlayCache.saveOverlays(entry.getKey(), overlays);
    }
  }

  /**
   * Recalculates overlays by reading the documents from remote document cache first, and save them
   * after they are calculated.
   */
  void recalculateAndSaveOverlays(Set<DocumentKey> documentKeys) {
    Map<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(documentKeys);
    recalculateAndSaveOverlays(docs);
  }

  /** Gets the local view of the next {@code count} documents based on their read time. */
  ImmutableSortedMap<DocumentKey, Document> getDocuments(
      String collectionGroup, IndexOffset offset, int count) {
    Map<DocumentKey, MutableDocument> docs =
        remoteDocumentCache.getAll(collectionGroup, offset, count);
    return getLocalViewOfDocuments(docs, new HashSet<>());
  }

  /**
   * Performs a query against the local view of all documents.
   *
   * @param query The query to match documents against.
   * @param offset Read time and key to start scanning by (exclusive).
   */
  ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query, IndexOffset offset) {
    ResourcePath path = query.getPath();
    if (query.isDocumentQuery()) {
      return getDocumentsMatchingDocumentQuery(path);
    } else if (query.isCollectionGroupQuery()) {
      return getDocumentsMatchingCollectionGroupQuery(query, offset);
    } else {
      return getDocumentsMatchingCollectionQuery(query, offset);
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
      Query query, IndexOffset offset) {
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
          getDocumentsMatchingCollectionQuery(collectionQuery, offset);
      for (Map.Entry<DocumentKey, Document> docEntry : collectionResults) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
      }
    }
    return results;
  }

  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingCollectionQuery(
      Query query, IndexOffset offset) {
    if (Persistence.OVERLAY_SUPPORT_ENABLED) {
      // TODO(Overlay): Remove the assert and just return `fromOverlay`.
      ImmutableSortedMap<DocumentKey, Document> fromOverlay =
          getDocumentsMatchingCollectionQueryFromOverlayCache(query, offset);
      // TODO(Overlay): Delete below before merging. The code passes, but there are tests
      // looking at how many documents read from remote document, and this would double
      // the count.
      /*
      ImmutableSortedMap<DocumentKey, Document> fromMutationQueue =
          getDocumentsMatchingCollectionQueryFromMutationQueue(query, sinceReadTime);
      hardAssert(
          fromOverlay.equals(fromMutationQueue),
          "Documents from overlay do not match mutation queue version.");
       */
      return fromOverlay;
    } else {
      return getDocumentsMatchingCollectionQueryFromMutationQueue(query, offset);
    }
  }

  /** Queries the remote documents and overlays by doing a full collection scan. */
  private ImmutableSortedMap<DocumentKey, Document>
      getDocumentsMatchingCollectionQueryFromOverlayCache(Query query, IndexOffset offset) {
    ImmutableSortedMap<DocumentKey, MutableDocument> remoteDocuments =
        remoteDocumentCache.getAllDocumentsMatchingQuery(query, offset);
    Map<DocumentKey, Mutation> overlays = documentOverlayCache.getOverlays(query.getPath(), -1);

    // As documents might match the query because of their overlay we need to include all documents
    // in the result.
    Set<DocumentKey> missingDocuments = new HashSet<>();
    for (Map.Entry<DocumentKey, Mutation> entry : overlays.entrySet()) {
      if (!remoteDocuments.containsKey(entry.getKey())) {
        missingDocuments.add(entry.getKey());
      }
    }
    for (Map.Entry<DocumentKey, MutableDocument> entry :
        remoteDocumentCache.getAll(missingDocuments).entrySet()) {
      remoteDocuments = remoteDocuments.insert(entry.getKey(), entry.getValue());
    }

    // Apply the overlays and match against the query.
    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();
    for (Map.Entry<DocumentKey, MutableDocument> docEntry : remoteDocuments) {
      Mutation overlay = overlays.get(docEntry.getKey());
      if (overlay != null) {
        overlay.applyToLocalView(docEntry.getValue(), null, Timestamp.now());
      }
      // Finally, insert the documents that still match the query
      if (query.matches(docEntry.getValue())) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
      }
    }

    return results;
  }

  /** Queries the remote documents and mutation queue, by doing a full collection scan. */
  private ImmutableSortedMap<DocumentKey, Document>
      getDocumentsMatchingCollectionQueryFromMutationQueue(Query query, IndexOffset offset) {
    ImmutableSortedMap<DocumentKey, MutableDocument> remoteDocuments =
        remoteDocumentCache.getAllDocumentsMatchingQuery(query, offset);

    // TODO(indexing): We should plumb sinceReadTime through to the mutation queue
    List<MutationBatch> matchingBatches = mutationQueue.getAllMutationBatchesAffectingQuery(query);

    remoteDocuments = addMissingBaseDocuments(matchingBatches, remoteDocuments);

    for (MutationBatch batch : matchingBatches) {
      for (Mutation mutation : batch.getMutations()) {
        // Only process documents belonging to the collection.
        if (!query.getPath().isImmediateParentOf(mutation.getKey().getPath())) {
          continue;
        }

        DocumentKey key = mutation.getKey();
        MutableDocument document = remoteDocuments.get(key);
        if (document == null) {
          // Create invalid document to apply mutations on top of
          document = MutableDocument.newInvalidDocument(key);
          remoteDocuments = remoteDocuments.insert(key, document);
        }
        mutation.applyToLocalView(
            document, FieldMask.fromSet(new HashSet<>()), batch.getLocalWriteTime());
        if (!document.isFoundDocument()) {
          remoteDocuments = remoteDocuments.remove(key);
        }
      }
    }

    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();
    for (Map.Entry<DocumentKey, MutableDocument> docEntry : remoteDocuments) {
      // Finally, insert the documents that still match the query
      if (query.matches(docEntry.getValue())) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
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
      List<MutationBatch> matchingBatches,
      ImmutableSortedMap<DocumentKey, MutableDocument> existingDocs) {
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
