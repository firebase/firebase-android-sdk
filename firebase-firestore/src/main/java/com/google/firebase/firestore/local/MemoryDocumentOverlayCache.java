// Copyright 2021 Google LLC
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

import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import com.google.firebase.firestore.model.mutation.Overlay;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

public class MemoryDocumentOverlayCache implements DocumentOverlayCache {
  // A map sorted by DocumentKey, whose value is a pair of the largest batch id for the overlay
  // and the overlay itself.
  private final TreeMap<DocumentKey, Overlay> overlays = new TreeMap<>();
  private final Map<Integer, Set<DocumentKey>> overlayByBatchId = new HashMap<>();

  @Nullable
  @Override
  public Overlay getOverlay(DocumentKey key) {
    return overlays.get(key);
  }

  private void saveOverlay(int largestBatchId, @Nullable Mutation mutation) {
    if (mutation == null) {
      return;
    }

    // Remove the association of the overlay to its batch id.
    Overlay existing = this.overlays.get(mutation.getKey());
    if (existing != null) {
      overlayByBatchId.get(existing.getLargestBatchId()).remove(mutation.getKey());
    }

    overlays.put(mutation.getKey(), Overlay.create(largestBatchId, mutation));

    // Create the associate of this overlay to the given largestBatchId.
    if (overlayByBatchId.get(largestBatchId) == null) {
      overlayByBatchId.put(largestBatchId, new HashSet<>());
    }
    overlayByBatchId.get(largestBatchId).add(mutation.getKey());
  }

  @Override
  public void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays) {
    for (Map.Entry<DocumentKey, Mutation> entry : overlays.entrySet()) {
      saveOverlay(largestBatchId, entry.getValue());
    }
  }

  @Override
  public void removeOverlaysForBatchId(int batchId) {
    if (overlayByBatchId.containsKey(batchId)) {
      Set<DocumentKey> keys = overlayByBatchId.get(batchId);
      overlayByBatchId.remove(batchId);
      for (DocumentKey key : keys) {
        overlays.remove(key);
      }
    }
  }

  @Override
  public Map<DocumentKey, Overlay> getOverlays(ResourcePath collection, int sinceBatchId) {
    Map<DocumentKey, Overlay> result = new HashMap<>();

    int immediateChildrenPathLength = collection.length() + 1;
    DocumentKey prefix = DocumentKey.fromPath(collection.append(""));
    Map<DocumentKey, Overlay> view = overlays.tailMap(prefix);

    for (Overlay overlay : view.values()) {
      DocumentKey key = overlay.getKey();
      if (!collection.isPrefixOf(key.getPath())) {
        break;
      }
      // Documents from sub-collections
      if (key.getPath().length() != immediateChildrenPathLength) {
        continue;
      }

      if (overlay.getLargestBatchId() > sinceBatchId) {
        result.put(overlay.getKey(), overlay);
      }
    }

    return result;
  }

  @Override
  public Map<DocumentKey, Overlay> getOverlays(
      String collectionGroup, int sinceBatchId, int count) {
    SortedMap<Integer, Map<DocumentKey, Overlay>> batchIdToOverlays = new TreeMap<>();

    for (Overlay overlay : overlays.values()) {
      DocumentKey key = overlay.getKey();
      if (!key.getCollectionGroup().equals(collectionGroup)) {
        continue;
      }
      if (overlay.getLargestBatchId() > sinceBatchId) {
        Map<DocumentKey, Overlay> overlays = batchIdToOverlays.get(overlay.getLargestBatchId());
        if (overlays == null) {
          overlays = new HashMap<>();
          batchIdToOverlays.put(overlay.getLargestBatchId(), overlays);
        }
        overlays.put(overlay.getKey(), overlay);
      }
    }

    Map<DocumentKey, Overlay> result = new HashMap<>();
    for (Map<DocumentKey, Overlay> overlays : batchIdToOverlays.values()) {
      result.putAll(overlays);
      if (result.size() >= count) {
        break;
      }
    }

    return result;
  }
}
