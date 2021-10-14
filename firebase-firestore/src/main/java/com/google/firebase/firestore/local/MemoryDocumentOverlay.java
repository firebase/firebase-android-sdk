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
import com.google.firebase.database.collection.ImmutableSortedMap;
import com.google.firebase.firestore.model.DocumentKey;
import com.google.firebase.firestore.model.ResourcePath;
import com.google.firebase.firestore.model.mutation.Mutation;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

public class MemoryDocumentOverlay implements DocumentOverlay {
  private ImmutableSortedMap<DocumentKey, Pair<Integer, Mutation>> overlays =
      ImmutableSortedMap.Builder.emptyMap(DocumentKey.comparator());
  Map<Integer, Set<DocumentKey>> overlayByBatchId = new HashMap<>();

  @Nullable
  @Override
  public Mutation getOverlay(DocumentKey key) {
    if (overlays.get(key) != null) {
      return overlays.get(key).second;
    }

    return null;
  }

  private void saveOverlay(int largestBatchId, DocumentKey key, @Nullable Mutation mutation) {
    if (mutation == null) {
      return;
    }

    int existingId = -1;
    Pair<Integer, Mutation> existing = this.overlays.get(key);
    if (existing != null) {
      existingId = existing.first;
    }
    overlays = overlays.insert(key, new Pair<>(largestBatchId, mutation));

    // {@code overlayByBatchId} maintenance.
    if (existingId >= 0) {
      overlayByBatchId.get(existingId).remove(key);
    }
    if (overlayByBatchId.get(largestBatchId) == null) {
      overlayByBatchId.put(largestBatchId, new HashSet<>());
    }
    overlayByBatchId.get(largestBatchId).add(key);
  }

  @Override
  public void saveOverlays(int largestBatchId, Map<DocumentKey, Mutation> overlays) {
    for (Map.Entry<DocumentKey, Mutation> entry : overlays.entrySet()) {
      saveOverlay(largestBatchId, entry.getKey(), entry.getValue());
    }
  }

  @Override
  public void removeOverlays(int batchId) {
    if (overlayByBatchId.containsKey(batchId)) {
      Set<DocumentKey> keys = overlayByBatchId.get(batchId);
      overlayByBatchId.remove(batchId);
      for (DocumentKey key : keys) {
        overlays = overlays.remove(key);
      }
    }
  }

  @Override
  public Map<DocumentKey, Mutation> getAllOverlays(ResourcePath collection) {
    Map<DocumentKey, Mutation> result = new HashMap<>();

    int immediateChildrenPathLength = path.length() + 1;
    DocumentKey prefix = DocumentKey.fromPath(path.append(""));
    Iterator<Map.Entry<DocumentKey, Pair<Integer, Mutation>>> iterator =
        overlays.iteratorFrom(prefix);
    while (iterator.hasNext()) {
      Map.Entry<DocumentKey, Pair<Integer, Mutation>> entry = iterator.next();

      DocumentKey key = entry.getKey();
      if (!path.isPrefixOf(key.getPath())) {
        break;
      }
      // Documents from sub-collections
      if (key.getPath().length() != immediateChildrenPathLength) {
        continue;
      }
      result.put(entry.getKey(), entry.getValue().second);
    }

    return result;
  }
}
