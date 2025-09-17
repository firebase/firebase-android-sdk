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

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import com.google.firebase.Timestamp;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.FieldMask;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.MutationBatch;
import com.google.firebase.firestore.model.mutation.Overlay;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.util.ImmutableArrayList;
import com.google.firebase.firestore.util.ImmutableCollection;
import com.google.firebase.firestore.util.ImmutableCollections;
import com.google.firebase.firestore.util.ImmutableHashMap;
import com.google.firebase.firestore.util.ImmutableMap;
import com.google.firebase.firestore.util.ImmutableMaps;
import com.google.firebase.firestore.util.ImmutableSets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * A readonly view of the local state of all documents we're tracking (specifically, we have a
 * cached version in remoteDocumentCache or local mutations for the document). The view is computed
 * by applying the mutations in the MutationQueue to the RemoteDocumentCache.
 */
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

  /**
   * Returns the the local view of the document identified by {@code key}.
   *
   * @return Local view of the document or a an invalid document if we don't have any cached state
   *     for it.
   */
  Document getDocument(DocumentKey key) {
    Overlay overlay = documentOverlayCache.getOverlay(key);
    MutableDocument document = getBaseDocument(key, overlay);
    if (overlay != null) {
      overlay.getMutation().applyToLocalView(document, FieldMask.EMPTY, Timestamp.now());
    }
    return document;
  }

  /**
   * Gets the local view of the documents identified by {@code keys}.
   *
   * <p>If we don't have cached state for a document in {@code keys}, a NoDocument will be stored
   * for that key in the resulting set.
   *
   * @return a newly created {@link HashMap} containing the results.
   */
  HashMap<DocumentKey, Document> getDocuments(ImmutableCollection<DocumentKey> keys) {
    HashMap<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(keys);
    return getLocalViewOfDocuments(ImmutableMaps.adopt(docs), ImmutableCollections.empty());
  }

  /**
   * Similar to {@link #getDocuments}, but creates the local view from the given {@code baseDocs}
   * without retrieving documents from the local store.
   *
   * @param docs The documents to apply local mutations to get the local views.
   * @param existenceStateChanged The set of document keys whose existence state is changed. This is
   *     useful to determine if some documents overlay needs to be recalculated.
   * @return a newly created {@link HashMap} containing the results.
   */
  HashMap<DocumentKey, Document> getLocalViewOfDocuments(
      ImmutableMap<DocumentKey, MutableDocument> docs,
      ImmutableCollection<DocumentKey> existenceStateChanged) {
    ImmutableHashMap.Builder<DocumentKey, Overlay> overlays = new ImmutableHashMap.Builder<>();
    populateOverlays(overlays, docs.keySet());
    HashMap<DocumentKey, Document> result = new HashMap<>();
    for (Map.Entry<DocumentKey, OverlayedDocument> entry :
        computeViews(docs, overlays.build(), existenceStateChanged).entrySet()) {
      result.put(entry.getKey(), entry.getValue().getDocument());
    }
    return result;
  }

  /**
   * Gets the overlayed documents for the given document map, which will include the local view of
   * those documents and a {@code FieldMask} indicating which fields are mutated locally, null if
   * overlay is a Set or Delete mutation.
   *
   * @param docs The documents to apply local mutations to get the local views.
   * @return a newly created {@link HashMap} containing the results.
   */
  HashMap<DocumentKey, OverlayedDocument> getOverlayedDocuments(
      ImmutableMap<DocumentKey, MutableDocument> docs) {
    ImmutableHashMap.Builder<DocumentKey, Overlay> overlays = new ImmutableHashMap.Builder<>();
    populateOverlays(overlays, docs.keySet());
    return computeViews(docs, overlays.build(), ImmutableSets.empty());
  }

  /**
   * Computes the local view for the given documents.
   *
   * @param docs The documents to compute views for. It also has the base version of the documents.
   * @param overlays The overlays that need to be applied to the given base version of the
   *     documents.
   * @param existenceStateChanged A set of documents whose existence states might have changed. This
   *     is used to determine if we need to re-calculate overlays from mutation queues.
   * @return A newly-created {@link HashMap} that represents the local documents view.
   */
  private HashMap<DocumentKey, OverlayedDocument> computeViews(
      ImmutableMap<DocumentKey, MutableDocument> docs,
      ImmutableMap<DocumentKey, Overlay> overlays,
      ImmutableCollection<DocumentKey> existenceStateChanged) {
    ImmutableHashMap.Builder<DocumentKey, MutableDocument> recalculateDocuments =
        new ImmutableHashMap.Builder<>();
    HashMap<DocumentKey, FieldMask> mutatedFields = new HashMap<>();
    for (MutableDocument doc : docs.values()) {
      Overlay overlay = overlays.get(doc.getKey());

      // Recalculate an overlay if the document's existence state is changed due to a remote
      // event *and* the overlay is a PatchMutation. This is because document existence state
      // can change if some patch mutation's preconditions are met.
      // NOTE: we recalculate when `overlay` is null as well, because there might be a patch
      // mutation whose precondition does not match before the change (hence overlay==null),
      // but would now match.
      if (existenceStateChanged.contains(doc.getKey())
          && (overlay == null || overlay.getMutation() instanceof PatchMutation)) {
        recalculateDocuments.put(doc.getKey(), doc);
      } else if (overlay != null) {
        mutatedFields.put(doc.getKey(), overlay.getMutation().getFieldMask());
        overlay
            .getMutation()
            .applyToLocalView(doc, overlay.getMutation().getFieldMask(), Timestamp.now());
      } else { // overlay == null
        // Using EMPTY to indicate there is no overlay for the document.
        mutatedFields.put(doc.getKey(), FieldMask.EMPTY);
      }
    }

    HashMap<DocumentKey, FieldMask> recalculatedFields =
        recalculateAndSaveOverlays(recalculateDocuments.build());
    mutatedFields.putAll(recalculatedFields);

    HashMap<DocumentKey, OverlayedDocument> result = new HashMap<>();
    for (Map.Entry<DocumentKey, MutableDocument> entry : docs.entrySet()) {
      result.put(
          entry.getKey(),
          new OverlayedDocument(entry.getValue(), mutatedFields.get(entry.getKey())));
    }
    return result;
  }

  private HashMap<DocumentKey, FieldMask> recalculateAndSaveOverlays(
      ImmutableMap<DocumentKey, MutableDocument> docs) {
    List<MutationBatch> batches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKeys(docs.keySet());

    HashMap<DocumentKey, FieldMask> masks = new HashMap<>();
    // A reverse lookup map from batch id to the documents within that batch.
    TreeMap<Integer, Set<DocumentKey>> documentsByBatchId = new TreeMap<>();

    // Apply mutations from mutation queue to the documents, collecting batch id and field masks
    // along the way.
    for (MutationBatch batch : batches) {
      for (DocumentKey key : batch.getKeys()) {
        MutableDocument baseDoc = docs.get(key);
        if (baseDoc == null) {
          // If this batch has documents not included in passed in `docs`, skip them.
          continue;
        }

        FieldMask mask = masks.containsKey(key) ? masks.get(key) : FieldMask.EMPTY;
        mask = batch.applyToLocalView(baseDoc, mask);
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
      ImmutableHashMap.Builder<DocumentKey, Mutation> overlays = new ImmutableHashMap.Builder<>();
      for (DocumentKey key : entry.getValue()) {
        if (!processed.contains(key)) {
          Mutation mutation = Mutation.calculateOverlayMutation(docs.get(key), masks.get(key));
          if (mutation != null) {
            overlays.put(key, mutation);
          }
          processed.add(key);
        }
      }
      documentOverlayCache.saveOverlays(entry.getKey(), overlays.build());
    }

    return masks;
  }

  /**
   * Recalculates overlays by reading the documents from remote document cache first, and save them
   * after they are calculated.
   */
  void recalculateAndSaveOverlays(ImmutableCollection<DocumentKey> documentKeys) {
    HashMap<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(documentKeys);
    recalculateAndSaveOverlays(ImmutableHashMap.adopt(docs));
  }

  /**
   * Performs a query against the local view of all documents.
   *
   * @param query The query to match documents against.
   * @param offset Read time and key to start scanning by (exclusive).
   * @param context A optional tracker to keep a record of important details during database local
   *     query execution.
   * @return a newly created {@link HashMap} containing the results.
   */
  HashMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query, IndexOffset offset, @Nullable QueryContext context) {
    ResourcePath path = query.getPath();
    if (query.isDocumentQuery()) {
      return getDocumentsMatchingDocumentQuery(path);
    } else if (query.isCollectionGroupQuery()) {
      return getDocumentsMatchingCollectionGroupQuery(query, offset, context);
    } else {
      return getDocumentsMatchingCollectionQuery(query, offset, context);
    }
  }

  /**
   * Performs a query against the local view of all documents.
   *
   * @param query The query to match documents against.
   * @param offset Read time and key to start scanning by (exclusive).
   * @return a newly created {@link HashMap} containing the results.
   */
  HashMap<DocumentKey, Document> getDocumentsMatchingQuery(Query query, IndexOffset offset) {
    return getDocumentsMatchingQuery(query, offset, /*context*/ null);
  }

  /**
   * Performs a simple document lookup for the given path.
   * @return a newly created {@link HashMap} containing the results.
   */
  private HashMap<DocumentKey, Document> getDocumentsMatchingDocumentQuery(ResourcePath path) {
    HashMap<DocumentKey, Document> result = new HashMap<>();
    // Just do a simple document lookup.
    Document doc = getDocument(DocumentKey.fromPath(path));
    if (doc.isFoundDocument()) {
      result.put(doc.getKey(), doc);
    }
    return result;
  }

  private HashMap<DocumentKey, Document> getDocumentsMatchingCollectionGroupQuery(
      Query query, IndexOffset offset, @Nullable QueryContext context) {
    hardAssert(
        query.getPath().isEmpty(),
        "Currently we only support collection group queries at the root.");
    String collectionId = query.getCollectionGroup();
    HashMap<DocumentKey, Document> results = new HashMap<>();
    List<ResourcePath> parents = indexManager.getCollectionParents(collectionId);

    // Perform a collection query against each parent that contains the collectionId and
    // aggregate the results.
    for (ResourcePath parent : parents) {
      Query collectionQuery = query.asCollectionQueryAtPath(parent.append(collectionId));
      HashMap<DocumentKey, Document> collectionResults =
          getDocumentsMatchingCollectionQuery(collectionQuery, offset, context);
      for (Map.Entry<DocumentKey, Document> docEntry : collectionResults.entrySet()) {
        results.put(docEntry.getKey(), docEntry.getValue());
      }
    }
    return results;
  }

  /**
   * Given a collection group, returns the next documents that follow the provided offset, along
   * with an updated batch ID.
   *
   * <p>The documents returned by this method are ordered by remote version from the provided
   * offset. If there are no more remote documents after the provided offset, documents with
   * mutations in order of batch id from the offset are returned. Since all documents in a batch are
   * returned together, the total number of documents returned can exceed {@code count}.
   *
   * @param collectionGroup The collection group for the documents.
   * @param offset The offset to index into.
   * @param count The number of documents to return
   * @return A LocalDocumentsResult with the documents that follow the provided offset and the last
   *     processed batch id.
   */
  LocalDocumentsResult getNextDocuments(String collectionGroup, IndexOffset offset, int count) {
    ImmutableHashMap.Builder<DocumentKey, MutableDocument> docs =
        ImmutableHashMap.Builder.adopt(remoteDocumentCache.getAll(collectionGroup, offset, count));
    ImmutableHashMap.Builder<DocumentKey, Overlay> overlays =
        count - docs.size() > 0
            ? ImmutableHashMap.Builder.adopt(
                documentOverlayCache.getOverlays(
                    collectionGroup, offset.getLargestBatchId(), count - docs.size()))
            : new ImmutableHashMap.Builder<>();

    int largestBatchId = FieldIndex.INITIAL_LARGEST_BATCH_ID;
    for (Overlay overlay : overlays.values()) {
      if (!docs.containsKey(overlay.getKey())) {
        docs.put(overlay.getKey(), getBaseDocument(overlay.getKey(), overlay));
      }
      // The callsite will use the largest batch ID together with the latest read time to create
      // a new index offset. Since we only process batch IDs if all remote documents have been read,
      // no overlay will increase the overall read time. This is why we only need to special case
      // the batch id.
      largestBatchId = Math.max(largestBatchId, overlay.getLargestBatchId());
    }

    populateOverlays(overlays, docs.build().keySet());
    HashMap<DocumentKey, OverlayedDocument> localDocs =
        computeViews(docs.build(), overlays.build(), ImmutableCollections.empty());
    return LocalDocumentsResult.fromOverlayedDocuments(
        largestBatchId, ImmutableHashMap.adopt(localDocs));
  }

  /**
   * Fetches the overlays for {@code keys} and adds them to provided overlay map if the map does not
   * already contain an entry for the given key.
   */
  private void populateOverlays(
      ImmutableHashMap.Builder<DocumentKey, Overlay> overlays,
      ImmutableCollection<DocumentKey> keys) {
    ImmutableArrayList.Builder<DocumentKey> missingOverlays = new ImmutableArrayList.Builder<>();
    for (DocumentKey key : keys) {
      if (!overlays.containsKey(key)) {
        missingOverlays.add(key);
      }
    }
    overlays.putAll(documentOverlayCache.getOverlays(missingOverlays.build()));
  }

  private HashMap<DocumentKey, Document> getDocumentsMatchingCollectionQuery(
      Query query, IndexOffset offset, @Nullable QueryContext context) {
    HashMap<DocumentKey, Overlay> overlays =
        documentOverlayCache.getOverlays(query.getPath(), offset.getLargestBatchId());
    HashMap<DocumentKey, MutableDocument> remoteDocuments =
        remoteDocumentCache.getDocumentsMatchingQuery(
            query, offset, ImmutableCollections.adopt(overlays.keySet()), context);

    // As documents might match the query because of their overlay we need to include documents
    // for all overlays in the initial document set.
    for (Map.Entry<DocumentKey, Overlay> entry : overlays.entrySet()) {
      if (!remoteDocuments.containsKey(entry.getKey())) {
        remoteDocuments.put(entry.getKey(), MutableDocument.newInvalidDocument(entry.getKey()));
      }
    }

    // Apply the overlays and match against the query.
    HashMap<DocumentKey, Document> results = new HashMap();
    for (Map.Entry<DocumentKey, MutableDocument> docEntry : remoteDocuments.entrySet()) {
      Overlay overlay = overlays.get(docEntry.getKey());
      if (overlay != null) {
        overlay
            .getMutation()
            .applyToLocalView(docEntry.getValue(), FieldMask.EMPTY, Timestamp.now());
      }
      // Finally, insert the documents that still match the query
      if (query.matches(docEntry.getValue())) {
        results.put(docEntry.getKey(), docEntry.getValue());
      }
    }

    return results;
  }

  /** Returns a base document that can be used to apply `overlay`. */
  private MutableDocument getBaseDocument(DocumentKey key, @Nullable Overlay overlay) {
    return (overlay == null || overlay.getMutation() instanceof PatchMutation)
        ? remoteDocumentCache.get(key)
        : MutableDocument.newInvalidDocument(key);
  }
}
