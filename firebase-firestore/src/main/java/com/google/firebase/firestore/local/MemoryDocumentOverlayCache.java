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

import android.util.Pair;
import androidx.annotation.Nullable;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class MemoryDocumentOverlayCache implements DocumentOverlayCache {
  // A map sorted by DocumentKey, whose value is a pair of the largest batch id for the overlay
  // and the overlay itself.
  private final TreeMap<DocumentKey, Pair<Integer, Mutation>> overlays = new TreeMap<>();
  private final Map<Integer, Set<DocumentKey>> overlayByBatchId = new HashMap<>();

  @Nullable
  @Override
  public Mutation getOverlay(DocumentKey key) {
    Pair<Integer, Mutation> overlay = overlays.get(key);
    if (overlay != null) {
      return overlay.second;
    }
    return null;
  }

  private void saveOverlay(int largestBatchId, @Nullable Mutation mutation) {
    if (mutation == null) {
      return;
    }

    // Remove the association of the overlay to its batch id.
    Pair<Integer, Mutation> existing = this.overlays.get(mutation.getKey());
    if (existing != null) {
      overlayByBatchId.get(existing.first).remove(mutation.getKey());
    }

    overlays.put(mutation.getKey(), new Pair<>(largestBatchId, mutation));

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
  public Map<DocumentKey, Mutation> getOverlays(ResourcePath collection, int sinceBatchId) {
    Map<DocumentKey, Mutation> result = new HashMap<>();

    int immediateChildrenPathLength = collection.length() + 1;
    DocumentKey prefix = DocumentKey.fromPath(collection.append(""));
    Map<DocumentKey, Pair<Integer, Mutation>> view = overlays.tailMap(prefix);

    for (Map.Entry<DocumentKey, Pair<Integer, Mutation>> entry : view.entrySet()) {
      DocumentKey key = entry.getKey();
      if (!collection.isPrefixOf(key.getPath())) {
        break;
      }
      // Documents from sub-collections
      if (key.getPath().length() != immediateChildrenPathLength) {
        continue;
      }

      Pair<Integer, Mutation> batchIdToOverlay = entry.getValue();
      if (batchIdToOverlay.first > sinceBatchId) {
        result.put(entry.getKey(), batchIdToOverlay.second);
      }
    }

    return result;
  }
}
