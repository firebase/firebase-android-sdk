// Copyright 2019 Google LLC
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.database.collection.ImmutableSortedSet;
import com.google.firebase.firestore.core.Query;
import com.google.firebase.firestore.model.Document;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.FieldIndex.IndexOffset;
import com.google.firebase.firestore.model.MutableDocument;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.SnapshotVersion;
import com.google.firebase.firestore.model.mutation.DeleteMutation;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import com.google.firebase.firestore.model.mutation.PatchMutation;
import com.google.firebase.firestore.model.mutation.SetMutation;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;

/**
 * A test-only QueryEngine that forwards all API calls and exposes the number of documents and
 * mutations read.
 */
class CountingQueryEngine extends QueryEngine {
  enum OverlayType {
    Patch,
    Set,
    Delete
  }

  private final QueryEngine queryEngine;

  private final int[] overlaysReadByCollection = new int[] {0};
  private final int[] overlaysReadByKey = new int[] {0};
  private final Map<DocumentKey, OverlayType> overlayTypes = new HashMap();
  private final int[] documentsReadByCollection = new int[] {0};
  private final int[] documentsReadByKey = new int[] {0};

  CountingQueryEngine(QueryEngine queryEngine) {
    this.queryEngine = queryEngine;
  }

  void resetCounts() {
    overlaysReadByCollection[0] = 0;
    overlaysReadByKey[0] = 0;
    overlayTypes.clear();
    documentsReadByCollection[0] = 0;
    documentsReadByKey[0] = 0;
  }

  @Override
  public void initialize(LocalDocumentsView localDocuments, IndexManager indexManager) {
    LocalDocumentsView wrappedView =
        new LocalDocumentsView(
            wrapRemoteDocumentCache(localDocuments.getRemoteDocumentCache()),
            localDocuments.getMutationQueue(),
            wrapOverlayCache(localDocuments.getDocumentOverlayCache()),
            indexManager);
    queryEngine.initialize(wrappedView, indexManager);
  }

  @Override
  public ImmutableSortedMap<DocumentKey, Document> getDocumentsMatchingQuery(
      Query query,
      SnapshotVersion lastLimboFreeSnapshotVersion,
      ImmutableSortedSet<DocumentKey> remoteKeys) {
    return queryEngine.getDocumentsMatchingQuery(query, lastLimboFreeSnapshotVersion, remoteKeys);
  }

  @Override
  public void setIndexAutoCreationEnabled(boolean isEnabled) {
    queryEngine.setIndexAutoCreationEnabled(isEnabled);
  }

  @Override
  public void setIndexAutoCreationMinCollectionSize(int newMin) {
    queryEngine.setIndexAutoCreationMinCollectionSize(newMin);
  }

  @Override
  public void setRelativeIndexReadCostPerDocument(double newCost) {
    queryEngine.setRelativeIndexReadCostPerDocument(newCost);
  }

  /**
   * Returns the number of documents returned by the RemoteDocumentCache's `getAll()` API (since the
   * last call to `resetCounts()`)
   */
  int getDocumentsReadByCollection() {
    return documentsReadByCollection[0];
  }

  /**
   * Returns the number of documents returned by the RemoteDocumentCache's `getEntry()` and
   * `getEntries()` APIs (since the last call to `resetCounts()`)
   */
  int getDocumentsReadByKey() {
    return documentsReadByKey[0];
  }

  /**
   * Returns the number of mutations returned by the OverlayCache's `getOverlays()` API (since the
   * last call to `resetCounts()`)
   */
  int getOverlaysReadByCollection() {
    return overlaysReadByCollection[0];
  }

  /**
   * Returns the number of mutations returned by the OverlayCache's `getOverlay()` API (since the
   * last call to `resetCounts()`)
   */
  int getOverlaysReadByKey() {
    return overlaysReadByKey[0];
  }

  /**
   * Returns the types of overlay returned by the OverlayCahce's `getOverlays()` API (since the last
   * call to `resetCounts()`)
   */
  Map<DocumentKey, OverlayType> getOverlayTypes() {
    return Collections.unmodifiableMap(overlayTypes);
  }

  private RemoteDocumentCache wrapRemoteDocumentCache(RemoteDocumentCache subject) {
    return new RemoteDocumentCache() {
      @Override
      public void setIndexManager(IndexManager indexManager) {
        // Not implemented.
      }

      @Override
      public void add(MutableDocument document, SnapshotVersion readTime) {
        subject.add(document, readTime);
      }

      @Override
      public void removeAll(Collection<DocumentKey> keys) {
        subject.removeAll(keys);
      }

      @Override
      public MutableDocument get(DocumentKey documentKey) {
        MutableDocument result = subject.get(documentKey);
        documentsReadByKey[0] += result.isValidDocument() ? 1 : 0;
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(Iterable<DocumentKey> documentKeys) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(documentKeys);
        for (MutableDocument document : result.values()) {
          documentsReadByKey[0] += document.isValidDocument() ? 1 : 0;
        }
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getAll(
          String collectionGroup, IndexOffset offset, int limit) {
        Map<DocumentKey, MutableDocument> result = subject.getAll(collectionGroup, offset, limit);
        documentsReadByCollection[0] += result.size();
        return result;
      }

      @Override
      public Map<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
          Query query, IndexOffset offset, @NonNull Set<DocumentKey> mutatedKeys) {
        return getDocumentsMatchingQuery(query, offset, mutatedKeys, /*context*/ null);
      }

      @Override
      public Map<DocumentKey, MutableDocument> getDocumentsMatchingQuery(
          Query query,
          IndexOffset offset,
          @NonNull Set<DocumentKey> mutatedKeys,
          @Nullable QueryContext context) {
        Map<DocumentKey, MutableDocument> result =
            subject.getDocumentsMatchingQuery(query, offset, mutatedKeys, context);
        documentsReadByCollection[0] += result.size();
        return result;
      }
    };
  }

  private DocumentOverlayCache wrapOverlayCache(DocumentOverlayCache subject) {
    return new DocumentOverlayCache() {
      @Nullable
      @Override
      public Overlay getOverlay(DocumentKey key) {
        ++overlaysReadByKey[0];
        Overlay overlay = subject.getOverlay(key);
        overlayTypes.put(key, getOverlayType(overlay));
        return overlay;
      }

      public Map<DocumentKey, Overlay> getOverlays(SortedSet<DocumentKey> keys) {
        overlaysReadByKey[0] += keys.size();
        Map<DocumentKey, Overlay> overlays = subject.getOverlays(keys);
        for (Map.Entry<DocumentKey, Overlay> entry : overlays.entrySet()) {
          overlayTypes.put(entry.getKey(), getOverlayType(entry.getValue()));
        }

        return overlays;
      }

      @Override
      public void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays) {
        subject.saveOverlays(largestBatchId, overlays);
      }

      @Override
      public void removeOverlaysForBatchId(int batchId) {
        subject.removeOverlaysForBatchId(batchId);
      }

      @Override
      public Map<DocumentKey, Overlay> getOverlays(ResourcePath collection, int sinceBatchId) {
        Map<DocumentKey, Overlay> result = subject.getOverlays(collection, sinceBatchId);
        overlaysReadByCollection[0] += result.size();
        for (Map.Entry<DocumentKey, Overlay> entry : result.entrySet()) {
          overlayTypes.put(entry.getKey(), getOverlayType(entry.getValue()));
        }
        return result;
      }

      @Override
      public Map<DocumentKey, Overlay> getOverlays(
          String collectionGroup, int sinceBatchId, int count) {
        Map<DocumentKey, Overlay> result =
            subject.getOverlays(collectionGroup, sinceBatchId, count);
        overlaysReadByCollection[0] += result.size();
        for (Map.Entry<DocumentKey, Overlay> entry : result.entrySet()) {
          overlayTypes.put(entry.getKey(), getOverlayType(entry.getValue()));
        }
        return result;
      }

      private OverlayType getOverlayType(Overlay overlay) {
        if (overlay.getMutation() instanceof SetMutation) {
          return OverlayType.Set;
        } else if (overlay.getMutation() instanceof PatchMutation) {
          return OverlayType.Patch;
        } else if (overlay.getMutation() instanceof DeleteMutation) {
          return OverlayType.Delete;
        } else {
          throw new IllegalStateException("Overlay is a unrecognizable mutation.");
        }
      }
    };
  }
}
