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
import com.google.firebase.Timestamp;
import com.google.firebase.database.collection.ImmutableSortedMap;
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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeMap;
import java.util.TreeSet;

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
    Map<DocumentKey, Overlay> overlays = new HashMap<>();
    populateOverlays(overlays, docs.keySet());
    ImmutableSortedMap<DocumentKey, Document> result = emptyDocumentMap();
    for (Map.Entry<DocumentKey, OverlayedDocument> entry :
        computeViews(docs, overlays, existenceStateChanged).entrySet()) {
      result = result.insert(entry.getKey(), entry.getValue().getDocument());
    }

    return result;
  }

  /**
   * Gets the overlayed documents for the given document map, which will include the local view of
   * those documents and a {@code FieldMask} indicating which fields are mutated locally, null if
   * overlay is a Set or Delete mutation.
   *
   * @param docs The documents to apply local mutations to get the local views.
   */
  Map<DocumentKey, OverlayedDocument> getOverlayedDocuments(
      Map<DocumentKey, MutableDocument> docs) {
    Map<DocumentKey, Overlay> overlays = new HashMap<>();
    populateOverlays(overlays, docs.keySet());
    return computeViews(docs, overlays, new HashSet<>());
  }

  /**
   * Computes the local view for the given documents.
   *
   * @param docs The documents to compute views for. It also has the base version of the documents.
   * @param overlays The overlays that need to be applied to the given base version of the
   *     documents.
   * @param existenceStateChanged A set of documents whose existence states might have changed. This
   *     is used to determine if we need to re-calculate overlays from mutation queues.
   * @return A map represents the local documents view.
   */
  private Map<DocumentKey, OverlayedDocument> computeViews(
      Map<DocumentKey, MutableDocument> docs,
      Map<DocumentKey, Overlay> overlays,
      Set<DocumentKey> existenceStateChanged) {
    Map<DocumentKey, MutableDocument> recalculateDocuments = new HashMap<>();
    Map<DocumentKey, FieldMask> mutatedFields = new HashMap<>();
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

    Map<DocumentKey, FieldMask> recalculatedFields =
        recalculateAndSaveOverlays(recalculateDocuments);
    mutatedFields.putAll(recalculatedFields);

    Map<DocumentKey, OverlayedDocument> result = new HashMap<>();
    for (Map.Entry<DocumentKey, MutableDocument> entry : docs.entrySet()) {
      result.put(
          entry.getKey(),
          new OverlayedDocument(entry.getValue(), mutatedFields.get(entry.getKey())));
    }
    return result;
  }

  private Map<DocumentKey, FieldMask> recalculateAndSaveOverlays(
      Map<DocumentKey, MutableDocument> docs) {
    List<MutationBatch> batches =
        mutationQueue.getAllMutationBatchesAffectingDocumentKeys(docs.keySet());

    Map<DocumentKey, FieldMask> masks = new HashMap<>();
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
      Map<DocumentKey, Mutation> overlays = new HashMap<>();
      for (DocumentKey key : entry.getValue()) {
        if (!processed.contains(key)) {
          Mutation mutation = Mutation.calculateOverlayMutation(docs.get(key), masks.get(key));
          if (mutation != null) {
            overlays.put(key, mutation);
          }
          processed.add(key);
        }
      }
      documentOverlayCache.saveOverlays(entry.getKey(), overlays);
    }

    return masks;
  }

  /**
   * Recalculates overlays by reading the documents from remote document cache first, and save them
   * after they are calculated.
   */
  void recalculateAndSaveOverlays(Set<DocumentKey> documentKeys) {
    Map<DocumentKey, MutableDocument> docs = remoteDocumentCache.getAll(documentKeys);
    recalculateAndSaveOverlays(docs);
  }

  /**
   * Performs a query against the local view of all documents.
   *
   * @param query The query to match documents against.
   * @param offset Read time and key to start scanning by (exclusive).
   * @param context A optional tracker to keep a record of important details during database local
   *     query execution.
   */
  ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
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
   */
  ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query, IndexOffset offset) {
    return getDocumentsMatchingQuery(query, offset, /*context*/ null);
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
      Query query, IndexOffset offset, @Nullable QueryContext context) {
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
          getDocumentsMatchingCollectionQuery(collectionQuery, offset, context);
      for (Map.Entry<DocumentKey, Document> docEntry : collectionResults) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
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
    Map<DocumentKey, MutableDocument> docs =
        remoteDocumentCache.getAll(collectionGroup, offset, count);
    Map<DocumentKey, Overlay> overlays =
        count - docs.size() > 0
            ? documentOverlayCache.getOverlays(
                collectionGroup, offset.getLargestBatchId(), count - docs.size())
            : new HashMap<>();

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

    populateOverlays(overlays, docs.keySet());
    Map<DocumentKey, OverlayedDocument> localDocs =
        computeViews(docs, overlays, Collections.emptySet());
    return LocalDocumentsResult.fromOverlayedDocuments(largestBatchId, localDocs);
  }

  /**
   * Fetches the overlays for {@code keys} and adds them to provided overlay map if the map does not
   * already contain an entry for the given key.
   */
  private void populateOverlays(Map<DocumentKey, Overlay> overlays, Set<DocumentKey> keys) {
    SortedSet<DocumentKey> missingOverlays = new TreeSet<>();
    for (DocumentKey key : keys) {
      if (!overlays.containsKey(key)) {
        missingOverlays.add(key);
      }
    }
    overlays.putAll(documentOverlayCache.getOverlays(missingOverlays));
  }

  private ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingCollectionQuery(
      Query query, IndexOffset offset, @Nullable QueryContext context) {
    Map<DocumentKey, Overlay> overlays =
        documentOverlayCache.getOverlays(query.getPath(), offset.getLargestBatchId());
    Map<DocumentKey, MutableDocument> remoteDocuments =
        remoteDocumentCache.getDocumentsMatchingQuery(query, offset, overlays.keySet(), context);

    // As documents might match the query because of their overlay we need to include documents
    // for all overlays in the initial document set.
    for (Map.Entry<DocumentKey, Overlay> entry : overlays.entrySet()) {
      if (!remoteDocuments.containsKey(entry.getKey())) {
        remoteDocuments.put(entry.getKey(), MutableDocument.newInvalidDocument(entry.getKey()));
      }
    }

    // Apply the overlays and match against the query.
    ImmutableSortedMap<DocumentKey, Document> results = emptyDocumentMap();
    for (Map.Entry<DocumentKey, MutableDocument> docEntry : remoteDocuments.entrySet()) {
      Overlay overlay = overlays.get(docEntry.getKey());
      if (overlay != null) {
        overlay
            .getMutation()
            .applyToLocalView(docEntry.getValue(), FieldMask.EMPTY, Timestamp.now());
      }
      // Finally, insert the documents that still match the query
      if (query.matches(docEntry.getValue())) {
        results = results.insert(docEntry.getKey(), docEntry.getValue());
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
